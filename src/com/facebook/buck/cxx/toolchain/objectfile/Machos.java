/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.facebook.buck.cxx.toolchain.objectfile;

import com.facebook.buck.core.filesystems.AbsPath;
import com.facebook.buck.util.ObjectFileCommonModificationDate;
import com.facebook.buck.util.nio.ByteBufferUnmapper;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.primitives.Ints;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public class Machos {

  // http://www.opensource.apple.com/source/xnu/xnu-1699.32.7/EXTERNAL_HEADERS/mach-o/loader.h
  // File magic
  static final byte[] MH_MAGIC = Ints.toByteArray(0xFEEDFACE);
  static final byte[] MH_MAGIC_64 = Ints.toByteArray(0xFEEDFACF);
  static final byte[] MH_CIGAM = Ints.toByteArray(0xCEFAEDFE);
  static final byte[] MH_CIGAM_64 = Ints.toByteArray(0xCFFAEDFE);
  // Map segment load command
  static final int LC_SEGMENT = 0x1;
  // Symbol table load command
  public static final int LC_SYMTAB = 0x2;
  // UUID load command
  static final int LC_UUID = 0x1B;
  // Map 64 bit segment load command
  static final int LC_SEGMENT_64 = 0x19;

  static final int LC_REQ_DYLD = 0x80000000;
  static final int LC_DYLD_INFO = 0x22;
  public static final int LC_DYLD_INFO_ONLY = (0x22 | LC_REQ_DYLD);

  // http://www.opensource.apple.com/source/xnu/xnu-1699.32.7/EXTERNAL_HEADERS/mach-o/stab.h
  // Description of object file STAB entries
  static final short N_OSO = (short) 0x66;

  static final String LINKEDIT = "__LINKEDIT";
  private static byte[] fakePathReplacement = "fake/path".getBytes();

  private static final int NO_VALUE_MARKER = -1;

  private Machos() {}

  /**
   * Extracts the UUID from a Mach-O file, if the file at the given path is not a Mach-O file, it
   * returns an empty optional.
   */
  public static Optional<String> getMachoUuid(AbsPath path) throws IOException {
    try (FileChannel file = FileChannel.open(path.getPath(), StandardOpenOption.READ)) {
      if (!Machos.isMacho(file)) {
        return Optional.empty();
      }

      try (ByteBufferUnmapper unmapper =
          // FileChannel.map() is limited to mapping a maximum of 2GiB, but that's more than enough
          // to read the file's load commands.
          ByteBufferUnmapper.createUnsafe(
              file.map(
                  FileChannel.MapMode.READ_ONLY, 0, Math.min(file.size(), Integer.MAX_VALUE)))) {

        try {
          Optional<byte[]> maybeUuid = Machos.getUuidIfPresent(unmapper.getByteBuffer());
          if (maybeUuid.isPresent()) {
            String hexBytes = ObjectFileScrubbers.bytesToHex(maybeUuid.get(), true);
            return Optional.of(hexBytes);
          }
        } catch (Machos.MachoException e) {
          // Even though it's a Mach-O file, we failed to read it safely
          throw new RuntimeException("Internal Mach-O file parsing failure");
        }

        return Optional.empty();
      }
    }
  }

  /**
   * Returns the UUID of the Mach-O file, if present. If it's not a Mach-O file, it throws a {@link
   * MachoException}. This method does not preserve the position of {@param fileBuffer}.
   */
  public static Optional<byte[]> getUuidIfPresent(ByteBuffer fileBuffer) throws MachoException {
    fileBuffer.position(0);
    MachoHeader header = getHeader(fileBuffer);

    // The caller may have truncated the Mach-O file if it's >= 2GiB. Verify the size of the file
    // header and load command data fit within that limit.
    if (header.getCommandsSize() > Integer.MAX_VALUE - fileBuffer.position()) {
      throw new RuntimeException("Cannot map all load commands");
    }

    for (int i = 0; i < header.getCommandsCount(); i++) {
      int commandType = ObjectFileScrubbers.getLittleEndianInt(fileBuffer);
      int commandTotalSize = ObjectFileScrubbers.getLittleEndianInt(fileBuffer);
      int commandTypeAndSizeByteLength = 4 /* command */ + 4 /* command size */;
      int payloadLength = (commandTotalSize - commandTypeAndSizeByteLength);
      byte[] payload = ObjectFileScrubbers.getBytes(fileBuffer, payloadLength);

      if (LC_UUID == commandType) {
        return Optional.of(payload);
      }
    }

    return Optional.empty();
  }

  static void setUuidIfPresent(ByteBuffer map, byte[] uuid) throws MachoException {
    int commandsCount = getHeader(map).getCommandsCount();

    for (int i = 0; i < commandsCount; i++) {
      int command = ObjectFileScrubbers.getLittleEndianInt(map);
      int commandSize = ObjectFileScrubbers.getLittleEndianInt(map);
      if (LC_UUID == command) {
        ObjectFileScrubbers.putBytes(map, uuid);
        return;
      } else {
        /* Command body */ ObjectFileScrubbers.getBytes(map, commandSize - 8);
      }
    }
  }

  /** Returns true if the given file is a Mach-O file. */
  public static boolean isMacho(FileChannel file) throws IOException {
    if (file.size() < MH_MAGIC.length) {
      return false;
    }

    byte[] magic = new byte[MH_MAGIC.length];
    ByteBuffer buffer = ByteBuffer.wrap(magic);
    file.read(buffer, 0);

    return Arrays.equals(MH_MAGIC, magic)
        || Arrays.equals(MH_CIGAM, magic)
        || Arrays.equals(MH_MAGIC_64, magic)
        || Arrays.equals(MH_CIGAM_64, magic);
  }

  /**
   * Relativize paths in OSO entries.
   *
   * <p>OSO entries point to other files containing debug information. These are generated by the
   * linker as absolute paths.
   */
  static void relativizeOsoSymbols(
      FileChannel file,
      Optional<ImmutableMap<Path, Path>> cellRoots,
      Optional<ImmutableSet<Path>> exemptPaths)
      throws IOException, MachoException {
    // We expect either cell roots or exempt paths be present, and only one of which should be
    // present.
    Preconditions.checkArgument(cellRoots.isPresent() ^ exemptPaths.isPresent());

    if (cellRoots.isPresent()) {
      cellRoots
          .get()
          .forEach(
              (from, to) -> {
                Preconditions.checkArgument(from.isAbsolute());
                Preconditions.checkArgument(!to.isAbsolute());
              });
    }

    long size = file.size();
    try (ByteBufferUnmapper unmapper =
        ByteBufferUnmapper.createUnsafe(file.map(FileChannel.MapMode.READ_WRITE, 0, size))) {
      ByteBuffer map = unmapper.getByteBuffer();

      MachoHeader header = getHeader(map);

      int symbolTableOffset = 0;
      int symbolTableCount = 0;
      int stringTableOffset = 0;
      int stringTableSizePosition = 0;
      int stringTableSize = 0;
      boolean symbolTableSegmentFound = false;
      int segmentSizePosition = 0;
      long segmentSize = 0;
      boolean linkEditSegmentFound = false;
      int segmentFileSizePosition = 0;
      int segment64FileSizePosition = 0;

      int commandsCount = header.getCommandsCount();
      for (int i = 0; i < commandsCount; i++) {
        int commandStart = map.position(); // NOPMD
        int command = ObjectFileScrubbers.getLittleEndianInt(map);
        int commandSize = ObjectFileScrubbers.getLittleEndianInt(map); // NOPMD
        switch (command) {
          case LC_SYMTAB:
            symbolTableOffset = ObjectFileScrubbers.getLittleEndianInt(map);
            symbolTableCount = ObjectFileScrubbers.getLittleEndianInt(map);
            stringTableOffset = ObjectFileScrubbers.getLittleEndianInt(map);
            stringTableSizePosition = map.position();
            stringTableSize = ObjectFileScrubbers.getLittleEndianInt(map);
            symbolTableSegmentFound = true;
            break;
          case LC_SEGMENT:
            byte[] segmentNameBytes = ObjectFileScrubbers.getBytes(map, 16);
            String segmentName = new String(segmentNameBytes, StandardCharsets.US_ASCII);
            if (segmentName.startsWith(LINKEDIT)) {
              linkEditSegmentFound = true;
              /* vm address */ ObjectFileScrubbers.getLittleEndianInt(map);
              /* vm size */ ObjectFileScrubbers.getLittleEndianInt(map);
              /* segment file offset */ ObjectFileScrubbers.getLittleEndianInt(map);
              segmentFileSizePosition = map.position();
              segmentSize = ObjectFileScrubbers.getLittleEndianInt(map);
              /* maximum vm protection */ ObjectFileScrubbers.getLittleEndianInt(map);
              /* initial vm protection */ ObjectFileScrubbers.getLittleEndianInt(map);
              /* number of sections */ ObjectFileScrubbers.getLittleEndianInt(map);
              /* flags */ ObjectFileScrubbers.getLittleEndianInt(map);

              if (segmentSizePosition != 0) {
                throw new MachoException("multiple map segment commands map string table");
              }
              segmentSizePosition = segmentFileSizePosition;
            }
            break;
          case LC_SEGMENT_64:
            byte[] segment64NameBytes = ObjectFileScrubbers.getBytes(map, 16);
            String segment64Name = new String(segment64NameBytes, StandardCharsets.US_ASCII);
            if (segment64Name.startsWith(LINKEDIT)) {
              linkEditSegmentFound = true;
              /* vm address */ ObjectFileScrubbers.getLittleEndianLong(map);
              /* vm size */ ObjectFileScrubbers.getLittleEndianLong(map);
              /* segment file offset */ ObjectFileScrubbers.getLittleEndianLong(map);
              segment64FileSizePosition = map.position();
              segmentSize = ObjectFileScrubbers.getLittleEndianLong(map);
              /* maximum vm protection */ ObjectFileScrubbers.getLittleEndianInt(map);
              /* initial vm protection */ ObjectFileScrubbers.getLittleEndianInt(map);
              /* number of sections */ ObjectFileScrubbers.getLittleEndianInt(map);
              /* flags */ ObjectFileScrubbers.getLittleEndianInt(map);

              if (segmentSizePosition != 0) {
                throw new MachoException("multiple map segment commands map string table");
              }
              segmentSizePosition = segment64FileSizePosition;
            }
            break;
        }
        map.position(commandStart + commandSize);
      }

      if (!linkEditSegmentFound) {
        /*The OSO entries are identified in segments named __LINKEDIT. If no segment is found with
        that name, there is nothing to scrub.*/
        return;
      }
      if (stringTableSize == 0 || symbolTableCount == 0) {
        return;
      }

      if (!isValidFilesize(header, segmentSize)) {
        throw new MachoException("32bit map segment file size too big");
      }

      if (!symbolTableSegmentFound) {
        throw new MachoException("LC_SYMTAB command not found");
      }
      if (stringTableOffset + stringTableSize != size) {
        throw new MachoException("String table does not end at end of file");
      }
      if (segmentSizePosition == 0 || segmentSize == 0) {
        throw new MachoException("LC_SEGMENT or LC_SEGMENT_64 command for string table not found");
      }

      // ld64 deliberately burns the first byte with the space character, so that zero is never a
      // valid string index and writes 0x00 at offset 1, so that it's always the empty string.
      // The code for this in ld64 is in LinkEditClassic.hpp (StringPoolAtom::StringPoolAtom).
      map.position(stringTableOffset);
      if (map.get() != 0x20) {
        throw new MachoException("First character in the string table is not a space");
      }
      if (map.get() != 0x00) {
        throw new MachoException("Second character in the string table is not a NUL");
      }
      int currentStringTableOffset = map.position();

      byte[] stringTableBytes = new byte[stringTableSize];
      map.position(stringTableOffset);
      map.get(stringTableBytes);

      map.position(symbolTableOffset);

      // NB: We need to rewrite the string table as it's not deterministic and it would break
      //     caching behavior. On the other hand, the symbol table order is deterministic.

      boolean is64bit = header.getIs64Bit();

      Optional<Map<byte[], byte[]>> replacementPathMap =
          cellRoots.map(Machos::generateReplacementMap);
      Optional<Set<byte[]>> exemptPathByteArraySet =
          exemptPaths.map(Machos::generateByteArraysFromPaths);

      IntIntMap strings = new IntIntMap4a(symbolTableCount, 0.75f, NO_VALUE_MARKER);
      for (int i = 0; i < symbolTableCount; i++) {
        // Each LC_SYMTAB entry consists of the following fields:
        // - String Index: 4 bytes (offset into the string table)
        // - Type: 1 byte
        // - Section: 1 byte
        // - Description: 2 bytes
        // - Value: 8 bytes on 64bit, 4 bytes on 32bit
        int stringTableIndexPosition = map.position();
        int stringTableIndex = ObjectFileScrubbers.getLittleEndianInt(map);
        byte type = map.get();

        if (stringTableIndex >= 2) {
          int newStringTableIndex = strings.get(stringTableIndex);
          if (newStringTableIndex == NO_VALUE_MARKER) {
            ByteBuffer charByteBuffer =
                ObjectFileScrubbers.getCharByteBuffer(stringTableBytes, stringTableIndex);

            if (type == N_OSO) {
              Optional<ByteBuffer> maybeRewrittenCharByteBuffer = Optional.empty();
              if (replacementPathMap.isPresent()) {
                maybeRewrittenCharByteBuffer =
                    tryRewritingMatchingPath(
                        stringTableBytes, stringTableIndex, replacementPathMap.get());
              } else if (exemptPathByteArraySet.isPresent()) {
                maybeRewrittenCharByteBuffer =
                    tryRewritingToFakePath(
                        stringTableBytes, stringTableIndex, exemptPathByteArraySet.get());
              }
              if (maybeRewrittenCharByteBuffer.isPresent()) {
                charByteBuffer = maybeRewrittenCharByteBuffer.get();
              }

              int valuePosition = stringTableIndexPosition + 8;
              map.position(valuePosition);
              int lastModifiedValue =
                  ObjectFileCommonModificationDate.COMMON_MODIFICATION_TIME_STAMP;
              if (is64bit) {
                ObjectFileScrubbers.putLittleEndianLong(map, lastModifiedValue);
              } else {
                ObjectFileScrubbers.putLittleEndianInt(map, lastModifiedValue);
              }
            }
            ObjectFileScrubbers.putCharByteBuffer(map, currentStringTableOffset, charByteBuffer);

            newStringTableIndex = currentStringTableOffset - stringTableOffset;
            strings.put(stringTableIndex, newStringTableIndex);

            currentStringTableOffset = map.position();
          }
          map.position(stringTableIndexPosition);
          ObjectFileScrubbers.putLittleEndianInt(map, newStringTableIndex);
        }

        int symtabEntrySize = 4 + 1 + 1 + 2 + (is64bit ? 8 : 4);
        int nextSymtabEntryOffset = stringTableIndexPosition + symtabEntrySize;
        map.position(nextSymtabEntryOffset);
      }

      map.position(stringTableSizePosition);
      int newStringTableSize = currentStringTableOffset - stringTableOffset;
      ObjectFileScrubbers.putLittleEndianInt(map, newStringTableSize);

      map.position(segmentSizePosition);
      long newSize = segmentSize + (newStringTableSize - stringTableSize);
      if (isValidFilesize(header, newSize)) {
        if (header.getIs64Bit()) {
          ObjectFileScrubbers.putLittleEndianLong(map, newSize);
        } else {
          ObjectFileScrubbers.putLittleEndianInt(map, (int) newSize);
        }
      } else {
        throw new MachoException("32bit scrubbed map segment file size too big");
      }

      file.truncate(currentStringTableOffset);
    }
  }

  private static boolean isValidFilesize(MachoHeader header, long filesize) {
    return (header.getIs64Bit() || filesize <= Integer.MAX_VALUE);
  }

  /** Returns the Mach-O header provided the file is Mach-O, otherwise throws an exception. */
  protected static MachoHeader getHeader(ByteBuffer map) throws MachoException {
    byte[] magic = ObjectFileScrubbers.getBytes(map, MH_MAGIC.length);
    boolean is64bit;
    if (Arrays.equals(MH_MAGIC, magic) || Arrays.equals(MH_CIGAM, magic)) {
      is64bit = false;
    } else if (Arrays.equals(MH_MAGIC_64, magic) || Arrays.equals(MH_CIGAM_64, magic)) {
      is64bit = true;
    } else {
      throw new MachoException("invalid Mach-O magic");
    }

    /* CPU type */
    ObjectFileScrubbers.getLittleEndianInt(map);
    /* CPU subtype */
    ObjectFileScrubbers.getLittleEndianInt(map);
    /* File type */
    ObjectFileScrubbers.getLittleEndianInt(map);
    int commandsCount = ObjectFileScrubbers.getLittleEndianInt(map);
    long commandsSize = Integer.toUnsignedLong(ObjectFileScrubbers.getLittleEndianInt(map));
    /* Flags */
    ObjectFileScrubbers.getLittleEndianInt(map);
    if (is64bit) {
      /* reserved */ ObjectFileScrubbers.getLittleEndianInt(map);
    }
    return ImmutableMachoHeader.ofImpl(commandsCount, commandsSize, is64bit);
  }

  public static class MachoException extends Exception {
    public MachoException(String msg) {
      super(msg);
    }
  }

  /**
   * Prepares a replacement map for prefixes. For example, if {@p pathMap} had two entries: 1.
   * "/path/to/repo" -> "", 2. "/path/to/repo/cell" -> "cell"
   *
   * <p>The resulting map would contain: 1. "/path/to/repo/" -> "./" 2. "/path/to/repo/cell/" ->
   * "cell/"
   *
   * <p>The above means we can do very simple (and fast!) search & replace.
   */
  public static Map<byte[], byte[]> generateReplacementMap(Map<Path, Path> pathMap) {
    // Preprocess the input map once, so we can be efficient with byte[] arrays instead of Strings
    Map<byte[], byte[]> replacementMap = new HashMap<>();
    for (Map.Entry<Path, Path> pathEntry : pathMap.entrySet()) {
      String searchPrefix = pathEntry.getKey() + "/";

      String replacementPrefix = pathEntry.getValue().toString();
      if (replacementPrefix.isEmpty()) {
        replacementPrefix = ".";
      }
      replacementPrefix = replacementPrefix + "/";

      byte[] searchPrefixBytes = searchPrefix.getBytes(StandardCharsets.UTF_8);
      byte[] replacementBytes = replacementPrefix.getBytes(StandardCharsets.UTF_8);

      if (replacementBytes.length > searchPrefixBytes.length) {
        throw new IllegalStateException(
            "Relativization should shorten paths, not lengthen. Prefix="
                + searchPrefix
                + "; replacement="
                + replacementPrefix);
      }

      replacementMap.put(searchPrefixBytes, replacementBytes);
    }

    return replacementMap;
  }

  private static Set<byte[]> generateByteArraysFromPaths(Set<Path> paths) {
    return paths.stream()
        .map(path -> path.toString().getBytes(StandardCharsets.UTF_8))
        .collect(Collectors.toSet());
  }

  /**
   * Checks whether a string matches a prefix and returns a rewritten copy if that's the case. For
   * example, given a string "/Users/fb/repo/cell" and a replacement map containing
   * "/Users/fb/repo/" -> "./", it will return a ByteBuffer wrapping "./cell".
   *
   * <p>NB: This is a perf sensitive method.
   *
   * @param stringBytes the byte array of the string to be rewritten.
   * @param stringOffset the offset in the array starting from which the string will be rewritten.
   * @param replacementMap the map on how we'll rewrite the string.
   * @return ByteBuffer containing the rewritten string.
   */
  public static Optional<ByteBuffer> tryRewritingMatchingPath(
      byte[] stringBytes, int stringOffset, Map<byte[], byte[]> replacementMap) {
    int nullCharOffset = stringOffset;
    while (stringBytes[nullCharOffset] != 0x0) {
      ++nullCharOffset;
    }

    int stringLength = nullCharOffset - stringOffset;
    for (Map.Entry<byte[], byte[]> replacementEntry : replacementMap.entrySet()) {
      byte[] searchPrefix = replacementEntry.getKey();
      if (bytesStartsWith(stringBytes, stringOffset, searchPrefix)) {
        // stringBytes variable:
        //
        //  stringOffset
        //       |
        //       v
        //       +------------------+---------------+
        //       |   searchPrefix   |    suffix     |
        //       +------------------+---------------+
        //                          ^
        //                          |
        //                     suffixOffset
        //
        // replacement variable:
        //
        //       0
        //       |
        //       v
        //       +-------------+---------------+
        //       | replacement |    suffix     |
        //       +-------------+---------------+
        //                     ^
        //                     |
        //             replacement.length

        byte[] replacement = replacementEntry.getValue();

        int suffixLength = stringLength - searchPrefix.length;
        int suffixOffset = stringOffset + searchPrefix.length;

        byte[] rewrittenPath = new byte[replacement.length + suffixLength];
        System.arraycopy(replacement, 0, rewrittenPath, 0, replacement.length);
        System.arraycopy(
            stringBytes, suffixOffset, rewrittenPath, replacement.length, suffixLength);

        return Optional.of(ByteBuffer.wrap(rewrittenPath));
      }
    }

    return Optional.empty();
  }

  /**
   * Rewrites the path into a fake path unless it matches any of the prefixes specified in
   * exemptPathByteArraySet.
   *
   * <p>NB: This is also a perf sensitive method.
   *
   * @param stringBytes the byte array of the string to be rewritten.
   * @param stringOffset the offset in the array starting from which the string will be rewritten.
   * @param exemptPathByteArraySet If the byte array starts with entries in this set, the rewrite
   *     won't happen.
   * @return ByteBuffer containing the rewritten string.
   */
  public static Optional<ByteBuffer> tryRewritingToFakePath(
      byte[] stringBytes, int stringOffset, Set<byte[]> exemptPathByteArraySet) {

    if (bytesStartsWithExemptPath(stringBytes, stringOffset, exemptPathByteArraySet)) {
      return Optional.empty();
    }

    return Optional.of(ByteBuffer.wrap(fakePathReplacement));
  }

  private static boolean bytesStartsWithExemptPath(
      byte[] stringBytes, int stringOffset, Set<byte[]> exemptPathByteArraySet) {
    for (byte[] exemptPath : exemptPathByteArraySet) {
      if (bytesStartsWith(stringBytes, stringOffset, exemptPath)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Checks whether {@code needle} can be found in {@code haystack} starting at {@code
   * haystackOffset}.
   */
  public static boolean bytesStartsWith(byte[] haystack, int haystackOffset, byte[] needle) {
    return bytesStartsWith(haystack, haystackOffset, needle, 0, needle.length);
  }

  /**
   * Checks whether a subrange of {@code needle}, starting at {@code needleOffset} of length {@code
   * needleLength}, can be found in {@code haystack} starting at {@code haystackOffset}.
   */
  public static boolean bytesStartsWith(
      byte[] haystack, int haystackOffset, byte[] needle, int needleOffset, int needleLength) {
    Preconditions.checkState(needleOffset >= 0 && haystackOffset >= 0);
    Preconditions.checkState(needleOffset + needleLength <= needle.length);

    if (haystackOffset + needleLength > haystack.length) {
      return false;
    }

    for (int i = 0; i < needleLength; i++) {
      if (haystack[haystackOffset + i] != needle[needleOffset + i]) {
        return false;
      }
    }

    return true;
  }
}

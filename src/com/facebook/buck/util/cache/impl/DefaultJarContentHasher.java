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

package com.facebook.buck.util.cache.impl;

import com.facebook.buck.core.util.log.Logger;
import com.facebook.buck.io.filesystem.ProjectFilesystem;
import com.facebook.buck.util.cache.HashCodeAndFileType;
import com.facebook.buck.util.cache.JarContentHasher;
import com.facebook.buck.util.zip.CustomJarOutputStream;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.common.hash.HashCode;
import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;
import com.google.common.hash.HashingInputStream;
import com.google.common.io.ByteStreams;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarInputStream;
import java.util.jar.Manifest;

class DefaultJarContentHasher implements JarContentHasher {

  private static final Logger LOG = Logger.get(DefaultJarContentHasher.class);
  private static final HashFunction HASH_FUNCTION = Hashing.murmur3_128();

  private final ProjectFilesystem filesystem;
  private final Path jarRelativePath;

  public DefaultJarContentHasher(ProjectFilesystem filesystem, Path jarRelativePath) {
    Preconditions.checkState(!jarRelativePath.isAbsolute());
    this.filesystem = filesystem;
    this.jarRelativePath = jarRelativePath;
  }

  @Override
  public Path getJarRelativePath() {
    return jarRelativePath;
  }

  @Override
  public ImmutableMap<String, HashCodeAndFileType> getContentHashes() throws IOException {
    Manifest manifest = getManifest();
    ImmutableMap.Builder<String, HashCodeAndFileType> builder = ImmutableMap.builder();
    for (Map.Entry<String, Attributes> nameAttributesEntry : manifest.getEntries().entrySet()) {
      Attributes attributes = nameAttributesEntry.getValue();
      String hashStringValue = attributes.getValue(CustomJarOutputStream.DIGEST_ATTRIBUTE_NAME);
      if (hashStringValue == null) {
        continue;
      }

      HashCode memberHash = HashCode.fromString(hashStringValue);
      HashCodeAndFileType memberHashCodeAndFileType = HashCodeAndFileType.ofFile(memberHash);

      // Need to invoke Paths.get because on windows the Path element will have `\` but on *nix/mac
      // it will be '/'. The manifest entry in the zip will always be `/`
      builder.put(Paths.get(nameAttributesEntry.getKey()).toString(), memberHashCodeAndFileType);
    }

    return builder.build();
  }

  private Manifest getManifest() throws IOException {
    Manifest manifest;
    try (JarInputStream inputStream =
        new JarInputStream(filesystem.newFileInputStream(jarRelativePath))) {
      manifest = inputStream.getManifest();
      JarEntry entry = inputStream.getNextJarEntry();
      while (manifest == null && entry != null) {
        if (JarFile.MANIFEST_NAME.equalsIgnoreCase(entry.getName())) {
          manifest = new Manifest();
          manifest.read(inputStream);
          break;
        }
        entry = inputStream.getNextJarEntry();
      }
    }
    if (manifest == null) {
      manifest = new Manifest();
      LOG.warn(jarRelativePath + " does not contain " + JarFile.MANIFEST_NAME);
    } else if (manifest.getEntries().isEmpty()) {
      LOG.warn(jarRelativePath + " does not contain entry hashes in its " + JarFile.MANIFEST_NAME);
    }

    if (manifest.getEntries().isEmpty()) {
      LOG.warn(
          "Generating "
              + CustomJarOutputStream.DIGEST_ATTRIBUTE_NAME
              + " for each file in "
              + jarRelativePath);
      try (JarInputStream inputStream =
          new JarInputStream(filesystem.newFileInputStream(jarRelativePath))) {
        JarEntry entry = inputStream.getNextJarEntry();
        while (entry != null) {
          Attributes attrs = new Attributes(1);
          attrs.put(
              new Attributes.Name(CustomJarOutputStream.DIGEST_ATTRIBUTE_NAME),
              hashEntry(inputStream));
          manifest.getEntries().put(entry.getName(), attrs);
          entry = inputStream.getNextJarEntry();
        }
      }
    }
    return manifest;
  }

  private String hashEntry(JarInputStream inputStream) throws IOException {
    HashingInputStream is = new HashingInputStream(HASH_FUNCTION, inputStream);
    ByteStreams.exhaust(is);
    return is.hash().toString();
  }
}

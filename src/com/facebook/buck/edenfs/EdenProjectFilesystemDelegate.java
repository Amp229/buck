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

package com.facebook.buck.edenfs;

import com.facebook.buck.core.filesystems.AbsPath;
import com.facebook.buck.core.filesystems.ForwardRelPath;
import com.facebook.buck.core.filesystems.RelPath;
import com.facebook.buck.core.util.log.Logger;
import com.facebook.buck.io.filesystem.ProjectFilesystemDelegate;
import com.facebook.buck.io.watchman.FileSystemNotWatchedException;
import com.facebook.buck.io.watchman.ProjectWatch;
import com.facebook.buck.io.watchman.WatchRoot;
import com.facebook.buck.io.watchman.Watchman;
import com.facebook.buck.io.watchman.WatchmanClient;
import com.facebook.buck.io.watchman.WatchmanError;
import com.facebook.buck.io.watchman.WatchmanFactory;
import com.facebook.buck.io.watchman.WatchmanQueryFailedException;
import com.facebook.buck.skylark.io.impl.WatchmanGlobber;
import com.facebook.buck.util.config.Config;
import com.facebook.buck.util.sha1.Sha1HashCode;
import com.facebook.eden.thrift.EdenError;
import com.facebook.thrift.TException;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.UserDefinedFileAttributeView;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Map;
import java.util.Optional;
import javax.annotation.Nullable;

public final class EdenProjectFilesystemDelegate implements ProjectFilesystemDelegate {

  private static final Logger LOG = Logger.get(EdenProjectFilesystemDelegate.class);

  /** Method to get sha1 for a path. */
  @VisibleForTesting
  enum Sha1Hasher {
    XATTR,
    WATCHMAN,
    EDEN_THRIFT
  }

  /**
   * Config option in the {@code [eden]} section of {@code .buckconfig} to disable going through
   * Eden's Thrift API to get the SHA-1 of a file. This defaults to {@code false}. This should be
   * tweaked during profiling to confirm that going through Eden's Thrift API is more efficient.
   */
  private static final String BUCKCONFIG_DISABLE_SHA1_FAST_PATH = "disable_sha1_fast_path";

  private static final String BUCKCONFIG_USE_XATTR_FOR_SHA1 = "use_xattr";
  private static final String BUCKCONFIG_USE_WATCHMAN_CONTENT_SHA1 = "use_watchman_content_sha1";
  private static final String BUCKCONFIG_SHA1_HASHER = "sha1_hasher";
  private static final String WATCHMAN_CONTENT_SHA1_FIELD = "content.sha1hex";
  private static final int SHA1_HEX_LENGTH = 40;

  private final EdenMount mount;

  /** Delegate to forward requests to for files that are outside of the {@link #mount}. */
  private final ProjectFilesystemDelegate delegate;

  private final boolean disableSha1FastPath;

  private final Sha1Hasher sha1Hasher;
  private final Watchman watchman;
  private final AbsPath rootPath;

  @VisibleForTesting
  static Sha1Hasher getSha1HasherFromConfig(Config config) {
    Optional<Boolean> use_xattr_for_sha1 = config.getBoolean("eden", BUCKCONFIG_USE_XATTR_FOR_SHA1);
    if (use_xattr_for_sha1.isPresent() && use_xattr_for_sha1.get()) {
      return Sha1Hasher.XATTR;
    }

    Optional<Boolean> use_watchman_for_sha1 =
        config.getBoolean("eden", BUCKCONFIG_USE_WATCHMAN_CONTENT_SHA1);
    if (use_watchman_for_sha1.isPresent() && use_watchman_for_sha1.get()) {
      return Sha1Hasher.WATCHMAN;
    }

    if (use_xattr_for_sha1.isPresent()
        && !use_xattr_for_sha1.get()
        && use_watchman_for_sha1.isPresent()
        && !use_watchman_for_sha1.get()) {
      return Sha1Hasher.EDEN_THRIFT;
    }
    return config
        .getEnum("eden", BUCKCONFIG_SHA1_HASHER, Sha1Hasher.class)
        .orElse(Sha1Hasher.XATTR);
  }

  public EdenProjectFilesystemDelegate(
      EdenMount mount,
      ProjectFilesystemDelegate delegate,
      Config config,
      Watchman watchman,
      AbsPath rootPath) {
    this(
        mount,
        delegate,
        config.getBooleanValue("eden", BUCKCONFIG_DISABLE_SHA1_FAST_PATH, false),
        getSha1HasherFromConfig(config),
        watchman,
        rootPath);
  }

  @VisibleForTesting
  EdenProjectFilesystemDelegate(
      EdenMount mount, ProjectFilesystemDelegate delegate, AbsPath rootPath) {
    this(
        mount,
        delegate, /* disableSha1FastPath */
        false,
        Sha1Hasher.EDEN_THRIFT,
        new WatchmanFactory.NullWatchman("test", WatchmanError.TEST),
        rootPath);
  }

  private EdenProjectFilesystemDelegate(
      EdenMount mount,
      ProjectFilesystemDelegate delegate,
      boolean disableSha1FastPath,
      Sha1Hasher sha1Hasher,
      Watchman watchman,
      AbsPath rootPath) {
    this.mount = mount;
    this.delegate = delegate;
    this.disableSha1FastPath = disableSha1FastPath;
    this.sha1Hasher = sha1Hasher;
    this.watchman = watchman;
    this.rootPath = rootPath;
    if (!(watchman instanceof WatchmanFactory.NullWatchman)) {
      ProjectWatch watch = watchman.getProjectWatches().get(rootPath);
      if (watch == null) {
        String msg =
            String.format(
                "Path [%s] is not watched. The list of watched project: [%s]",
                rootPath, watchman.getProjectWatches().keySet());
        throw new FileSystemNotWatchedException(msg);
      }
    }
  }

  @Override
  public ImmutableMap<String, ? extends Object> getDetailsForLogging() {
    return ImmutableMap.<String, Object>builder()
        .put("filesystem", "eden")
        .put("eden_filesystem", true)
        .put("eden_mountpoint", mount.getProjectRoot().toString())
        .put("eden_disablesha1fastpath", disableSha1FastPath)
        .build();
  }

  @Override
  public Sha1HashCode computeSha1(Path pathRelativeToProjectRootOrJustAbsolute) throws IOException {
    AbsPath fileToHash = getPathForRelativePath(pathRelativeToProjectRootOrJustAbsolute);
    return computeSha1(fileToHash, /* retryWithRealPathIfEdenError */ true);
  }

  private Sha1HashCode computeSha1(AbsPath path, boolean retryWithRealPathIfEdenError)
      throws IOException {
    if (disableSha1FastPath) {
      return delegate.computeSha1(path.getPath());
    }

    Optional<Sha1HashCode> ret = Optional.empty();
    switch (this.sha1Hasher) {
      case XATTR:
        ret = computeSha1ViaXAttr(path);
        break;
      case WATCHMAN:
        ret = computeSha1ViaWatchman(path, retryWithRealPathIfEdenError);
        break;
      case EDEN_THRIFT:
        ret = computeSha1ViaThrift(path, retryWithRealPathIfEdenError);
        break;
      default:
        throw new AssertionError("unreachable");
    }

    return ret.isPresent() ? ret.get() : delegate.computeSha1(path.getPath());
  }

  private Optional<Sha1HashCode> computeSha1ViaXAttr(AbsPath path) throws IOException {
    try {
      if (path.getFileSystem().supportedFileAttributeViews().contains("user")) {
        UserDefinedFileAttributeView view =
            Files.getFileAttributeView(path.getPath(), UserDefinedFileAttributeView.class);
        if (view != null) {
          // Eden returns the SHA1 as a UTF-8 encoded hexadecimal string
          ByteBuffer buf = ByteBuffer.allocate(SHA1_HEX_LENGTH);
          view.read("sha1", buf);
          buf.position(0);
          CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder();
          decoder.onUnmappableCharacter(CodingErrorAction.REPORT);
          try {
            return Optional.of(Sha1HashCode.of(decoder.decode(buf).toString()));
          } catch (CharacterCodingException e) {
            LOG.warn(e, "Got invalid UTF-8 from getxattr for %s", path);
          }
        }
      }
    } catch (FileSystemException | IllegalArgumentException e) {
      LOG.debug("Failed when fetching SHA-1 for %s", path);
    }
    return Optional.empty();
  }

  private Optional<Sha1HashCode> computeSha1ViaThrift(
      AbsPath path, boolean retryWithRealPathIfEdenError) throws IOException {
    Optional<RelPath> entry = mount.getPathRelativeToProjectRoot(path.getPath());
    if (entry.isPresent()) {
      try {
        return Optional.of(mount.getSha1(entry.get()));
      } catch (TException | IOException e) {
        LOG.info(e, "Failed when fetching SHA-1 for %s", path);
      } catch (EdenError e) {
        if (retryWithRealPathIfEdenError) {
          // It's possible that an EdenError was thrown because entry.get() was a path to a
          // symlink, which is not supported by Eden's getSha1() API. Try again if the real path
          // is different from the original path.
          AbsPath realPath = path.toRealPath();
          if (!realPath.equals(path)) {
            return Optional.of(computeSha1(realPath, /* retryWithRealPathIfEdenError */ false));
          }
        }
      }
    }
    return Optional.empty();
  }

  private Optional<Sha1HashCode> computeSha1ViaWatchman(
      AbsPath path, boolean retryWithRealPathIfEdenError) throws IOException {
    Optional<Sha1HashCode> sha1 = Optional.empty();
    try {
      sha1 = glob(path);
    } catch (IOException | InterruptedException | WatchmanQueryFailedException e) {
      LOG.info(e, "Failed when fetching SHA-1 for %s", path);
      if (retryWithRealPathIfEdenError) {
        AbsPath realPath = path.toRealPath();
        if (!realPath.equals(path)) {
          return Optional.of(computeSha1(realPath, /* retryWithRealPathIfEdenError */ false));
        }
      }
    }

    return sha1;
  }

  @VisibleForTesting
  Optional<Sha1HashCode> globOnPath(Path path)
      throws IOException, InterruptedException, WatchmanQueryFailedException {
    AbsPath fileToHash = getPathForRelativePath(path);
    return glob(fileToHash);
  }

  private Watchman realWatchman() {
    if (watchman instanceof WatchmanFactory.NullWatchman) {
      throw new IllegalStateException(
          String.format(
              "Watchman is not set. Please turn off eden.%s",
              BUCKCONFIG_USE_WATCHMAN_CONTENT_SHA1));
    }
    return watchman;
  }

  @SuppressWarnings("unchecked")
  private Optional<Sha1HashCode> glob(AbsPath path)
      throws IOException, InterruptedException, WatchmanQueryFailedException {
    Watchman watchman = realWatchman();
    WatchmanClient watchmanClient = watchman.getPooledClient();
    // TODO: `watchRoot` should be actually watch root, not cell root
    WatchRoot watchRoot = new WatchRoot(rootPath);
    long queryWarnTimeoutNanos = watchman.getQueryWarnTimeoutNanos();
    long queryPollTimeoutNanos = watchman.getQueryPollTimeoutNanos();
    WatchmanGlobber globber =
        WatchmanGlobber.create(
            watchmanClient,
            queryPollTimeoutNanos,
            queryWarnTimeoutNanos,
            ForwardRelPath.EMPTY,
            watchRoot);
    String pathString = rootPath.relativize(path.getPath()).toString();
    Optional<ImmutableMap<String, WatchmanGlobber.WatchmanFileAttributes>> ret =
        globber.runWithExtraFields(
            Collections.singleton(pathString),
            ImmutableSet.of(),
            EnumSet.of(WatchmanGlobber.Option.FORCE_CASE_SENSITIVE),
            queryPollTimeoutNanos,
            queryWarnTimeoutNanos,
            ImmutableList.of("name", WATCHMAN_CONTENT_SHA1_FIELD));
    if (ret.isPresent() && ret.get().containsKey(pathString)) {
      @Nullable
      Object sha1Ret = ret.get().get(pathString).getAttributeMap().get(WATCHMAN_CONTENT_SHA1_FIELD);
      if (sha1Ret instanceof String) {
        String sha1 = (String) sha1Ret;
        return Optional.of(Sha1HashCode.of(sha1));
      } else {
        // Watchman could not resolve some cases, i.e. symlink, fallback to xattr
        if (sha1Ret instanceof Map) {
          @Nullable Object error = ((Map<String, Object>) sha1Ret).get("error");
          if (error != null) {
            LOG.debug("Failed to query watchman SHA1, error message: %s", (String) error);
          }
        }
        LOG.debug("Failed to query watchman SHA1 for path %s, Fallback to XAttr", path);
        return computeSha1ViaXAttr(path);
      }
    }
    return Optional.empty();
  }

  @Override
  public AbsPath getPathForRelativePath(Path pathRelativeToProjectRootOrJustAbsolute) {
    return delegate.getPathForRelativePath(pathRelativeToProjectRootOrJustAbsolute);
  }
}

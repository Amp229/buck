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

package com.facebook.buck.core.filesystems;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;

/** Absolute path. */
public interface AbsPath extends PathWrapper {
  /**
   * Cosnstruct using {@link java.nio.file.Path} object.
   *
   * <p>Note this operation is just a cast if the path is {@link BuckUnixPath}.
   *
   * @throws RuntimeException the path is not absolute.
   */
  static AbsPath of(Path path) {
    if (path instanceof AbsPath) {
      return (AbsPath) path;
    } else {
      return new AbsPathImpl(path);
    }
  }

  static AbsPath get(String path) {
    return AbsPath.of(Paths.get(path));
  }

  /** Behaves exactly like {@link Path#normalize()}. */
  default AbsPath normalize() {
    return of(getPath().normalize());
  }

  default AbsPath toRealPath(LinkOption... options) throws IOException {
    return AbsPath.of(getPath().toRealPath(options));
  }

  default AbsPath resolve(RelPath other) {
    return resolve(other.getPath());
  }

  default AbsPath resolve(ForwardRelPath other) {
    // TODO(nga): optimize for `BuckUnixAbsPath`
    return resolve(other.toRelPath(getFileSystem()));
  }

  default AbsPath resolve(FileName fileName) {
    // TODO(nga): optimize for `BuckUnixAbsPath`
    return resolve(fileName.getName());
  }

  default boolean startsWith(AbsPath path) {
    return startsWith(path.getPath());
  }

  /**
   * Remove prefix from this path, return {@code null} if this path does not start with given
   * prefix.
   */
  // @Nullable
  default RelPath removePrefixIfStartsWith(AbsPath prefix) {
    if (!this.startsWith(prefix)) {
      return null;
    }
    return prefix.relativize(this);
  }

  /** Remove prefix from this path, throw if this path does not start with given prefix. */
  default RelPath removePrefix(AbsPath prefix) {
    RelPath relPath = removePrefixIfStartsWith(prefix);
    if (relPath == null) {
      throw new IllegalArgumentException(
          String.format("path %s does not start with %s", this, prefix));
    }
    return relPath;
  }

  default AbsPath resolve(Path path) {
    return AbsPath.of(getPath().resolve(path));
  }

  default AbsPath resolve(String path) {
    return AbsPath.of(getPath().resolve(path));
  }

  default AbsPath getParent() {
    Path parent = getPath().getParent();
    return parent != null ? AbsPath.of(parent) : null;
  }

  default RelPath relativize(Path other) {
    return RelPath.of(this.getPath().relativize(other));
  }

  default RelPath relativize(AbsPath other) {
    return relativize(other.getPath());
  }

  /**
   * Get the filesystem root of the current path. Note unlike {@link Path#getRoot()} this function
   * never returns {@code null} because absolute paths always have root.
   */
  default AbsPath getRoot() {
    Path root = getPath().getRoot();
    if (root == null) {
      throw new IllegalStateException("abs path must have a root: " + this);
    }
    return AbsPath.of(root);
  }

  default File toFile() {
    return getPath().toFile();
  }

  /** We cannot implement {@link java.lang.Comparable} directly. */
  static Comparator<AbsPath> comparator() {
    return Comparator.comparing(AbsPath::getPath);
  }

  default URI toUri() {
    return getPath().toUri();
  }
}

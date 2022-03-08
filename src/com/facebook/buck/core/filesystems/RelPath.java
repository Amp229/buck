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

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;

/** Relative path. */
public interface RelPath extends PathWrapper {

  /**
   * Construct using {@link java.nio.file.Path} object.
   *
   * <p>Note this operation is just a cast if the path is {@link BuckUnixPath}.
   *
   * @throws RuntimeException the path is not relative.
   */
  static RelPath of(Path path) {
    if (path instanceof RelPath) {
      return (RelPath) path;
    } else {
      return new RelPathImpl(path);
    }
  }

  /**
   * Construct a path.
   *
   * @throws RuntimeException if the path is absolute.
   */
  static RelPath get(String first, String... more) {
    return of(Paths.get(first, more));
  }

  /** Behaves exactly like {@link Path#normalize()}. */
  default RelPath normalize() {
    return of(getPath().normalize());
  }

  default RelPath getParent() {
    Path parent = getPath().getParent();
    return parent != null ? RelPath.of(parent) : null;
  }

  default Path resolve(String other) {
    return getPath().resolve(other);
  }

  default Path resolve(Path other) {
    // TODO(nga): optimize for `BuckUnixRelPath`
    return getPath().resolve(other);
  }

  default RelPath resolveRel(String other) {
    return RelPath.of(resolve(other));
  }

  default RelPath resolve(RelPath other) {
    return RelPath.of(getPath().resolve(other.getPath()));
  }

  default RelPath resolve(ForwardRelPath other) {
    return resolve(other.toRelPath(getFileSystem()));
  }

  default RelPath resolve(FileName fileName) {
    // TODO(nga): specialize in `BuckUnixRelPath`
    return resolveRel(fileName.getName());
  }

  default RelPath subpath(int beginIndex, int endIndex) {
    return RelPath.of(getPath().subpath(beginIndex, endIndex));
  }

  default int getNameCount() {
    return getPath().getNameCount();
  }

  /** Convert path to an absolute path resolving it from current directory. */
  default AbsPath toAbsolutePath() {
    return AbsPath.of(getPath().toAbsolutePath());
  }

  default RelPath relativize(Path path) {
    return RelPath.of(getPath().relativize(path));
  }

  default RelPath relativize(RelPath path) {
    return relativize(path.getPath());
  }

  default boolean startsWith(RelPath path) {
    return getPath().startsWith(path.getPath());
  }

  /** We cannot implement {@link java.lang.Comparable} directly. */
  static Comparator<RelPath> comparator() {
    return Comparator.comparing(RelPath::getPath);
  }
}

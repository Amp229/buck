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

package com.facebook.buck.core.model.targetgraph.impl;

import com.facebook.buck.core.exceptions.HumanReadableException;
import com.facebook.buck.core.filesystems.AbsPath;
import com.facebook.buck.core.filesystems.ForwardRelPath;
import com.facebook.buck.core.model.BuildTarget;
import com.facebook.buck.io.filesystem.ProjectFilesystem;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import java.io.IOException;
import java.nio.file.NoSuchFileException;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Set;

/** Checks that paths exist and throw an exception if at least one path doesn't exist. */
class MissingPathsChecker implements PathsChecker {

  private final LoadingCache<AbsPath, Set<ForwardRelPath>> pathsCache = initCache();
  private final LoadingCache<AbsPath, Set<ForwardRelPath>> filePathsCache = initCache();
  private final LoadingCache<AbsPath, Set<ForwardRelPath>> dirPathsCache = initCache();

  private static LoadingCache<AbsPath, Set<ForwardRelPath>> initCache() {
    return CacheBuilder.newBuilder()
        .weakValues()
        .build(CacheLoader.from(rootPath -> Sets.newConcurrentHashSet()));
  }

  @Override
  public void checkPaths(
      ProjectFilesystem projectFilesystem,
      BuildTarget buildTarget,
      ImmutableSet<ForwardRelPath> paths) {
    checkPathsWithExtraCheck(
        projectFilesystem, buildTarget, paths, pathsCache, (path, attrs) -> {});
  }

  @Override
  public void checkFilePaths(
      ProjectFilesystem projectFilesystem,
      BuildTarget buildTarget,
      ImmutableSet<ForwardRelPath> filePaths) {
    checkPathsWithExtraCheck(
        projectFilesystem,
        buildTarget,
        filePaths,
        filePathsCache,
        (path, attrs) -> {
          if (!attrs.isRegularFile()) {
            throw new HumanReadableException("In %s expected regular file: %s", buildTarget, path);
          }
        });
  }

  @Override
  public void checkDirPaths(
      ProjectFilesystem projectFilesystem,
      BuildTarget buildTarget,
      ImmutableSet<ForwardRelPath> dirPaths) {
    checkPathsWithExtraCheck(
        projectFilesystem,
        buildTarget,
        dirPaths,
        dirPathsCache,
        (path, attrs) -> {
          if (!attrs.isDirectory()) {
            throw new HumanReadableException("In %s expected directory: %s", buildTarget, path);
          }
        });
  }

  private interface ExtraCheck {
    void check(ForwardRelPath path, BasicFileAttributes attrs);
  }

  private static void checkPathsWithExtraCheck(
      ProjectFilesystem projectFilesystem,
      BuildTarget buildTarget,
      ImmutableSet<ForwardRelPath> paths,
      LoadingCache<AbsPath, Set<ForwardRelPath>> pathsCache,
      ExtraCheck extraCheck) {
    Set<ForwardRelPath> checkedPaths = pathsCache.getUnchecked(projectFilesystem.getRootPath());
    for (ForwardRelPath path : paths) {
      if (!checkedPaths.add(path)) {
        continue;
      }
      try {
        BasicFileAttributes attrs =
            projectFilesystem.readAttributes(
                path.toPath(projectFilesystem.getFileSystem()), BasicFileAttributes.class);
        extraCheck.check(path, attrs);
      } catch (NoSuchFileException e) {
        throw new HumanReadableException(
            e, "%s references non-existing file or directory '%s'", buildTarget, path);
      } catch (IOException e) {
        throw new HumanReadableException(
            e,
            "%s references inaccessible file or directory '%s': %s",
            buildTarget,
            path,
            e.getMessage());
      }
    }
  }
}

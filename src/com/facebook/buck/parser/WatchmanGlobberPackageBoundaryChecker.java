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

package com.facebook.buck.parser;

import com.facebook.buck.core.cell.Cell;
import com.facebook.buck.core.exceptions.HumanReadableException;
import com.facebook.buck.core.filesystems.AbsPath;
import com.facebook.buck.core.filesystems.ForwardRelPath;
import com.facebook.buck.core.filesystems.RelPath;
import com.facebook.buck.core.model.BuildTarget;
import com.facebook.buck.core.util.log.Logger;
import com.facebook.buck.io.filesystem.ProjectFilesystem;
import com.facebook.buck.io.watchman.FileSystemNotWatchedException;
import com.facebook.buck.io.watchman.ProjectWatch;
import com.facebook.buck.io.watchman.Watchman;
import com.facebook.buck.io.watchman.WatchmanClient;
import com.facebook.buck.io.watchman.WatchmanQueryFailedException;
import com.facebook.buck.parser.config.ParserConfig;
import com.facebook.buck.skylark.io.impl.WatchmanGlobber;
import com.facebook.buck.util.types.Pair;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

/**
 * Similar to {@link ThrowingPackageBoundaryChecker}, a {@link PackageBoundaryChecker}
 * implementation that throws an exception if any file in a set does not belong to the same package
 * as provided build target only if cell configuration allows that, otherwise noop. This package
 * boundary checker uses watchman query to avoid file system stat, and the existence of the path
 * which should be checked by {@link com.facebook.buck.core.model.targetgraph.impl.PathsChecker}
 */
public class WatchmanGlobberPackageBoundaryChecker implements PackageBoundaryChecker {

  private static final Logger LOG = Logger.get(WatchmanGlobberPackageBoundaryChecker.class);

  private final Watchman watchman;
  private final Optional<PackageBoundaryChecker> fallbackPackageBoundaryChecker;
  private final LoadingCache<Pair<Cell, ForwardRelPath>, Optional<ForwardRelPath>>
      basePathOfAncestorCache =
          CacheBuilder.newBuilder()
              .weakValues()
              .build(
                  new CacheLoader<Pair<Cell, ForwardRelPath>, Optional<ForwardRelPath>>() {
                    @Override
                    public Optional<ForwardRelPath> load(Pair<Cell, ForwardRelPath> cellPathPair)
                        throws Exception {
                      ForwardRelPath buildFileName =
                          ForwardRelPath.ofFileName(
                              cellPathPair
                                  .getFirst()
                                  .getBuckConfigView(ParserConfig.class)
                                  .getBuildFileName());
                      ForwardRelPath folderPath = cellPathPair.getSecond();
                      ForwardRelPath buildFileCandidate = folderPath.resolve(buildFileName);
                      // This will stat the build file for EdenFS, but should be fine since
                      // we need to materialize build files anyway
                      if (cellPathPair.getFirst().getFilesystem().isFile(buildFileCandidate)) {
                        return Optional.of(folderPath);
                      }

                      // traverse up
                      ForwardRelPath parent = folderPath.getParentButEmptyForSingleSegment();
                      if (parent == null) {
                        return Optional.empty();
                      }

                      return basePathOfAncestorCache.get(
                          new Pair<>(cellPathPair.getFirst(), parent));
                    }
                  });

  public WatchmanGlobberPackageBoundaryChecker(
      Watchman watchman, PackageBoundaryChecker fallbackPackageBoundaryChecker) {
    this.watchman = watchman;
    this.fallbackPackageBoundaryChecker = Optional.of(fallbackPackageBoundaryChecker);
  }

  @VisibleForTesting
  WatchmanGlobberPackageBoundaryChecker(Watchman watchman) {
    this.watchman = watchman;
    this.fallbackPackageBoundaryChecker = Optional.empty();
  }

  @Override
  public void enforceBuckPackageBoundaries(
      Cell targetCell, BuildTarget target, ImmutableSet<ForwardRelPath> paths) {

    ForwardRelPath basePath = target.getCellRelativeBasePath().getPath();

    ParserConfig.PackageBoundaryEnforcement enforcing =
        targetCell
            .getBuckConfigView(ParserConfig.class)
            .getPackageBoundaryEnforcementPolicy(
                basePath.toPath(targetCell.getFilesystem().getFileSystem()));
    if (enforcing == ParserConfig.PackageBoundaryEnforcement.DISABLED) {
      return;
    }

    ImmutableSet<ForwardRelPath> folderPaths;
    try {
      folderPaths = filterFolderPaths(paths, targetCell.getFilesystem());
    } catch (Exception e) {
      String formatString = "Failed to query watchman for target %s: %s";
      warnOrError(
          enforcing,
          targetCell,
          target,
          paths,
          fallbackPackageBoundaryChecker,
          formatString,
          target,
          e);
      return;
    }

    boolean isBasePathEmpty = basePath.isEmpty();

    for (ForwardRelPath path : paths) {
      if (!isBasePathEmpty && !path.startsWith(basePath)) {
        String formatString = "'%s' in '%s' refers to a parent directory.";
        warnOrError(
            enforcing,
            targetCell,
            target,
            paths,
            fallbackPackageBoundaryChecker,
            formatString,
            basePath
                .toPath(targetCell.getFilesystem().getFileSystem())
                .relativize(path.toPath(targetCell.getFilesystem().getFileSystem())),
            target);
        continue;
      }

      Optional<ForwardRelPath> ancestor =
          getBasePathOfAncestorTarget(path, targetCell, folderPaths);

      if (!ancestor.isPresent()) {
        warnOrError(
            enforcing,
            targetCell,
            target,
            paths,
            fallbackPackageBoundaryChecker,
            "Target '%s' refers to file '%s', which doesn't belong to any package. "
                + "More info at:\nhttps://dev.buck.build/about/overview.html\n",
            target,
            path);
      }

      if (!ancestor.get().equals(basePath)) {
        ForwardRelPath buildFileName =
            ForwardRelPath.ofFileName(
                targetCell.getBuckConfigView(ParserConfig.class).getBuildFileName());
        ForwardRelPath buckFile = ancestor.get().resolve(buildFileName);
        String formatString =
            "The target '%1$s' tried to reference '%2$s'.\n"
                + "This is not allowed because '%2$s' can only be referenced from '%3$s' \n"
                + "which is its closest parent '%4$s' file.\n"
                + "\n"
                + "You should find or create a rule in '%3$s' that references\n"
                + "'%2$s' and use that in '%1$s'\n"
                + "instead of directly referencing '%2$s'.\n"
                + "More info at:\nhttps://dev.buck.build/concept/build_rule.html\n"
                + "\n"
                + "This issue might also be caused by a bug in buckd's caching.\n"
                + "Please check whether using `buck kill` resolves it.";

        warnOrError(
            enforcing,
            targetCell,
            target,
            paths,
            fallbackPackageBoundaryChecker,
            formatString,
            target,
            path,
            buckFile,
            buildFileName);
      }
    }
  }

  /**
   * return the nearest folder that contains build file for a path. This assume the exists of files
   * (which should have been checked by {@link
   * com.facebook.buck.core.model.targetgraph.impl.PathsChecker}
   *
   * @param path input path to find the nearest folder contains build files
   * @param cell the cell of input path
   * @param folderPaths paths that known to be folders, paths not in the set were considered as
   *     files or symlinks.
   * @return optional base path of ancestor of a target
   */
  private Optional<ForwardRelPath> getBasePathOfAncestorTarget(
      ForwardRelPath path, Cell cell, ImmutableSet<ForwardRelPath> folderPaths) {

    ForwardRelPath folderPath = folderPaths.contains(path) ? path : path.getParent();
    if (folderPath == null) {
      folderPath = ForwardRelPath.EMPTY;
    }
    return basePathOfAncestorCache.getUnchecked(new Pair<>(cell, folderPath));
  }

  /**
   * Return paths that are folders from the input paths
   *
   * @param paths paths that to be filtered
   * @param projectFilesystem project file system for the input path
   * @return a set of folder paths out of input path
   */
  private ImmutableSet<ForwardRelPath> filterFolderPaths(
      Set<ForwardRelPath> paths, ProjectFilesystem projectFilesystem)
      throws IOException, InterruptedException, WatchmanQueryFailedException {
    ProjectWatch watch = watchman.getProjectWatches().get(projectFilesystem.getRootPath());
    AbsPath watchmanRootPath = watch.getWatchRoot().toPath(projectFilesystem.getFileSystem());

    Set<String> watchmanPaths = new HashSet<>();
    for (ForwardRelPath path : paths) {
      Path actualPath = path.toPath(projectFilesystem.getFileSystem());
      RelPath watchmanPath = watchmanRootPath.relativize(projectFilesystem.resolve(actualPath));
      watchmanPaths.add(watchmanPath.toString());
    }
    Optional<ImmutableSet<String>> watchmanGlob =
        glob(
            watchman,
            watchmanPaths,
            projectFilesystem,
            EnumSet.of(
                WatchmanGlobber.Option.FORCE_CASE_SENSITIVE,
                WatchmanGlobber.Option.EXCLUDE_REGULAR_FILES));
    if (!watchmanGlob.isPresent()) {
      return ImmutableSet.of();
    } else {
      return watchmanGlob.get().stream()
          .map(pathString -> ForwardRelPath.of(pathString))
          .collect(ImmutableSet.toImmutableSet());
    }
  }

  private Optional<ImmutableSet<String>> glob(
      Watchman watchman,
      Collection<String> patterns,
      ProjectFilesystem projectFilesystem,
      EnumSet<WatchmanGlobber.Option> options)
      throws IOException, InterruptedException, WatchmanQueryFailedException {
    if (patterns.isEmpty()) {
      return Optional.empty();
    }
    WatchmanClient watchmanClient = watchman.getPooledClient();
    ProjectWatch watch = watchman.getProjectWatches().get(projectFilesystem.getRootPath());
    if (watch == null) {
      String msg =
          String.format(
              "Path [%s] is not watched. The list of watched project: [%s]",
              projectFilesystem.getRootPath(), watchman.getProjectWatches().keySet());
      throw new FileSystemNotWatchedException(msg);
    }

    long queryPollTimeoutNanos = watchman.getQueryPollTimeoutNanos();
    long queryWarnTimeoutNanos = watchman.getQueryWarnTimeoutNanos();
    WatchmanGlobber globber =
        WatchmanGlobber.create(
            watchmanClient,
            queryPollTimeoutNanos,
            queryWarnTimeoutNanos,
            ForwardRelPath.EMPTY,
            watch.getWatchRoot());
    return globber.run(
        patterns, ImmutableList.of(), options, queryPollTimeoutNanos, queryWarnTimeoutNanos);
  }

  private static void warnOrError(
      ParserConfig.PackageBoundaryEnforcement enforcing,
      Cell cell,
      BuildTarget target,
      ImmutableSet<ForwardRelPath> paths,
      Optional<PackageBoundaryChecker> fallbackPackageBoundaryChecker,
      String formatString,
      Object... formatArgs) {
    if (enforcing == ParserConfig.PackageBoundaryEnforcement.ENFORCE) {
      if (fallbackPackageBoundaryChecker.isPresent()) {
        LOG.warn(
            "Did not pass the watchman-based package boundary checker, fallback to filesystem-based package boundary checker: "
                + formatString,
            formatArgs);
        fallbackPackageBoundaryChecker.get().enforceBuckPackageBoundaries(cell, target, paths);
      } else {
        throw new HumanReadableException(formatString, formatArgs);
      }
    } else {
      LOG.warn(formatString, formatArgs);
    }
  }
}

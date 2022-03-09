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
import com.facebook.buck.core.filesystems.AbsPath;
import com.facebook.buck.core.filesystems.FileName;
import com.facebook.buck.core.filesystems.ForwardRelPath;
import com.facebook.buck.core.model.targetgraph.impl.Package;
import com.facebook.buck.core.util.log.Logger;
import com.facebook.buck.event.BuckEventBus;
import com.facebook.buck.event.SimplePerfEvent;
import com.facebook.buck.parser.api.PackageFileManifest;
import com.facebook.buck.parser.api.PackageMetadata;
import com.facebook.buck.parser.config.ParserConfig;
import com.facebook.buck.parser.exceptions.BuildTargetException;
import com.google.common.base.Preconditions;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Converts {@link PackageMetadata}s in a {@link PackageFileManifest} into {@link Package}s and
 * caches them for reuse.
 */
public class PackagePipeline implements AutoCloseable {
  public static final FileName PACKAGE_FILE_NAME = FileName.of("PACKAGE");
  private static final Logger LOG = Logger.get(PackagePipeline.class);

  private final ListeningExecutorService executorService;
  private final BuckEventBus eventBus;
  private final PackageFileParsePipeline packageFileParsePipeline;

  private final SimplePerfEvent.Scope perfEventScope;
  private final SimplePerfEvent.PerfEventTitle perfEventTitle;
  /**
   * minimum duration time for performance events to be logged (for use with {@link
   * SimplePerfEvent}s). This is on the base class to make it simpler to enable verbose tracing for
   * all of the parsing pipelines.
   */
  private final long minimumPerfEventTimeMs;

  private final PipelineNodeCache<ForwardRelPath, Package> cache;

  private final AtomicBoolean shuttingDown = new AtomicBoolean(false);

  PackagePipeline(
      ListeningExecutorService executorService,
      BuckEventBus eventBus,
      PackageFileParsePipeline packageFileParsePipeline) {
    this.executorService = executorService;
    this.eventBus = eventBus;
    this.packageFileParsePipeline = packageFileParsePipeline;

    this.minimumPerfEventTimeMs = LOG.isVerboseEnabled() ? 0 : 10;
    this.perfEventTitle = SimplePerfEvent.PerfEventTitle.of("GetPackage");
    this.perfEventScope =
        SimplePerfEvent.scope(
            eventBus.isolated(), SimplePerfEvent.PerfEventTitle.of("package_pipeline"));

    this.cache =
        new PipelineNodeCache<>(
            new PackageCachePerBuild(), DaemonicParserValidationToken.invalid(), n -> false);
  }

  /**
   * Given a build file at a particular path, returns the path of the package file in the same
   * directory.
   */
  static ForwardRelPath getPackageFileFromBuildFile(Cell cell, ForwardRelPath buildFile) {
    Preconditions.checkArgument(
        buildFile.endsWith(cell.getBuckConfigView(ParserConfig.class).getBuildFileName()),
        "Invalid build file: %s",
        buildFile);
    ForwardRelPath parent =
        Preconditions.checkNotNull(
            buildFile.getParentButEmptyForSingleSegment(),
            "The build file path must have a parent: %s",
            buildFile);
    return parent.resolve(PACKAGE_FILE_NAME);
  }

  static boolean isPackageFile(AbsPath path) {
    return path.endsWith(PACKAGE_FILE_NAME);
  }

  /**
   * @return the path of the parent package file for the given {@param packageFile} package file, if
   *     it exists, else an empty optional.
   */
  static Optional<ForwardRelPath> getParentPackageFile(ForwardRelPath packageFile) {
    ForwardRelPath currentDir =
        Preconditions.checkNotNull(
            packageFile.getParentButEmptyForSingleSegment(),
            "The package file path must have a parent: %s",
            packageFile);
    if (currentDir.isEmpty()) {
      return Optional.empty();
    }
    ForwardRelPath parentPackageFile =
        currentDir.getParentButEmptyForSingleSegment().resolve(PACKAGE_FILE_NAME);
    return Optional.of(parentPackageFile);
  }

  /**
   * @return a future (potentially immediate) for the {@link Package} in the package file
   *     corresponding to the {@param buildFile} provided.
   */
  public ListenableFuture<Package> getPackageJob(Cell cell, ForwardRelPath buildFile) {
    ForwardRelPath packageFile = getPackageFileFromBuildFile(cell, buildFile);

    if (!cell.getBuckConfig().getView(ParserConfig.class).getEnablePackageFiles()) {
      Package pkg =
          PackageFactory.create(
              cell, packageFile, PackageMetadata.EMPTY_SINGLETON, Optional.empty());
      return Futures.immediateFuture(pkg);
    }

    return getPackageJobInternal(cell, packageFile);
  }

  private ListenableFuture<Package> getPackageJobInternal(Cell cell, ForwardRelPath packageFile)
      throws BuildTargetException {
    if (shuttingDown()) {
      return Futures.immediateCancelledFuture();
    }

    return cache.getJobWithCacheLookup(
        cell,
        packageFile,
        () ->
            Futures.transformAsync(
                getParentPackageJob(cell, packageFile),
                parentPkg ->
                    Futures.transformAsync(
                        packageFileParsePipeline.getFileJob(cell, packageFile),
                        packageFileManifest ->
                            computePackage(
                                cell, packageFile, packageFileManifest.getPackage(), parentPkg),
                        executorService),
                executorService));
  }

  private ListenableFuture<Optional<Package>> getParentPackageJob(
      Cell cell, ForwardRelPath childBuildFile) {
    if (shuttingDown()) {
      return Futures.immediateCancelledFuture();
    }

    Optional<ForwardRelPath> parentBuildFile = getParentPackageFile(childBuildFile);
    if (!parentBuildFile.isPresent()) {
      // childBuildFile is at the cell root and we have no more parents
      return Futures.immediateFuture(Optional.empty());
    }

    return Futures.transformAsync(
        getPackageJobInternal(cell, parentBuildFile.get()),
        cachedPackageNode -> Futures.immediateFuture(Optional.of(cachedPackageNode)),
        executorService);
  }

  private ListenableFuture<Package> computePackage(
      Cell cell, ForwardRelPath packageFile, PackageMetadata pkg, Optional<Package> parentPkg)
      throws BuildTargetException {
    if (shuttingDown()) {
      return Futures.immediateCancelledFuture();
    }
    Package result;

    try (SimplePerfEvent.Scope scope =
        SimplePerfEvent.scopeIgnoringShortEvents(
            eventBus.isolated(),
            perfEventTitle,
            "packageFile",
            packageFile,
            perfEventScope,
            minimumPerfEventTimeMs,
            TimeUnit.MILLISECONDS)) {

      result = PackageFactory.create(cell, packageFile, pkg, parentPkg);
    }
    return Futures.immediateFuture(result);
  }

  private boolean shuttingDown() {
    return shuttingDown.get();
  }

  @Override
  public void close() {
    perfEventScope.close();
    shuttingDown.set(true);

    // At this point external callers should not schedule more work, internally job creation
    // should also stop. Any scheduled futures should eventually cancel themselves (all of the
    // AsyncFunctions that interact with the Cache are wired to early-out if `shuttingDown` is
    // true).
    // We could block here waiting for all ongoing work to complete, however the user has already
    // gotten everything they want out of the pipeline, so the only interesting thing that could
    // happen here are exceptions thrown by the ProjectBuildFileParser as its shutting down. These
    // aren't critical enough to warrant bringing down the entire process, as they don't affect the
    // state that has already been extracted from the parser.
  }
}

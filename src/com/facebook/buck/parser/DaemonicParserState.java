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
import com.facebook.buck.core.cell.CellPathResolver;
import com.facebook.buck.core.cell.name.CanonicalCellName;
import com.facebook.buck.core.config.BuckConfig;
import com.facebook.buck.core.filesystems.AbsPath;
import com.facebook.buck.core.filesystems.ForwardRelPath;
import com.facebook.buck.core.model.BuildFileTree;
import com.facebook.buck.core.model.BuildTarget;
import com.facebook.buck.core.model.UnconfiguredBuildTarget;
import com.facebook.buck.core.model.impl.FilesystemBackedBuildFileTree;
import com.facebook.buck.core.model.targetgraph.TargetNodeMaybeIncompatible;
import com.facebook.buck.core.model.targetgraph.raw.UnconfiguredTargetNode;
import com.facebook.buck.core.util.log.Logger;
import com.facebook.buck.counters.Counter;
import com.facebook.buck.io.watchman.WatchmanEvent.Kind;
import com.facebook.buck.io.watchman.WatchmanOverflowEvent;
import com.facebook.buck.io.watchman.WatchmanPathEvent;
import com.facebook.buck.io.watchman.WatchmanWatcherOneBigEvent;
import com.facebook.buck.parser.api.BuildFileManifest;
import com.facebook.buck.parser.api.PackageFileManifest;
import com.facebook.buck.parser.config.ParserConfig;
import com.facebook.buck.parser.exceptions.BuildFileParseException;
import com.facebook.buck.parser.exceptions.BuildTargetException;
import com.facebook.buck.parser.implicit.ImplicitIncludePath;
import com.facebook.buck.util.concurrent.AutoCloseableReadLocked;
import com.facebook.buck.util.concurrent.AutoCloseableWriteLocked;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.base.Throwables;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.eventbus.Subscribe;
import com.google.common.util.concurrent.UncheckedExecutionException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;
import javax.annotation.Nullable;

/** Persistent parsing data, that can exist between invocations of the {@link Parser}. */
public class DaemonicParserState {

  private static final Logger LOG = Logger.get(DaemonicParserState.class);

  /** Stateless view of caches on object that conforms to {@link PipelineNodeCache.Cache}. */
  private class DaemonicCacheView<K, T> implements PipelineNodeCache.Cache<K, T> {

    protected final DaemonicCellState.CellCacheType<K, T> type;

    private DaemonicCacheView(DaemonicCellState.CellCacheType<K, T> type) {
      this.type = type;
    }

    @Override
    public Optional<T> lookupComputedNode(
        Cell cell, K target, DaemonicParserValidationToken validationToken)
        throws BuildTargetException {
      DaemonicCellState.Cache<K, T> state = getCache(cell);
      if (state == null) {
        return Optional.empty();
      }
      return state.lookupComputedNode(target, validationToken);
    }

    @Override
    public T putComputedNodeIfNotPresent(
        Cell cell,
        K target,
        T targetNode,
        boolean targetIsConfiguration,
        DaemonicParserValidationToken validationToken)
        throws BuildTargetException {

      AutoCloseableReadLocked readLock = locks.lockForUpdate(validationToken);
      if (readLock == null) {
        return targetNode;
      }

      try {
        AbsPath buildFile =
            cell.getBuckConfigView(ParserConfig.class)
                .getAbsolutePathToBuildFileUnsafe(
                    cell, type.convertToUnconfiguredBuildTargetView(target));

        if (targetIsConfiguration) {
          configurationBuildFiles.add(buildFile);
        }

        return getOrCreateCache(cell, readLock)
            .putComputedNodeIfNotPresent(target, targetNode, readLock);
      } finally {
        readLock.close();
      }
    }

    private @Nullable DaemonicCellState.Cache<K, T> getCache(Cell cell) {
      DaemonicCellState cellState = getCellState(cell);
      if (cellState == null) {
        return null;
      }
      return cellState.getCache(type);
    }

    private DaemonicCellState.Cache<K, T> getOrCreateCache(
        Cell cell, AutoCloseableReadLocked readLock) {
      return getOrCreateCellState(cell, readLock).getCache(type);
    }
  }

  /** Stateless view of caches on object that conforms to {@link PipelineNodeCache.Cache}. */
  private class DaemonicRawCacheView
      implements PipelineNodeCache.Cache<ForwardRelPath, BuildFileManifest> {

    @Override
    public Optional<BuildFileManifest> lookupComputedNode(
        Cell cell, ForwardRelPath buildFile, DaemonicParserValidationToken validationToken)
        throws BuildTargetException {

      DaemonicCellState state = getCellState(cell);
      if (state == null) {
        return Optional.empty();
      }
      return state.lookupBuildFileManifest(buildFile, validationToken);
    }

    /**
     * Insert item into the cache if it was not already there. The cache will also strip any meta
     * entries from the raw nodes (these are intended for the cache as they contain information
     * about what other files to invalidate entries on).
     *
     * @return previous nodes for the file if the cache contained it, new ones otherwise.
     */
    @Override
    public BuildFileManifest putComputedNodeIfNotPresent(
        Cell cell,
        ForwardRelPath buildFile,
        BuildFileManifest manifest,
        boolean targetIsConfiguration,
        DaemonicParserValidationToken validationToken)
        throws BuildTargetException {

      AutoCloseableReadLocked locked = locks.lockForUpdate(validationToken);
      if (locked == null) {
        return manifest;
      }

      try {
        ImmutableSet.Builder<AbsPath> dependentsOfEveryNode = ImmutableSet.builder();

        addAllIncludes(dependentsOfEveryNode, manifest.getIncludes(), cell);

        if (cell.getBuckConfig().getView(ParserConfig.class).getEnablePackageFiles()) {
          // Add the PACKAGE file in the build file's directory, regardless of whether they
          // currently
          // exist. If a PACKAGE file is added, or a parent PACKAGE file is modified/added we need
          // to
          // invalidate all relevant nodes.
          ForwardRelPath packageFile = PackagePipeline.getPackageFileFromBuildFile(cell, buildFile);
          dependentsOfEveryNode.add(cell.getRoot().resolve(packageFile));
        }

        return getOrCreateCellState(cell, locked)
            .putBuildFileManifestIfNotPresent(
                buildFile, manifest, dependentsOfEveryNode.build(), locked);
      } finally {
        locked.close();
      }
    }
  }

  /** Stateless view of caches on object that conforms to {@link PipelineNodeCache.Cache}. */
  private class DaemonicPackageCache
      implements PipelineNodeCache.Cache<ForwardRelPath, PackageFileManifest> {

    @Override
    public Optional<PackageFileManifest> lookupComputedNode(
        Cell cell, ForwardRelPath packageFile, DaemonicParserValidationToken validationToken)
        throws BuildTargetException {

      DaemonicCellState state = getCellState(cell);
      if (state == null) {
        return Optional.empty();
      }
      return state.lookupPackageFileManifest(packageFile, validationToken);
    }

    /**
     * Insert item into the cache if it was not already there.
     *
     * @return previous manifest for the file if the cache contained it, new ones otherwise.
     */
    @Override
    public PackageFileManifest putComputedNodeIfNotPresent(
        Cell cell,
        ForwardRelPath packageFile,
        PackageFileManifest manifest,
        boolean targetIsConfiguration,
        DaemonicParserValidationToken validationToken)
        throws BuildTargetException {

      AutoCloseableReadLocked locked = locks.lockForUpdate(validationToken);
      if (locked == null) {
        return manifest;
      }

      try {
        ImmutableSet.Builder<AbsPath> packageDependents = ImmutableSet.builder();

        addAllIncludes(packageDependents, manifest.getIncludes(), cell);

        // Package files may depend on their parent PACKAGE file.
        Optional<ForwardRelPath> parentPackageFile =
            PackagePipeline.getParentPackageFile(packageFile);
        parentPackageFile.ifPresent(path -> packageDependents.add(cell.getRoot().resolve(path)));

        return getOrCreateCellState(cell, locked)
            .putPackageFileManifestIfNotPresent(
                packageFile, manifest, packageDependents.build(), locked);
      } finally {
        locked.close();
      }
    }
  }

  /** Add all the includes from the manifest and Buck defaults. */
  private static void addAllIncludes(
      ImmutableSet.Builder<AbsPath> dependents, ImmutableSet<String> manifestIncludes, Cell cell) {
    for (String includedPath : manifestIncludes) {
      Path path = cell.getFilesystem().getPath(includedPath);
      dependents.add(AbsPath.of(path));
    }

    // We also know that the all manifests depend on the default includes for the cell.
    // Note: This is a bad assumption. While both the project build file and package parsers set
    // the default includes of the ParserConfig, it is not required and this assumption may not
    // always hold.
    BuckConfig buckConfig = cell.getBuckConfig();
    ImmutableList<ImplicitIncludePath> defaultIncludes =
        buckConfig.getView(ParserConfig.class).getDefaultIncludes();
    for (ImplicitIncludePath include : defaultIncludes) {
      dependents.add(resolveIncludePath(cell, include, cell.getCellPathResolver()));
    }
  }

  /**
   * Resolves a path of an include string like {@code repo//foo/macro_defs} to a filesystem path.
   */
  private static AbsPath resolveIncludePath(
      Cell cell, ImplicitIncludePath include, CellPathResolver cellPathResolver) {
    // Default includes are given as "cell//path/to/file". They look like targets
    // but they are not. However, I bet someone will try and treat it like a
    // target, so find the owning cell if necessary, and then fully resolve
    // the path against the owning cell's root.
    String cellName = include.getCellName();
    CanonicalCellName canonicalCellName =
        (Objects.isNull(cellName) || cellName.isEmpty())
            ? cell.getCanonicalName()
            : cellPathResolver.getCellNameResolver().getName(Optional.of(cellName));

    return cellPathResolver.getCellPathOrThrow(canonicalCellName).resolve(include.getCellRelPath());
  }

  /**
   * The set of {@link Cell} instances that have been seen by this state. This information is used
   * for cache invalidation. Please see {@link #invalidateBasedOn(WatchmanWatcherOneBigEvent)} for
   * example usage.
   */
  private final ConcurrentMap<CanonicalCellName, DaemonicCellState> cellToDaemonicState;

  private final DaemonicCacheView<BuildTarget, TargetNodeMaybeIncompatible> targetNodeCache =
      new DaemonicCacheView<>(DaemonicCellState.TARGET_NODE_CACHE_TYPE);
  private final DaemonicCacheView<UnconfiguredBuildTarget, UnconfiguredTargetNode>
      rawTargetNodeCache = new DaemonicCacheView<>(DaemonicCellState.RAW_TARGET_NODE_CACHE_TYPE);

  /**
   * Build files that contain configuration targets.
   *
   * <p>Changes in configuration targets are handled differently from the changes in build targets.
   * Whenever there is a change in configuration targets the state in all cells is reset. Parser
   * state doesn't provide information about dependencies among build rules and configuration rules
   * and changes in configuration rules can affect build targets (including build targets in other
   * cells).
   */
  // TODO: remove logic around this field when proper tracking of dependencies on
  // configuration rules is implemented
  private final Set<AbsPath> configurationBuildFiles = ConcurrentHashMap.newKeySet();

  private final DaemonicRawCacheView rawNodeCache;

  private final DaemonicPackageCache packageFileCache;

  private final DaemonicParserStateCounters counters;

  private final LoadingCache<Cell, BuildFileTree> buildFileTrees;

  private final DaemonicParserStateLocks locks;

  public DaemonicParserState() {
    this.counters = new DaemonicParserStateCounters();
    this.buildFileTrees =
        CacheBuilder.newBuilder()
            .build(
                new CacheLoader<Cell, BuildFileTree>() {
                  @Override
                  public BuildFileTree load(Cell cell) {
                    return new FilesystemBackedBuildFileTree(
                        cell.getFilesystem(),
                        cell.getBuckConfigView(ParserConfig.class).getBuildFileName());
                  }
                });
    this.cellToDaemonicState = new ConcurrentHashMap<>();

    this.rawNodeCache = new DaemonicRawCacheView();
    this.packageFileCache = new DaemonicPackageCache();

    this.locks = new DaemonicParserStateLocks();
  }

  LoadingCache<Cell, BuildFileTree> getBuildFileTrees() {
    return buildFileTrees;
  }

  /** Type-safe accessor to one of state caches */
  static final class CacheType<K, T> {
    private final Function<DaemonicParserState, DaemonicCacheView<K, T>> getCacheView;

    public CacheType(Function<DaemonicParserState, DaemonicCacheView<K, T>> getCacheView) {
      this.getCacheView = getCacheView;
    }
  }

  public static final CacheType<BuildTarget, TargetNodeMaybeIncompatible> TARGET_NODE_CACHE_TYPE =
      new CacheType<>(state -> state.targetNodeCache);
  public static final CacheType<UnconfiguredBuildTarget, UnconfiguredTargetNode>
      RAW_TARGET_NODE_CACHE_TYPE = new CacheType<>(state -> state.rawTargetNodeCache);

  /**
   * Retrieve the cache view for caching a particular type.
   *
   * <p>Note that the output type is not constrained to the type of the Class object to allow for
   * types with generics. Care should be taken to ensure that the correct class object is passed in.
   */
  public <K, T> PipelineNodeCache.Cache<K, T> getOrCreateNodeCache(CacheType<K, T> cacheType) {
    return cacheType.getCacheView.apply(this);
  }

  public PipelineNodeCache.Cache<ForwardRelPath, BuildFileManifest> getRawNodeCache() {
    return rawNodeCache;
  }

  public PipelineNodeCache.Cache<ForwardRelPath, PackageFileManifest> getPackageFileCache() {
    return packageFileCache;
  }

  @VisibleForTesting
  PipelineNodeCache.Cache<BuildTarget, TargetNodeMaybeIncompatible> getTargetNodeCache() {
    return targetNodeCache;
  }

  @Nullable
  private DaemonicCellState getCellState(Cell cell) {
    return cellToDaemonicState.get(cell.getCanonicalName());
  }

  private DaemonicCellState getOrCreateCellState(Cell cell, AutoCloseableReadLocked locked) {
    locked.markUsed();

    return cellToDaemonicState.computeIfAbsent(
        cell.getCanonicalName(), r -> new DaemonicCellState(cell, locks));
  }

  @Subscribe
  public void invalidateBasedOn(WatchmanWatcherOneBigEvent event) {
    LOG.debug("Invalidating based on watchman event: " + event.summaryForLogging());

    try (AutoCloseableWriteLocked locked = locks.cachesLock.lockWrite()) {
      if (!event.getOverflowEvents().isEmpty()) {
        locks.markInvalidated(locked);
        invalidateBasedOn(event.getOverflowEvents(), locked);
      } else {
        ImmutableList<WatchmanPathEvent> pathEvents = event.getPathEvents();
        if (!pathEvents.isEmpty()) {
          locks.markInvalidated(locked);
          for (WatchmanPathEvent pathEvent : pathEvents) {
            invalidateBasedOn(pathEvent, locked);
          }
        }
      }
    }
  }

  @VisibleForTesting
  void invalidateBasedOn(
      ImmutableList<WatchmanOverflowEvent> events, AutoCloseableWriteLocked locked) {
    Preconditions.checkArgument(!events.isEmpty(), "overflow event list must not be empty");

    // Non-path change event, likely an overflow due to many change events: invalidate everything.
    LOG.debug("Received non-path change event %s, assuming overflow and checking caches.", events);

    if (invalidateAllCachesLocked(locked)) {
      LOG.warn("Invalidated cache on watch event %s.", events);
      counters.recordCacheInvalidatedByWatchOverflow();
    }
  }

  @VisibleForTesting
  void invalidateBasedOn(WatchmanPathEvent event, AutoCloseableWriteLocked locked) {
    LOG.verbose("Parser watched event %s %s", event.getKind(), event.getPath());

    counters.recordFilesChanged();

    ForwardRelPath path = event.getPath();
    AbsPath fullPath = event.getCellPath().resolve(event.getPath());

    // We only care about creation and deletion events because modified should result in a
    // rule key change.  For parsing, these are the only events we need to care about.
    if (isPathCreateOrDeleteEvent(event)) {
      for (DaemonicCellState state : cellToDaemonicState.values()) {
        try {
          Cell cell = state.getCell();
          BuildFileTree buildFiles = buildFileTrees.get(cell);

          if (fullPath.endsWith(cell.getBuckConfigView(ParserConfig.class).getBuildFileName())) {
            LOG.debug(
                "Build file %s changed, invalidating build file tree for cell %s", fullPath, cell);
            // If a build file has been added or removed, reconstruct the build file tree.
            buildFileTrees.invalidate(cell);
          }

          // Added or removed files can affect globs, so invalidate the package build file
          // "containing" {@code path} unless its filename matches a temp file pattern.
          if (!cell.getFilesystem().isIgnored(path)) {
            invalidateContainingBuildFile(state, cell, buildFiles, path, locked);
          } else {
            LOG.debug(
                "Not invalidating the owning build file of %s because it is a temporary file.",
                fullPath);
          }
        } catch (ExecutionException | UncheckedExecutionException e) {
          try {
            Throwables.throwIfInstanceOf(e, BuildFileParseException.class);
            Throwables.throwIfUnchecked(e);
            throw new RuntimeException(e);
          } catch (BuildFileParseException bfpe) {
            LOG.warn("Unable to parse already parsed build file.", bfpe);
          }
        }
      }
    }

    if (configurationBuildFiles.contains(fullPath) || configurationRulesDependOn(path, locked)) {
      invalidateAllCachesLocked(locked);
    } else {
      invalidatePath(fullPath, locked);
    }
  }

  /**
   * Check whether at least one build file in {@link #configurationBuildFiles} depends on the given
   * file.
   */
  private boolean configurationRulesDependOn(ForwardRelPath path, AutoCloseableWriteLocked locked) {
    for (DaemonicCellState state : cellToDaemonicState.values()) {
      if (state.pathDependentPresentIn(path, configurationBuildFiles, locked)) {
        return true;
      }
    }
    return false;
  }

  /** Invalidate everything which depend on path. */
  private void invalidatePath(AbsPath path, AutoCloseableWriteLocked locked) {
    // The paths from watchman are not absolute. Because of this, we adopt a conservative approach
    // to invalidating the caches.
    for (DaemonicCellState state : cellToDaemonicState.values()) {
      invalidatePath(state, path, locked);
    }
  }

  /** Invalidate everything which depend on path. */
  public void invalidatePaths(Collection<AbsPath> paths) {
    if (paths.isEmpty()) {
      return;
    }

    try (AutoCloseableWriteLocked locked = locks.cachesLock.lockWrite()) {
      locks.markInvalidated(locked);
      for (AbsPath path : paths) {
        invalidatePath(path, locked);
      }
    }
  }

  /**
   * Finds the build file responsible for the given {@link Path} and invalidates all of the cached
   * rules dependent on it.
   *
   * @param path A {@link Path}, relative to the project root and "contained" within the build file
   *     to find and invalidate.
   */
  private void invalidateContainingBuildFile(
      DaemonicCellState state,
      Cell cell,
      BuildFileTree buildFiles,
      ForwardRelPath path,
      AutoCloseableWriteLocked locked) {
    LOG.verbose("Invalidating rules dependent on change to %s in cell %s", path, cell);
    Set<ForwardRelPath> packageBuildFiles = new HashSet<>();

    // Find the closest ancestor package for the input path.  We'll definitely need to invalidate
    // that.
    Optional<ForwardRelPath> packageBuildFile = buildFiles.getBasePathOfAncestorTarget(path);
    if (packageBuildFile.isPresent()) {
      packageBuildFiles.add(packageBuildFile.get());
    }

    // If we're *not* enforcing package boundary checks, it's possible for multiple ancestor
    // packages to reference the same file
    if (cell.getBuckConfigView(ParserConfig.class).getPackageBoundaryEnforcementPolicy(path)
        != ParserConfig.PackageBoundaryEnforcement.ENFORCE) {
      while (packageBuildFile.isPresent() && packageBuildFile.get().getParent() != null) {
        packageBuildFile =
            buildFiles.getBasePathOfAncestorTarget(packageBuildFile.get().getParent());
        if (packageBuildFile.isPresent()) {
          packageBuildFiles.add(packageBuildFile.get());
        }
      }
    }

    if (packageBuildFiles.isEmpty()) {
      LOG.debug("%s is not owned by any build file.  Not invalidating anything.", path);
      return;
    }

    counters.recordBuildFilesInvalidatedByFileAddOrRemove(packageBuildFiles.size());
    counters.recordPathsAddedOrRemovedInvalidatingBuildFiles(path.toString());

    // Invalidate all the packages we found.
    for (ForwardRelPath buildFile : packageBuildFiles) {
      ForwardRelPath withBuildFile =
          buildFile.resolve(cell.getBuckConfigView(ParserConfig.class).getBuildFileName());
      invalidatePath(state, cell.getRoot().resolve(withBuildFile), locked);
    }
  }

  /**
   * Remove the targets and rules defined by {@code path} from the cache and recursively remove the
   * targets and rules defined by files that transitively include {@code path} from the cache.
   *
   * @param path The File that has changed.
   */
  private void invalidatePath(
      DaemonicCellState state, AbsPath path, AutoCloseableWriteLocked locked) {
    LOG.verbose("Invalidating path %s for cell %s", path, state.getCellRoot());

    int invalidatedNodes = state.invalidatePath(path, true, locked);
    counters.recordRulesInvalidatedByWatchEvents(invalidatedNodes);
  }

  public static boolean isPathCreateOrDeleteEvent(WatchmanPathEvent event) {
    return event.getKind() == Kind.CREATE || event.getKind() == Kind.DELETE;
  }

  private boolean invalidateAllCachesLocked(AutoCloseableWriteLocked writeLock) {
    writeLock.markUsed();

    LOG.debug("Starting to invalidate all caches..");
    boolean invalidated = !cellToDaemonicState.isEmpty();
    cellToDaemonicState.clear();
    buildFileTrees.invalidateAll();
    configurationBuildFiles.clear();
    if (invalidated) {
      LOG.debug("Cache data invalidated.");
    } else {
      LOG.debug("Caches were empty, no data invalidated.");
    }
    return invalidated;
  }

  public ImmutableList<Counter> getCounters() {
    return counters.get();
  }

  public DaemonicParserValidationToken validationToken() {
    return locks.validationToken();
  }

  @Override
  public String toString() {
    return String.format("memoized=%s", cellToDaemonicState);
  }
}

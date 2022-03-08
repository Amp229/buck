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

package com.facebook.buck.core.cell.impl;

import com.facebook.buck.core.cell.Cell;
import com.facebook.buck.core.cell.CellConfig;
import com.facebook.buck.core.cell.CellName;
import com.facebook.buck.core.cell.CellPathResolver;
import com.facebook.buck.core.cell.CellPathResolverView;
import com.facebook.buck.core.cell.CellProvider;
import com.facebook.buck.core.cell.Cells;
import com.facebook.buck.core.cell.InvalidCellOverrideException;
import com.facebook.buck.core.cell.NewCellPathResolver;
import com.facebook.buck.core.cell.name.CanonicalCellName;
import com.facebook.buck.core.cell.nameresolver.CellNameResolver;
import com.facebook.buck.core.config.BuckConfig;
import com.facebook.buck.core.exceptions.HumanReadableException;
import com.facebook.buck.core.filesystems.AbsPath;
import com.facebook.buck.core.parser.buildtargetparser.UnconfiguredBuildTargetViewFactory;
import com.facebook.buck.core.toolchain.ToolchainProvider;
import com.facebook.buck.core.toolchain.ToolchainProviderFactory;
import com.facebook.buck.io.filesystem.BuckPaths;
import com.facebook.buck.io.filesystem.EmbeddedCellBuckOutInfo;
import com.facebook.buck.io.filesystem.ProjectFilesystem;
import com.facebook.buck.io.filesystem.ProjectFilesystemFactory;
import com.facebook.buck.io.watchman.Watchman;
import com.facebook.buck.rules.keys.config.RuleKeyConfiguration;
import com.facebook.buck.rules.keys.config.impl.ConfigRuleKeyConfigurationFactory;
import com.facebook.buck.util.config.Config;
import com.facebook.buck.util.config.Configs;
import com.facebook.buck.util.config.RawConfig;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import java.io.IOException;
import java.util.Optional;

/** The only implementation of {@link com.facebook.buck.core.cell.CellProvider}. */
final class CellProviderImpl implements CellProvider {

  private final NewCellPathResolver newCellPathResolver;
  private final ImmutableMap<CanonicalCellName, Cell> cells;
  private final ImmutableSet<AbsPath> allRoots;
  private final ImmutableMap<AbsPath, RawConfig> pathToConfigOverrides;
  private final CellPathResolver rootCellCellPathResolver;

  /**
   * Create a cell provider with a specific cell loader, and optionally a special factory function
   * for the root cell.
   *
   * <p>The indirection for passing in CellProvider allows cells to reference the current
   * CellProvider object.
   */
  CellProviderImpl(
      ProjectFilesystem rootFilesystem,
      BuckConfig rootConfig,
      CellConfig rootCellConfigOverrides,
      DefaultCellPathResolver rootCellCellPathResolver,
      ToolchainProviderFactory toolchainProviderFactory,
      ProjectFilesystemFactory projectFilesystemFactory,
      UnconfiguredBuildTargetViewFactory unconfiguredBuildTargetFactory,
      Watchman watchman,
      Optional<ImmutableMap<CanonicalCellName, Config>> reusePreviousConfigs) {
    this.rootCellCellPathResolver = rootCellCellPathResolver;

    ImmutableMap<CellName, AbsPath> cellPathMapping = rootCellCellPathResolver.getPathMapping();

    try {
      pathToConfigOverrides = rootCellConfigOverrides.getOverridesByPath(cellPathMapping);
    } catch (InvalidCellOverrideException e) {
      throw new HumanReadableException(e.getMessage());
    }

    allRoots = ImmutableSet.copyOf(cellPathMapping.values());

    if (reusePreviousConfigs.isPresent()) {
      Preconditions.checkState(
          rootConfig.getConfig() == reusePreviousConfigs.get().get(CanonicalCellName.rootCell()),
          "Buckconfig is a different object that was passed in reusePreviousConfigs");
    }

    newCellPathResolver =
        CellMappingsFactory.create(rootFilesystem.getRootPath(), rootConfig.getConfig());

    Cell rootCell =
        RootCellFactory.create(
            newCellPathResolver,
            rootCellCellPathResolver,
            toolchainProviderFactory,
            rootFilesystem,
            rootConfig);

    // The cell should only contain a subset of cell mappings of the root cell.
    // TODO(13777679): cells in other watchman roots do not work correctly.
    this.cells =
        newCellPathResolver.getCellToPathMap().keySet().stream()
            .collect(
                ImmutableMap.toImmutableMap(
                    cellName -> cellName,
                    cellName -> {
                      if (cellName == CanonicalCellName.rootCell()) {
                        return rootCell;
                      } else {
                        try {
                          return loadCell(
                              cellName,
                              newCellPathResolver,
                              allRoots,
                              pathToConfigOverrides,
                              rootCellCellPathResolver,
                              rootFilesystem,
                              projectFilesystemFactory,
                              watchman,
                              rootConfig,
                              unconfiguredBuildTargetFactory,
                              toolchainProviderFactory,
                              reusePreviousConfigs);
                        } catch (IOException e) {
                          throw new HumanReadableException(
                              e.getCause(), "Failed to load Cell at: %s", cellName);
                        }
                      }
                    }));

    Preconditions.checkState(cells.containsKey(CanonicalCellName.rootCell()));
  }

  private static Cell loadCell(
      CanonicalCellName canonicalCellName,
      NewCellPathResolver newCellPathResolver,
      ImmutableSet<AbsPath> allRoots,
      ImmutableMap<AbsPath, RawConfig> pathToConfigOverrides,
      CellPathResolver rootCellCellPathResolver,
      ProjectFilesystem rootFilesystem,
      ProjectFilesystemFactory projectFilesystemFactory,
      Watchman watchman,
      BuckConfig rootConfig,
      UnconfiguredBuildTargetViewFactory unconfiguredBuildTargetFactory,
      ToolchainProviderFactory toolchainProviderFactory,
      Optional<ImmutableMap<CanonicalCellName, Config>> reusePreviousConfigs)
      throws IOException {
    AbsPath cellPath = newCellPathResolver.getCellPath(canonicalCellName);
    AbsPath normalizedCellPath = cellPath.toRealPath().normalize();

    Preconditions.checkState(
        allRoots.contains(normalizedCellPath),
        "Cell %s outside of transitive closure of root cell (%s).",
        normalizedCellPath,
        allRoots);

    Config config;

    if (reusePreviousConfigs.isPresent()) {
      config = reusePreviousConfigs.get().get(canonicalCellName);
      Preconditions.checkState(
          config != null, "no mapping for cell '%s' in config overrides map", canonicalCellName);
    } else {
      RawConfig configOverrides =
          Optional.ofNullable(pathToConfigOverrides.get(normalizedCellPath))
              .orElse(RawConfig.of(ImmutableMap.of()));
      config = Configs.createDefaultConfig(normalizedCellPath.getPath(), configOverrides);
    }

    ImmutableMap<String, AbsPath> cellMapping =
        DefaultCellPathResolver.getCellPathsFromConfigRepositoriesSection(
            cellPath, config.get(DefaultCellPathResolver.REPOSITORIES_SECTION));

    // The cell should only contain a subset of cell mappings of the root cell.
    cellMapping.forEach(
        (name, path) -> {
          AbsPath pathInRootResolver =
              rootCellCellPathResolver.getCellPathsByRootCellExternalName().get(name);
          if (pathInRootResolver == null) {
            throw new HumanReadableException(
                "In the config of %s:  %s.%s must exist in the root cell's cell mappings.",
                cellPath.toString(), DefaultCellPathResolver.REPOSITORIES_SECTION, name);
          } else if (!pathInRootResolver.equals(path)) {
            throw new HumanReadableException(
                "In the config of %s:  %s.%s must point to the same directory as the root "
                    + "cell's cell mapping: (root) %s != (current) %s",
                cellPath.toString(),
                DefaultCellPathResolver.REPOSITORIES_SECTION,
                name,
                pathInRootResolver,
                path);
          }
        });
    CellNameResolver cellNameResolver =
        CellMappingsFactory.createCellNameResolver(cellPath, config, newCellPathResolver);

    CellPathResolver cellPathResolver =
        new CellPathResolverView(
            rootCellCellPathResolver, cellNameResolver, cellMapping.keySet(), cellPath);

    Optional<EmbeddedCellBuckOutInfo> embeddedCellBuckOutInfo = Optional.empty();
    if (canonicalCellName.getLegacyName().isPresent()) {
      embeddedCellBuckOutInfo =
          Optional.of(
              EmbeddedCellBuckOutInfo.of(
                  rootFilesystem.getRootPath().getPath(),
                  rootFilesystem.getBuckPaths(),
                  canonicalCellName));
    }
    ProjectFilesystem cellFilesystem =
        projectFilesystemFactory.createProjectFilesystem(
            canonicalCellName,
            normalizedCellPath,
            config,
            embeddedCellBuckOutInfo,
            BuckPaths.getBuckOutIncludeTargetConfigHashFromRootCellConfig(rootConfig.getConfig()),
            watchman);

    BuckConfig buckConfig =
        new BuckConfig(
            config,
            cellFilesystem,
            rootConfig.getArchitecture(),
            rootConfig.getPlatform(),
            rootConfig.getEnvironment(),
            unconfiguredBuildTargetFactory,
            cellPathResolver.getCellNameResolver());

    RuleKeyConfiguration ruleKeyConfiguration =
        ConfigRuleKeyConfigurationFactory.create(buckConfig);

    ToolchainProvider toolchainProvider =
        toolchainProviderFactory.create(buckConfig, cellFilesystem, ruleKeyConfiguration);

    // TODO(13777679): cells in other watchman roots do not work correctly.

    return ImmutableCellImpl.ofImpl(
        canonicalCellName,
        cellFilesystem,
        buckConfig,
        toolchainProvider,
        cellPathResolver,
        newCellPathResolver,
        cellNameResolver);
  }

  @Override
  public Cell getCellByCanonicalCellName(CanonicalCellName canonicalCellName) {
    Cell cell = cells.get(canonicalCellName);
    Preconditions.checkState(cell != null, "unknown cell: '%s'", canonicalCellName);
    return cell;
  }

  @Override
  public Cells getRootCell() {
    return new Cells(this);
  }

  @Override
  public CellPathResolver getRootCellCellPathResolver() {
    return rootCellCellPathResolver;
  }

  @Override
  public ImmutableList<Cell> getAllCells() {
    return cells.values().asList();
  }
}

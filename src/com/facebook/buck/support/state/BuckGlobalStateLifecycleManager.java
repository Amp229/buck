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

package com.facebook.buck.support.state;

import com.facebook.buck.command.config.ConfigDifference;
import com.facebook.buck.core.cell.Cell;
import com.facebook.buck.core.cell.Cells;
import com.facebook.buck.core.cell.name.CanonicalCellName;
import com.facebook.buck.core.config.BuckConfig;
import com.facebook.buck.core.model.TargetConfigurationSerializer;
import com.facebook.buck.core.parser.buildtargetparser.UnconfiguredBuildTargetViewFactory;
import com.facebook.buck.core.rules.knowntypes.provider.KnownRuleTypesProvider;
import com.facebook.buck.core.util.immutables.BuckStyleValue;
import com.facebook.buck.core.util.log.Logger;
import com.facebook.buck.httpserver.WebServer;
import com.facebook.buck.io.watchman.Watchman;
import com.facebook.buck.io.watchman.WatchmanFactory;
import com.facebook.buck.util.Console;
import com.facebook.buck.util.timing.Clock;
import com.facebook.buck.util.types.Pair;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import javax.annotation.concurrent.ThreadSafe;
import org.immutables.value.Value;

/**
 * Guards access to the {@link BuckGlobalState} instance behind cell configuration checks. Creates
 * or re-creates the daemon state as necessary if the cell configuration changes.
 */
@ThreadSafe
public class BuckGlobalStateLifecycleManager {
  private static final Logger LOG = Logger.get(BuckGlobalStateLifecycleManager.class);

  @Nullable private volatile BuckGlobalState buckGlobalState;

  /** Indicates whether a daemon's {@link BuckGlobalState} is reused, or why it can't be reused */
  public enum LifecycleStatus {
    REUSED,
    NEW,
    INVALIDATED_NO_WATCHMAN,
    INVALIDATED_WATCHMAN_RESTARTED,
    INVALIDATED_FILESYSTEM_CHANGED,
    INVALIDATED_BUCK_CONFIG_CHANGED,
    INVALIDATED_TOOLCHAINS_INCOMPATIBLE;

    private String toHumanReadableError() {
      switch (this) {
        case REUSED:
          return "Reusing existing daemon's stored global state";
        case NEW:
          return "Initializing daemon state for the first time";
        case INVALIDATED_NO_WATCHMAN:
          return "Watchman failed to start";
        case INVALIDATED_WATCHMAN_RESTARTED:
          return "Watchman restarted";
        case INVALIDATED_FILESYSTEM_CHANGED:
          return "The project directory changed between invocations";
        case INVALIDATED_BUCK_CONFIG_CHANGED:
          return "Buck configuration options changed between invocations";
        case INVALIDATED_TOOLCHAINS_INCOMPATIBLE:
          return "Available / configured toolchains changed between invocations";
        default:
          throw new AssertionError(String.format("Unknown value: %s", this));
      }
    }

    /** Get the string to be logged as an event, if an event should be logged. */
    public Optional<String> getLifecycleStatusString() {
      switch (this) {
        case REUSED:
          return Optional.empty();
        case NEW:
          return Optional.of("DaemonInitialized");
        case INVALIDATED_NO_WATCHMAN:
          return Optional.of("DaemonWatchmanInvalidated");
        case INVALIDATED_WATCHMAN_RESTARTED:
          return Optional.of("DaemonWatchmanRestarted");
        case INVALIDATED_FILESYSTEM_CHANGED:
          return Optional.of("DaemonFilesystemInvalidated");
        case INVALIDATED_BUCK_CONFIG_CHANGED:
          return Optional.of("DaemonBuckConfigInvalidated");
        case INVALIDATED_TOOLCHAINS_INCOMPATIBLE:
          return Optional.of("DaemonToolchainsInvalidated");
        default:
          throw new AssertionError(String.format("Unknown value: %s", this));
      }
    }
  }

  public synchronized boolean hasStoredBuckGlobalState() {
    return buckGlobalState != null;
  }

  public synchronized Optional<BuckConfig> getBuckConfig() {
    return getCells().map(Cells::getBuckConfig);
  }

  public synchronized Optional<Cells> getCells() {
    return Optional.ofNullable(buckGlobalState).map(BuckGlobalState::getCells);
  }

  /** Get or create Daemon. */
  public synchronized Pair<BuckGlobalState, LifecycleStatus> getBuckGlobalState(
      Cells cells,
      Supplier<KnownRuleTypesProvider> knownRuleTypesProviderFactory,
      Watchman watchman,
      Console console,
      Clock clock,
      UnconfiguredBuildTargetViewFactory unconfiguredBuildTargetFactory,
      TargetConfigurationSerializer targetConfigurationSerializer) {

    Cell rootCell = cells.getRootCell();

    BuckGlobalState currentState = buckGlobalState;
    LifecycleStatus lifecycleStatus =
        buckGlobalState == null ? LifecycleStatus.NEW : LifecycleStatus.REUSED;

    // If Watchman failed to start, drop all caches
    if (buckGlobalState != null && watchman instanceof WatchmanFactory.NullWatchman) {
      // TODO(buck_team): make Watchman a requirement
      LOG.info("Restarting daemon state because watchman failed to start");
      lifecycleStatus = LifecycleStatus.INVALIDATED_NO_WATCHMAN;
      buckGlobalState = null;
    }

    // If the state was previously created without Watchman, but now Watchman becomes available,
    // drop all caches. Ideally, Watchman should be a requirement.
    if (buckGlobalState != null
        && !(watchman instanceof WatchmanFactory.NullWatchman)
        && !buckGlobalState.getUsesWatchman()) {
      LOG.info("Restarting daemon state because watchman was restarted");
      lifecycleStatus = LifecycleStatus.INVALIDATED_WATCHMAN_RESTARTED;
      buckGlobalState = null;
    }

    ImmutableMap<ConfigDifference.ConfigKey, ConfigDifference.ConfigChange> configDifference =
        ImmutableMap.of();
    CanonicalCellName configDifferenceCell = CanonicalCellName.rootCell();
    // If Buck config has changed or SDKs have changed, drop all caches
    if (buckGlobalState != null) {
      // Check if current cell and cell for cached state are similar enough to re-use global state.
      // Considers only a subset of the information provided by the cells.

      Cells stateCells = buckGlobalState.getCells();

      CellCompareResult compareCells = compareCells(stateCells, cells);
      if (compareCells.getLifecycleStatus() != LifecycleStatus.REUSED) {
        lifecycleStatus = compareCells.getLifecycleStatus();
        configDifference = compareCells.getConfigDifference();
        configDifferenceCell = compareCells.getCellName();

        LOG.info(
            "Shutting down and restarting daemon state on config or directory graphBuilder change (%s != %s)",
            stateCells.getRootCell(), rootCell);
        buckGlobalState = null;
      }
    }

    // if we restart daemon, notify user that caches are screwed
    if (buckGlobalState == null
        && currentState != null
        && console.getVerbosity().shouldPrintStandardInformation()) {

      StringBuilder warning =
          new StringBuilder(
              String.format(
                  "Invalidating internal cached state: %s. This may cause slower builds.",
                  lifecycleStatus.toHumanReadableError()));
      if (!configDifference.isEmpty()) {
        warning
            .append(System.lineSeparator())
            .append(
                ConfigDifference.formatConfigDiffShort(configDifferenceCell, configDifference, 2));
        LOG.info(
            "Config changes: %s",
            ConfigDifference.formatConfigDiff(configDifferenceCell, configDifference));
      }
      // Use the raw stream because otherwise this will stop superconsole from ever printing again
      console
          .getStdErr()
          .getRawStream()
          .println(console.getAnsi().asWarningText(warning.toString()));
    }

    // start new daemon, clean old one if needed
    if (buckGlobalState == null) {
      LOG.debug(
          "Starting up daemon state for project root [%s]", rootCell.getFilesystem().getRootPath());

      // try to reuse webserver from previous state
      // does it need to be in daemon at all?
      Optional<WebServer> webServer =
          currentState != null && shouldReuseWebServer(rootCell)
              ? currentState.getWebServer()
              : Optional.empty();

      if (currentState != null) {
        currentState.close();
      }

      buckGlobalState =
          BuckGlobalStateFactory.create(
              cells,
              knownRuleTypesProviderFactory.get(),
              watchman,
              webServer,
              unconfiguredBuildTargetFactory,
              targetConfigurationSerializer,
              clock);
    }

    return new Pair<>(buckGlobalState, lifecycleStatus);
  }

  /** A pair returned from cell compare. */
  @BuckStyleValue
  abstract static class CellCompareResult {

    /** Which cell produced the result. Ignore when there's no change. */
    public abstract CanonicalCellName getCellName();

    public abstract LifecycleStatus getLifecycleStatus();

    public abstract ImmutableMap<ConfigDifference.ConfigKey, ConfigDifference.ConfigChange>
        getConfigDifference();

    @Value.Check
    protected void check() {
      Preconditions.checkArgument(
          (getLifecycleStatus() == LifecycleStatus.INVALIDATED_BUCK_CONFIG_CHANGED)
              == !getConfigDifference().isEmpty());
    }

    public static CellCompareResult of(
        CanonicalCellName cellName, LifecycleStatus lifecycleStatus) {
      return ImmutableCellCompareResult.ofImpl(cellName, lifecycleStatus, ImmutableMap.of());
    }
  }

  /** Compare cells, return non-empty lifecycle status if global state needs to be invalidated. */
  private CellCompareResult compareCells(Cells stateCells, Cells newCells) {
    CellCompareResult rootCellCompareResult =
        compareCell(stateCells.getRootCell(), newCells.getRootCell());
    if (rootCellCompareResult.getLifecycleStatus() != LifecycleStatus.REUSED) {
      return rootCellCompareResult;
    }

    ImmutableMap<CanonicalCellName, Cell> stateCellsByName =
        stateCells.getAllCells().stream()
            .collect(ImmutableMap.toImmutableMap(Cell::getCanonicalName, c -> c));
    ImmutableMap<CanonicalCellName, Cell> newCellsByName =
        newCells.getAllCells().stream()
            .collect(ImmutableMap.toImmutableMap(Cell::getCanonicalName, c -> c));

    Preconditions.checkState(
        stateCellsByName.keySet().equals(newCellsByName.keySet()),
        "we already validated root cell buckconfig is identical");

    for (Map.Entry<CanonicalCellName, Cell> stateCellEntry : stateCellsByName.entrySet()) {
      CanonicalCellName cellName = stateCellEntry.getKey();
      if (cellName == CanonicalCellName.rootCell()) {
        // we already compared root cell before
        continue;
      }

      Cell stateCell = stateCellEntry.getValue();
      Cell newCell = newCellsByName.get(cellName);
      Preconditions.checkNotNull(
          newCell,
          "we already compared root cell, and it is identical, so new cell must be not null");
      CellCompareResult lifecycleStatus = compareCell(stateCell, newCell);
      if (lifecycleStatus.getLifecycleStatus() != LifecycleStatus.REUSED) {
        return lifecycleStatus;
      }
    }

    return CellCompareResult.of(CanonicalCellName.rootCell(), LifecycleStatus.REUSED);
  }

  private CellCompareResult compareCell(Cell stateCell, Cell newCell) {
    Preconditions.checkArgument(
        stateCell.getCanonicalName().equals(newCell.getCanonicalName()),
        "comparing different cells: '%s' and '%s'",
        stateCell.getCanonicalName(),
        newCell.getCanonicalName());

    CanonicalCellName cellName = stateCell.getCanonicalName();

    if (!stateCell.getFilesystem().equals(newCell.getFilesystem())) {
      return CellCompareResult.of(cellName, LifecycleStatus.INVALIDATED_FILESYSTEM_CHANGED);
    }

    ImmutableMap<ConfigDifference.ConfigKey, ConfigDifference.ConfigChange> configDifference =
        ConfigDifference.compareForCaching(stateCell.getBuckConfig(), newCell.getBuckConfig());
    if (!configDifference.isEmpty()) {
      return ImmutableCellCompareResult.ofImpl(
          cellName, LifecycleStatus.INVALIDATED_BUCK_CONFIG_CHANGED, configDifference);
    }

    if (!BuckGlobalStateCompatibilityCellChecker.areToolchainsCompatibleForCaching(
        stateCell, newCell)) {
      return CellCompareResult.of(cellName, LifecycleStatus.INVALIDATED_TOOLCHAINS_INCOMPATIBLE);
    }

    return CellCompareResult.of(cellName, LifecycleStatus.REUSED);
  }

  /** Manually reset the {@link BuckGlobalState}, used for testing. */
  public synchronized void resetBuckGlobalState() {
    if (buckGlobalState != null) {
      LOG.info("Closing daemon's global state on reset request.");
      buckGlobalState.close();
    }
    buckGlobalState = null;
  }

  private boolean shouldReuseWebServer(Cell newCell) {
    if (newCell == null || buckGlobalState == null) {
      return false;
    }
    OptionalInt portFromOldConfig =
        getBuckConfig()
            .map(BuckGlobalStateFactory::getValidWebServerPort)
            .orElse(OptionalInt.empty());
    OptionalInt portFromUpdatedConfig =
        BuckGlobalStateFactory.getValidWebServerPort(newCell.getBuckConfig());

    return portFromOldConfig.equals(portFromUpdatedConfig);
  }
}

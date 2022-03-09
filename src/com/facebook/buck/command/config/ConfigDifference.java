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

package com.facebook.buck.command.config;

import com.facebook.buck.core.cell.name.CanonicalCellName;
import com.facebook.buck.core.config.BuckConfig;
import com.facebook.buck.core.util.immutables.BuckStyleValue;
import com.facebook.buck.util.config.Config;
import com.facebook.buck.util.string.MoreStrings;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.MapDifference;
import com.google.common.collect.MapDifference.ValueDifference;
import com.google.common.collect.Maps;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import org.immutables.value.Value;

/**
 * Helper methods for calculating and logging the config differences that cause state invalidation
 */
public class ConfigDifference {
  /** Compares all values in two sets of configs */
  public static ImmutableMap<ConfigKey, ConfigChange> compare(Config config1, Config config2) {
    return compare(config1.getRawConfig().getValues(), config2.getRawConfig().getValues());
  }

  /**
   * Compares only the config options that invalidate global state
   *
   * @return The difference as a map of 'section.key' strings to changed values
   */
  public static ImmutableMap<ConfigKey, ConfigChange> compareForCaching(
      BuckConfig buckConfig1, BuckConfig buckConfig2) {
    // This is a hack. A cleaner approach would be to expose a narrow view of the config to any
    // code that affects the state cached.
    ImmutableMap<String, ImmutableMap<String, String>> rawConfig1 =
        buckConfig1.getView(ConfigIgnoredByDaemon.class).getRawConfigForParser();
    ImmutableMap<String, ImmutableMap<String, String>> rawConfig2 =
        buckConfig2.getView(ConfigIgnoredByDaemon.class).getRawConfigForParser();

    return compare(rawConfig1, rawConfig2);
  }

  /**
   * Compares sets of config options, and returns the difference as a map of 'section.key' strings
   * to pairs containing the different values.
   */
  @VisibleForTesting
  public static ImmutableMap<ConfigKey, ConfigChange> compare(
      ImmutableMap<String, ImmutableMap<String, String>> rawConfig1,
      ImmutableMap<String, ImmutableMap<String, String>> rawConfig2) {
    MapDifference<String, ImmutableMap<String, String>> diffSections =
        Maps.difference(rawConfig1, rawConfig2);
    if (!diffSections.areEqual()) {
      ImmutableMap.Builder<ConfigKey, ConfigChange> result = ImmutableMap.builder();

      BiConsumer<String, Map<String, ValueDifference<String>>> appendChange =
          (section, diff) ->
              diff.forEach(
                  (option, value) ->
                      result.put(
                          ImmutableConfigKey.ofImpl(section, option),
                          ImmutableConfigChange.ofImpl(value.leftValue(), value.rightValue())));
      BiConsumer<String, Map<String, String>> appendLeft =
          (section, diff) ->
              diff.forEach(
                  (option, value) ->
                      result.put(
                          ImmutableConfigKey.ofImpl(section, option),
                          ImmutableConfigChange.ofImpl(value, null)));
      BiConsumer<String, Map<String, String>> appendRight =
          (section, diff) ->
              diff.forEach(
                  (option, value) ->
                      result.put(
                          ImmutableConfigKey.ofImpl(section, option),
                          ImmutableConfigChange.ofImpl(null, value)));

      diffSections
          .entriesDiffering()
          .forEach(
              (section, diff) -> {
                MapDifference<String, String> sectionDiff =
                    Maps.difference(diff.leftValue(), diff.rightValue());
                appendChange.accept(section, sectionDiff.entriesDiffering());
                appendLeft.accept(section, sectionDiff.entriesOnlyOnLeft());
                appendRight.accept(section, sectionDiff.entriesOnlyOnRight());
              });

      diffSections.entriesOnlyOnLeft().forEach(appendLeft);
      diffSections.entriesOnlyOnRight().forEach(appendRight);
      return result.build();
    }
    return ImmutableMap.of();
  }

  /** Section-dot-property. */
  @BuckStyleValue
  public abstract static class ConfigKey {
    public abstract String getSection();

    public abstract String getProperty();

    @Override
    public final String toString() {
      return getSection() + "." + getProperty();
    }

    public static ConfigKey of(String section, String property) {
      return ImmutableConfigKey.ofImpl(section, property);
    }
  }

  /** A single changed config value */
  @BuckStyleValue
  public abstract static class ConfigChange {
    @Nullable
    public abstract String getPrevValue();

    @Nullable
    public abstract String getNewValue();

    @Value.Check
    protected void check() {
      Preconditions.checkArgument(
          getPrevValue() != null || getNewValue() != null, "prevValue or newValue must be defined");
    }

    public static ConfigChange of(@Nullable String prevValue, @Nullable String newValue) {
      return ImmutableConfigChange.ofImpl(prevValue, newValue);
    }
  }

  /** Format a set of changes between configs for the console */
  public static String formatConfigDiffShort(
      CanonicalCellName cellName, ImmutableMap<ConfigKey, ConfigChange> diff, int maxLines) {
    StringBuilder builder = new StringBuilder();
    int linesToPrint = diff.size() <= maxLines ? maxLines : maxLines - 1;
    builder
        .append("  ")
        .append(
            diff.entrySet().stream()
                .limit(linesToPrint)
                .map(change -> formatConfigChange(cellName, change, true))
                .collect(Collectors.joining(System.lineSeparator() + "  ")));
    if (linesToPrint < diff.size()) {
      builder
          .append(System.lineSeparator())
          .append("  ... and ")
          .append(diff.size() - linesToPrint)
          .append(" more. See logs for all changes");
    }
    return builder.toString();
  }

  /** Format the full set of changes between configs to be logged */
  public static String formatConfigDiff(
      CanonicalCellName cellName, Map<ConfigKey, ConfigChange> diff) {
    return diff.entrySet().stream()
        .map(change -> formatConfigChange(cellName, change, false))
        .collect(Collectors.joining(", "));
  }

  /** Format a single config change */
  public static String formatConfigChange(
      CanonicalCellName cellName, Entry<ConfigKey, ConfigChange> change, boolean truncate) {
    String prevVal = change.getValue().getPrevValue();
    String newVal = change.getValue().getNewValue();
    BiFunction<String, Integer, String> abbrev =
        (value, width) -> truncate ? MoreStrings.abbreviate(value, width) : value;
    if (prevVal == null) {
      return String.format(
          "New value %s//%s='%s'", cellName, change.getKey(), abbrev.apply(newVal, 80));
    }
    if (newVal == null) {
      return String.format(
          "Removed value %s//%s='%s'", cellName, change.getKey(), abbrev.apply(prevVal, 80));
    }

    return String.format(
        "Changed value %s//%s='%s' (was '%s')",
        cellName, change.getKey(), abbrev.apply(newVal, 40), abbrev.apply(prevVal, 40));
  }
}

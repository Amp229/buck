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

package com.facebook.buck.core.config;

import com.facebook.buck.core.cell.CellPathResolver;
import com.facebook.buck.core.cell.impl.DefaultCellPathResolver;
import com.facebook.buck.core.parser.buildtargetparser.ParsingUnconfiguredBuildTargetViewFactory;
import com.facebook.buck.core.parser.buildtargetparser.UnconfiguredBuildTargetViewFactory;
import com.facebook.buck.io.filesystem.ProjectFilesystem;
import com.facebook.buck.io.filesystem.impl.FakeProjectFilesystem;
import com.facebook.buck.util.config.Config;
import com.facebook.buck.util.config.ConfigBuilder;
import com.facebook.buck.util.config.RawConfig;
import com.facebook.buck.util.environment.Architecture;
import com.facebook.buck.util.environment.EnvVariablesProvider;
import com.facebook.buck.util.environment.Platform;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import java.nio.file.Path;
import java.util.Optional;
import javax.annotation.Nullable;

/**
 * Implementation of {@link BuckConfig} with no data, or only the data specified by {@link
 * FakeBuckConfig.Builder#setSections(ImmutableMap)}}. This makes it possible to get an instance of
 * a {@link BuckConfig} without reading {@code .buckconfig} files from disk. Designed exclusively
 * for testing.
 */
public class FakeBuckConfig {

  private FakeBuckConfig() {
    // Utility class
  }

  public static Builder builder() {
    return new Builder();
  }

  public static BuckConfig empty() {
    return builder().build();
  }

  public static String getPropertyString(String propertyName, boolean enabled) {
    return String.format("%s = %s", propertyName, enabled);
  }

  public static class Builder {
    @Nullable private ProjectFilesystem filesystem = null;
    private ImmutableMap<String, String> environment = EnvVariablesProvider.getSystemEnv();
    private RawConfig sections = RawConfig.of();
    private ImmutableMap<Path, RawConfig> configsMap = ImmutableMap.of();
    private Optional<RawConfig> rawOverrides = Optional.empty();
    private Architecture architecture = Architecture.detect();
    private Platform platform = Platform.detect();
    private final int numThreads = -1;
    private ImmutableMap<String, ImmutableSet<String>> nonSerializableREConfigFields;

    public Builder setArchitecture(Architecture architecture) {
      this.architecture = architecture;
      return this;
    }

    public Builder setEnvironment(ImmutableMap<String, String> environment) {
      this.environment = environment;
      return this;
    }

    public Builder setFilesystem(ProjectFilesystem filesystem) {
      Preconditions.checkNotNull(filesystem);
      this.filesystem = filesystem;
      return this;
    }

    public Builder setPlatform(Platform platform) {
      this.platform = platform;
      return this;
    }

    public Builder setSections(RawConfig sections) {
      this.sections = sections;
      return this;
    }

    public Builder setSections(RawConfig sections, Path sourcePath) {
      this.sections = sections;
      this.configsMap = ImmutableMap.of(sourcePath, sections);
      return this;
    }

    public Builder setSections(ImmutableMap<String, ImmutableMap<String, String>> sections) {
      this.sections = RawConfig.of(sections);
      return this;
    }

    public Builder setSections(String... iniFileLines) {
      sections = ConfigBuilder.rawFromLines(iniFileLines);
      return this;
    }

    public Builder setSections(RawConfig sections, ImmutableMap<Path, RawConfig> configsMap) {
      this.sections = sections;
      this.configsMap = configsMap;
      return this;
    }

    public Builder setOverrides(RawConfig overrides) {
      this.rawOverrides = Optional.of(overrides);
      return this;
    }

    public Builder setNonSerializableREConfigFields(
        ImmutableMap<String, ImmutableSet<String>> nonSerializableREConfigFields) {
      this.nonSerializableREConfigFields = nonSerializableREConfigFields;
      return this;
    }

    public BuckConfig build() {
      ProjectFilesystem filesystem =
          this.filesystem != null ? this.filesystem : new FakeProjectFilesystem();

      Config config =
          new Config(sections, configsMap)
              .overrideWith(new Config(rawOverrides.orElseGet(RawConfig::of)));

      CellPathResolver cellPathResolver =
          DefaultCellPathResolver.create(filesystem.getRootPath(), config);
      UnconfiguredBuildTargetViewFactory buildTargetFactory =
          new ParsingUnconfiguredBuildTargetViewFactory();
      BuckConfig buckConfig =
          new BuckConfig(
              config,
              filesystem,
              architecture,
              platform,
              environment,
              buildTargetFactory,
              cellPathResolver.getCellNameResolver());
      buckConfig.setNonSerializableREConfigFields(nonSerializableREConfigFields);

      return buckConfig;
    }
  }
}

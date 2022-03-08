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
import com.facebook.buck.core.cell.CellPathResolver;
import com.facebook.buck.core.cell.NewCellPathResolver;
import com.facebook.buck.core.cell.name.CanonicalCellName;
import com.facebook.buck.core.config.BuckConfig;
import com.facebook.buck.core.toolchain.ToolchainProvider;
import com.facebook.buck.core.toolchain.ToolchainProviderFactory;
import com.facebook.buck.io.filesystem.ProjectFilesystem;
import com.facebook.buck.rules.keys.config.RuleKeyConfiguration;
import com.facebook.buck.rules.keys.config.impl.ConfigRuleKeyConfigurationFactory;
import com.google.common.base.Preconditions;

/**
 * Creates a root cell, i.e. a cell that is representing the current repository.
 *
 * <p>The root cell is different from other cells: it doesn't require a path to the repository
 * directory since the root of the provided filesystem is considered to be the root of the cell. Its
 * name is also empty.
 */
public class RootCellFactory {

  static Cell create(
      NewCellPathResolver newCellPathResolver,
      CellPathResolver rootCellCellPathResolver,
      ToolchainProviderFactory toolchainProviderFactory,
      ProjectFilesystem rootFilesystem,
      BuckConfig rootConfig) {
    Preconditions.checkState(
        !rootCellCellPathResolver.getCanonicalCellName(rootFilesystem.getRootPath()).isPresent(),
        "Root cell should be nameless");
    RuleKeyConfiguration ruleKeyConfiguration =
        ConfigRuleKeyConfigurationFactory.create(rootConfig);
    ToolchainProvider toolchainProvider =
        toolchainProviderFactory.create(rootConfig, rootFilesystem, ruleKeyConfiguration);

    return ImmutableCellImpl.ofImpl(
        CanonicalCellName.rootCell(),
        rootFilesystem,
        rootConfig,
        toolchainProvider,
        rootCellCellPathResolver,
        newCellPathResolver,
        rootCellCellPathResolver.getCellNameResolver());
  }
}

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

package com.facebook.buck.cxx.toolchain;

import com.facebook.buck.apple.clang.ModuleMap;
import com.facebook.buck.core.build.buildable.context.BuildableContext;
import com.facebook.buck.core.build.context.BuildContext;
import com.facebook.buck.core.filesystems.RelPath;
import com.facebook.buck.core.model.BuildTarget;
import com.facebook.buck.core.model.impl.BuildTargetPaths;
import com.facebook.buck.core.rulekey.AddToRuleKey;
import com.facebook.buck.core.sourcepath.ExplicitBuildTargetSourcePath;
import com.facebook.buck.core.sourcepath.SourcePath;
import com.facebook.buck.io.filesystem.ProjectFilesystem;
import com.facebook.buck.step.Step;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSortedMap;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Ordering;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

public final class HeaderSymlinkTreeWithModuleMap extends HeaderSymlinkTree {

  @AddToRuleKey private final String moduleName;
  @AddToRuleKey private final boolean useSubmodules;
  @AddToRuleKey private boolean moduleRequiresCplusplus;

  private HeaderSymlinkTreeWithModuleMap(
      BuildTarget target,
      ProjectFilesystem filesystem,
      Path root,
      ImmutableMap<Path, SourcePath> links,
      String moduleName,
      boolean useSubmodules,
      boolean moduleRequiresCplusplus) {
    super(target, filesystem, root, links);
    this.moduleName = moduleName;
    this.useSubmodules = useSubmodules;
    this.moduleRequiresCplusplus = moduleRequiresCplusplus;
  }

  public static HeaderSymlinkTreeWithModuleMap create(
      BuildTarget target,
      ProjectFilesystem filesystem,
      Path root,
      ImmutableMap<Path, SourcePath> links,
      String moduleName,
      boolean useSubmodules,
      boolean moduleRequiresCplusplus) {
    return new HeaderSymlinkTreeWithModuleMap(
        target, filesystem, root, links, moduleName, useSubmodules, moduleRequiresCplusplus);
  }

  @Override
  public SourcePath getSourcePathToOutput() {
    return ExplicitBuildTargetSourcePath.of(
        getBuildTarget(), moduleMapPath(getProjectFilesystem(), getBuildTarget()));
  }

  @Override
  public ImmutableList<Step> getBuildSteps(
      BuildContext context, BuildableContext buildableContext) {
    ImmutableSortedSet<Path> paths = getLinks().keySet();

    ImmutableList.Builder<Step> builder =
        ImmutableList.<Step>builder().addAll(super.getBuildSteps(context, buildableContext));
    Path expectedSwiftHeaderPath = Paths.get(moduleName, moduleName + "-Swift.h");
    ImmutableSortedSet<Path> pathsWithoutSwiftHeader =
        paths.stream()
            .filter(path -> !path.equals(expectedSwiftHeaderPath))
            .collect(ImmutableSortedSet.toImmutableSortedSet(Ordering.natural()));
    Path moduleMapPath = moduleMapPath(getProjectFilesystem(), getBuildTarget()).getPath();

    builder.add(
        new ModuleMapStep(
            getProjectFilesystem(),
            moduleMapPath,
            ModuleMap.create(
                moduleName,
                pathsWithoutSwiftHeader,
                paths.contains(expectedSwiftHeaderPath)
                    ? Optional.of(expectedSwiftHeaderPath)
                    : Optional.empty(),
                useSubmodules,
                moduleRequiresCplusplus)));
    return builder.build();
  }

  static RelPath moduleMapPath(ProjectFilesystem filesystem, BuildTarget target) {
    return BuildTargetPaths.getGenPath(filesystem.getBuckPaths(), target, "%s/module.modulemap");
  }

  @Override
  public ImmutableSortedMap<Path, SourcePath> getExtraHeaders() {
    // If this symlink tree has a modulemap we need to add it to the nameToPathMap. This is
    // required for depfile pruning to work correctly, if we don't include the modulemap path
    // then the .d file entry will not be included in the CxxPreprocessAndCompile inputs.
    Path modulemapPath = moduleMapPath(getProjectFilesystem(), getBuildTarget()).getPath();
    return ImmutableSortedMap.of(modulemapPath, getSourcePathToOutput());
  }

  public String getModuleName() {
    return moduleName;
  }
}

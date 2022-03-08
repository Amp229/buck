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

package com.facebook.buck.features.halide;

import com.facebook.buck.core.build.buildable.context.BuildableContext;
import com.facebook.buck.core.build.context.BuildContext;
import com.facebook.buck.core.filesystems.RelPath;
import com.facebook.buck.core.model.BuildTarget;
import com.facebook.buck.core.model.impl.BuildTargetPaths;
import com.facebook.buck.core.rulekey.AddToRuleKey;
import com.facebook.buck.core.rules.BuildRuleParams;
import com.facebook.buck.core.rules.impl.AbstractBuildRuleWithDeclaredAndExtraDeps;
import com.facebook.buck.core.sourcepath.ExplicitBuildTargetSourcePath;
import com.facebook.buck.core.sourcepath.SourcePath;
import com.facebook.buck.core.toolchain.tool.Tool;
import com.facebook.buck.io.filesystem.BuildCellRelativePath;
import com.facebook.buck.io.filesystem.ProjectFilesystem;
import com.facebook.buck.io.filesystem.impl.ProjectFilesystemUtils;
import com.facebook.buck.step.Step;
import com.facebook.buck.step.fs.MakeCleanDirectoryStep;
import com.google.common.collect.ImmutableList;
import java.nio.file.Path;
import java.util.Optional;

public class HalideCompile extends AbstractBuildRuleWithDeclaredAndExtraDeps {

  @AddToRuleKey private final Tool halideCompiler;
  @AddToRuleKey private final String targetPlatform;
  @AddToRuleKey private final Optional<ImmutableList<String>> compilerInvocationFlags;
  @AddToRuleKey private final Optional<String> functionNameOverride;
  @AddToRuleKey private final boolean withDownwardApi;

  public HalideCompile(
      BuildTarget buildTarget,
      ProjectFilesystem projectFilesystem,
      BuildRuleParams params,
      Tool halideCompiler,
      String targetPlatform,
      Optional<ImmutableList<String>> compilerInvocationFlags,
      Optional<String> functionNameOverride,
      boolean withDownwardApi) {
    super(buildTarget, projectFilesystem, params);
    this.halideCompiler = halideCompiler;
    this.targetPlatform = targetPlatform;
    this.compilerInvocationFlags = compilerInvocationFlags;
    this.functionNameOverride = functionNameOverride;
    this.withDownwardApi = withDownwardApi;
  }

  @Override
  public ImmutableList<Step> getBuildSteps(
      BuildContext context, BuildableContext buildableContext) {
    RelPath outputDir =
        context.getSourcePathResolver().getCellUnsafeRelPath(getSourcePathToOutput());
    buildableContext.recordArtifact(
        objectOutputPath(getBuildTarget(), getProjectFilesystem(), functionNameOverride));
    buildableContext.recordArtifact(
        headerOutputPath(getBuildTarget(), getProjectFilesystem(), functionNameOverride));

    ImmutableList.Builder<Step> commands = ImmutableList.builder();
    ProjectFilesystem projectFilesystem = getProjectFilesystem();
    commands.addAll(
        MakeCleanDirectoryStep.of(
            BuildCellRelativePath.fromCellRelativePath(
                context.getBuildCellRootPath(), getProjectFilesystem(), outputDir)));

    commands.add(
        new HalideCompilerStep(
            projectFilesystem.getRootPath(),
            halideCompiler.getEnvironment(context.getSourcePathResolver()),
            halideCompiler.getCommandPrefix(context.getSourcePathResolver()),
            outputDir,
            fileOutputName(getBuildTarget(), functionNameOverride),
            targetPlatform,
            compilerInvocationFlags,
            ProjectFilesystemUtils.relativize(
                projectFilesystem.getRootPath(), context.getBuildCellRootPath()),
            withDownwardApi));
    return commands.build();
  }

  @Override
  public SourcePath getSourcePathToOutput() {
    return ExplicitBuildTargetSourcePath.of(
        getBuildTarget(), pathToOutput(getBuildTarget(), getProjectFilesystem()));
  }

  private static RelPath pathToOutput(BuildTarget buildTarget, ProjectFilesystem filesystem) {
    return BuildTargetPaths.getGenPath(filesystem.getBuckPaths(), buildTarget, "%s");
  }

  public static Path objectOutputPath(
      BuildTarget buildTarget,
      ProjectFilesystem filesystem,
      Optional<String> functionNameOverride) {
    String functionName = fileOutputName(buildTarget, functionNameOverride);
    return pathToOutput(buildTarget, filesystem).resolve(functionName + ".o");
  }

  public static Path headerOutputPath(
      BuildTarget buildTarget,
      ProjectFilesystem filesystem,
      Optional<String> functionNameOverride) {
    String functionName = fileOutputName(buildTarget, functionNameOverride);
    return pathToOutput(buildTarget, filesystem).resolve(functionName + ".h");
  }

  public static String fileOutputName(
      BuildTarget buildTarget, Optional<String> functionNameOverride) {
    return functionNameOverride.orElse(buildTarget.getShortName());
  }
}

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

package com.facebook.buck.features.haskell;

import com.facebook.buck.core.build.buildable.context.BuildableContext;
import com.facebook.buck.core.build.context.BuildContext;
import com.facebook.buck.core.build.execution.context.IsolatedExecutionContext;
import com.facebook.buck.core.build.execution.context.StepExecutionContext;
import com.facebook.buck.core.filesystems.AbsPath;
import com.facebook.buck.core.filesystems.RelPath;
import com.facebook.buck.core.model.BuildTarget;
import com.facebook.buck.core.model.impl.BuildTargetPaths;
import com.facebook.buck.core.rulekey.AddToRuleKey;
import com.facebook.buck.core.rules.BuildRuleParams;
import com.facebook.buck.core.rules.impl.AbstractBuildRuleWithDeclaredAndExtraDeps;
import com.facebook.buck.core.sourcepath.ExplicitBuildTargetSourcePath;
import com.facebook.buck.core.sourcepath.SourcePath;
import com.facebook.buck.core.sourcepath.resolver.SourcePathResolverAdapter;
import com.facebook.buck.core.toolchain.tool.Tool;
import com.facebook.buck.io.filesystem.BuildCellRelativePath;
import com.facebook.buck.io.filesystem.ProjectFilesystem;
import com.facebook.buck.io.filesystem.impl.ProjectFilesystemUtils;
import com.facebook.buck.rules.args.Arg;
import com.facebook.buck.step.Step;
import com.facebook.buck.step.StepExecutionResult;
import com.facebook.buck.step.StepExecutionResults;
import com.facebook.buck.step.fs.MkdirStep;
import com.facebook.buck.step.fs.RmStep;
import com.facebook.buck.step.isolatedsteps.shell.IsolatedShellStep;
import com.facebook.buck.util.Escaper;
import com.facebook.buck.util.MoreIterables;
import com.facebook.buck.util.Verbosity;
import com.facebook.buck.util.environment.Platform;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import java.io.IOException;
import java.nio.file.Path;

public class HaskellLinkRule extends AbstractBuildRuleWithDeclaredAndExtraDeps {

  @AddToRuleKey private final Tool linker;

  @AddToRuleKey(stringify = true)
  private final Path outputPath;

  @AddToRuleKey private final ImmutableList<Arg> args;

  @AddToRuleKey private final ImmutableList<Arg> linkerArgs;

  private final boolean cacheable;

  private final boolean useArgsfile;

  @AddToRuleKey private final boolean withDownwardApi;

  public HaskellLinkRule(
      BuildTarget buildTarget,
      ProjectFilesystem projectFilesystem,
      BuildRuleParams buildRuleParams,
      Tool linker,
      Path outputPath,
      ImmutableList<Arg> args,
      ImmutableList<Arg> linkerArgs,
      boolean cacheable,
      boolean useArgsfile,
      boolean withDownwardApi) {
    super(buildTarget, projectFilesystem, buildRuleParams);
    this.linker = linker;
    this.outputPath = outputPath;
    this.args = args;
    this.linkerArgs = linkerArgs;
    this.cacheable = cacheable;
    this.useArgsfile = useArgsfile;
    this.withDownwardApi = withDownwardApi;
  }

  private Path getOutputDir() {
    return getOutput().getParent();
  }

  private Path getOutput() {
    return this.outputPath;
  }

  private AbsPath getArgsfile() {
    RelPath scratchDir =
        BuildTargetPaths.getScratchPath(getProjectFilesystem(), getBuildTarget(), "%s");
    return getProjectFilesystem().resolve(scratchDir).resolve("haskell-link.argsfile");
  }

  private Iterable<String> getLinkerArgs(SourcePathResolverAdapter resolver) {
    return MoreIterables.zipAndConcat(
        Iterables.cycle("-optl"), Arg.stringify(linkerArgs, resolver));
  }

  @Override
  public ImmutableList<Step> getBuildSteps(
      BuildContext buildContext, BuildableContext buildableContext) {
    buildableContext.recordArtifact(getOutput());
    return new ImmutableList.Builder<Step>()
        .add(
            MkdirStep.of(
                BuildCellRelativePath.fromCellRelativePath(
                    buildContext.getBuildCellRootPath(), getProjectFilesystem(), getOutputDir())))
        .add(
            // The output path might be a folder, so delete it all
            RmStep.of(
                BuildCellRelativePath.fromCellRelativePath(
                    buildContext.getBuildCellRootPath(), getProjectFilesystem(), getOutput()),
                true))
        .add(
            new Step() {

              @Override
              public StepExecutionResult execute(StepExecutionContext context) throws IOException {
                getProjectFilesystem().createParentDirs(getArgsfile());
                getProjectFilesystem()
                    .writeLinesToPath(
                        Iterables.transform(
                            getLinkerArgs(buildContext.getSourcePathResolver()),
                            Escaper.ARGFILE_ESCAPER::apply),
                        getArgsfile().getPath());
                return StepExecutionResults.SUCCESS;
              }

              @Override
              public String getShortName() {
                return "write-haskell-link-argsfile";
              }

              @Override
              public String getDescription(StepExecutionContext context) {
                return "Write argsfile for haskell-link";
              }
            })
        .add(
            new IsolatedShellStep(
                getProjectFilesystem().getRootPath(),
                ProjectFilesystemUtils.relativize(
                    getProjectFilesystem().getRootPath(), buildContext.getBuildCellRootPath()),
                withDownwardApi) {

              @Override
              public ImmutableMap<String, String> getEnvironmentVariables(Platform platform) {
                return ImmutableMap.<String, String>builder()
                    .putAll(super.getEnvironmentVariables(platform))
                    .putAll(linker.getEnvironment(buildContext.getSourcePathResolver()))
                    .build();
              }

              @Override
              protected ImmutableList<String> getShellCommandInternal(
                  IsolatedExecutionContext context) {
                ImmutableList.Builder<String> builder = ImmutableList.builder();
                builder
                    .addAll(linker.getCommandPrefix(buildContext.getSourcePathResolver()))
                    .add("-o", getProjectFilesystem().resolve(getOutput()).toString())
                    .addAll(Arg.stringify(args, buildContext.getSourcePathResolver()));
                if (useArgsfile) {
                  builder.add("@" + getArgsfile());
                } else {
                  builder.addAll(getLinkerArgs(buildContext.getSourcePathResolver()));
                }
                return builder.build();
              }

              @Override
              public boolean shouldPrintStderr(Verbosity verbosity) {
                return !verbosity.isSilent();
              }

              @Override
              public String getShortName() {
                return "haskell-link";
              }
            })
        .build();
  }

  @Override
  public SourcePath getSourcePathToOutput() {
    return ExplicitBuildTargetSourcePath.of(getBuildTarget(), getOutput());
  }

  @Override
  public boolean isCacheable() {
    return cacheable;
  }
}

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
import com.facebook.buck.core.filesystems.AbsPath;
import com.facebook.buck.core.filesystems.RelPath;
import com.facebook.buck.core.model.BuildTarget;
import com.facebook.buck.core.model.impl.BuildTargetPaths;
import com.facebook.buck.core.rulekey.AddToRuleKey;
import com.facebook.buck.core.rules.BuildRule;
import com.facebook.buck.core.rules.BuildRuleParams;
import com.facebook.buck.core.rules.SourcePathRuleFinder;
import com.facebook.buck.core.rules.common.BuildableSupport;
import com.facebook.buck.core.rules.impl.AbstractBuildRuleWithDeclaredAndExtraDeps;
import com.facebook.buck.core.sourcepath.ExplicitBuildTargetSourcePath;
import com.facebook.buck.core.sourcepath.SourcePath;
import com.facebook.buck.core.sourcepath.resolver.SourcePathResolverAdapter;
import com.facebook.buck.core.toolchain.tool.Tool;
import com.facebook.buck.core.util.log.Logger;
import com.facebook.buck.io.filesystem.BuildCellRelativePath;
import com.facebook.buck.io.filesystem.ProjectFilesystem;
import com.facebook.buck.io.filesystem.impl.ProjectFilesystemUtils;
import com.facebook.buck.step.Step;
import com.facebook.buck.step.fs.CopyStep;
import com.facebook.buck.step.fs.MakeCleanDirectoryStep;
import com.facebook.buck.step.isolatedsteps.shell.IsolatedShellStep;
import com.facebook.buck.util.MoreIterables;
import com.facebook.buck.util.MoreSuppliers;
import com.facebook.buck.util.Verbosity;
import com.facebook.buck.util.stream.RichStream;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Iterables;
import java.util.function.Supplier;

public class HaskellHaddockRule extends AbstractBuildRuleWithDeclaredAndExtraDeps {

  private static final Logger LOG = Logger.get(HaskellHaddockRule.class);

  @AddToRuleKey private final Tool haddockTool;
  @AddToRuleKey private final ImmutableList<String> flags;
  @AddToRuleKey private final ImmutableSet<SourcePath> interfaces;
  @AddToRuleKey private final ImmutableSet<SourcePath> outputDirs;
  @AddToRuleKey private final boolean withDownwardApi;

  private HaskellHaddockRule(
      BuildTarget buildTarget,
      ProjectFilesystem projectFilesystem,
      BuildRuleParams buildRuleParams,
      Tool haddockTool,
      ImmutableList<String> flags,
      ImmutableSet<SourcePath> interfaces,
      ImmutableSet<SourcePath> outputDirs,
      boolean withDownwardApi) {
    super(buildTarget, projectFilesystem, buildRuleParams);
    this.haddockTool = haddockTool;
    this.flags = flags;
    this.interfaces = interfaces;
    this.outputDirs = outputDirs;
    this.withDownwardApi = withDownwardApi;
  }

  public static HaskellHaddockRule from(
      BuildTarget buildTarget,
      ProjectFilesystem projectFilesystem,
      BuildRuleParams buildRuleParams,
      SourcePathRuleFinder ruleFinder,
      Tool haddockTool,
      ImmutableList<String> flags,
      ImmutableSet<HaskellHaddockInput> inputs,
      boolean withDownwardApi) {
    ImmutableSet.Builder<SourcePath> ifacesBuilder = ImmutableSet.builder();
    ImmutableSet.Builder<SourcePath> outDirsBuilder = ImmutableSet.builder();
    for (HaskellHaddockInput i : inputs) {
      ifacesBuilder.addAll(i.getInterfaces());
      outDirsBuilder.addAll(i.getHaddockOutputDirs());
    }
    ImmutableSet<SourcePath> ifaces = ifacesBuilder.build();
    ImmutableSet<SourcePath> outDirs = outDirsBuilder.build();

    Supplier<ImmutableSortedSet<BuildRule>> declaredDeps =
        MoreSuppliers.memoize(
            () ->
                ImmutableSortedSet.<BuildRule>naturalOrder()
                    .addAll(BuildableSupport.getDepsCollection(haddockTool, ruleFinder))
                    .addAll(ruleFinder.filterBuildRuleInputs(ifaces))
                    .addAll(ruleFinder.filterBuildRuleInputs(outDirs))
                    .build());
    return new HaskellHaddockRule(
        buildTarget,
        projectFilesystem,
        buildRuleParams.withDeclaredDeps(declaredDeps).withoutExtraDeps(),
        haddockTool,
        flags,
        ifaces,
        outDirs,
        withDownwardApi);
  }

  private RelPath getOutputDir() {
    return BuildTargetPaths.getGenPath(
        getProjectFilesystem().getBuckPaths(), getBuildTarget(), "%s");
  }

  private RelPath getHaddockOuptutDir() {
    return getOutputDir().resolveRel("ALL");
  }

  @Override
  public SourcePath getSourcePathToOutput() {
    return ExplicitBuildTargetSourcePath.of(getBuildTarget(), getOutputDir());
  }

  @Override
  public ImmutableList<Step> getBuildSteps(
      BuildContext context, BuildableContext buildableContext) {

    SourcePathResolverAdapter resolver = context.getSourcePathResolver();
    String name = getBuildTarget().getShortName();

    LOG.info(name);

    ImmutableList.Builder<Step> steps = ImmutableList.builder();

    steps.addAll(
        MakeCleanDirectoryStep.of(
            BuildCellRelativePath.fromCellRelativePath(
                context.getBuildCellRootPath(), getProjectFilesystem(), getOutputDir())));
    steps.add(new HaddockStep(getProjectFilesystem().getRootPath(), context, withDownwardApi));

    // Copy the generated data from dependencies into our output directory
    RelPath haddockOutputDir = getHaddockOuptutDir();
    for (SourcePath odir : outputDirs) {
      steps.add(
          CopyStep.forDirectory(
              resolver.getCellUnsafeRelPath(odir),
              haddockOutputDir,
              CopyStep.DirectoryMode.CONTENTS_ONLY));
    }

    buildableContext.recordArtifact(getOutputDir().getPath());
    return steps.build();
  }

  private class HaddockStep extends IsolatedShellStep {

    private BuildContext buildContext;

    public HaddockStep(AbsPath rootPath, BuildContext buildContext, boolean withDownwardApi) {
      super(
          rootPath,
          ProjectFilesystemUtils.relativize(rootPath, buildContext.getBuildCellRootPath()),
          withDownwardApi);
      this.buildContext = buildContext;
    }

    @Override
    public boolean shouldPrintStderr(Verbosity verbosity) {
      return !verbosity.isSilent();
    }

    @Override
    protected ImmutableList<String> getShellCommandInternal(IsolatedExecutionContext context) {
      SourcePathResolverAdapter resolver = buildContext.getSourcePathResolver();
      return ImmutableList.<String>builder()
          .addAll(haddockTool.getCommandPrefix(resolver))
          .addAll(flags)
          .add("--gen-index")
          .add("--gen-contents")
          .addAll(
              MoreIterables.zipAndConcat(
                  Iterables.cycle("--read-interface"),
                  RichStream.from(interfaces)
                      .map(sp -> resolver.getAbsolutePath(sp).toString())
                      .toImmutableList()))
          .add("-o", getHaddockOuptutDir().toString())
          .build();
    }

    @Override
    public String getShortName() {
      return "haddock-build";
    }
  }
}

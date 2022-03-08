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

package com.facebook.buck.features.go;

import com.facebook.buck.core.build.buildable.context.BuildableContext;
import com.facebook.buck.core.build.context.BuildContext;
import com.facebook.buck.core.filesystems.AbsPath;
import com.facebook.buck.core.filesystems.RelPath;
import com.facebook.buck.core.model.BuildTarget;
import com.facebook.buck.core.model.OutputLabel;
import com.facebook.buck.core.model.impl.BuildTargetPaths;
import com.facebook.buck.core.rulekey.AddToRuleKey;
import com.facebook.buck.core.rules.BuildRule;
import com.facebook.buck.core.rules.BuildRuleParams;
import com.facebook.buck.core.rules.BuildRuleResolver;
import com.facebook.buck.core.rules.attr.HasRuntimeDeps;
import com.facebook.buck.core.rules.impl.AbstractBuildRuleWithDeclaredAndExtraDeps;
import com.facebook.buck.core.rules.impl.SymlinkTree;
import com.facebook.buck.core.rules.tool.BinaryBuildRule;
import com.facebook.buck.core.sourcepath.ExplicitBuildTargetSourcePath;
import com.facebook.buck.core.sourcepath.SourcePath;
import com.facebook.buck.core.sourcepath.resolver.SourcePathResolverAdapter;
import com.facebook.buck.core.toolchain.tool.Tool;
import com.facebook.buck.core.toolchain.tool.impl.CommandTool;
import com.facebook.buck.cxx.CxxPrepareForLinkStep;
import com.facebook.buck.cxx.toolchain.linker.Linker;
import com.facebook.buck.io.filesystem.BuildCellRelativePath;
import com.facebook.buck.io.filesystem.ProjectFilesystem;
import com.facebook.buck.io.filesystem.impl.ProjectFilesystemUtils;
import com.facebook.buck.rules.args.Arg;
import com.facebook.buck.rules.args.SourcePathArg;
import com.facebook.buck.step.Step;
import com.facebook.buck.step.fs.CopyStep;
import com.facebook.buck.step.fs.MkdirStep;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSortedSet;
import java.nio.file.Files;
import java.util.stream.Stream;

public class GoBinary extends AbstractBuildRuleWithDeclaredAndExtraDeps
    implements BinaryBuildRule, HasRuntimeDeps {

  @AddToRuleKey private final Tool linker;
  @AddToRuleKey private final Linker cxxLinker;
  @AddToRuleKey private final ImmutableList<Arg> linkerArgs;
  @AddToRuleKey private final ImmutableList<Arg> cxxLinkerArgs;
  @AddToRuleKey private final GoLinkStep.BuildMode buildMode;
  @AddToRuleKey private final GoLinkStep.LinkMode linkMode;
  @AddToRuleKey private final GoPlatform platform;

  private final RelPath output;
  private final GoCompile mainObject;
  private final SymlinkTree linkTree;
  private final ImmutableSortedSet<SourcePath> resources;
  @AddToRuleKey private final boolean withDownwardApi;

  public GoBinary(
      BuildTarget buildTarget,
      ProjectFilesystem projectFilesystem,
      BuildRuleParams params,
      ImmutableSortedSet<SourcePath> resources,
      SymlinkTree linkTree,
      GoCompile mainObject,
      Tool linker,
      Linker cxxLinker,
      GoLinkStep.BuildMode buildMode,
      GoLinkStep.LinkMode linkMode,
      ImmutableList<Arg> linkerArgs,
      ImmutableList<Arg> cxxLinkerArgs,
      GoPlatform platform,
      boolean withDownwardApi) {

    super(buildTarget, projectFilesystem, params);
    this.cxxLinker = cxxLinker;
    this.cxxLinkerArgs = cxxLinkerArgs;
    this.resources = resources;
    this.linker = linker;
    this.linkTree = linkTree;
    this.mainObject = mainObject;
    this.platform = platform;
    this.withDownwardApi = withDownwardApi;

    String outputFormat = getOutputFormat(buildTarget, platform, buildMode);
    this.output =
        BuildTargetPaths.getGenPath(projectFilesystem.getBuckPaths(), buildTarget, outputFormat);

    this.linkerArgs = linkerArgs;
    this.linkMode = linkMode;
    this.buildMode = buildMode;
  }

  @Override
  public Tool getExecutableCommand(OutputLabel outputLabel) {
    return new CommandTool.Builder().addArg(SourcePathArg.of(getSourcePathToOutput())).build();
  }

  private ImmutableMap<String, String> getEnvironment(BuildContext context) {
    ImmutableMap.Builder<String, String> environment = ImmutableMap.builder();

    if (linkMode == GoLinkStep.LinkMode.EXTERNAL) {
      environment.putAll(cxxLinker.getEnvironment(context.getSourcePathResolver()));
    }
    environment.putAll(linker.getEnvironment(context.getSourcePathResolver()));

    return environment.build();
  }

  protected RelPath getPathToBinaryDirectory() {
    return output.getParent();
  }

  private String getOutputFormat(
      BuildTarget buildTarget, GoPlatform platform, GoLinkStep.BuildMode buildMode) {
    String outputFormat = "%s/" + buildTarget.getShortName();

    switch (buildMode) {
      case C_SHARED:
        if (platform.getGoOs() == GoOs.DARWIN) {
          return outputFormat + ".dylib";
        }
        if (platform.getGoOs() == GoOs.WINDOWS) {
          return outputFormat + ".dll";
        }
        return outputFormat + ".so";
      case C_ARCHIVE:
        return outputFormat + ".a";
      case EXECUTABLE:
        if (platform.getGoOs() == GoOs.WINDOWS) {
          return outputFormat + ".exe";
        }
    }

    return outputFormat;
  }

  @Override
  public ImmutableList<Step> getBuildSteps(
      BuildContext context, BuildableContext buildableContext) {

    buildableContext.recordArtifact(output.getPath());

    ProjectFilesystem filesystem = getProjectFilesystem();
    SourcePathResolverAdapter resolver = context.getSourcePathResolver();
    ImmutableList.Builder<Step> steps = ImmutableList.builder();

    steps.add(
        MkdirStep.of(
            BuildCellRelativePath.fromCellRelativePath(
                context.getBuildCellRootPath(), getProjectFilesystem(), output.getParent())));

    // copy resources to target directory
    for (SourcePath resource : resources) {
      // sourcePathName is the name of the resource as found in BUCK file:
      // testdata/level2
      String sourcePathName = resolver.getSourcePathName(getBuildTarget(), resource);
      // outputResourcePath is the full path to buck-out/gen/targetdir...
      // buck-out/gen/test-with-resources-2directory-2resources#test-main/testdata/level2
      RelPath outputResourcePath = output.getParent().resolveRel(sourcePathName);
      buildableContext.recordArtifact(outputResourcePath.getPath());
      if (Files.isDirectory(resolver.getAbsolutePath(resource).getPath())) {
        steps.add(
            MkdirStep.of(
                BuildCellRelativePath.fromCellRelativePath(
                    context.getBuildCellRootPath(), filesystem, outputResourcePath.getParent())));
        steps.add(
            CopyStep.forDirectory(
                resolver.getCellUnsafeRelPath(resource),
                outputResourcePath.getParent(),
                CopyStep.DirectoryMode.DIRECTORY_AND_CONTENTS));
      } else {
        steps.add(
            MkdirStep.of(
                BuildCellRelativePath.fromCellRelativePath(
                    context.getBuildCellRootPath(), filesystem, outputResourcePath.getParent())));
        steps.add(CopyStep.forFile(resolver.getCellUnsafeRelPath(resource), outputResourcePath));
      }
    }

    // cxxLinkerArgs comes from cgo rules and are reuqired for cxx deps linking
    ImmutableList.Builder<String> externalLinkerFlags = ImmutableList.builder();
    if (linkMode == GoLinkStep.LinkMode.EXTERNAL) {
      AbsPath argFilePath =
          getProjectFilesystem()
              .getRootPath()
              .resolve(
                  BuildTargetPaths.getScratchPath(
                      getProjectFilesystem(), getBuildTarget(), "%s.argsfile"));
      AbsPath fileListPath =
          getProjectFilesystem()
              .getRootPath()
              .resolve(
                  BuildTargetPaths.getScratchPath(
                      getProjectFilesystem(), getBuildTarget(), "%s__filelist.txt"));
      steps.addAll(
          CxxPrepareForLinkStep.create(
              argFilePath.getPath(),
              fileListPath.getPath(),
              cxxLinker.fileList(fileListPath),
              output.getPath(),
              cxxLinkerArgs,
              cxxLinker,
              getBuildTarget().getCell(),
              getProjectFilesystem().getRootPath().getPath(),
              resolver,
              ImmutableMap.of(),
              ImmutableList.of()));
      externalLinkerFlags.add(String.format("@%s", argFilePath));
    }

    steps.add(
        new GoLinkStep(
            getProjectFilesystem().getRootPath().getPath(),
            getEnvironment(context),
            cxxLinker.getCommandPrefix(resolver),
            linker.getCommandPrefix(resolver),
            Arg.stringify(linkerArgs, resolver),
            externalLinkerFlags.build(),
            ImmutableList.of(linkTree.getRoot()),
            platform,
            resolver.getCellUnsafeRelPath(mainObject.getSourcePathToOutput()).getPath(),
            buildMode,
            linkMode,
            output.getPath(),
            ProjectFilesystemUtils.relativize(
                getProjectFilesystem().getRootPath(), context.getBuildCellRootPath()),
            withDownwardApi));
    return steps.build();
  }

  @Override
  public SourcePath getSourcePathToOutput() {
    return ExplicitBuildTargetSourcePath.of(getBuildTarget(), output);
  }

  @Override
  public Stream<BuildTarget> getRuntimeDeps(BuildRuleResolver buildRuleResolver) {
    // For shared-style linked binaries, we need to ensure that the symlink tree and its
    // dependencies are available, or we will get a runtime linking error
    return getDeclaredDeps().stream().map(BuildRule::getBuildTarget);
  }
}

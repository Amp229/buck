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

package com.facebook.buck.jvm.java;

import com.facebook.buck.core.build.buildable.context.BuildableContext;
import com.facebook.buck.core.build.context.BuildContext;
import com.facebook.buck.core.build.execution.context.StepExecutionContext;
import com.facebook.buck.core.filesystems.AbsPath;
import com.facebook.buck.core.filesystems.RelPath;
import com.facebook.buck.core.model.BuildTarget;
import com.facebook.buck.core.model.Flavor;
import com.facebook.buck.core.model.InternalFlavor;
import com.facebook.buck.core.model.impl.BuildTargetPaths;
import com.facebook.buck.core.rulekey.AddToRuleKey;
import com.facebook.buck.core.rules.BuildRule;
import com.facebook.buck.core.rules.BuildRuleParams;
import com.facebook.buck.core.rules.impl.AbstractBuildRuleWithDeclaredAndExtraDeps;
import com.facebook.buck.core.sourcepath.ExplicitBuildTargetSourcePath;
import com.facebook.buck.core.sourcepath.SourcePath;
import com.facebook.buck.io.filesystem.BuildCellRelativePath;
import com.facebook.buck.io.filesystem.ProjectFilesystem;
import com.facebook.buck.jvm.core.HasClasspathEntries;
import com.facebook.buck.jvm.core.HasMavenCoordinates;
import com.facebook.buck.maven.aether.AetherUtil;
import com.facebook.buck.shell.ShellStep;
import com.facebook.buck.step.Step;
import com.facebook.buck.step.fs.MakeCleanDirectoryStep;
import com.facebook.buck.step.fs.MkdirStep;
import com.facebook.buck.step.fs.RmStep;
import com.facebook.buck.step.fs.ZipStep;
import com.facebook.buck.step.isolatedsteps.common.WriteFileIsolatedStep;
import com.facebook.buck.util.zip.ZipCompressionLevel;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import java.io.File;
import java.nio.file.Path;
import java.util.Optional;

public class Javadoc extends AbstractBuildRuleWithDeclaredAndExtraDeps implements MavenPublishable {

  public static final Flavor DOC_JAR = InternalFlavor.of("doc");

  @AddToRuleKey private final ImmutableSet<SourcePath> sources;
  @AddToRuleKey private final Optional<String> mavenCoords;
  @AddToRuleKey private final Iterable<HasMavenCoordinates> mavenDeps;

  private final RelPath output;
  private final RelPath scratchDir;
  @AddToRuleKey private final boolean withDownwardApi;

  protected Javadoc(
      BuildTarget buildTarget,
      ProjectFilesystem projectFilesystem,
      BuildRuleParams buildRuleParams,
      Optional<String> mavenCoords,
      Iterable<HasMavenCoordinates> mavenDeps,
      ImmutableSet<SourcePath> sources,
      boolean withDownwardApi) {
    super(buildTarget, projectFilesystem, buildRuleParams);

    this.mavenCoords = mavenCoords.map(coord -> AetherUtil.addClassifier(coord, "javadoc"));
    this.mavenDeps = mavenDeps;
    this.sources = sources;
    this.withDownwardApi = withDownwardApi;

    this.output =
        BuildTargetPaths.getGenPath(
            getProjectFilesystem().getBuckPaths(),
            getBuildTarget(),
            String.format("%%s/%s-javadoc.jar", getBuildTarget().getShortName()));
    this.scratchDir =
        BuildTargetPaths.getScratchPath(
            getProjectFilesystem(),
            getBuildTarget(),
            String.format("%%s/%s-javadoc.tmp", getBuildTarget().getShortName()));
  }

  @Override
  public ImmutableList<Step> getBuildSteps(
      BuildContext context, BuildableContext buildableContext) {
    buildableContext.recordArtifact(output.getPath());

    ImmutableList.Builder<Step> steps = ImmutableList.builder();

    steps.add(
        MkdirStep.of(
            BuildCellRelativePath.fromCellRelativePath(
                context.getBuildCellRootPath(), getProjectFilesystem(), output.getParent())));
    steps.add(
        RmStep.of(
            BuildCellRelativePath.fromCellRelativePath(
                context.getBuildCellRootPath(), getProjectFilesystem(), output)));

    // Fast path: nothing to do so just create an empty zip and return.
    if (sources.isEmpty()) {
      steps.add(
          ZipStep.of(
              getProjectFilesystem(),
              output.getPath(),
              ImmutableSet.of(),
              false,
              ZipCompressionLevel.NONE,
              output.getPath()));
      return steps.build();
    }

    Path sourcesListFilePath = scratchDir.resolve("all-sources.txt");

    steps.addAll(
        MakeCleanDirectoryStep.of(
            BuildCellRelativePath.fromCellRelativePath(
                context.getBuildCellRootPath(), getProjectFilesystem(), scratchDir)));
    // Write an @-file with all the source files in
    steps.add(
        WriteFileIsolatedStep.of(
            Joiner.on("\n")
                .join(
                    sources.stream()
                        .map(context.getSourcePathResolver()::getAbsolutePath)
                        .map(AbsPath::toString)
                        .iterator()),
            sourcesListFilePath,
            /* can execute */ false));

    Path atArgs = scratchDir.resolve("options");
    // Write an @-file with the classpath
    StringBuilder argsBuilder = new StringBuilder("-classpath ");
    Joiner.on(File.pathSeparator)
        .appendTo(
            argsBuilder,
            getBuildDeps().stream()
                .filter(HasClasspathEntries.class::isInstance)
                .flatMap(rule -> ((HasClasspathEntries) rule).getTransitiveClasspaths().stream())
                .map(context.getSourcePathResolver()::getAbsolutePath)
                .map(Object::toString)
                .iterator());
    steps.add(WriteFileIsolatedStep.of(argsBuilder.toString(), atArgs, /* can execute */ false));

    Path uncompressedOutputDir = scratchDir.resolve("docs");

    steps.addAll(
        MakeCleanDirectoryStep.of(
            BuildCellRelativePath.fromCellRelativePath(
                context.getBuildCellRootPath(), getProjectFilesystem(), uncompressedOutputDir)));
    steps.add(
        new ShellStep(getProjectFilesystem().resolve(scratchDir), withDownwardApi) {
          @Override
          protected ImmutableList<String> getShellCommandInternal(StepExecutionContext context) {
            return ImmutableList.of(
                "javadoc",
                "-Xdoclint:none",
                "-notimestamp",
                "-d",
                uncompressedOutputDir.getFileName().toString(),
                "@" + getProjectFilesystem().resolve(atArgs),
                "@" + getProjectFilesystem().resolve(sourcesListFilePath));
          }

          @Override
          public String getShortName() {
            return "javadoc";
          }
        });
    steps.add(
        ZipStep.of(
            getProjectFilesystem(),
            output.getPath(),
            ImmutableSet.of(),
            false,
            ZipCompressionLevel.DEFAULT,
            uncompressedOutputDir));

    return steps.build();
  }

  @Override
  public SourcePath getSourcePathToOutput() {
    return ExplicitBuildTargetSourcePath.of(getBuildTarget(), output);
  }

  @Override
  public Optional<String> getMavenCoords() {
    return mavenCoords;
  }

  @Override
  public Iterable<HasMavenCoordinates> getMavenDeps() {
    return mavenDeps;
  }

  @Override
  public Iterable<BuildRule> getPackagedDependencies() {
    return ImmutableSet.of(this); // I think that this is right
  }
}

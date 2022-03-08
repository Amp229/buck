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

package com.facebook.buck.features.gwt;

import com.facebook.buck.core.build.buildable.context.BuildableContext;
import com.facebook.buck.core.build.context.BuildContext;
import com.facebook.buck.core.filesystems.RelPath;
import com.facebook.buck.core.model.BuildTarget;
import com.facebook.buck.core.model.impl.BuildTargetPaths;
import com.facebook.buck.core.rulekey.AddToRuleKey;
import com.facebook.buck.core.rules.BuildRule;
import com.facebook.buck.core.rules.BuildRuleParams;
import com.facebook.buck.core.rules.SourcePathRuleFinder;
import com.facebook.buck.core.rules.impl.AbstractBuildRuleWithDeclaredAndExtraDeps;
import com.facebook.buck.core.sourcepath.ExplicitBuildTargetSourcePath;
import com.facebook.buck.core.sourcepath.SourcePath;
import com.facebook.buck.io.filesystem.BuildCellRelativePath;
import com.facebook.buck.io.filesystem.ProjectFilesystem;
import com.facebook.buck.jvm.java.CopyResourcesStep;
import com.facebook.buck.jvm.java.JarParameters;
import com.facebook.buck.jvm.java.ResourcesParameters;
import com.facebook.buck.step.Step;
import com.facebook.buck.step.fs.MakeCleanDirectoryStep;
import com.facebook.buck.step.isolatedsteps.java.JarDirectoryStep;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSortedSet;
import java.util.Optional;

/**
 * {@link BuildRule} whose output file is a JAR containing the .java files and resources suitable
 * for a GWT module. (It differs slightly from a source JAR because it contains resources.)
 */
public class GwtModule extends AbstractBuildRuleWithDeclaredAndExtraDeps {

  private final RelPath outputFile;
  @AddToRuleKey private final ImmutableSortedSet<SourcePath> filesForGwtModule;
  private final SourcePathRuleFinder ruleFinder;
  @AddToRuleKey private final Optional<String> resourcesRoot;

  GwtModule(
      BuildTarget buildTarget,
      ProjectFilesystem projectFilesystem,
      BuildRuleParams params,
      SourcePathRuleFinder ruleFinder,
      ImmutableSortedSet<SourcePath> filesForGwtModule,
      Optional<String> resourcesRoot) {
    super(buildTarget, projectFilesystem, params);
    this.ruleFinder = ruleFinder;
    this.outputFile =
        BuildTargetPaths.getGenPath(
            getProjectFilesystem().getBuckPaths(),
            buildTarget,
            "__gwt_module_%s__/" + buildTarget.getShortNameAndFlavorPostfix() + ".jar");
    this.filesForGwtModule = filesForGwtModule;
    this.resourcesRoot = resourcesRoot;
  }

  @Override
  public ImmutableList<Step> getBuildSteps(
      BuildContext context, BuildableContext buildableContext) {

    ImmutableList.Builder<Step> steps = ImmutableList.builder();

    RelPath workingDirectory = outputFile.getParent();
    steps.addAll(
        MakeCleanDirectoryStep.of(
            BuildCellRelativePath.fromCellRelativePath(
                context.getBuildCellRootPath(), getProjectFilesystem(), workingDirectory)));

    // A CopyResourcesStep is needed so that a file that is at java/com/example/resource.txt in the
    // repository will be added as com/example/resource.txt in the resulting JAR (assuming that
    // "/java/" is listed under src_roots in .buckconfig).
    RelPath tempJarFolder = workingDirectory.resolveRel("tmp");
    steps.addAll(
        CopyResourcesStep.of(
            CopyResourcesStep.getResourcesMap(
                context,
                getProjectFilesystem(),
                tempJarFolder.getPath(),
                ResourcesParameters.of(
                    ResourcesParameters.getNamedResources(
                        ruleFinder, getProjectFilesystem(), filesForGwtModule),
                    resourcesRoot),
                getBuildTarget())));

    steps.add(
        new JarDirectoryStep(
            JarParameters.builder()
                .setJarPath(outputFile)
                .setEntriesToJar(
                    ImmutableSortedSet.orderedBy(RelPath.comparator()).add(tempJarFolder).build())
                .setMergeManifests(true)
                .build()));

    buildableContext.recordArtifact(outputFile.getPath());

    return steps.build();
  }

  @Override
  public SourcePath getSourcePathToOutput() {
    return ExplicitBuildTargetSourcePath.of(getBuildTarget(), outputFile);
  }
}

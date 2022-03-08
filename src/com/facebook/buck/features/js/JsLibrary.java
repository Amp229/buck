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

package com.facebook.buck.features.js;

import com.facebook.buck.core.build.context.BuildContext;
import com.facebook.buck.core.exceptions.HumanReadableException;
import com.facebook.buck.core.filesystems.AbsPath;
import com.facebook.buck.core.model.BuildTarget;
import com.facebook.buck.core.model.FlavorSet;
import com.facebook.buck.core.model.impl.BuildTargetPaths;
import com.facebook.buck.core.rulekey.AddToRuleKey;
import com.facebook.buck.core.rules.BuildRule;
import com.facebook.buck.core.rules.SourcePathRuleFinder;
import com.facebook.buck.core.sourcepath.BuildTargetSourcePath;
import com.facebook.buck.core.sourcepath.SourcePath;
import com.facebook.buck.core.sourcepath.resolver.SourcePathResolverAdapter;
import com.facebook.buck.io.filesystem.ProjectFilesystem;
import com.facebook.buck.rules.args.Arg;
import com.facebook.buck.rules.modern.BuildCellRelativePathFactory;
import com.facebook.buck.rules.modern.Buildable;
import com.facebook.buck.rules.modern.ModernBuildRule;
import com.facebook.buck.rules.modern.OutputPath;
import com.facebook.buck.rules.modern.OutputPathResolver;
import com.facebook.buck.rules.modern.PublicOutputPath;
import com.facebook.buck.shell.WorkerTool;
import com.facebook.buck.step.Step;
import com.facebook.buck.util.json.JsonBuilder;
import com.facebook.buck.util.json.JsonBuilder.ObjectBuilder;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSortedSet;
import java.nio.file.Path;
import java.util.Optional;
import java.util.stream.Stream;

/** JsLibrary rule */
public class JsLibrary extends ModernBuildRule<JsLibrary.JsLibraryImpl> {

  JsLibrary(
      BuildTarget buildTarget,
      ProjectFilesystem projectFilesystem,
      SourcePathRuleFinder ruleFinder,
      BuildTargetSourcePath filesDependency,
      ImmutableSortedSet<SourcePath> libraryDependencies,
      WorkerTool worker,
      boolean withDownwardApi,
      Optional<Arg> extraJson) {
    super(
        buildTarget,
        projectFilesystem,
        ruleFinder,
        new JsLibraryImpl(
            libraryDependencies,
            filesDependency,
            worker,
            buildTarget,
            projectFilesystem,
            withDownwardApi,
            extraJson));
  }

  /** Abstract buildable implementation that is used by JsLibrary and JsLibrary.Files rules */
  abstract static class AbstractJsLibraryBuildable implements Buildable {
    @AddToRuleKey final WorkerTool worker;
    @AddToRuleKey final BuildTarget buildTarget;
    @AddToRuleKey final OutputPath output;
    @AddToRuleKey private final boolean withDownwardApi;

    protected AbstractJsLibraryBuildable(
        WorkerTool worker,
        BuildTarget buildTarget,
        ProjectFilesystem projectFilesystem,
        boolean withDownwardApi) {
      this.worker = worker;
      this.buildTarget = buildTarget;
      this.withDownwardApi = withDownwardApi;
      this.output =
          new PublicOutputPath(
              BuildTargetPaths.getGenPath(
                  projectFilesystem.getBuckPaths(), buildTarget, "%s.jslib"));
    }

    @Override
    public ImmutableList<Step> getBuildSteps(
        BuildContext buildContext,
        ProjectFilesystem filesystem,
        OutputPathResolver outputPathResolver,
        BuildCellRelativePathFactory buildCellPathFactory) {
      SourcePathResolverAdapter resolver = buildContext.getSourcePathResolver();
      AbsPath outputPath = filesystem.resolve(outputPathResolver.resolvePath(output));
      return ImmutableList.of(
          JsUtil.jsonWorkerShellStepAddingFlavors(
              worker,
              getJobArgs(resolver, outputPath.getPath(), filesystem),
              buildTarget,
              resolver,
              filesystem,
              withDownwardApi));
    }

    abstract ObjectBuilder getJobArgs(
        SourcePathResolverAdapter resolver, Path outputPath, ProjectFilesystem filesystem);
  }

  /** JsLibrary buildable implementation */
  static class JsLibraryImpl extends AbstractJsLibraryBuildable {

    @AddToRuleKey private final ImmutableSortedSet<SourcePath> libraryDependencies;
    @AddToRuleKey private final BuildTargetSourcePath filesDependency;
    @AddToRuleKey private final Optional<Arg> extraJson;

    JsLibraryImpl(
        ImmutableSortedSet<SourcePath> libraryDependencies,
        BuildTargetSourcePath filesDependency,
        WorkerTool worker,
        BuildTarget buildTarget,
        ProjectFilesystem projectFilesystem,
        boolean withDownwardApi,
        Optional<Arg> extraJson) {
      super(worker, buildTarget, projectFilesystem, withDownwardApi);
      this.libraryDependencies = libraryDependencies;
      this.filesDependency = filesDependency;
      this.extraJson = extraJson;
    }

    @Override
    ObjectBuilder getJobArgs(
        SourcePathResolverAdapter resolver, Path outputPath, ProjectFilesystem filesystem) {
      FlavorSet flavors = buildTarget.getFlavors();

      return JsonBuilder.object()
          .addString("command", "library-dependencies")
          .addBoolean("release", flavors.contains(JsFlavors.RELEASE))
          .addString("rootPath", filesystem.getRootPath().toString())
          .addString("platform", JsUtil.getPlatformString(flavors))
          .addString("outputPath", outputPath.toString())
          .addArray(
              "dependencyLibraryFilePaths",
              libraryDependencies.stream()
                  .map(resolver::getAbsolutePath)
                  .map(AbsPath::toString)
                  .collect(JsonBuilder.toArrayOfStrings()))
          .addString(
              "aggregatedSourceFilesFilePath", resolver.getAbsolutePath(filesDependency).toString())
          .addRaw("extraData", getExtraJson(resolver));
    }

    private Optional<String> getExtraJson(SourcePathResolverAdapter sourcePathResolverAdapter) {
      return extraJson.map(a -> Arg.stringify(a, sourcePathResolverAdapter));
    }
  }

  @Override
  public BuildTargetSourcePath getSourcePathToOutput() {
    return getSourcePath(getBuildable().output);
  }

  ImmutableSortedSet<SourcePath> getLibraryDependencies() {
    return getBuildable().libraryDependencies;
  }

  Stream<JsFile> getJsFiles(SourcePathRuleFinder ruleFinder) {
    BuildRule fileRule = ruleFinder.getRule(getBuildable().filesDependency);
    if (fileRule instanceof Files) {
      return ((Files) fileRule).getJsFiles(ruleFinder);
    }
    throw new IllegalStateException(
        String.format(
            "JsLibrary rule %s was set up with 'filesDependency' that is not an instance of 'JsLibrary.Files'",
            getBuildTarget()));
  }

  /**
   * An internal rule type to make the aggregation result of {@link JsFile} dependencies cacheable
   * independently of {@link JsLibrary} dependencies.
   */
  static class Files extends ModernBuildRule<FilesImpl> {

    Files(
        BuildTarget target,
        ProjectFilesystem filesystem,
        SourcePathRuleFinder ruleFinder,
        ImmutableSortedSet<BuildTargetSourcePath> sources,
        WorkerTool worker,
        boolean withDownwardApi,
        Optional<String> forbidBuildingReason) {
      super(
          target,
          filesystem,
          ruleFinder,
          new FilesImpl(
              sources, worker, target, filesystem, withDownwardApi, forbidBuildingReason));
    }

    @Override
    public BuildTargetSourcePath getSourcePathToOutput() {
      return getSourcePath(getBuildable().output);
    }

    Stream<JsFile> getJsFiles(SourcePathRuleFinder ruleFinder) {
      return getBuildable().sources.stream().map(ruleFinder::getRule).map(this::buildRuleAsJsFile);
    }

    private JsFile buildRuleAsJsFile(BuildRule x) {
      if (x instanceof JsFile) {
        return (JsFile) x;
      }
      throw new IllegalStateException(
          String.format(
              "JsLibrary.Files rule %s has a source that is not a JsFile instance: %s",
              getBuildTarget(), x.getBuildTarget()));
    }
  }

  /** JsLibrary.Files buildable implementation */
  static class FilesImpl extends AbstractJsLibraryBuildable {

    @AddToRuleKey private final ImmutableSortedSet<BuildTargetSourcePath> sources;
    @AddToRuleKey private final Optional<String> forbidBuildingReason;

    FilesImpl(
        ImmutableSortedSet<BuildTargetSourcePath> sources,
        WorkerTool worker,
        BuildTarget buildTarget,
        ProjectFilesystem projectFilesystem,
        boolean withDownwardApi,
        Optional<String> forbidBuildingReason) {
      super(worker, buildTarget, projectFilesystem, withDownwardApi);
      this.sources = sources;
      this.forbidBuildingReason = forbidBuildingReason;
    }

    @Override
    ObjectBuilder getJobArgs(
        SourcePathResolverAdapter resolver, Path outputPath, ProjectFilesystem filesystem) {
      this.forbidBuildingReason.ifPresent(
          reason -> {
            throw new HumanReadableException(
                "This js_library files instance is a dummy rule that cannot be built because: %s",
                reason);
          });
      FlavorSet flavors = buildTarget.getFlavors();

      return JsonBuilder.object()
          .addString("command", "library-files")
          .addBoolean("release", flavors.contains(JsFlavors.RELEASE))
          .addString("rootPath", filesystem.getRootPath().toString())
          .addString("platform", JsUtil.getPlatformString(flavors))
          .addString("outputFilePath", outputPath.toString())
          .addArray(
              "sourceFilePaths",
              sources.stream()
                  .map(resolver::getAbsolutePath)
                  .map(AbsPath::toString)
                  .collect(JsonBuilder.toArrayOfStrings()));
    }
  }
}

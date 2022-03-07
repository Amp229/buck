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

package com.facebook.buck.android;

import com.facebook.buck.core.build.buildable.context.BuildableContext;
import com.facebook.buck.core.build.context.BuildContext;
import com.facebook.buck.core.build.execution.context.IsolatedExecutionContext;
import com.facebook.buck.core.build.execution.context.StepExecutionContext;
import com.facebook.buck.core.filesystems.RelPath;
import com.facebook.buck.core.model.BuildTarget;
import com.facebook.buck.core.model.impl.BuildPaths;
import com.facebook.buck.core.rulekey.AddToRuleKey;
import com.facebook.buck.core.rules.BuildRule;
import com.facebook.buck.core.rules.SourcePathRuleFinder;
import com.facebook.buck.core.rules.common.BuildableSupport;
import com.facebook.buck.core.rules.impl.AbstractBuildRule;
import com.facebook.buck.core.sourcepath.ExplicitBuildTargetSourcePath;
import com.facebook.buck.core.sourcepath.SourcePath;
import com.facebook.buck.core.sourcepath.resolver.SourcePathResolverAdapter;
import com.facebook.buck.core.toolchain.tool.Tool;
import com.facebook.buck.core.util.log.Logger;
import com.facebook.buck.io.filesystem.BuildCellRelativePath;
import com.facebook.buck.io.filesystem.ProjectFilesystem;
import com.facebook.buck.io.filesystem.impl.ProjectFilesystemUtils;
import com.facebook.buck.step.AbstractExecutionStep;
import com.facebook.buck.step.Step;
import com.facebook.buck.step.StepExecutionResult;
import com.facebook.buck.step.StepExecutionResults;
import com.facebook.buck.step.fs.CopyStep;
import com.facebook.buck.step.fs.MakeCleanDirectoryStep;
import com.facebook.buck.step.fs.MkdirStep;
import com.facebook.buck.step.isolatedsteps.shell.IsolatedShellStep;
import com.facebook.buck.util.ProcessExecutor;
import com.facebook.buck.zip.ZipScrubberStep;
import com.google.common.base.Joiner;
import com.google.common.collect.Collections2;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.SortedSet;
import java.util.stream.Collectors;
import javax.annotation.Nullable;

/** Perform the "aapt2 link" step of building an Android app. */
public class Aapt2Link extends AbstractBuildRule {
  private static final Logger LOG = Logger.get(Aapt2Link.class);

  @AddToRuleKey private final boolean includesVectorDrawables;
  @AddToRuleKey private final boolean noAutoVersion;
  @AddToRuleKey private final boolean noVersionTransitions;
  @AddToRuleKey private final boolean noAutoAddOverlay;
  @AddToRuleKey private final boolean useProtoFormat;
  @AddToRuleKey private final boolean noResourceRemoval;
  @AddToRuleKey private final ImmutableList<Aapt2Compile> compileRules;
  @AddToRuleKey private final SourcePath manifest;
  @AddToRuleKey private final int packageIdOffset;
  @AddToRuleKey private final ImmutableList<SourcePath> dependencyResourceApks;
  @AddToRuleKey private final Tool aapt2Tool;
  @AddToRuleKey private final ImmutableList<String> additionalAaptParams;
  @AddToRuleKey private final boolean filterLocales;
  @AddToRuleKey private final ImmutableSet<String> locales;
  @AddToRuleKey private final ImmutableSet<String> extraFilteredResources;
  @AddToRuleKey private final Optional<SourcePath> resourceStableIds;
  @AddToRuleKey private final Optional<String> preferredDensity;
  @AddToRuleKey private final Optional<Integer> minSdk;
  @AddToRuleKey private final boolean shouldKeepRawValues;
  private final Path androidJar;
  private final BuildableSupport.DepsSupplier depsSupplier;
  @AddToRuleKey private final boolean withDownwardApi;

  private static final int BASE_PACKAGE_ID = 0x7f;

  Aapt2Link(
      BuildTarget buildTarget,
      ProjectFilesystem projectFilesystem,
      SourcePathRuleFinder ruleFinder,
      ImmutableList<Aapt2Compile> compileRules,
      SourcePath manifest,
      int packageIdOffset,
      ImmutableList<SourcePath> dependencyResourceApks,
      boolean includesVectorDrawables,
      boolean noAutoVersion,
      boolean noVersionTransitions,
      boolean noAutoAddOverlay,
      boolean useProtoFormat,
      boolean noResourceRemoval,
      Tool aapt2Tool,
      ImmutableList<String> additionalAaptParams,
      Path androidJar,
      boolean filterLocales,
      ImmutableSet<String> locales,
      ImmutableSet<String> extraFilteredResources,
      Optional<SourcePath> resourceStableIds,
      Optional<String> preferredDensity,
      Optional<Integer> minSdk,
      boolean shouldKeepRawValues,
      boolean withDownwardApi) {
    super(buildTarget, projectFilesystem);
    this.compileRules = compileRules;
    this.manifest = manifest;
    this.packageIdOffset = packageIdOffset;
    this.dependencyResourceApks = dependencyResourceApks;
    this.includesVectorDrawables = includesVectorDrawables;
    this.noAutoVersion = noAutoVersion;
    this.noVersionTransitions = noVersionTransitions;
    this.noAutoAddOverlay = noAutoAddOverlay;
    this.noResourceRemoval = noResourceRemoval;
    this.useProtoFormat = useProtoFormat;
    this.androidJar = androidJar;
    this.aapt2Tool = aapt2Tool;
    this.additionalAaptParams = additionalAaptParams;
    this.depsSupplier = BuildableSupport.buildDepsSupplier(this, ruleFinder);
    this.filterLocales = filterLocales;
    this.locales = locales;
    this.extraFilteredResources = extraFilteredResources;
    this.resourceStableIds = resourceStableIds;
    this.preferredDensity = preferredDensity;
    this.minSdk = minSdk;
    this.shouldKeepRawValues = shouldKeepRawValues;
    this.withDownwardApi = withDownwardApi;
  }

  @Override
  public SortedSet<BuildRule> getBuildDeps() {
    return depsSupplier.get();
  }

  @Override
  public ImmutableList<Step> getBuildSteps(
      BuildContext context, BuildableContext buildableContext) {
    ImmutableList.Builder<Step> steps = ImmutableList.builder();

    steps.addAll(
        MakeCleanDirectoryStep.of(
            BuildCellRelativePath.fromCellRelativePath(
                context.getBuildCellRootPath(),
                getProjectFilesystem(),
                getResourceApkPath().getParent())));

    steps.add(
        MkdirStep.of(
            BuildCellRelativePath.fromCellRelativePath(
                context.getBuildCellRootPath(),
                getProjectFilesystem(),
                getFinalManifestPath().getParent())));
    steps.add(
        CopyStep.forFile(
            context.getSourcePathResolver().getRelativePath(getProjectFilesystem(), manifest),
            getFinalManifestPath()));

    // Need to reverse the order of the rules because aapt2 allows later resources
    // to override earlier ones, but aapt gives the earlier ones precedence.
    List<Path> compiledResourcePaths =
        Lists.reverse(compileRules).stream()
            .map(Aapt2Compile::getSourcePathToOutput)
            .map(
                sourcePath ->
                    context.getSourcePathResolver().getCellUnsafeRelPath(sourcePath).getPath())
            .collect(Collectors.toList());

    List<Path> compiledApkPaths =
        dependencyResourceApks.stream()
            .map(
                sourcePath ->
                    context.getSourcePathResolver().getCellUnsafeRelPath(sourcePath).getPath())
            .collect(Collectors.toList());
    steps.add(new Aapt2LinkArgsStep(getProjectFilesystem(), getArgsPath(), compiledResourcePaths));

    steps.add(
        new Aapt2LinkStep(
            getProjectFilesystem(),
            context.getSourcePathResolver(),
            getArgsPath(),
            compiledApkPaths,
            ProjectFilesystemUtils.relativize(
                getProjectFilesystem().getRootPath(), context.getBuildCellRootPath()),
            withDownwardApi));
    steps.add(ZipScrubberStep.of(getProjectFilesystem().resolve(getResourceApkPath())));

    if (!extraFilteredResources.isEmpty()) {
      steps.add(
          new ExtraFilterResourcesStep(
              getProjectFilesystem(),
              ProjectFilesystemUtils.relativize(
                  getProjectFilesystem().getRootPath(), context.getBuildCellRootPath()),
              withDownwardApi));
    }

    buildableContext.recordArtifact(getFinalManifestPath().getPath());
    buildableContext.recordArtifact(getResourceApkPath());
    buildableContext.recordArtifact(getProguardConfigPath());
    buildableContext.recordArtifact(getRDotTxtPath());
    // Don't really need this, but it's small and might help with debugging.
    buildableContext.recordArtifact(getInitialRDotJavaDir());

    return steps.build();
  }

  @Nullable
  @Override
  public SourcePath getSourcePathToOutput() {
    return null;
  }

  private RelPath getGenDir() {
    return BuildPaths.getGenDir(getProjectFilesystem().getBuckPaths(), getBuildTarget());
  }

  private Path getArgsPath() {
    return getGenDir().resolve("aapt2-R-args.txt");
  }

  private RelPath getFinalManifestPath() {
    return getGenDir().resolveRel("AndroidManifest.xml");
  }

  private Path getResourceApkPath() {
    return getGenDir().resolve("resource-apk.ap_");
  }

  private Path getProguardConfigPath() {
    return getGenDir().resolve("proguard-for-resources.pro");
  }

  private Path getRDotTxtPath() {
    return getGenDir().resolve("R.txt");
  }

  /** Directory containing R.java files produced by aapt2 link. */
  private Path getInitialRDotJavaDir() {
    return getGenDir().resolve("initial-rdotjava");
  }

  public AaptOutputInfo getAaptOutputInfo() {
    return ImmutableAaptOutputInfo.ofImpl(
        ExplicitBuildTargetSourcePath.of(getBuildTarget(), getRDotTxtPath()),
        ExplicitBuildTargetSourcePath.of(getBuildTarget(), getResourceApkPath()),
        ExplicitBuildTargetSourcePath.of(getBuildTarget(), getFinalManifestPath()),
        ExplicitBuildTargetSourcePath.of(getBuildTarget(), getProguardConfigPath()));
  }

  /**
   * The normal resource filtering apparatus is super slow, because it extracts the whole apk,
   * strips files out of it, then repackages it.
   *
   * <p>This is a faster filtering step that just uses zip -d to remove entries from the archive.
   * It's also superbly dangerous.
   */
  class ExtraFilterResourcesStep extends IsolatedShellStep {
    private static final int ZIP_NOTHING_TO_DO_EXIT_CODE = 12;

    ExtraFilterResourcesStep(
        ProjectFilesystem filesystem, RelPath cellPath, boolean withDownwardApi) {
      super(filesystem.getRootPath(), cellPath, withDownwardApi);
    }

    @Override
    public String getShortName() {
      return "aapt2_extra_filter_resources";
    }

    @Override
    protected ImmutableList<String> getShellCommandInternal(IsolatedExecutionContext context) {
      ImmutableList.Builder<String> builder = ImmutableList.builder();
      builder.add("zip");
      builder.add("-d");
      builder.add(getResourceApkPath().toString());
      for (String extra : extraFilteredResources) {
        builder.add(extra);
      }
      return builder.build();
    }

    @Override
    public int getExitCodeFromResult(ProcessExecutor.Result result) {
      // If there's nothing to do (i.e. no matches), print a warning, but don't fail.
      int realExitCode = result.getExitCode();
      if (realExitCode == ZIP_NOTHING_TO_DO_EXIT_CODE) {
        LOG.warn(
            "extra_filtered_resources pattern '%s' has no matches in '%s'",
            extraFilteredResources, getResourceApkPath().toString());
        return 0;
      }
      return realExitCode;
    }
  }

  class Aapt2LinkStep extends IsolatedShellStep {
    private final ProjectFilesystem filesystem;
    private final SourcePathResolverAdapter pathResolver;
    private final Path argsFile;
    private final List<Path> compiledResourceApkPaths;

    Aapt2LinkStep(
        ProjectFilesystem filesystem,
        SourcePathResolverAdapter pathResolver,
        Path argsFile,
        List<Path> compiledResourceApkPaths,
        RelPath cellPath,
        boolean withDownwardApi) {
      super(filesystem.getRootPath(), cellPath, withDownwardApi);
      this.filesystem = filesystem;
      this.pathResolver = pathResolver;
      this.argsFile = argsFile;
      this.compiledResourceApkPaths = compiledResourceApkPaths;
    }

    @Override
    public String getShortName() {
      return "aapt2_link";
    }

    @Override
    protected ImmutableList<String> getShellCommandInternal(IsolatedExecutionContext context) {
      ImmutableList.Builder<String> builder = ImmutableList.builder();
      builder.addAll(aapt2Tool.getCommandPrefix(pathResolver));

      builder.add("link");
      // aapt2 only supports @ for -R or input files, not for all args, so we pass in all "normal"
      // args here.
      builder.add("-o", getResourceApkPath().toString());
      builder.add("--manifest", getFinalManifestPath().toString());
      if (context.getVerbosity().shouldUseVerbosityFlagIfAvailable()) {
        builder.add("-v");
      }

      if (includesVectorDrawables) {
        builder.add("--no-version-vectors");
      }

      if (noAutoVersion) {
        builder.add("--no-auto-version");
      }

      if (noVersionTransitions) {
        builder.add("--no-version-transitions");
      }

      if (!noAutoAddOverlay) {
        builder.add("--auto-add-overlay");
      }

      if (useProtoFormat) {
        builder.add("--proto-format");
      }

      if (noResourceRemoval) {
        builder.add("--no-resource-removal");
      }

      if (packageIdOffset != 0) {
        builder.add("--package-id", String.format("0x%x", BASE_PACKAGE_ID + packageIdOffset));
      }

      if (shouldKeepRawValues) {
        builder.add("--keep-raw-values");
      }

      if (resourceStableIds.isPresent()) {
        builder.add(
            "--stable-ids", pathResolver.getAbsolutePath(resourceStableIds.get()).toString());
      }

      preferredDensity.ifPresent(density -> builder.add("--preferred-density", density));

      minSdk.ifPresent(minSdk -> builder.add("--min-sdk-version", Integer.toString(minSdk)));

      if (filterLocales && !locales.isEmpty()) {
        // "NONE" means "en", update the list of locales
        ImmutableSet<String> updatedLocales =
            ImmutableSet.copyOf(
                Collections2.transform(locales, (String i) -> "NONE".equals(i) ? "en" : i));
        builder.add("-c", Joiner.on(',').join(updatedLocales));
      }

      builder.add("--proguard", getProguardConfigPath().toString());
      builder.add("-I", androidJar.toString());
      for (Path resourceApk : compiledResourceApkPaths) {
        builder.add("-I", resourceApk.toString());
      }
      // We don't need the R.java output, but aapt2 won't output R.txt
      // unless we also request R.java.
      builder.add("--java", getInitialRDotJavaDir().toString());
      builder.add("--output-text-symbols", getRDotTxtPath().toString());

      builder.add("-R", "@" + filesystem.resolve(argsFile).toString());

      builder.addAll(additionalAaptParams);

      return builder.build();
    }
  }

  /** Generates aapt2 args into a file that can be passed to the tool. */
  class Aapt2LinkArgsStep extends AbstractExecutionStep {
    private final ProjectFilesystem filesystem;
    private final Path argsFilePath;
    private final List<Path> compiledResourcePaths;

    Aapt2LinkArgsStep(
        ProjectFilesystem filesystem, Path argsFilePath, List<Path> compiledResourcePaths) {
      super("write_aapt2_command_line_arguments");
      this.filesystem = filesystem;
      this.argsFilePath = argsFilePath;
      this.compiledResourcePaths = compiledResourcePaths;
    }

    @Override
    public StepExecutionResult execute(StepExecutionContext context) throws IOException {
      String args = Joiner.on(' ').join(getParameters());
      filesystem.writeContentsToPath(args, argsFilePath);

      return StepExecutionResults.SUCCESS;
    }

    private ImmutableList<String> getParameters() {
      ImmutableList.Builder<String> builder = ImmutableList.builder();
      compiledResourcePaths.forEach(r -> builder.add(r.toString()));
      return builder.build();
    }
  }
}

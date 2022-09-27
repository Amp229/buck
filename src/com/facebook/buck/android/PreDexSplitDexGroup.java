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

import com.facebook.buck.android.apkmodule.APKModule;
import com.facebook.buck.android.apkmodule.APKModuleGraph;
import com.facebook.buck.android.toolchain.AndroidPlatformTarget;
import com.facebook.buck.core.build.buildable.context.BuildableContext;
import com.facebook.buck.core.build.context.BuildContext;
import com.facebook.buck.core.build.execution.context.StepExecutionContext;
import com.facebook.buck.core.model.BuildTarget;
import com.facebook.buck.core.model.impl.BuildPaths;
import com.facebook.buck.core.rulekey.AddToRuleKey;
import com.facebook.buck.core.rules.BuildRuleParams;
import com.facebook.buck.core.rules.attr.BuildOutputInitializer;
import com.facebook.buck.core.rules.attr.InitializableFromDisk;
import com.facebook.buck.core.rules.attr.SupportsInputBasedRuleKey;
import com.facebook.buck.core.rules.impl.AbstractBuildRuleWithDeclaredAndExtraDeps;
import com.facebook.buck.core.sourcepath.SourcePath;
import com.facebook.buck.core.sourcepath.resolver.SourcePathResolverAdapter;
import com.facebook.buck.io.filesystem.BuildCellRelativePath;
import com.facebook.buck.io.filesystem.ProjectFilesystem;
import com.facebook.buck.step.AbstractExecutionStep;
import com.facebook.buck.step.Step;
import com.facebook.buck.step.StepExecutionResult;
import com.facebook.buck.step.StepExecutionResults;
import com.facebook.buck.step.fs.CopyStep;
import com.facebook.buck.step.fs.MakeCleanDirectoryStep;
import com.facebook.buck.step.fs.MkdirStep;
import com.facebook.buck.util.json.ObjectMappers;
import com.facebook.buck.util.sha1.Sha1HashCode;
import com.fasterxml.jackson.core.type.TypeReference;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimaps;
import com.google.common.collect.Ordering;
import com.google.common.util.concurrent.ListeningExecutorService;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.annotation.Nullable;

/**
 * Takes a subset of the predexed libraries (for a single APKModule) and produces copies of the
 * predexed libraries to be merged into the primary dex, and a set of secondary dexes, capped at the
 * dex weight limit.
 *
 * <p>Separating this from PreDexSplitDexMerge and making it cacheable makes it possible to fetch a
 * much smaller set of artifacts from cache when the predexed libraries are invalidated.
 */
public class PreDexSplitDexGroup extends AbstractBuildRuleWithDeclaredAndExtraDeps
    implements InitializableFromDisk<PreDexSplitDexGroup.BuildOutput>,
        TrimUberRDotJava.UsesResources,
        SupportsInputBasedRuleKey {

  @AddToRuleKey private final DexSplitMode dexSplitMode;
  @AddToRuleKey final int secondaryDexWeightLimit;

  private final APKModuleGraph<BuildTarget> apkModuleGraph;
  final APKModule apkModule;
  public final Collection<DexProducedFromJavaLibrary> preDexDeps;
  private final ListeningExecutorService dxExecutorService;
  @AddToRuleKey private final int xzCompressionLevel;

  @AddToRuleKey final AndroidPlatformTarget androidPlatformTarget;

  // If this isn't added to the rulekey, it's possible to clobber existing dex files and canary
  // names if predex inputs match, but groups indices don't
  @AddToRuleKey private Optional<Integer> groupIndex;

  private final BuildOutputInitializer<BuildOutput> buildOutputInitializer;

  @AddToRuleKey
  @SuppressWarnings("PMD.UnusedPrivateField")
  private final ImmutableList<SourcePath> preDexInputs;

  public PreDexSplitDexGroup(
      BuildTarget buildTarget,
      ProjectFilesystem projectFilesystem,
      BuildRuleParams params,
      AndroidPlatformTarget androidPlatformTarget,
      DexSplitMode dexSplitMode,
      APKModuleGraph<BuildTarget> apkModuleGraph,
      APKModule apkModule,
      Collection<DexProducedFromJavaLibrary> preDexDeps,
      ListeningExecutorService dxExecutorService,
      int xzCompressionLevel,
      Optional<Integer> groupIndex,
      int secondaryDexWeightLimit) {
    super(buildTarget, projectFilesystem, params);
    this.androidPlatformTarget = androidPlatformTarget;
    this.dexSplitMode = dexSplitMode;
    this.apkModuleGraph = apkModuleGraph;
    this.dxExecutorService = dxExecutorService;
    this.xzCompressionLevel = xzCompressionLevel;
    this.apkModule = apkModule;
    this.preDexDeps = preDexDeps;
    this.groupIndex = groupIndex;
    this.buildOutputInitializer = new BuildOutputInitializer<>(buildTarget, this);
    this.preDexInputs =
        preDexDeps.stream()
            .map(DexProducedFromJavaLibrary::getSourcePathToDex)
            .collect(ImmutableList.toImmutableList());
    this.secondaryDexWeightLimit = secondaryDexWeightLimit;
  }

  public List<DexWithClasses> getDexWithClasses() {
    return preDexDeps.stream()
        .map(DexWithClasses.TO_DEX_WITH_CLASSES)
        .filter(Objects::nonNull)
        .collect(Collectors.toList());
  }

  @Override
  public ImmutableList<Step> getBuildSteps(
      BuildContext context, BuildableContext buildableContext) {

    ImmutableList.Builder<Step> steps = ImmutableList.builder();
    ProjectFilesystem filesystem = getProjectFilesystem();
    steps.addAll(
        MakeCleanDirectoryStep.of(
            BuildCellRelativePath.fromCellRelativePath(
                context.getBuildCellRootPath(), filesystem, getPrimaryDexRoot())));

    Path primaryDexDir = getPrimaryDexRoot();
    Path primaryDexInputHashesPath = getPrimaryDexInputHashesPath();
    Path primaryDexClassNamesPath = getPrimaryDexClassNamesPath();
    Path secondaryDexDir = getSecondaryDexRoot();
    Path outputHashDir = getOutputHashDirectory();
    Path metadataTxtPath = getMetadataTxtPath();
    Path canaryDir = getCanaryDirectory();
    steps.add(
        MkdirStep.of(
            BuildCellRelativePath.fromCellRelativePath(
                context.getBuildCellRootPath(), filesystem, outputHashDir)));
    // Do not clear existing directory which might contain secondary dex files that are not
    // re-merged (since their contents did not change).
    steps.add(
        MkdirStep.of(
            BuildCellRelativePath.fromCellRelativePath(
                context.getBuildCellRootPath(), filesystem, secondaryDexDir)));
    steps.addAll(
        MakeCleanDirectoryStep.of(
            BuildCellRelativePath.fromCellRelativePath(
                context.getBuildCellRootPath(), filesystem, canaryDir)));

    buildableContext.recordArtifact(primaryDexDir);
    buildableContext.recordArtifact(primaryDexInputHashesPath);
    buildableContext.recordArtifact(primaryDexClassNamesPath);
    buildableContext.recordArtifact(secondaryDexDir);
    buildableContext.recordArtifact(outputHashDir);
    buildableContext.recordArtifact(metadataTxtPath);
    buildableContext.recordArtifact(getReferencedResourcesPath());

    final ImmutableSet<String> primaryDexPatterns = getPrimaryDexPatterns();
    PreDexedFilesSorter preDexedFilesSorter =
        new PreDexedFilesSorter(
            getDexWithClasses(),
            primaryDexPatterns,
            apkModuleGraph,
            apkModule,
            canaryDir,
            secondaryDexWeightLimit,
            dexSplitMode.getDexStore(),
            secondaryDexDir,
            groupIndex);
    PreDexedFilesSorter.Result result =
        preDexedFilesSorter.sortIntoPrimaryAndSecondaryDexes(filesystem, steps);

    SourcePathResolverAdapter sourcePathResolverAdapter = context.getSourcePathResolver();
    ImmutableMultimap.Builder<Path, SourcePath> aggregatedOutputToInputs =
        ImmutableMultimap.builder();
    aggregatedOutputToInputs.orderKeysBy(Ordering.natural());

    aggregatedOutputToInputs.putAll(result.secondaryOutputToInputs);
    ImmutableMap<Path, Sha1HashCode> dexInputHashes =
        resolveDexInputHashPaths(sourcePathResolverAdapter, result.secondaryDexInputHashes);

    steps.addAll(
        MakeCleanDirectoryStep.of(
            BuildCellRelativePath.fromCellRelativePath(
                context.getBuildCellRootPath(), filesystem, primaryDexDir)));

    result.primaryDexInputs.forEach(
        (primaryDexInput) -> {
          Path input =
              sourcePathResolverAdapter
                  .getRelativePath(filesystem, primaryDexInput.sourcePath)
                  .getPath();
          steps.add(CopyStep.forFile(input, primaryDexDir.resolve(primaryDexInput.jarName)));
        });

    steps.add(
        new AbstractExecutionStep("maybe_write_primary_dex_class_names") {
          @Override
          public StepExecutionResult execute(StepExecutionContext context) throws IOException {
            ImmutableList.Builder<String> classNames = ImmutableList.builder();
            result.primaryDexInputs.forEach(
                primaryDexInput -> classNames.addAll(primaryDexInput.classNames));

            writePrimaryDexClassNames(primaryDexClassNamesPath, classNames.build());
            return StepExecutionResults.SUCCESS;
          }
        });

    steps.add(
        new AbstractExecutionStep("write_primary_dex_input_metadata") {
          @Override
          public StepExecutionResult execute(StepExecutionContext context) throws IOException {
            writePrimaryDexInputMetadata(primaryDexInputHashesPath, result.primaryDexInputMetadata);
            return StepExecutionResults.SUCCESS;
          }
        });

    steps.add(
        new AbstractExecutionStep("write_referenced_resources") {
          @Override
          public StepExecutionResult execute(StepExecutionContext context) throws IOException {
            ImmutableList.Builder<String> builder = ImmutableList.builder();
            for (DexProducedFromJavaLibrary dex : preDexDeps) {
              builder.addAll(dex.getReferencedResources());
            }
            writeReferencedResources(getReferencedResourcesPath(), builder.build());
            return StepExecutionResults.SUCCESS;
          }
        });

    steps.add(
        new SmartDexingStep(
            androidPlatformTarget,
            context,
            filesystem,
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.of(secondaryDexDir),
            Optional.of(
                Suppliers.ofInstance(
                    Multimaps.transformValues(
                        aggregatedOutputToInputs.build(),
                        path ->
                            sourcePathResolverAdapter
                                .getRelativePath(filesystem, path)
                                .getPath()))),
            () -> dexInputHashes,
            outputHashDir,
            PreDexMerge.DX_MERGE_OPTIONS,
            dxExecutorService,
            xzCompressionLevel,
            false,
            Optional.empty(),
            getBuildTarget(),
            Optional.empty() /* minSdkVersion */));

    steps.add(
        new AbstractExecutionStep("write_metadata_txt") {
          @Override
          public StepExecutionResult execute(StepExecutionContext executionContext)
              throws IOException {
            Map<Path, DexWithClasses> metadataTxtEntries = result.metadataTxtDexEntries;
            List<String> lines = Lists.newArrayListWithCapacity(metadataTxtEntries.size());
            for (Map.Entry<Path, DexWithClasses> entry : metadataTxtEntries.entrySet()) {
              Path pathToSecondaryDex = entry.getKey();
              String containedClass = Iterables.get(entry.getValue().getClassNames(), 0);
              containedClass = containedClass.replace('/', '.');
              Sha1HashCode hash = filesystem.computeSha1(pathToSecondaryDex);
              lines.add(
                  String.format(
                      "%s %s %s", pathToSecondaryDex.getFileName(), hash, containedClass));
            }
            filesystem.writeLinesToPath(lines, metadataTxtPath);
            return StepExecutionResults.SUCCESS;
          }
        });
    return steps.build();
  }

  @Override
  public ImmutableList<String> getReferencedResources() {
    return buildOutputInitializer.getBuildOutput().referencedResources;
  }

  @VisibleForTesting
  public Optional<Integer> getGroupIndex() {
    return groupIndex;
  }

  int getSecondaryDexCount() {
    try {
      return getProjectFilesystem().readLines(getMetadataTxtPath()).size();
    } catch (IOException e) {
      throw new RuntimeException("Couldn't get secondary dex count", e);
    }
  }

  public Path getPrimaryDexRoot() {
    return BuildPaths.getGenDir(getProjectFilesystem().getBuckPaths(), getBuildTarget())
        .resolve("primary");
  }

  public Path getReferencedResourcesPath() {
    return BuildPaths.getGenDir(getProjectFilesystem().getBuckPaths(), getBuildTarget())
        .resolve("referenced_resources.txt");
  }

  public Path getPrimaryDexInputHashesPath() {
    return BuildPaths.getGenDir(getProjectFilesystem().getBuckPaths(), getBuildTarget())
        .resolve("primary_dex_input_hashes.txt");
  }

  public Path getPrimaryDexClassNamesPath() {
    return BuildPaths.getGenDir(getProjectFilesystem().getBuckPaths(), getBuildTarget())
        .resolve("primary_dex_class_names.txt");
  }

  Path getSecondaryDexRoot() {
    return BuildPaths.getGenDir(getProjectFilesystem().getBuckPaths(), getBuildTarget())
        .resolve("secondary");
  }

  public Path getMetadataTxtPath() {
    return BuildPaths.getGenDir(getProjectFilesystem().getBuckPaths(), getBuildTarget())
        .resolve("metadata.txt");
  }

  Path getOutputHashDirectory() {
    return BuildPaths.getScratchDir(getProjectFilesystem(), getBuildTarget())
        .resolve("output_hashes");
  }

  Path getCanaryDirectory() {
    return BuildPaths.getScratchDir(getProjectFilesystem(), getBuildTarget()).resolve("canaries");
  }

  private ImmutableSet<String> getPrimaryDexPatterns() {
    if (dexSplitMode.isAllowRDotJavaInSecondaryDex()) {
      return dexSplitMode.getPrimaryDexPatterns();
    } else {
      return ImmutableSet.<String>builder()
          .addAll(dexSplitMode.getPrimaryDexPatterns())
          .add(
              "/R^",
              "/R$",
              // Pin this to the primary for test apps with no primary dex classes.
              // The exact match makes it fairly efficient.
              "^com/facebook/buck_generated/AppWithoutResourcesStub^")
          .build();
    }
  }

  private ImmutableMap<Path, Sha1HashCode> resolveDexInputHashPaths(
      SourcePathResolverAdapter sourcePathResolverAdapter,
      ImmutableMap<SourcePath, Sha1HashCode> dexInputHashes) {
    ImmutableMap.Builder<Path, Sha1HashCode> dexInputHashesBuilder = ImmutableMap.builder();
    ProjectFilesystem projectFilesystem = getProjectFilesystem();
    for (Map.Entry<SourcePath, Sha1HashCode> entry : dexInputHashes.entrySet()) {
      dexInputHashesBuilder.put(
          sourcePathResolverAdapter.getRelativePath(projectFilesystem, entry.getKey()).getPath(),
          entry.getValue());
    }
    return dexInputHashesBuilder.build();
  }

  @Nullable
  @Override
  public SourcePath getSourcePathToOutput() {
    return null;
  }

  @Override
  public BuildOutput initializeFromDisk(SourcePathResolverAdapter pathResolver) throws IOException {
    return new BuildOutput(
        readPrimaryDexInputMetadata(), readReferencedResources(), readPrimaryDexClassNames());
  }

  private ImmutablePrimaryDexInputMetadata readPrimaryDexInputMetadata() throws IOException {
    return ObjectMappers.readValue(
        getProjectFilesystem().readFileIfItExists(getPrimaryDexInputHashesPath()).get(),
        new TypeReference<ImmutablePrimaryDexInputMetadata>() {});
  }

  private List<String> readPrimaryDexClassNames() throws IOException {
    return ObjectMappers.readValue(
        getProjectFilesystem().readFileIfItExists(getPrimaryDexClassNamesPath()).get(),
        new TypeReference<List<String>>() {});
  }

  private ImmutableList<String> readReferencedResources() throws IOException {
    List<String> list =
        ObjectMappers.readValue(
            getProjectFilesystem().readFileIfItExists(getReferencedResourcesPath()).get(),
            new TypeReference<List<String>>() {});
    return ImmutableList.copyOf(list);
  }

  private void writePrimaryDexInputMetadata(
      Path outputPath, ImmutablePrimaryDexInputMetadata primaryDexInputs) throws IOException {
    getProjectFilesystem()
        .writeContentsToPath(ObjectMappers.WRITER.writeValueAsString(primaryDexInputs), outputPath);
  }

  private void writePrimaryDexClassNames(Path outputPath, List<String> classNames)
      throws IOException {
    getProjectFilesystem()
        .writeContentsToPath(ObjectMappers.WRITER.writeValueAsString(classNames), outputPath);
  }

  private void writeReferencedResources(Path outputPath, ImmutableList<String> referencedResources)
      throws IOException {
    getProjectFilesystem()
        .writeContentsToPath(
            ObjectMappers.WRITER.writeValueAsString(referencedResources), outputPath);
  }

  @Override
  public BuildOutputInitializer<BuildOutput> getBuildOutputInitializer() {
    return buildOutputInitializer;
  }

  /** Contains serialized build output accessible from the rule type */
  static class BuildOutput {
    final ImmutablePrimaryDexInputMetadata primaryDexInputMetadata;
    final ImmutableList<String> referencedResources;
    final List<String> primaryDexClassNames;

    BuildOutput(
        ImmutablePrimaryDexInputMetadata primaryDexInputMetadata,
        ImmutableList<String> referencedResources,
        List<String> primaryDexClassNames) {
      this.primaryDexInputMetadata = primaryDexInputMetadata;
      this.referencedResources = referencedResources;
      this.primaryDexClassNames = primaryDexClassNames;
    }
  }

  public ImmutablePrimaryDexInputMetadata getPrimaryDexInputMetadata() {
    return buildOutputInitializer.getBuildOutput().primaryDexInputMetadata;
  }

  public List<String> getPrimaryDexClassNames() {
    return buildOutputInitializer.getBuildOutput().primaryDexClassNames;
  }
}

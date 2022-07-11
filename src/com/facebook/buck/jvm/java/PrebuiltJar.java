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

import com.facebook.buck.android.packageable.AndroidPackageable;
import com.facebook.buck.android.packageable.AndroidPackageableCollector;
import com.facebook.buck.android.toolchain.ndk.NdkCxxPlatform;
import com.facebook.buck.core.build.buildable.context.BuildableContext;
import com.facebook.buck.core.build.context.BuildContext;
import com.facebook.buck.core.filesystems.AbsPath;
import com.facebook.buck.core.filesystems.RelPath;
import com.facebook.buck.core.model.BuildTarget;
import com.facebook.buck.core.model.impl.BuildTargetPaths;
import com.facebook.buck.core.rulekey.AddToRuleKey;
import com.facebook.buck.core.rules.ActionGraphBuilder;
import com.facebook.buck.core.rules.BuildRule;
import com.facebook.buck.core.rules.BuildRuleParams;
import com.facebook.buck.core.rules.BuildRuleResolver;
import com.facebook.buck.core.rules.attr.BuildOutputInitializer;
import com.facebook.buck.core.rules.attr.ExportDependencies;
import com.facebook.buck.core.rules.attr.InitializableFromDisk;
import com.facebook.buck.core.rules.attr.SupportsInputBasedRuleKey;
import com.facebook.buck.core.rules.common.BuildRules;
import com.facebook.buck.core.rules.impl.AbstractBuildRuleWithDeclaredAndExtraDeps;
import com.facebook.buck.core.sourcepath.ExplicitBuildTargetSourcePath;
import com.facebook.buck.core.sourcepath.SourcePath;
import com.facebook.buck.core.sourcepath.resolver.SourcePathResolverAdapter;
import com.facebook.buck.event.ConsoleEvent;
import com.facebook.buck.io.file.MorePaths;
import com.facebook.buck.io.filesystem.BuildCellRelativePath;
import com.facebook.buck.io.filesystem.ProjectFilesystem;
import com.facebook.buck.jvm.cd.JavaLibraryRules;
import com.facebook.buck.jvm.core.DefaultJavaAbiInfo;
import com.facebook.buck.jvm.core.JavaAbiInfo;
import com.facebook.buck.jvm.core.JavaAbis;
import com.facebook.buck.jvm.core.JavaClassHashesProvider;
import com.facebook.buck.jvm.core.JavaLibrary;
import com.facebook.buck.step.Step;
import com.facebook.buck.step.fs.CopyStep;
import com.facebook.buck.step.fs.MakeCleanDirectoryStep;
import com.facebook.buck.step.fs.MkdirStep;
import com.facebook.buck.step.isolatedsteps.IsolatedStep;
import com.facebook.buck.util.MoreSuppliers;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedMap;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Ordering;
import com.google.common.collect.Streams;
import com.google.common.hash.HashCode;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.SortedSet;
import java.util.function.Supplier;
import java.util.stream.Stream;

public class PrebuiltJar extends AbstractBuildRuleWithDeclaredAndExtraDeps
    implements AndroidPackageable,
        ExportDependencies,
        InitializableFromDisk<JavaLibrary.Data>,
        JavaLibrary,
        MaybeRequiredForSourceOnlyAbi,
        SupportsInputBasedRuleKey {

  @AddToRuleKey private final SourcePath binaryJar;
  @AddToRuleKey private final Optional<String> mavenCoords;
  @AddToRuleKey private final boolean requiredForSourceOnlyAbi;
  @AddToRuleKey private final boolean generateAbi;
  @AddToRuleKey private final boolean neverMarkAsUnusedDependency;

  private final JavaAbiInfo javaAbiInfo;
  private final RelPath copiedBinaryJar;

  private final Supplier<ImmutableSet<SourcePath>> transitiveClasspathsSupplier;
  private final Supplier<ImmutableSet<JavaLibrary>> transitiveClasspathDepsSupplier;

  private final BuildOutputInitializer<JavaLibrary.Data> buildOutputInitializer;
  private final JavaClassHashesProvider javaClassHashesProvider;
  private final boolean shouldDesugarInterfaceMethodsInPrebuiltJars;

  public PrebuiltJar(
      BuildTarget buildTarget,
      ProjectFilesystem projectFilesystem,
      BuildRuleParams params,
      SourcePathResolverAdapter resolver,
      SourcePath binaryJar,
      Optional<String> mavenCoords,
      boolean requiredForSourceOnlyAbi,
      boolean generateAbi,
      boolean neverMarkAsUnusedDependency,
      boolean shouldDesugarInterfaceMethodsInPrebuiltJars) {
    super(buildTarget, projectFilesystem, params);
    this.binaryJar = binaryJar;
    this.mavenCoords = mavenCoords;
    this.requiredForSourceOnlyAbi = requiredForSourceOnlyAbi;
    this.generateAbi = generateAbi;
    this.neverMarkAsUnusedDependency = neverMarkAsUnusedDependency;

    this.transitiveClasspathsSupplier =
        MoreSuppliers.memoize(
            () ->
                JavaLibraryClasspathProvider.getClasspathsFromLibraries(
                    getTransitiveClasspathDeps()));

    this.transitiveClasspathDepsSupplier =
        MoreSuppliers.memoize(
            () -> {
              return ImmutableSet.<JavaLibrary>builder()
                  .add(PrebuiltJar.this)
                  .addAll(
                      JavaLibraryClasspathProvider.getClasspathDeps(
                          PrebuiltJar.this.getDeclaredDeps()))
                  .build();
            });

    Path fileName = resolver.getCellUnsafeRelPath(binaryJar).getFileName();
    String fileNameWithJarExtension =
        String.format("%s.jar", MorePaths.getNameWithoutExtension(fileName));
    this.copiedBinaryJar =
        BuildTargetPaths.getGenPath(
            getProjectFilesystem().getBuckPaths(),
            buildTarget,
            "__%s__/" + fileNameWithJarExtension);
    this.javaAbiInfo = DefaultJavaAbiInfo.of(getSourcePathToOutput());

    this.buildOutputInitializer = new BuildOutputInitializer<>(buildTarget, this);

    this.javaClassHashesProvider =
        new DefaultJavaClassHashesProvider(
            ExplicitBuildTargetSourcePath.of(buildTarget, getPathToClassHashes().getPath()));

    this.shouldDesugarInterfaceMethodsInPrebuiltJars = shouldDesugarInterfaceMethodsInPrebuiltJars;
  }

  @Override
  public boolean getRequiredForSourceOnlyAbi() {
    return requiredForSourceOnlyAbi;
  }

  @Override
  public boolean isDesugarEnabled() {
    return true;
  }

  @Override
  public boolean isInterfaceMethodsDesugarEnabled() {
    return shouldDesugarInterfaceMethodsInPrebuiltJars;
  }

  @Override
  public ImmutableSortedSet<SourcePath> getDesugarDeps() {
    // We only need deps for desugaring interface methods
    if (isDesugarEnabled() && isInterfaceMethodsDesugarEnabled()) {
      // We provide all transitive Java dependencies to support Java 8 interface desugaring in D8.
      // If this JAR is built with Java 8 and depends on anther Java 8 library using default or
      // static interface methods, we need to desugar them together so that implementers of that
      // interface in this JAR get correctly desugared as well.
      return Streams.concat(
              getDeclaredDeps().stream(), BuildRules.getExportedRules(getDeclaredDeps()).stream())
          .filter(JavaLibrary.class::isInstance)
          .map(BuildRule::getSourcePathToOutput)
          .filter(Objects::nonNull)
          .collect(ImmutableSortedSet.toImmutableSortedSet(Ordering.natural()));
    }
    return ImmutableSortedSet.of();
  }

  @Override
  public ImmutableSortedMap<String, HashCode> getClassNamesToHashes() {
    return buildOutputInitializer.getBuildOutput().getClassNamesToHashes();
  }

  @Override
  public void invalidateInitializeFromDiskState() {
    javaAbiInfo.invalidate();
    javaClassHashesProvider.invalidate();
  }

  @Override
  public JavaLibrary.Data initializeFromDisk(SourcePathResolverAdapter pathResolver)
      throws IOException {
    // Warm up the jar contents. We just wrote the thing, so it should be in the filesystem cache
    javaAbiInfo.load(pathResolver);
    return JavaLibraryRules.initializeFromDisk(getBuildTarget(), getProjectFilesystem());
  }

  @Override
  public BuildOutputInitializer<JavaLibrary.Data> getBuildOutputInitializer() {
    return buildOutputInitializer;
  }

  @Override
  public Set<BuildRule> getDepsForTransitiveClasspathEntries() {
    return getDeclaredDeps();
  }

  @Override
  public ImmutableSet<SourcePath> getTransitiveClasspaths() {
    return transitiveClasspathsSupplier.get();
  }

  @Override
  public ImmutableSet<JavaLibrary> getTransitiveClasspathDeps() {
    return transitiveClasspathDepsSupplier.get();
  }

  @Override
  public ImmutableSet<SourcePath> getImmediateClasspaths() {
    return ImmutableSet.of(getSourcePathToOutput());
  }

  @Override
  public ImmutableSet<SourcePath> getOutputClasspaths() {
    return ImmutableSet.of(getSourcePathToOutput());
  }

  @Override
  public ImmutableSet<SourcePath> getCompileTimeClasspathSourcePaths() {
    return ImmutableSet.of();
  }

  @Override
  public ImmutableSortedSet<SourcePath> getJavaSrcs() {
    return ImmutableSortedSet.of();
  }

  @Override
  public ImmutableSortedSet<SourcePath> getSources() {
    return ImmutableSortedSet.of();
  }

  @Override
  public ImmutableSortedSet<SourcePath> getResources() {
    return ImmutableSortedSet.of();
  }

  @Override
  public Optional<String> getResourcesRoot() {
    return Optional.empty();
  }

  @Override
  public SortedSet<BuildRule> getExportedDeps() {
    return getDeclaredDeps();
  }

  @Override
  public SortedSet<BuildRule> getExportedProvidedDeps() {
    return ImmutableSortedSet.of();
  }

  @Override
  public Optional<SourcePath> getGeneratedAnnotationSourcePath() {
    return Optional.empty();
  }

  @Override
  public boolean hasAnnotationProcessing() {
    return false;
  }

  @Override
  public boolean neverMarkAsUnusedDependency() {
    return neverMarkAsUnusedDependency;
  }

  @Override
  public Stream<BuildTarget> getRuntimeDeps(BuildRuleResolver buildRuleResolver) {
    return Stream.of();
  }

  @Override
  public ImmutableList<Step> getBuildSteps(
      BuildContext context, BuildableContext buildableContext) {
    ImmutableList.Builder<Step> steps = ImmutableList.builder();

    SourcePathResolverAdapter resolver = context.getSourcePathResolver();

    // Create a copy of the JAR in case it was generated by another rule.
    AbsPath resolvedBinaryJar = resolver.getAbsolutePath(binaryJar);
    ProjectFilesystem projectFilesystem = getProjectFilesystem();
    AbsPath resolvedCopiedBinaryJar = projectFilesystem.resolve(copiedBinaryJar);
    Preconditions.checkState(
        !resolvedBinaryJar.equals(resolvedCopiedBinaryJar),
        "%s: source (%s) can't be equal to destination (%s) when copying prebuilt JAR.",
        getBuildTarget().getFullyQualifiedName(),
        resolvedBinaryJar,
        copiedBinaryJar);

    if (resolver.getFilesystem(binaryJar).isDirectory(resolvedBinaryJar)) {
      steps.addAll(
          MakeCleanDirectoryStep.of(
              BuildCellRelativePath.fromCellRelativePath(
                  context.getBuildCellRootPath(), projectFilesystem, copiedBinaryJar)));
      steps.add(
          CopyStep.forDirectory(
              resolvedBinaryJar.getPath(),
              copiedBinaryJar.getPath(),
              CopyStep.DirectoryMode.CONTENTS_ONLY));
    } else {
      if (!MorePaths.getFileExtension(copiedBinaryJar.getFileName())
          .equals(MorePaths.getFileExtension(resolvedBinaryJar))) {
        context
            .getEventBus()
            .post(
                ConsoleEvent.warning(
                    "Assuming %s is a JAR and renaming to %s in %s. "
                        + "Change the extension of the binary_jar to '.jar' to remove this warning.",
                    resolvedBinaryJar.getFileName(),
                    copiedBinaryJar.getFileName(),
                    getBuildTarget().getFullyQualifiedName()));
      }

      steps.add(
          MkdirStep.of(
              BuildCellRelativePath.fromCellRelativePath(
                  context.getBuildCellRootPath(), projectFilesystem, copiedBinaryJar.getParent())));
      steps.add(CopyStep.forFile(resolvedBinaryJar.getPath(), copiedBinaryJar.getPath()));
    }
    buildableContext.recordArtifact(copiedBinaryJar.getPath());

    RelPath pathToClassHashes = getPathToClassHashes();
    buildableContext.recordArtifact(pathToClassHashes.getPath());

    ImmutableList.Builder<IsolatedStep> isolatedSteps = ImmutableList.builder();
    JavaLibraryRules.addAccumulateClassNamesStep(
        projectFilesystem.getIgnoredPaths(),
        isolatedSteps,
        Optional.of(context.getSourcePathResolver().getCellUnsafeRelPath(getSourcePathToOutput())),
        pathToClassHashes);
    steps.addAll(isolatedSteps.build());

    return steps.build();
  }

  private RelPath getPathToClassHashes() {
    return JavaLibraryRules.getPathToClassHashes(getBuildTarget(), getProjectFilesystem());
  }

  @Override
  public Iterable<AndroidPackageable> getRequiredPackageables(
      BuildRuleResolver ruleResolver, Supplier<Iterable<NdkCxxPlatform>> ndkCxxPlatforms) {
    return AndroidPackageableCollector.getPackageableRules(getDeclaredDeps());
  }

  @Override
  public void addToCollector(
      ActionGraphBuilder graphBuilder, AndroidPackageableCollector collector) {
    collector.addClasspathEntry(this, getSourcePathToOutput());
    collector.addPathToThirdPartyJar(getBuildTarget(), getSourcePathToOutput());
  }

  @Override
  public SourcePath getSourcePathToOutput() {
    return ExplicitBuildTargetSourcePath.of(getBuildTarget(), copiedBinaryJar);
  }

  @Override
  public JavaAbiInfo getAbiInfo() {
    return javaAbiInfo;
  }

  @Override
  public Optional<BuildTarget> getAbiJar() {
    if (!generateAbi) {
      return Optional.of(getBuildTarget());
    }

    return Optional.of(
        getAbiInfo().getBuildTarget().withAppendedFlavors(JavaAbis.CLASS_ABI_FLAVOR));
  }

  @Override
  public Optional<String> getMavenCoords() {
    return mavenCoords;
  }

  @Override
  public JavaClassHashesProvider getClassHashesProvider() {
    return javaClassHashesProvider;
  }
}

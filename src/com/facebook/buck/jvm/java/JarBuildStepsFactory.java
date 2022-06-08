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

import com.facebook.buck.cd.model.java.AbiGenerationMode;
import com.facebook.buck.cd.model.java.PipelineState;
import com.facebook.buck.core.build.context.BuildContext;
import com.facebook.buck.core.cell.CellPathResolver;
import com.facebook.buck.core.cell.impl.CellPathResolverUtils;
import com.facebook.buck.core.cell.name.CanonicalCellName;
import com.facebook.buck.core.filesystems.AbsPath;
import com.facebook.buck.core.filesystems.RelPath;
import com.facebook.buck.core.model.BuildTarget;
import com.facebook.buck.core.rulekey.AddToRuleKey;
import com.facebook.buck.core.rulekey.AddsToRuleKey;
import com.facebook.buck.core.rulekey.CustomFieldBehavior;
import com.facebook.buck.core.rulekey.DefaultFieldSerialization;
import com.facebook.buck.core.rulekey.ExcludeFromRuleKey;
import com.facebook.buck.core.rules.BuildRule;
import com.facebook.buck.core.rules.SourcePathRuleFinder;
import com.facebook.buck.core.rules.attr.HasCustomDepsLogic;
import com.facebook.buck.core.rules.common.RecordArtifactVerifier;
import com.facebook.buck.core.rules.pipeline.CompilationDaemonStep;
import com.facebook.buck.core.rules.pipeline.RulePipelineStateFactory;
import com.facebook.buck.core.sourcepath.ArchiveMemberSourcePath;
import com.facebook.buck.core.sourcepath.DefaultBuildTargetSourcePath;
import com.facebook.buck.core.sourcepath.ExplicitBuildTargetSourcePath;
import com.facebook.buck.core.sourcepath.SourcePath;
import com.facebook.buck.core.sourcepath.resolver.SourcePathResolverAdapter;
import com.facebook.buck.io.filesystem.BuckPaths;
import com.facebook.buck.io.filesystem.ProjectFilesystem;
import com.facebook.buck.jvm.cd.serialization.java.BuildTargetValueSerializer;
import com.facebook.buck.jvm.cd.serialization.java.JarParametersSerializer;
import com.facebook.buck.jvm.cd.serialization.java.ResolvedJavacOptionsSerializer;
import com.facebook.buck.jvm.cd.serialization.java.ResolvedJavacSerializer;
import com.facebook.buck.jvm.core.BaseJavaAbiInfo;
import com.facebook.buck.jvm.core.BuildTargetValue;
import com.facebook.buck.jvm.core.DefaultBaseJavaAbiInfo;
import com.facebook.buck.jvm.core.DefaultJavaAbiInfo;
import com.facebook.buck.jvm.core.HasJavaAbi;
import com.facebook.buck.jvm.core.JavaAbis;
import com.facebook.buck.jvm.java.abi.AbiGenerationModeUtils;
import com.facebook.buck.jvm.java.stepsbuilder.AbiStepsBuilder;
import com.facebook.buck.jvm.java.stepsbuilder.JavaLibraryRules;
import com.facebook.buck.jvm.java.stepsbuilder.LibraryStepsBuilder;
import com.facebook.buck.jvm.java.stepsbuilder.params.JavaCDParams;
import com.facebook.buck.jvm.java.stepsbuilder.params.RulesJavaCDParams;
import com.facebook.buck.rules.modern.CustomFieldInputs;
import com.facebook.buck.rules.modern.CustomFieldSerialization;
import com.facebook.buck.rules.modern.ValueCreator;
import com.facebook.buck.rules.modern.ValueVisitor;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Ordering;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;
import javax.annotation.Nullable;

/** Jar (java-archive) Build steps factory. */
public class JarBuildStepsFactory<T extends CompileToJarStepFactory.ExtraParams>
    implements AddsToRuleKey, RulePipelineStateFactory<JavacPipelineState, PipelineState> {

  private static final Path[] METADATA_DIRS =
      new Path[] {Paths.get("META-INF"), Paths.get("_STRIPPED_RESOURCES")};

  @CustomFieldBehavior(DefaultFieldSerialization.class)
  private final BuildTarget libraryTarget;

  @AddToRuleKey private final CompileToJarStepFactory<T> configuredCompiler;

  @AddToRuleKey private final Javac javac;

  @AddToRuleKey private final ImmutableSortedSet<SourcePath> srcs;

  @AddToRuleKey private final ImmutableSortedSet<SourcePath> resources;

  @AddToRuleKey private final ResourcesParameters resourcesParameters;

  @AddToRuleKey private final Optional<SourcePath> manifestFile;

  @AddToRuleKey private final DependencyInfoHolder dependencyInfos;
  @AddToRuleKey private final ZipArchiveDependencySupplier abiClasspath;

  @AddToRuleKey private final boolean trackClassUsage;

  @CustomFieldBehavior(DefaultFieldSerialization.class)
  private final boolean trackJavacPhaseEvents;

  @AddToRuleKey private final boolean isRequiredForSourceOnlyAbi;
  @AddToRuleKey private final RemoveClassesPatternsMatcher classesToRemoveFromJar;

  @AddToRuleKey private final AbiGenerationMode abiGenerationMode;
  @AddToRuleKey private final AbiGenerationMode abiCompatibilityMode;
  @AddToRuleKey private final boolean withDownwardApi;

  @AddToRuleKey private final RulesJavaCDParams javaCDParams;

  /** Creates {@link JarBuildStepsFactory} */
  public static <T extends CompileToJarStepFactory.ExtraParams> JarBuildStepsFactory<T> of(
      BuildTarget libraryTarget,
      CompileToJarStepFactory<T> configuredCompiler,
      Javac javac,
      ImmutableSortedSet<SourcePath> srcs,
      ImmutableSortedSet<SourcePath> resources,
      ResourcesParameters resourcesParameters,
      Optional<SourcePath> manifestFile,
      boolean trackClassUsage,
      boolean trackJavacPhaseEvents,
      RemoveClassesPatternsMatcher classesToRemoveFromJar,
      AbiGenerationMode abiGenerationMode,
      AbiGenerationMode abiCompatibilityMode,
      ImmutableList<JavaDependencyInfo> dependencyInfos,
      boolean isRequiredForSourceOnlyAbi,
      boolean withDownwardApi,
      RulesJavaCDParams javaCDParams) {
    return new JarBuildStepsFactory<>(
        libraryTarget,
        configuredCompiler,
        javac,
        srcs,
        resources,
        resourcesParameters,
        manifestFile,
        trackClassUsage,
        trackJavacPhaseEvents,
        classesToRemoveFromJar,
        abiGenerationMode,
        abiCompatibilityMode,
        dependencyInfos,
        isRequiredForSourceOnlyAbi,
        withDownwardApi,
        javaCDParams);
  }

  /** Contains information about a Java classpath dependency. */
  public static class JavaDependencyInfo implements AddsToRuleKey {

    @AddToRuleKey public final SourcePath compileTimeJar;
    @AddToRuleKey public final SourcePath abiJar;
    @AddToRuleKey public final boolean isRequiredForSourceOnlyAbi;

    public JavaDependencyInfo(
        SourcePath compileTimeJar, SourcePath abiJar, boolean isRequiredForSourceOnlyAbi) {
      this.compileTimeJar = compileTimeJar;
      this.abiJar = abiJar;
      this.isRequiredForSourceOnlyAbi = isRequiredForSourceOnlyAbi;
    }
  }

  private static class DependencyInfoHolder implements AddsToRuleKey, HasCustomDepsLogic {

    // TODO(cjhopman): This is pretty much all due to these things caching all AddsToRuleKey things,
    // but we don't want that for these things because nobody else uses them. We should improve the
    // rulekey and similar stuff to better handle this.
    @ExcludeFromRuleKey(
        reason =
            "Adding this to the rulekey is slow for large projects and the abiClasspath already "
                + " reflects all the inputs. For the same reason, we need to do custom inputs"
                + " derivation and custom serialization.",
        serialization = InfosBehavior.class,
        inputs = InfosBehavior.class)
    private final ImmutableList<JavaDependencyInfo> infos;

    public DependencyInfoHolder(ImmutableList<JavaDependencyInfo> infos) {
      this.infos = infos;
    }

    @Override
    public Stream<BuildRule> getDeps(SourcePathRuleFinder ruleFinder) {
      Stream.Builder<BuildRule> builder = Stream.builder();
      infos.forEach(
          info ->
              ruleFinder
                  .filterBuildRuleInputs(info.abiJar, info.compileTimeJar)
                  .forEach(builder::add));
      return builder.build();
    }

    public ZipArchiveDependencySupplier getAbiClasspath() {
      return new ZipArchiveDependencySupplier(
          this.infos.stream()
              .map(i -> i.abiJar)
              .collect(ImmutableSortedSet.toImmutableSortedSet(Ordering.natural())));
    }

    public ImmutableSortedSet<SourcePath> getCompileTimeClasspathSourcePaths() {
      return infos.stream()
          .map(info -> info.compileTimeJar)
          .collect(ImmutableSortedSet.toImmutableSortedSet(Ordering.natural()));
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      DependencyInfoHolder that = (DependencyInfoHolder) o;
      return Objects.equal(infos, that.infos);
    }

    @Override
    public int hashCode() {
      return Objects.hashCode(infos);
    }

    @Override
    public String toString() {
      return MoreObjects.toStringHelper(this).add("infos", infos).toString();
    }

    private static class InfosBehavior
        implements CustomFieldSerialization<ImmutableList<JavaDependencyInfo>>,
            CustomFieldInputs<ImmutableList<JavaDependencyInfo>> {

      @Override
      public <E extends Exception> void serialize(
          ImmutableList<JavaDependencyInfo> value, ValueVisitor<E> serializer) throws E {
        serializer.visitInteger(value.size());
        for (JavaDependencyInfo info : value) {
          serializer.visitSourcePath(info.compileTimeJar);
          serializer.visitSourcePath(info.abiJar);
          serializer.visitBoolean(info.isRequiredForSourceOnlyAbi);
        }
      }

      @Override
      public <E extends Exception> ImmutableList<JavaDependencyInfo> deserialize(
          ValueCreator<E> deserializer) throws E {
        int size = deserializer.createInteger();
        ImmutableList.Builder<JavaDependencyInfo> infos =
            ImmutableList.builderWithExpectedSize(size);
        for (int i = 0; i < size; i++) {
          SourcePath compileTimeJar = deserializer.createSourcePath();
          SourcePath abiJar = deserializer.createSourcePath();
          boolean isRequiredForSourceOnlyAbi = deserializer.createBoolean();
          infos.add(new JavaDependencyInfo(compileTimeJar, abiJar, isRequiredForSourceOnlyAbi));
        }
        return infos.build();
      }

      @Override
      public void getInputs(
          ImmutableList<JavaDependencyInfo> value, Consumer<SourcePath> consumer) {
        for (JavaDependencyInfo info : value) {
          consumer.accept(info.abiJar);
          consumer.accept(info.compileTimeJar);
        }
      }
    }
  }

  private JarBuildStepsFactory(
      BuildTarget libraryTarget,
      CompileToJarStepFactory<T> configuredCompiler,
      Javac javac,
      ImmutableSortedSet<SourcePath> srcs,
      ImmutableSortedSet<SourcePath> resources,
      ResourcesParameters resourcesParameters,
      Optional<SourcePath> manifestFile,
      boolean trackClassUsage,
      boolean trackJavacPhaseEvents,
      RemoveClassesPatternsMatcher classesToRemoveFromJar,
      AbiGenerationMode abiGenerationMode,
      AbiGenerationMode abiCompatibilityMode,
      ImmutableList<JavaDependencyInfo> dependencyInfos,
      boolean isRequiredForSourceOnlyAbi,
      boolean withDownwardApi,
      RulesJavaCDParams javaCDParams) {
    this.libraryTarget = libraryTarget;
    this.configuredCompiler = configuredCompiler;
    this.javac = javac;
    this.srcs = srcs;
    this.resources = resources;
    this.resourcesParameters = resourcesParameters;
    this.manifestFile = manifestFile;
    this.trackClassUsage = trackClassUsage;
    this.trackJavacPhaseEvents = trackJavacPhaseEvents;
    this.classesToRemoveFromJar = classesToRemoveFromJar;
    this.abiGenerationMode = abiGenerationMode;
    this.abiCompatibilityMode = abiCompatibilityMode;
    this.dependencyInfos = new DependencyInfoHolder(dependencyInfos);
    this.withDownwardApi = withDownwardApi;
    this.abiClasspath = this.dependencyInfos.getAbiClasspath();
    this.isRequiredForSourceOnlyAbi = isRequiredForSourceOnlyAbi;
    this.javaCDParams = javaCDParams;
  }

  public boolean producesJar() {
    return !srcs.isEmpty() || !resources.isEmpty() || manifestFile.isPresent();
  }

  public ImmutableSortedSet<SourcePath> getSources() {
    return srcs;
  }

  public ImmutableSortedSet<SourcePath> getResources() {
    return resources;
  }

  public Optional<String> getResourcesRoot() {
    return resourcesParameters.getResourcesRoot();
  }

  @Nullable
  public SourcePath getSourcePathToOutput(BuildTarget buildTarget, BuckPaths buckPaths) {
    return getOutputJarPath(buildTarget, buckPaths)
        .map(path -> ExplicitBuildTargetSourcePath.of(buildTarget, path))
        .orElse(null);
  }

  @Nullable
  public SourcePath getSourcePathToGeneratedAnnotationPath(
      BuildTarget buildTarget, ProjectFilesystem filesystem) {
    return getGeneratedAnnotationPath(buildTarget, filesystem)
        .map(path -> ExplicitBuildTargetSourcePath.of(buildTarget, path))
        .orElse(null);
  }

  @VisibleForTesting
  public ImmutableSortedSet<SourcePath> getCompileTimeClasspathSourcePaths() {
    return dependencyInfos.getCompileTimeClasspathSourcePaths();
  }

  public boolean useDependencyFileRuleKeys() {
    return !srcs.isEmpty() && trackClassUsage;
  }

  /** Returns a predicate indicating whether a SourcePath is covered by the depfile. */
  public Predicate<SourcePath> getCoveredByDepFilePredicate(SourcePathRuleFinder ruleFinder) {
    // a hash set is intentionally used to achieve constant time look-up
    // TODO(cjhopman): This could probably be changed to be a 2-level check of archivepath->inner,
    // withinarchivepath->boolean.
    return abiClasspath.getArchiveMembers(ruleFinder).collect(ImmutableSet.toImmutableSet())
        ::contains;
  }

  public Predicate<SourcePath> getExistenceOfInterestPredicate() {
    // Annotation processors might enumerate all files under a certain path and then generate
    // code based on that list (without actually reading the files), making the list of files
    // itself a used dependency that must be part of the dependency-based key. We don't
    // currently have the instrumentation to detect such enumeration perfectly, but annotation
    // processors are most commonly looking for files under META-INF, so as a stopgap we add
    // the listing of META-INF to the rule key.
    return (SourcePath path) -> {
      if (!(path instanceof ArchiveMemberSourcePath)) {
        return false;
      }
      Path memberPath = ((ArchiveMemberSourcePath) path).getMemberPath();
      for (Path metadataPath : METADATA_DIRS) {
        if (memberPath.startsWith(metadataPath)) {
          return true;
        }
      }
      return false;
    };
  }

  public boolean useRulePipelining() {
    return configuredCompiler instanceof DaemonJavacToJarStepFactory
        && AbiGenerationModeUtils.isNotClassAbi(abiGenerationMode)
        && AbiGenerationModeUtils.usesDependencies(abiGenerationMode);
  }

  @SuppressWarnings("unchecked")
  private T createExtraParams(BuildContext buildContext, AbsPath rootPath) {
    if (configuredCompiler instanceof CompileToJarStepFactory.CreatesExtraParams) {
      return ((CompileToJarStepFactory.CreatesExtraParams<T>) configuredCompiler)
          .createExtraParams(buildContext, rootPath);
    } else {
      throw new UnsupportedOperationException(
          "Configured compiler factory cannot create extra params.");
    }
  }

  /** Adds build steps for ABI jar. */
  public void addBuildStepsForAbiJar(
      BuildContext context,
      ProjectFilesystem filesystem,
      RecordArtifactVerifier buildableContext,
      BuildTarget buildTarget,
      AbiStepsBuilder stepsBuilder) {
    Preconditions.checkState(producesJar());
    Preconditions.checkArgument(
        buildTarget.equals(JavaAbis.getSourceAbiJar(libraryTarget))
            || buildTarget.equals(JavaAbis.getSourceOnlyAbiJar(libraryTarget)));

    SourcePathResolverAdapter sourcePathResolver = context.getSourcePathResolver();
    ImmutableSortedSet<RelPath> compileTimeClasspathPaths =
        getCompileTimeClasspathPaths(filesystem, sourcePathResolver);
    ImmutableSortedSet<RelPath> javaSrcs = getJavaSrcs(filesystem, sourcePathResolver);
    AbsPath rootPath = filesystem.getRootPath();
    BuckPaths buckPaths = filesystem.getBuckPaths();

    ImmutableList<BaseJavaAbiInfo> fullJarInfos =
        dependencyInfos.infos.stream()
            .map(this::toBaseJavaAbiInfo)
            .collect(ImmutableList.toImmutableList());

    ImmutableList<BaseJavaAbiInfo> abiJarInfos =
        dependencyInfos.infos.stream()
            .filter(info -> info.isRequiredForSourceOnlyAbi)
            .map(this::toBaseJavaAbiInfo)
            .collect(ImmutableList.toImmutableList());

    BuildTargetValue buildTargetValue = BuildTargetValue.withExtraParams(buildTarget, buckPaths);
    CompilerOutputPaths compilerOutputPaths = CompilerOutputPaths.of(buildTarget, buckPaths);
    RelPath classesDir = compilerOutputPaths.getClassesDir();
    ImmutableMap<RelPath, RelPath> resourcesMap =
        CopyResourcesStep.getResourcesMap(
            context, filesystem, classesDir.getPath(), resourcesParameters, buildTarget);

    ImmutableMap<CanonicalCellName, RelPath> cellToPathMappings =
        CellPathResolverUtils.getCellToPathMappings(rootPath, context.getCellPathResolver());

    JarParameters abiJarParameters =
        getAbiJarParameters(buildTarget, context, filesystem, classesDir).orElse(null);
    JarParameters libraryJarParameters =
        getLibraryJarParameters(context, filesystem, classesDir).orElse(null);

    AbsPath buildCellRootPath = context.getBuildCellRootPath();
    ResolvedJavac resolvedJavac = javac.resolve(sourcePathResolver, rootPath);
    T extraParams = createExtraParams(context, rootPath);

    stepsBuilder.addBuildStepsForAbi(
        abiCompatibilityMode,
        abiGenerationMode,
        isRequiredForSourceOnlyAbi,
        trackClassUsage,
        trackJavacPhaseEvents,
        withDownwardApi,
        FilesystemParamsUtils.of(filesystem),
        buildableContext,
        buildTargetValue,
        CompilerOutputPathsValue.of(buckPaths, buildTarget),
        compileTimeClasspathPaths,
        javaSrcs,
        fullJarInfos,
        abiJarInfos,
        resourcesMap,
        cellToPathMappings,
        abiJarParameters,
        libraryJarParameters,
        buildCellRootPath,
        resolvedJavac,
        extraParams);
  }

  /** Adds build steps for library jar */
  public void addBuildStepsForLibraryJar(
      BuildContext context,
      ProjectFilesystem filesystem,
      RecordArtifactVerifier buildableContext,
      BuildTarget buildTarget,
      RelPath pathToClassHashes,
      LibraryStepsBuilder stepsBuilder) {
    Preconditions.checkArgument(buildTarget.equals(libraryTarget));

    SourcePathResolverAdapter sourcePathResolver = context.getSourcePathResolver();
    ImmutableSortedSet<RelPath> compileTimeClasspathPaths =
        getCompileTimeClasspathPaths(filesystem, sourcePathResolver);
    ImmutableSortedSet<RelPath> javaSrcs = getJavaSrcs(filesystem, sourcePathResolver);
    AbsPath rootPath = filesystem.getRootPath();
    BuckPaths buckPaths = filesystem.getBuckPaths();

    ImmutableList<BaseJavaAbiInfo> fullJarInfos =
        dependencyInfos.infos.stream()
            .map(this::toBaseJavaAbiInfo)
            .collect(ImmutableList.toImmutableList());

    ImmutableList<BaseJavaAbiInfo> abiJarInfos =
        dependencyInfos.infos.stream()
            .filter(info -> info.isRequiredForSourceOnlyAbi)
            .map(this::toBaseJavaAbiInfo)
            .collect(ImmutableList.toImmutableList());

    BuildTargetValue buildTargetValue = BuildTargetValue.withExtraParams(buildTarget, buckPaths);
    CompilerOutputPaths compilerOutputPaths = CompilerOutputPaths.of(buildTarget, buckPaths);

    ImmutableMap<RelPath, RelPath> resourcesMap =
        CopyResourcesStep.getResourcesMap(
            context,
            filesystem,
            compilerOutputPaths.getClassesDir().getPath(),
            resourcesParameters,
            buildTarget);

    ImmutableMap<CanonicalCellName, RelPath> cellToPathMappings =
        CellPathResolverUtils.getCellToPathMappings(rootPath, context.getCellPathResolver());

    JarParameters libraryJarParameters =
        getLibraryJarParameters(context, filesystem, compilerOutputPaths.getClassesDir())
            .orElse(null);

    AbsPath buildCellRootPath = context.getBuildCellRootPath();

    ResolvedJavac resolvedJavac = javac.resolve(sourcePathResolver, rootPath);
    Optional<RelPath> pathToClasses = getPathToClasses(context, buildTarget, buckPaths);
    T extraParams = createExtraParams(context, rootPath);

    stepsBuilder.addBuildStepsForLibrary(
        abiCompatibilityMode,
        abiGenerationMode,
        isRequiredForSourceOnlyAbi,
        trackClassUsage,
        trackJavacPhaseEvents,
        withDownwardApi,
        FilesystemParamsUtils.of(filesystem),
        buildableContext,
        buildTargetValue,
        CompilerOutputPathsValue.of(buckPaths, buildTarget),
        pathToClassHashes,
        compileTimeClasspathPaths,
        javaSrcs,
        fullJarInfos,
        abiJarInfos,
        resourcesMap,
        cellToPathMappings,
        libraryJarParameters,
        buildCellRootPath,
        pathToClasses,
        resolvedJavac,
        extraParams);
  }

  public Optional<RelPath> getPathToClasses(
      BuildContext context, BuildTarget buildTarget, BuckPaths buckPaths) {
    return Optional.ofNullable(getSourcePathToOutput(buildTarget, buckPaths))
        .map(sourcePath -> context.getSourcePathResolver().getCellUnsafeRelPath(sourcePath));
  }

  private BaseJavaAbiInfo toBaseJavaAbiInfo(JavaDependencyInfo info) {
    return new DefaultBaseJavaAbiInfo(
        DefaultJavaAbiInfo.extractBuildTargetFromSourcePath(info.compileTimeJar)
            .getUnflavoredBuildTarget()
            .toString());
  }

  private ImmutableSortedSet<RelPath> getJavaSrcs(
      ProjectFilesystem filesystem, SourcePathResolverAdapter sourcePathResolver) {
    return srcs.stream()
        .map(src -> filesystem.relativize(sourcePathResolver.getAbsolutePath(src)))
        .collect(ImmutableSortedSet.toImmutableSortedSet(RelPath.comparator()));
  }

  private ImmutableSortedSet<RelPath> getCompileTimeClasspathPaths(
      ProjectFilesystem filesystem, SourcePathResolverAdapter sourcePathResolver) {
    AbsPath rootPath = filesystem.getRootPath();
    return sourcePathResolver
        .getAllAbsolutePaths(dependencyInfos.getCompileTimeClasspathSourcePaths()).stream()
        .map(rootPath::relativize)
        .collect(ImmutableSortedSet.toImmutableSortedSet(RelPath.comparator()));
  }

  private Optional<JarParameters> getLibraryJarParameters(
      BuildContext context, ProjectFilesystem filesystem, RelPath classesDir) {
    return getJarParameters(context, filesystem, libraryTarget, classesDir);
  }

  private Optional<JarParameters> getAbiJarParameters(
      BuildTarget buildTarget,
      BuildContext context,
      ProjectFilesystem filesystem,
      RelPath classesDir) {
    if (JavaAbis.isLibraryTarget(buildTarget)) {
      return Optional.empty();
    }
    Preconditions.checkState(JavaAbis.hasAbi(buildTarget));
    return getJarParameters(context, filesystem, buildTarget, classesDir);
  }

  private Optional<JarParameters> getJarParameters(
      BuildContext context,
      ProjectFilesystem filesystem,
      BuildTarget buildTarget,
      RelPath classesDir) {
    SourcePathResolverAdapter sourcePathResolver = context.getSourcePathResolver();
    ImmutableSortedSet<RelPath> entriesToJar =
        ImmutableSortedSet.orderedBy(RelPath.comparator()).add(classesDir).build();
    Optional<RelPath> manifestRelFile =
        manifestFile.map(sourcePath -> sourcePathResolver.getRelativePath(filesystem, sourcePath));
    return getOutputJarPath(buildTarget, filesystem.getBuckPaths())
        .map(
            output ->
                JarParameters.builder()
                    .setEntriesToJar(entriesToJar)
                    .setManifestFile(manifestRelFile)
                    .setJarPath(output)
                    .setRemoveEntryPredicate(classesToRemoveFromJar)
                    .build());
  }

  public ImmutableList<SourcePath> getInputsAfterBuildingLocally(
      BuildContext context,
      ProjectFilesystem filesystem,
      SourcePathRuleFinder ruleFinder,
      CellPathResolver cellPathResolver,
      BuildTarget buildTarget) {
    Preconditions.checkState(useDependencyFileRuleKeys());
    return DefaultClassUsageFileReader.loadFromFile(
        filesystem.getRootPath(),
        cellPathResolver,
        configuredCompiler.getDepFilePaths(filesystem, buildTarget).stream()
            .map(filesystem::getPathForRelativePath)
            .collect(ImmutableList.toImmutableList()),
        getDepOutputPathToAbiSourcePath(context.getSourcePathResolver(), ruleFinder));
  }

  private Optional<RelPath> getOutputJarPath(BuildTarget buildTarget, BuckPaths buckPaths) {
    if (!producesJar()) {
      return Optional.empty();
    }

    CompilerOutputPaths compilerOutputPaths = CompilerOutputPaths.of(buildTarget, buckPaths);
    if (JavaAbis.hasAbi(buildTarget)) {
      return Optional.of(compilerOutputPaths.getAbiJarPath().get());
    } else if (JavaAbis.isLibraryTarget(buildTarget)) {
      return Optional.of(compilerOutputPaths.getOutputJarPath().get());
    }
    throw new IllegalStateException(
        buildTarget
            + " has to have supported java compilation type: library, source-abi and source-only-abi");
  }

  private Optional<Path> getGeneratedAnnotationPath(
      BuildTarget buildTarget, ProjectFilesystem filesystem) {
    if (!hasAnnotationProcessing()) {
      return Optional.empty();
    }
    return Optional.of(
        CompilerOutputPaths.of(buildTarget, filesystem.getBuckPaths())
            .getAnnotationPath()
            .getPath());
  }

  private ImmutableMap<Path, SourcePath> getDepOutputPathToAbiSourcePath(
      SourcePathResolverAdapter pathResolver, SourcePathRuleFinder ruleFinder) {
    ImmutableMap.Builder<Path, SourcePath> pathToSourcePathMapBuilder = ImmutableMap.builder();
    for (JavaDependencyInfo depInfo : dependencyInfos.infos) {
      SourcePath sourcePath = depInfo.compileTimeJar;
      BuildRule rule = ruleFinder.getRule(sourcePath).orElseThrow(IllegalStateException::new);
      AbsPath path = pathResolver.getAbsolutePath(sourcePath);
      if (rule instanceof HasJavaAbi) {
        HasJavaAbi hasJavaAbi = (HasJavaAbi) rule;
        Optional<BuildTarget> abiJar = hasJavaAbi.getAbiJar();
        abiJar.ifPresent(
            buildTarget ->
                pathToSourcePathMapBuilder.put(
                    path.getPath(), DefaultBuildTargetSourcePath.of(buildTarget)));
      }
    }
    return pathToSourcePathMapBuilder.build();
  }

  @Override
  public PipelineState createPipelineStateMessage(
      BuildContext context, ProjectFilesystem filesystem, BuildTarget buildTarget) {
    JavacToJarStepFactory javacToJarStepFactory = (JavacToJarStepFactory) configuredCompiler;

    SourcePathResolverAdapter sourcePathResolver = context.getSourcePathResolver();
    ImmutableSortedSet<RelPath> compileTimeClasspathPaths =
        getCompileTimeClasspathPaths(filesystem, sourcePathResolver);
    ImmutableSortedSet<RelPath> javaSrcs = getJavaSrcs(filesystem, sourcePathResolver);
    BuckPaths buckPaths = filesystem.getBuckPaths();

    ImmutableList<BaseJavaAbiInfo> fullJarInfos =
        dependencyInfos.infos.stream()
            .map(this::toBaseJavaAbiInfo)
            .collect(ImmutableList.toImmutableList());

    ImmutableList<BaseJavaAbiInfo> abiJarInfos =
        dependencyInfos.infos.stream()
            .filter(info -> info.isRequiredForSourceOnlyAbi)
            .map(this::toBaseJavaAbiInfo)
            .collect(ImmutableList.toImmutableList());

    BuildTargetValue buildTargetValue = BuildTargetValue.of(buildTarget);
    CompilerOutputPaths compilerOutputPaths = CompilerOutputPaths.of(buildTarget, buckPaths);
    RelPath classesDir = compilerOutputPaths.getClassesDir();
    JarParameters abiJarParameters =
        getAbiJarParameters(buildTarget, context, filesystem, classesDir).orElse(null);
    JarParameters libraryJarParameters =
        getLibraryJarParameters(context, filesystem, classesDir).orElse(null);

    AbsPath rootPath = filesystem.getRootPath();

    ResolvedJavac resolvedJavac = javac.resolve(sourcePathResolver, rootPath);
    JavaExtraParams extraParams = javacToJarStepFactory.createExtraParams(context, rootPath);

    return javacToJarStepFactory.createPipelineState(
        buildTargetValue,
        compileTimeClasspathPaths,
        javaSrcs,
        fullJarInfos,
        abiJarInfos,
        trackClassUsage,
        trackJavacPhaseEvents,
        abiGenerationMode,
        abiCompatibilityMode,
        isRequiredForSourceOnlyAbi,
        compilerOutputPaths,
        abiJarParameters,
        libraryJarParameters,
        withDownwardApi,
        resolvedJavac,
        extraParams.getResolvedJavacOptions());
  }

  @Override
  public Function<PipelineState, JavacPipelineState> getStateCreatorFunction() {
    return (PipelineState pipelineState) ->
        new JavacPipelineState(
            ResolvedJavacSerializer.deserialize(pipelineState.getResolvedJavac()),
            ResolvedJavacOptionsSerializer.deserialize(pipelineState.getResolvedJavacOptions()),
            BuildTargetValueSerializer.deserialize(pipelineState.getBuildTargetValue()),
            JavaLibraryRules.createCompilerParameters(pipelineState),
            pipelineState.hasAbiJarParameters()
                ? JarParametersSerializer.deserialize(pipelineState.getAbiJarParameters())
                : null,
            pipelineState.hasLibraryJarParameters()
                ? JarParametersSerializer.deserialize(pipelineState.getLibraryJarParameters())
                : null,
            pipelineState.getWithDownwardApi());
  }

  @Override
  public Function<PipelineState, CompilationDaemonStep> getCompilationStepCreatorFunction(
      ProjectFilesystem projectFilesystem) {
    return (state) -> {
      DaemonJavacToJarStepFactory configuredCompiler =
          (DaemonJavacToJarStepFactory) getConfiguredCompiler();
      return new JavaCDPipeliningWorkerToolStep(
          state,
          hasAnnotationProcessing(),
          withDownwardApi,
          configuredCompiler.getSpoolMode(),
          createJavaCDParams(projectFilesystem));
    };
  }

  private JavaCDParams createJavaCDParams(ProjectFilesystem filesystem) {
    return JavaCDParams.of(javaCDParams, filesystem);
  }

  boolean hasAnnotationProcessing() {
    return configuredCompiler.hasAnnotationProcessing();
  }

  public CompileToJarStepFactory<T> getConfiguredCompiler() {
    return configuredCompiler;
  }

  public ResourcesParameters getResourcesParameters() {
    return resourcesParameters;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    JarBuildStepsFactory<?> that = (JarBuildStepsFactory<?>) o;
    return trackClassUsage == that.trackClassUsage
        && trackJavacPhaseEvents == that.trackJavacPhaseEvents
        && isRequiredForSourceOnlyAbi == that.isRequiredForSourceOnlyAbi
        && withDownwardApi == that.withDownwardApi
        && abiGenerationMode == that.abiGenerationMode
        && abiCompatibilityMode == that.abiCompatibilityMode
        && Objects.equal(libraryTarget, that.libraryTarget)
        && Objects.equal(configuredCompiler, that.configuredCompiler)
        && Objects.equal(javac, that.javac)
        && Objects.equal(srcs, that.srcs)
        && Objects.equal(resources, that.resources)
        && Objects.equal(resourcesParameters, that.resourcesParameters)
        && Objects.equal(manifestFile, that.manifestFile)
        && Objects.equal(dependencyInfos, that.dependencyInfos)
        && Objects.equal(abiClasspath, that.abiClasspath)
        && Objects.equal(classesToRemoveFromJar, that.classesToRemoveFromJar)
        && Objects.equal(javaCDParams, that.javaCDParams);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(
        libraryTarget,
        configuredCompiler,
        javac,
        srcs,
        resources,
        resourcesParameters,
        manifestFile,
        dependencyInfos,
        abiClasspath,
        trackClassUsage,
        trackJavacPhaseEvents,
        isRequiredForSourceOnlyAbi,
        classesToRemoveFromJar,
        abiGenerationMode,
        abiCompatibilityMode,
        withDownwardApi,
        javaCDParams);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("libraryTarget", libraryTarget)
        .add("configuredCompiler", configuredCompiler)
        .add("javac", javac)
        .add("srcs", srcs)
        .add("resources", resources)
        .add("resourcesParameters", resourcesParameters)
        .add("manifestFile", manifestFile)
        .add("dependencyInfos", dependencyInfos)
        .add("abiClasspath", abiClasspath)
        .add("trackClassUsage", trackClassUsage)
        .add("trackJavacPhaseEvents", trackJavacPhaseEvents)
        .add("isRequiredForSourceOnlyAbi", isRequiredForSourceOnlyAbi)
        .add("classesToRemoveFromJar", classesToRemoveFromJar)
        .add("abiGenerationMode", abiGenerationMode)
        .add("abiCompatibilityMode", abiCompatibilityMode)
        .add("withDownwardApi", withDownwardApi)
        .add("javaCDParams", javaCDParams)
        .toString();
  }
}

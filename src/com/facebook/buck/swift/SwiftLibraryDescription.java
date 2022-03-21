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

package com.facebook.buck.swift;

import com.facebook.buck.apple.common.AppleCompilerTargetTriple;
import com.facebook.buck.apple.common.AppleFlavors;
import com.facebook.buck.core.cell.CellPathResolver;
import com.facebook.buck.core.cell.nameresolver.CellNameResolver;
import com.facebook.buck.core.description.arg.BuildRuleArg;
import com.facebook.buck.core.description.arg.HasDeclaredDeps;
import com.facebook.buck.core.description.arg.HasSrcs;
import com.facebook.buck.core.description.attr.ImplicitDepsInferringDescription;
import com.facebook.buck.core.exceptions.HumanReadableException;
import com.facebook.buck.core.filesystems.RelPath;
import com.facebook.buck.core.model.BuildTarget;
import com.facebook.buck.core.model.Flavor;
import com.facebook.buck.core.model.FlavorConvertible;
import com.facebook.buck.core.model.FlavorDomain;
import com.facebook.buck.core.model.FlavorSet;
import com.facebook.buck.core.model.Flavored;
import com.facebook.buck.core.model.InternalFlavor;
import com.facebook.buck.core.model.TargetConfiguration;
import com.facebook.buck.core.model.UnflavoredBuildTarget;
import com.facebook.buck.core.model.impl.BuildTargetPaths;
import com.facebook.buck.core.rules.ActionGraphBuilder;
import com.facebook.buck.core.rules.BuildRule;
import com.facebook.buck.core.rules.BuildRuleCreationContextWithTargetGraph;
import com.facebook.buck.core.rules.BuildRuleParams;
import com.facebook.buck.core.rules.DescriptionWithTargetGraph;
import com.facebook.buck.core.sourcepath.SourcePath;
import com.facebook.buck.core.sourcepath.resolver.SourcePathResolverAdapter;
import com.facebook.buck.core.toolchain.ToolchainProvider;
import com.facebook.buck.core.util.graph.AbstractBreadthFirstTraversal;
import com.facebook.buck.core.util.immutables.RuleArg;
import com.facebook.buck.cxx.AbstractSwiftCxxCommonArg;
import com.facebook.buck.cxx.CxxDescriptionEnhancer;
import com.facebook.buck.cxx.CxxHeaders;
import com.facebook.buck.cxx.CxxLibraryDescription;
import com.facebook.buck.cxx.CxxLibraryGroup;
import com.facebook.buck.cxx.CxxLinkOptions;
import com.facebook.buck.cxx.CxxLinkableEnhancer;
import com.facebook.buck.cxx.CxxPreprocessables;
import com.facebook.buck.cxx.CxxPreprocessorDep;
import com.facebook.buck.cxx.CxxPreprocessorInput;
import com.facebook.buck.cxx.CxxToolFlags;
import com.facebook.buck.cxx.PreprocessorFlags;
import com.facebook.buck.cxx.TransitiveCxxPreprocessorInputCache;
import com.facebook.buck.cxx.config.CxxBuckConfig;
import com.facebook.buck.cxx.toolchain.CxxPlatform;
import com.facebook.buck.cxx.toolchain.CxxPlatformsProvider;
import com.facebook.buck.cxx.toolchain.HeaderSymlinkTreeWithModuleMap;
import com.facebook.buck.cxx.toolchain.LinkerMapMode;
import com.facebook.buck.cxx.toolchain.PathShortener;
import com.facebook.buck.cxx.toolchain.Preprocessor;
import com.facebook.buck.cxx.toolchain.UnresolvedCxxPlatform;
import com.facebook.buck.cxx.toolchain.linker.Linker;
import com.facebook.buck.cxx.toolchain.nativelink.NativeLinkable;
import com.facebook.buck.cxx.toolchain.nativelink.NativeLinkableGroup;
import com.facebook.buck.cxx.toolchain.nativelink.NativeLinkableInput;
import com.facebook.buck.downwardapi.config.DownwardApiConfig;
import com.facebook.buck.io.filesystem.ProjectFilesystem;
import com.facebook.buck.rules.args.AddsToRuleKeyFunction;
import com.facebook.buck.rules.args.Arg;
import com.facebook.buck.rules.args.StringArg;
import com.facebook.buck.rules.coercer.FrameworkPath;
import com.facebook.buck.rules.macros.AbsoluteOutputMacroExpander;
import com.facebook.buck.rules.macros.LocationMacroExpander;
import com.facebook.buck.rules.macros.StringWithMacros;
import com.facebook.buck.rules.macros.StringWithMacrosConverter;
import com.facebook.buck.swift.toolchain.ExplicitModuleOutput;
import com.facebook.buck.swift.toolchain.SwiftPlatform;
import com.facebook.buck.swift.toolchain.SwiftPlatformsProvider;
import com.facebook.buck.swift.toolchain.SwiftSdkDependenciesProvider;
import com.facebook.buck.swift.toolchain.UnresolvedSwiftPlatform;
import com.facebook.buck.util.stream.RichStream;
import com.facebook.buck.util.types.Pair;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Ordering;
import com.google.common.collect.Sets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.SortedSet;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import org.immutables.value.Value;

public class SwiftLibraryDescription
    implements DescriptionWithTargetGraph<SwiftLibraryDescriptionArg>,
        Flavored,
        ImplicitDepsInferringDescription<
            SwiftLibraryDescription.AbstractSwiftLibraryDescriptionArg> {

  static final Flavor SWIFT_COMPANION_FLAVOR = InternalFlavor.of("swift-companion");
  static final Flavor SWIFT_COMPILE_FLAVOR = InternalFlavor.of("swift-compile");

  private static final Set<Flavor> SUPPORTED_FLAVORS =
      ImmutableSet.of(
          SWIFT_COMPANION_FLAVOR, SWIFT_COMPILE_FLAVOR, LinkerMapMode.NO_LINKER_MAP.getFlavor());

  @Override
  public void findDepsForTargetFromConstructorArgs(
      BuildTarget buildTarget,
      CellNameResolver cellRoots,
      AbstractSwiftLibraryDescriptionArg constructorArg,
      ImmutableCollection.Builder<BuildTarget> extraDepsBuilder,
      ImmutableCollection.Builder<BuildTarget> targetGraphOnlyDepsBuilder) {
    getSwiftPlatformsFlavorDomain(buildTarget.getTargetConfiguration())
        .getValues()
        .forEach(
            platform ->
                extraDepsBuilder.addAll(
                    platform.getParseTimeDeps(buildTarget.getTargetConfiguration())));
  }

  public enum Type implements FlavorConvertible {
    SHARED(CxxDescriptionEnhancer.SHARED_FLAVOR),
    STATIC(CxxDescriptionEnhancer.STATIC_FLAVOR),
    MACH_O_BUNDLE(CxxDescriptionEnhancer.MACH_O_BUNDLE_FLAVOR),
    ;

    private final Flavor flavor;

    Type(Flavor flavor) {
      this.flavor = flavor;
    }

    @Override
    public Flavor getFlavor() {
      return flavor;
    }
  }

  private static final FlavorDomain<Type> LIBRARY_TYPE =
      FlavorDomain.from("Swift Library Type", Type.class);

  private final ToolchainProvider toolchainProvider;
  private final CxxBuckConfig cxxBuckConfig;
  private final SwiftBuckConfig swiftBuckConfig;
  private final DownwardApiConfig downwardApiConfig;

  public SwiftLibraryDescription(
      ToolchainProvider toolchainProvider,
      CxxBuckConfig cxxBuckConfig,
      SwiftBuckConfig swiftBuckConfig,
      DownwardApiConfig downwardApiConfig) {
    this.toolchainProvider = toolchainProvider;
    this.cxxBuckConfig = cxxBuckConfig;
    this.swiftBuckConfig = swiftBuckConfig;
    this.downwardApiConfig = downwardApiConfig;
  }

  @Override
  public Class<SwiftLibraryDescriptionArg> getConstructorArgType() {
    return SwiftLibraryDescriptionArg.class;
  }

  @Override
  public Optional<ImmutableSet<FlavorDomain<?>>> flavorDomains(
      TargetConfiguration toolchainTargetConfiguration) {
    return Optional.of(
        ImmutableSet.of(
            // Missing: swift-companion
            // Missing: swift-compile
            getCxxPlatforms(toolchainTargetConfiguration)));
  }

  @Override
  public boolean hasFlavors(
      ImmutableSet<Flavor> flavors, TargetConfiguration toolchainTargetConfiguration) {
    ImmutableSet<Flavor> currentUnsupportedFlavors =
        ImmutableSet.copyOf(Sets.filter(flavors, Predicates.not(SUPPORTED_FLAVORS::contains)));
    if (currentUnsupportedFlavors.isEmpty()) {
      return true;
    }
    return getCxxPlatforms(toolchainTargetConfiguration).containsAnyOf(flavors);
  }

  @Override
  public BuildRule createBuildRule(
      BuildRuleCreationContextWithTargetGraph context,
      BuildTarget buildTarget,
      BuildRuleParams params,
      SwiftLibraryDescriptionArg args) {

    Optional<LinkerMapMode> flavoredLinkerMapMode =
        LinkerMapMode.FLAVOR_DOMAIN.getValue(buildTarget);
    buildTarget =
        LinkerMapMode.removeLinkerMapModeFlavorInTarget(buildTarget, flavoredLinkerMapMode);
    UnflavoredBuildTarget unflavoredBuildTarget = buildTarget.getUnflavoredBuildTarget();

    // See if we're building a particular "type" and "platform" of this library, and if so, extract
    // them from the flavors attached to the build target.
    Optional<Map.Entry<Flavor, UnresolvedCxxPlatform>> platform =
        getCxxPlatforms(buildTarget.getTargetConfiguration()).getFlavorAndValue(buildTarget);
    FlavorSet buildFlavors = buildTarget.getFlavors();
    ImmutableSortedSet<BuildRule> filteredExtraDeps =
        params.getExtraDeps().get().stream()
            .filter(
                input ->
                    !input
                        .getBuildTarget()
                        .getUnflavoredBuildTarget()
                        .equals(unflavoredBuildTarget))
            .collect(ImmutableSortedSet.toImmutableSortedSet(Ordering.natural()));
    params = params.withExtraDeps(filteredExtraDeps);

    FlavorDomain<UnresolvedSwiftPlatform> swiftPlatformFlavorDomain =
        getSwiftPlatformsFlavorDomain(buildTarget.getTargetConfiguration());

    ProjectFilesystem projectFilesystem = context.getProjectFilesystem();
    CellPathResolver cellRoots = context.getCellPathResolver();
    ActionGraphBuilder graphBuilder = context.getActionGraphBuilder();
    if (!buildFlavors.contains(SWIFT_COMPANION_FLAVOR) && platform.isPresent()) {
      // TODO(cjhopman): This doesn't properly handle parse time deps...
      CxxPlatform cxxPlatform =
          platform.get().getValue().resolve(graphBuilder, buildTarget.getTargetConfiguration());
      Optional<SwiftPlatform> swiftPlatform =
          swiftPlatformFlavorDomain.getRequiredValue(buildTarget).resolve(graphBuilder);
      if (!swiftPlatform.isPresent()) {
        throw new HumanReadableException("Platform %s is missing swift compiler", cxxPlatform);
      }

      // See if we're building a particular "type" and "platform" of this library, and if so,
      // extract them from the flavors attached to the build target.
      Optional<Map.Entry<Flavor, Type>> type = LIBRARY_TYPE.getFlavorAndValue(buildTarget);
      if (!buildFlavors.contains(SWIFT_COMPILE_FLAVOR) && type.isPresent()) {
        Set<Flavor> flavors = Sets.newHashSet(buildTarget.getFlavors().getSet());
        flavors.remove(type.get().getKey());
        BuildTarget target = buildTarget.withFlavors(flavors);
        if (flavoredLinkerMapMode.isPresent()) {
          target = target.withAppendedFlavors(flavoredLinkerMapMode.get().getFlavor());
        }

        switch (type.get().getValue()) {
          case SHARED:
            return createSharedLibraryBuildRule(
                cellRoots,
                projectFilesystem,
                params,
                graphBuilder,
                target,
                swiftPlatform.get(),
                cxxPlatform,
                args.getSoname());
          case STATIC:
          case MACH_O_BUNDLE:
            // TODO(tho@uber.com) create build rule for other types.
        }
        throw new RuntimeException("unhandled library build type");
      }

      // All swift-compile rules of swift-lib deps are required since we need their swiftmodules
      // during compilation.

      // Direct swift dependencies.
      SortedSet<BuildRule> buildDeps = params.getBuildDeps();

      List<CxxPreprocessorDep> preprocessorDeps = new ArrayList<>();
      // Build up the map of all C/C++ preprocessable dependencies.
      new AbstractBreadthFirstTraversal<BuildRule>(buildDeps) {
        @Override
        public Iterable<BuildRule> visit(BuildRule rule) {
          if (rule instanceof CxxPreprocessorDep) {
            preprocessorDeps.add((CxxPreprocessorDep) rule);
          }
          return rule.getBuildDeps();
        }
      }.start();

      // Transitive C libraries whose headers might be visible to swift via bridging.
      CxxPreprocessorInput inputs =
          CxxPreprocessorInput.concat(
              CxxPreprocessables.getTransitiveCxxPreprocessorInput(
                  cxxPlatform, graphBuilder, preprocessorDeps));

      PreprocessorFlags cxxDeps =
          PreprocessorFlags.of(
              Optional.empty(),
              CxxToolFlags.of(),
              RichStream.from(inputs.getIncludes())
                  .filter(
                      headers -> headers.getIncludeType() != CxxPreprocessables.IncludeType.SYSTEM)
                  .distinct()
                  .toImmutableList(),
              ImmutableList.copyOf(inputs.getFrameworks()));
      Preprocessor preprocessor =
          cxxPlatform.getCpp().resolve(graphBuilder, buildTarget.getTargetConfiguration());

      BuildTarget buildTargetCopy = buildTarget;

      AppleCompilerTargetTriple targetTriple =
          args.getTargetSdkVersion()
              .map(version -> swiftPlatform.get().getSwiftTarget().withTargetSdkVersion(version))
              .orElse(swiftPlatform.get().getSwiftTarget());

      return new SwiftCompile(
          swiftBuckConfig,
          buildTarget,
          targetTriple,
          projectFilesystem,
          graphBuilder,
          swiftPlatform.get().getSwiftc(),
          getSystemFrameworkSearchPaths(swiftPlatform.get()),
          args.getFrameworks(),
          getFrameworkPathToSearchPath(
              cxxPlatform, graphBuilder, cxxBuckConfig.getSkipSystemFrameworkSearchPaths()),
          cxxPlatform.getFlavor(),
          getModuleName(buildTarget, args),
          BuildTargetPaths.getGenPath(projectFilesystem.getBuckPaths(), buildTarget, "%s")
              .getPath(),
          args.getSrcs(),
          args.getVersion(),
          getCompilerFlags(
              swiftPlatform.get(),
              swiftBuckConfig,
              cxxPlatform,
              buildTargetCopy,
              graphBuilder,
              cellRoots,
              args),
          args.getEnableCxxInterop(),
          args.getBridgingHeader(),
          getPlatformPathIfRequired(swiftPlatform.get(), args),
          preprocessor,
          cxxDeps,
          swiftPlatform.get().getDebugPrefixMap(),
          false,
          downwardApiConfig.isEnabledForApple(),
          swiftPlatform.get().getPrefixSerializedDebugInfo(),
          swiftBuckConfig.getAddXctestImportPaths(),
          args.getSerializeDebuggingOptions(),
          getUsesExplicitModules(args, swiftPlatform.get()),
          getModuleDependencies(
              graphBuilder,
              projectFilesystem,
              cellRoots.getCellNameResolver(),
              buildTarget,
              swiftPlatform.get(),
              cxxPlatform,
              targetTriple,
              args,
              ImmutableSet.of(inputs),
              cxxDeps));
    }

    // Otherwise, we return the generic placeholder of this library.
    buildTarget =
        LinkerMapMode.restoreLinkerMapModeFlavorInTarget(buildTarget, flavoredLinkerMapMode);
    return new SwiftLibrary(
        buildTarget,
        projectFilesystem,
        params,
        graphBuilder,
        ImmutableSet.of(),
        swiftPlatformFlavorDomain,
        args.getBridgingHeader(),
        args.getFrameworks(),
        args.getLibraries(),
        args.getSupportedPlatformsRegex(),
        args.getPreferredLinkage().orElse(NativeLinkableGroup.Linkage.ANY));
  }

  private BuildRule createSharedLibraryBuildRule(
      CellPathResolver cellPathResolver,
      ProjectFilesystem projectFilesystem,
      BuildRuleParams params,
      ActionGraphBuilder graphBuilder,
      BuildTarget buildTarget,
      SwiftPlatform swiftPlatform,
      CxxPlatform cxxPlatform,
      Optional<String> soname) {

    String sharedLibrarySoname =
        CxxDescriptionEnhancer.getSharedLibrarySoname(
            soname, buildTarget.withoutFlavors(SUPPORTED_FLAVORS), cxxPlatform, projectFilesystem);
    RelPath sharedLibOutput =
        CxxDescriptionEnhancer.getSharedLibraryPath(
            projectFilesystem, buildTarget, sharedLibrarySoname);

    NativeLinkable swiftRuntimeLinkable =
        new SwiftRuntimeNativeLinkableGroup(swiftPlatform, buildTarget.getTargetConfiguration())
            .getNativeLinkable(cxxPlatform, graphBuilder);

    BuildTarget requiredBuildTarget =
        buildTarget
            .withoutFlavors(CxxDescriptionEnhancer.SHARED_FLAVOR)
            .withoutFlavors(LinkerMapMode.FLAVOR_DOMAIN.getFlavors())
            .withAppendedFlavors(SWIFT_COMPILE_FLAVOR);
    SwiftCompile rule = (SwiftCompile) graphBuilder.requireRule(requiredBuildTarget);

    NativeLinkableInput.Builder inputBuilder =
        NativeLinkableInput.builder()
            .from(
                swiftRuntimeLinkable.getNativeLinkableInput(
                    Linker.LinkableDepType.SHARED,
                    graphBuilder,
                    buildTarget.getTargetConfiguration()))
            .addAllArgs(rule.getFileListLinkArg())
            .addAllSwiftmodulePaths(rule.getSwiftmoduleLinkerInput());
    return graphBuilder.addToIndex(
        CxxLinkableEnhancer.createCxxLinkableBuildRule(
            cxxBuckConfig,
            downwardApiConfig,
            cxxPlatform,
            projectFilesystem,
            graphBuilder,
            buildTarget,
            Linker.LinkType.SHARED,
            Optional.of(sharedLibrarySoname),
            sharedLibOutput.getPath(),
            ImmutableList.of(),
            Linker.LinkableDepType.SHARED,
            Optional.empty(),
            CxxLinkOptions.of(),
            RichStream.from(params.getBuildDeps())
                .filter(NativeLinkableGroup.class)
                .map(g -> g.getNativeLinkable(cxxPlatform, graphBuilder))
                .concat(RichStream.of(swiftRuntimeLinkable))
                .collect(ImmutableSet.toImmutableSet()),
            Optional.empty(),
            Optional.empty(),
            ImmutableSet.of(),
            ImmutableSet.of(),
            inputBuilder.build(),
            Optional.empty(),
            cellPathResolver));
  }

  private FlavorDomain<UnresolvedSwiftPlatform> getSwiftPlatformsFlavorDomain(
      TargetConfiguration toolchainTargetConfiguration) {
    SwiftPlatformsProvider switftPlatformsProvider =
        toolchainProvider.getByName(
            SwiftPlatformsProvider.DEFAULT_NAME,
            toolchainTargetConfiguration,
            SwiftPlatformsProvider.class);
    return switftPlatformsProvider.getUnresolvedSwiftPlatforms();
  }

  public Optional<BuildRule> createCompanionBuildRule(
      BuildRuleCreationContextWithTargetGraph context,
      BuildTarget buildTarget,
      BuildRuleParams params,
      ActionGraphBuilder graphBuilder,
      CxxLibraryDescription.CommonArg args,
      Optional<String> targetSdkVersion) {
    if (!isSwiftTarget(buildTarget)) {
      boolean hasSwiftSource =
          !SwiftDescriptions.filterSwiftSources(
                  graphBuilder.getSourcePathResolver(), args.getSrcs())
              .isEmpty();
      return hasSwiftSource
          ? Optional.of(
              graphBuilder.requireRule(buildTarget.withAppendedFlavors(SWIFT_COMPANION_FLAVOR)))
          : Optional.empty();
    }

    SwiftLibraryDescriptionArg.Builder delegateArgsBuilder = SwiftLibraryDescriptionArg.builder();
    SwiftDescriptions.populateSwiftLibraryDescriptionArg(
        swiftBuckConfig,
        graphBuilder.getSourcePathResolver(),
        delegateArgsBuilder,
        args,
        buildTarget);
    delegateArgsBuilder.setTargetSdkVersion(targetSdkVersion);
    SwiftLibraryDescriptionArg delegateArgs = delegateArgsBuilder.build();
    if (!delegateArgs.getSrcs().isEmpty()) {
      return Optional.of(
          graphBuilder.addToIndex(createBuildRule(context, buildTarget, params, delegateArgs)));
    } else {
      return Optional.empty();
    }
  }

  /** Returns a rule which writes a list of compilation Swift commands to a JSON file. */
  public static SwiftCompilationDatabase createSwiftCompilationDatabaseRule(
      CxxPlatform cxxPlatform,
      SwiftPlatform swiftPlatform,
      SwiftBuckConfig swiftBuckConfig,
      CxxBuckConfig cxxBuckConfig,
      DownwardApiConfig downwardApiConfig,
      BuildTarget buildTarget,
      ActionGraphBuilder graphBuilder,
      CellPathResolver cellRoots,
      ProjectFilesystem projectFilesystem,
      SwiftLibraryDescriptionArg args,
      Preprocessor preprocessor,
      PreprocessorFlags preprocessFlags,
      boolean importUnderlyingModule,
      Optional<AppleCompilerTargetTriple> swiftTarget,
      ImmutableSet<CxxPreprocessorInput> preprocessorInputs) {
    AppleCompilerTargetTriple targetTriple = getSwiftTarget(swiftPlatform, swiftTarget);
    return new SwiftCompilationDatabase(
        swiftBuckConfig,
        buildTarget,
        targetTriple,
        projectFilesystem,
        graphBuilder,
        swiftPlatform.getSwiftc(),
        getSystemFrameworkSearchPaths(swiftPlatform),
        args.getFrameworks(),
        getFrameworkPathToSearchPath(
            cxxPlatform, graphBuilder, cxxBuckConfig.getSkipSystemFrameworkSearchPaths()),
        cxxPlatform.getFlavor(),
        getModuleName(buildTarget, args),
        BuildTargetPaths.getGenPath(projectFilesystem.getBuckPaths(), buildTarget, "%s").getPath(),
        args.getSrcs(),
        args.getVersion(),
        getCompilerFlags(
            swiftPlatform,
            swiftBuckConfig,
            cxxPlatform,
            buildTarget,
            graphBuilder,
            cellRoots,
            args),
        args.getEnableCxxInterop(),
        args.getBridgingHeader(),
        getPlatformPathIfRequired(swiftPlatform, args),
        preprocessor,
        preprocessFlags,
        swiftPlatform.getDebugPrefixMap(),
        importUnderlyingModule,
        downwardApiConfig.isEnabledForApple(),
        swiftPlatform.getPrefixSerializedDebugInfo(),
        swiftBuckConfig.getAddXctestImportPaths(),
        args.getSerializeDebuggingOptions(),
        getUsesExplicitModules(args, swiftPlatform),
        getModuleDependencies(
            graphBuilder,
            projectFilesystem,
            cellRoots.getCellNameResolver(),
            buildTarget,
            swiftPlatform,
            cxxPlatform,
            targetTriple,
            args,
            preprocessorInputs,
            preprocessFlags));
  }

  private static AppleCompilerTargetTriple getSwiftTarget(
      SwiftPlatform swiftPlatform, Optional<AppleCompilerTargetTriple> swiftTarget) {
    return swiftTarget.orElse(swiftPlatform.getSwiftTarget());
  }

  private static AddsToRuleKeyFunction<FrameworkPath, Optional<Path>> getFrameworkPathToSearchPath(
      CxxPlatform cxxPlatform,
      ActionGraphBuilder graphBuilder,
      boolean skipSystemFrameworkSearchPaths) {
    return CxxDescriptionEnhancer.frameworkPathToSearchPath(
        cxxPlatform, graphBuilder.getSourcePathResolver(), skipSystemFrameworkSearchPaths);
  }

  private static ImmutableList<Arg> getCompilerFlags(
      SwiftPlatform swiftPlatform,
      SwiftBuckConfig swiftBuckConfig,
      CxxPlatform cxxPlatform,
      BuildTarget buildTarget,
      ActionGraphBuilder graphBuilder,
      CellPathResolver cellRoots,
      SwiftLibraryDescriptionArg args) {
    ImmutableList.Builder<Arg> builder = ImmutableList.builder();
    builder.addAll(swiftPlatform.getSwiftFlags());

    for (String flag : swiftBuckConfig.getCompilerFlags().orElse(ImmutableList.of())) {
      builder.add(StringArg.of(flag));
    }

    if (!args.getCompilerFlags().isEmpty()) {
      StringWithMacrosConverter converter =
          CxxDescriptionEnhancer.getStringWithMacrosArgsConverter(
              buildTarget, cellRoots, graphBuilder, cxxPlatform);
      for (StringWithMacros arg : args.getCompilerFlags()) {
        builder.add(converter.convert(arg));
      }
    }

    return builder.build();
  }

  public static SwiftCompile createSwiftCompileRule(
      CxxPlatform cxxPlatform,
      SwiftPlatform swiftPlatform,
      SwiftBuckConfig swiftBuckConfig,
      CxxBuckConfig cxxBuckConfig,
      DownwardApiConfig downwardApiConfig,
      BuildTarget buildTarget,
      ActionGraphBuilder graphBuilder,
      CellPathResolver cellRoots,
      ProjectFilesystem projectFilesystem,
      SwiftLibraryDescriptionArg args,
      Preprocessor preprocessor,
      PreprocessorFlags preprocessFlags,
      boolean importUnderlyingModule,
      Optional<AppleCompilerTargetTriple> swiftTarget,
      ImmutableSet<CxxPreprocessorInput> preprocessorInputs) {
    AppleCompilerTargetTriple targetTriple = getSwiftTarget(swiftPlatform, swiftTarget);
    return new SwiftCompile(
        swiftBuckConfig,
        buildTarget,
        targetTriple,
        projectFilesystem,
        graphBuilder,
        swiftPlatform.getSwiftc(),
        getSystemFrameworkSearchPaths(swiftPlatform),
        args.getFrameworks(),
        getFrameworkPathToSearchPath(
            cxxPlatform, graphBuilder, cxxBuckConfig.getSkipSystemFrameworkSearchPaths()),
        cxxPlatform.getFlavor(),
        getModuleName(buildTarget, args),
        BuildTargetPaths.getGenPath(projectFilesystem.getBuckPaths(), buildTarget, "%s").getPath(),
        args.getSrcs(),
        args.getVersion(),
        getCompilerFlags(
            swiftPlatform,
            swiftBuckConfig,
            cxxPlatform,
            buildTarget,
            graphBuilder,
            cellRoots,
            args),
        args.getEnableCxxInterop(),
        args.getBridgingHeader(),
        getPlatformPathIfRequired(swiftPlatform, args),
        preprocessor,
        preprocessFlags,
        swiftPlatform.getDebugPrefixMap(),
        importUnderlyingModule,
        downwardApiConfig.isEnabledForApple(),
        swiftPlatform.getPrefixSerializedDebugInfo(),
        swiftBuckConfig.getAddXctestImportPaths(),
        args.getSerializeDebuggingOptions(),
        getUsesExplicitModules(args, swiftPlatform),
        getModuleDependencies(
            graphBuilder,
            projectFilesystem,
            cellRoots.getCellNameResolver(),
            buildTarget,
            swiftPlatform,
            cxxPlatform,
            targetTriple,
            args,
            preprocessorInputs,
            preprocessFlags));
  }

  private static boolean getUsesExplicitModules(
      SwiftLibraryDescriptionArg args, SwiftPlatform swiftPlatform) {
    return args.getUsesExplicitModules() && swiftPlatform.getSdkDependencies().isPresent();
  }

  private static ImmutableSet<ExplicitModuleOutput> getModuleDependencies(
      ActionGraphBuilder graphBuilder,
      ProjectFilesystem projectFilesystem,
      CellNameResolver cellNameResolver,
      BuildTarget buildTarget,
      SwiftPlatform swiftPlatform,
      CxxPlatform cxxPlatform,
      AppleCompilerTargetTriple targetTriple,
      SwiftLibraryDescriptionArg args,
      ImmutableSet<CxxPreprocessorInput> preprocessorInputs,
      PreprocessorFlags cxxDeps) {

    ImmutableSet.Builder<ExplicitModuleOutput> depsBuilder = ImmutableSet.builder();

    for (BuildRule dep : cxxDeps.getDeps(graphBuilder)) {
      if (dep instanceof SwiftCompile) {
        SwiftCompile swiftCompile = (SwiftCompile) dep;
        depsBuilder.add(
            ExplicitModuleOutput.of(
                swiftCompile.getModuleName(), true, swiftCompile.getSwiftModuleOutputPath()));
      }
    }

    // For implicit modules, we don't need to return any SDK's deps.
    if (!getUsesExplicitModules(args, swiftPlatform)) {
      return depsBuilder.build();
    }

    // Get the list of Swift args that will contribute to the clang module output and target hash.
    ImmutableList<Arg> moduleMapCompileArgs =
        getModuleMapCompileArgs(buildTarget, cellNameResolver, graphBuilder, swiftPlatform, args);
    Flavor pcmFlavor =
        getPcmFlavor(targetTriple, moduleMapCompileArgs, graphBuilder.getSourcePathResolver());

    // Collect all Swift and Clang module dependencies of SDK dependencies of this target and its
    // transitive dependencies.
    ImmutableSet.Builder<FrameworkPath> frameworkPathBuilder = ImmutableSet.builder();
    frameworkPathBuilder.addAll(args.getFrameworks());
    frameworkPathBuilder.addAll(args.getLibraries());
    for (CxxPreprocessorInput input : preprocessorInputs) {
      frameworkPathBuilder.addAll(input.getFrameworks());
      frameworkPathBuilder.addAll(input.getLibraries());
    }
    ImmutableSet<ExplicitModuleOutput> sdkDependencies =
        getSdkSwiftmoduleDependencies(
            graphBuilder, swiftPlatform, frameworkPathBuilder.build(), targetTriple);
    depsBuilder.addAll(sdkDependencies);

    // Create PCM compilation rules for all modular dependencies.
    ImmutableSet<ExplicitModuleOutput> pcmDependencyRules =
        createPcmCompileRules(
            graphBuilder,
            projectFilesystem,
            swiftPlatform,
            cxxPlatform,
            targetTriple,
            preprocessorInputs,
            moduleMapCompileArgs,
            pcmFlavor);
    depsBuilder.addAll(pcmDependencyRules);

    // If required add a rule to compile the underlying clang module for this target.
    depsBuilder.addAll(
        createUnderlyingModulePcmCompileRule(
            graphBuilder,
            projectFilesystem,
            buildTarget,
            swiftPlatform,
            cxxPlatform,
            targetTriple,
            args,
            preprocessorInputs,
            Stream.concat(sdkDependencies.stream(), pcmDependencyRules.stream())
                .filter(o -> !o.getIsSwiftmodule())
                .collect(ImmutableSet.toImmutableSet()),
            moduleMapCompileArgs,
            pcmFlavor));

    return depsBuilder.build();
  }

  private static ImmutableSet<ExplicitModuleOutput> getSdkSwiftmoduleDependencies(
      ActionGraphBuilder graphBuilder,
      SwiftPlatform swiftPlatform,
      Iterable<FrameworkPath> frameworks,
      AppleCompilerTargetTriple targetTriple) {
    Preconditions.checkState(
        swiftPlatform.getSdkDependencies().isPresent(),
        "Explicit module compilation requires the sdk_dependencies_path to be set on swift_toolchain.");
    SwiftSdkDependenciesProvider sdkDependencyProvider = swiftPlatform.getSdkDependencies().get();

    ImmutableList.Builder<String> modules = ImmutableList.builder();

    // We always need the stdlib and Onone support
    modules.add("Swift", "SwiftOnoneSupport");

    // _Concurrency is implicity added to all Swift compilation
    modules.add("_Concurrency");

    // Dispatch is a separate module but not a framework. We don't have a way to model that in
    // the apple rules so just always include it for now.
    modules.add("Dispatch");

    for (FrameworkPath frameworkPath : frameworks) {
      if (frameworkPath.isSDKROOTFrameworkPath() || frameworkPath.isPlatformDirFrameworkPath()) {
        String linkName =
            frameworkPath.getName(
                sp -> graphBuilder.getSourcePathResolver().getAbsolutePath(sp).getPath());
        modules.add(sdkDependencyProvider.getModuleNameForLinkName(linkName));
      }
    }

    ImmutableSet.Builder<ExplicitModuleOutput> swiftDependenciesBuilder = ImmutableSet.builder();
    for (String module : modules.build()) {
      swiftDependenciesBuilder.addAll(
          sdkDependencyProvider.getSdkModuleDependencies(module, targetTriple));
    }
    return swiftDependenciesBuilder.build();
  }

  private static ImmutableSet<ExplicitModuleOutput> createPcmCompileRules(
      ActionGraphBuilder graphBuilder,
      ProjectFilesystem projectFilesystem,
      SwiftPlatform swiftPlatform,
      CxxPlatform cxxPlatform,
      AppleCompilerTargetTriple targetTriple,
      Iterable<CxxPreprocessorInput> preprocessorInputs,
      Iterable<Arg> moduleMapCompileArgs,
      Flavor pcmFlavor) {
    // Collect all the modular inputs and create PCM rules for each of them.
    ImmutableSet.Builder<ExplicitModuleOutput> outputBuilder = ImmutableSet.builder();
    for (HeaderSymlinkTreeWithModuleMap moduleMapRule :
        getModuleMapDepRules(preprocessorInputs, graphBuilder)) {
      BuildTarget pcmBuildTarget =
          moduleMapRule.getBuildTarget().withFlavors(cxxPlatform.getFlavor(), pcmFlavor);
      outputBuilder.add(
          createPcmCompile(
              graphBuilder,
              projectFilesystem,
              swiftPlatform,
              cxxPlatform,
              targetTriple,
              pcmBuildTarget,
              ExplicitModuleInput.of(moduleMapRule.getSourcePathToOutput()),
              moduleMapRule.getModuleName(),
              moduleMapCompileArgs,
              pcmFlavor));
    }

    return outputBuilder.build();
  }

  private static Iterable<ExplicitModuleOutput> createUnderlyingModulePcmCompileRule(
      ActionGraphBuilder graphBuilder,
      ProjectFilesystem projectFilesystem,
      BuildTarget buildTarget,
      SwiftPlatform swiftPlatform,
      CxxPlatform cxxPlatform,
      AppleCompilerTargetTriple targetTriple,
      SwiftLibraryDescriptionArg args,
      Iterable<CxxPreprocessorInput> preprocessorInputs,
      ImmutableSet<ExplicitModuleOutput> clangModuleDependencies,
      Iterable<Arg> moduleMapCompileFlags,
      Flavor pcmFlavor) {
    // Check if this target requires an underlying module
    if (graphBuilder
        .requireMetadata(
            buildTarget.withFlavors(
                cxxPlatform.getFlavor(), AppleFlavors.SWIFT_UNDERLYING_MODULE_INPUT_FLAVOR),
            CxxPreprocessorInput.class)
        .isEmpty()) {
      return ImmutableList.of();
    }

    BuildTarget underlyingModuleCompileTarget =
        buildTarget.withFlavors(
            cxxPlatform.getFlavor(), AppleFlavors.SWIFT_UNDERLYING_MODULE_FLAVOR);
    String moduleName = getModuleName(buildTarget, args);

    BuildRule underlyingModuleCompileRule =
        graphBuilder.computeIfAbsent(
            underlyingModuleCompileTarget.withAppendedFlavors(pcmFlavor),
            target ->
                new SwiftModuleMapCompile(
                    target,
                    projectFilesystem,
                    graphBuilder,
                    targetTriple.getVersionedTriple(),
                    swiftPlatform.getSwiftc(),
                    getModuleCompileSwiftArgs(
                        moduleMapCompileFlags,
                        preprocessorInputs,
                        graphBuilder,
                        projectFilesystem,
                        cxxPlatform,
                        target),
                    false,
                    moduleName,
                    false,
                    ExplicitModuleInput.of(
                        graphBuilder
                            .requireRule(underlyingModuleCompileTarget)
                            .getSourcePathToOutput()),
                    clangModuleDependencies));
    return ImmutableList.of(
        ExplicitModuleOutput.of(
            moduleName, false, underlyingModuleCompileRule.getSourcePathToOutput()));
  }

  private static Iterable<HeaderSymlinkTreeWithModuleMap> getModuleMapDepRules(
      Iterable<CxxPreprocessorInput> preprocessorInputs, ActionGraphBuilder graphBuilder) {
    ImmutableSet.Builder<HeaderSymlinkTreeWithModuleMap> ruleBuilder = ImmutableSet.builder();
    for (CxxPreprocessorInput input : preprocessorInputs) {
      for (BuildRule dep : input.getDeps(graphBuilder)) {
        if (dep instanceof HeaderSymlinkTreeWithModuleMap) {
          ruleBuilder.add((HeaderSymlinkTreeWithModuleMap) dep);
        }
      }
    }

    return ruleBuilder.build();
  }

  /**
   * Return a mapping of underlying module path -> exported module path. This is used when
   * generating the VFS overlay for this module to remap the underlying module import.
   */
  public static Optional<Pair<RelPath, RelPath>> getUnderlyingModulePaths(
      BuildTarget buildTarget,
      CellNameResolver cellNameResolver,
      ActionGraphBuilder graphBuilder,
      CxxPlatform cxxPlatform,
      SwiftPlatform swiftPlatform,
      SwiftLibraryDescriptionArg args,
      AppleCompilerTargetTriple targetTriple,
      SourcePathResolverAdapter resolver,
      ProjectFilesystem projectFilesystem) {
    if (swiftPlatform.getSdkDependencies().isEmpty()) {
      return Optional.empty();
    }

    ImmutableList<Arg> compileArgs =
        getModuleMapCompileArgs(buildTarget, cellNameResolver, graphBuilder, swiftPlatform, args);
    Flavor pcmFlavor = getPcmFlavor(targetTriple, compileArgs, resolver);
    BuildTarget moduleTarget = buildTarget.withFlavors(cxxPlatform.getFlavor(), pcmFlavor);
    BuildTarget underlyingModuleTarget =
        buildTarget.withFlavors(
            cxxPlatform.getFlavor(), AppleFlavors.SWIFT_UNDERLYING_MODULE_FLAVOR, pcmFlavor);
    return Optional.of(
        new Pair<>(
            BuildTargetPaths.getGenPath(
                projectFilesystem.getBuckPaths(),
                moduleTarget,
                "%s/" + getModuleName(buildTarget, args) + ".pcm"),
            BuildTargetPaths.getGenPath(
                projectFilesystem.getBuckPaths(),
                underlyingModuleTarget,
                "%s/" + getModuleName(buildTarget, args) + ".pcm")));
  }

  private static ImmutableList<Arg> getModuleMapCompileArgs(
      BuildTarget buildTarget,
      CellNameResolver cellNameResolver,
      ActionGraphBuilder graphBuilder,
      SwiftPlatform swiftPlatform,
      SwiftLibraryDescriptionArg args) {
    ImmutableList.Builder<Arg> filteredArgs = ImmutableList.builder();

    // We always add the platform flags, these include the -sdk and -resource-dir flags that we
    // need to compile correctly.
    filteredArgs.addAll(swiftPlatform.getSwiftFlags());

    // Only apply a version if one is specified in the target
    args.getVersion().map(v -> filteredArgs.add(StringArg.of("-swift-version"), StringArg.of(v)));

    // Target flags will only take the explicitly passed -Xcc flags to the Swift driver.
    // These should be rare.
    StringWithMacrosConverter macrosConverter =
        StringWithMacrosConverter.of(
            buildTarget,
            cellNameResolver,
            graphBuilder,
            ImmutableList.of(LocationMacroExpander.INSTANCE, AbsoluteOutputMacroExpander.INSTANCE));

    Iterator<StringWithMacros> argIter = args.getCompilerFlags().iterator();
    while (argIter.hasNext()) {
      StringWithMacros arg = argIter.next();
      if (arg.matches("-Xcc")) {
        filteredArgs.add(StringArg.of("-Xcc"), macrosConverter.convert(argIter.next()));
      }
    }

    // If we need c++ interop make sure that all dependent PCM modules are compiled with c++
    // support.
    if (args.getEnableCxxInterop()) {
      filteredArgs.add(StringArg.of("-Xfrontend"), StringArg.of("-enable-cxx-interop"));
    }

    return filteredArgs.build();
  }

  private static Flavor getPcmFlavor(
      AppleCompilerTargetTriple targetTriple,
      Iterable<Arg> moduleMapCompileFlags,
      SourcePathResolverAdapter resolver) {
    ImmutableList<String> stringArgs = Arg.stringify(moduleMapCompileFlags, resolver);
    String flagHashSuffix =
        Integer.toHexString(targetTriple.getVersionedTriple().hashCode() ^ stringArgs.hashCode());
    return InternalFlavor.of("swift-pcm-" + flagHashSuffix);
  }

  private static ExplicitModuleOutput createPcmCompile(
      ActionGraphBuilder graphBuilder,
      ProjectFilesystem projectFilesystem,
      SwiftPlatform swiftPlatform,
      CxxPlatform cxxPlatform,
      AppleCompilerTargetTriple targetTriple,
      BuildTarget buildTarget,
      ExplicitModuleInput moduleMapInput,
      String moduleName,
      Iterable<Arg> moduleMapCompileFlags,
      Flavor pcmFlavor) {
    BuildRule rule =
        graphBuilder.computeIfAbsent(
            buildTarget,
            target -> {
              // Collect the transitive preprocessor input for this target
              CxxLibraryGroup lib =
                  (CxxLibraryGroup) graphBuilder.requireRule(target.withFlavors());
              ImmutableCollection<CxxPreprocessorInput> preprocessorInputs =
                  TransitiveCxxPreprocessorInputCache.computeTransitiveCxxToPreprocessorInputMap(
                          cxxPlatform, lib, false, graphBuilder)
                      .values();

              // Collect the dependent PCM inputs for this rule
              ImmutableSet<ExplicitModuleOutput> pcmInputs =
                  createPcmCompileRules(
                      graphBuilder,
                      projectFilesystem,
                      swiftPlatform,
                      cxxPlatform,
                      targetTriple,
                      preprocessorInputs,
                      moduleMapCompileFlags,
                      pcmFlavor);

              // Collect this libraries preprocessor input too so that we have all include and
              // framework flags.
              preprocessorInputs =
                  TransitiveCxxPreprocessorInputCache.computeTransitiveCxxToPreprocessorInputMap(
                          cxxPlatform, lib, true, graphBuilder)
                      .values();

              // get framework PCM dependencies
              ImmutableSet.Builder<ExplicitModuleOutput> depsBuilder = ImmutableSet.builder();
              depsBuilder.addAll(pcmInputs);
              for (CxxPreprocessorInput input : preprocessorInputs) {
                collectFrameworkPcmDependencies(
                    depsBuilder, input, graphBuilder, swiftPlatform, targetTriple);
              }

              return new SwiftModuleMapCompile(
                  target,
                  projectFilesystem,
                  graphBuilder,
                  targetTriple.getVersionedTriple(),
                  swiftPlatform.getSwiftc(),
                  getModuleCompileSwiftArgs(
                      moduleMapCompileFlags,
                      preprocessorInputs,
                      graphBuilder,
                      projectFilesystem,
                      cxxPlatform,
                      buildTarget),
                  false,
                  moduleName,
                  false,
                  moduleMapInput,
                  depsBuilder.build());
            });

    return ExplicitModuleOutput.of(moduleName, false, rule.getSourcePathToOutput());
  }

  private static void collectFrameworkPcmDependencies(
      ImmutableSet.Builder<ExplicitModuleOutput> builder,
      CxxPreprocessorInput input,
      ActionGraphBuilder graphBuilder,
      SwiftPlatform swiftPlatform,
      AppleCompilerTargetTriple targetTriple) {
    SwiftSdkDependenciesProvider sdkDepsProvider = swiftPlatform.getSdkDependencies().get();
    Consumer<FrameworkPath> addDeps =
        (frameworkPath) -> {
          String linkName =
              frameworkPath.getName(
                  fp -> graphBuilder.getSourcePathResolver().getAbsolutePath(fp).getPath());
          String moduleName = sdkDepsProvider.getModuleNameForLinkName(linkName);
          builder.addAll(sdkDepsProvider.getSdkClangModuleDependencies(moduleName, targetTriple));
        };
    input.getFrameworks().forEach(addDeps);
    input.getLibraries().forEach(addDeps);

    // Always import Darwin, this is required for the C stdlib
    builder.addAll(sdkDepsProvider.getSdkClangModuleDependencies("Darwin", targetTriple));
  }

  private static ImmutableList<Arg> getModuleCompileSwiftArgs(
      Iterable<Arg> platformArgs,
      Iterable<CxxPreprocessorInput> preprocessorInputs,
      ActionGraphBuilder graphBuilder,
      ProjectFilesystem projectFilesystem,
      CxxPlatform cxxPlatform,
      BuildTarget buildTarget) {
    ImmutableList.Builder<Arg> argBuilder = ImmutableList.builder();
    argBuilder.addAll(platformArgs);

    Preprocessor preprocessor =
        cxxPlatform.getCpp().resolve(graphBuilder, buildTarget.getTargetConfiguration());
    Iterable<CxxHeaders> cxxHeaders =
        StreamSupport.stream(preprocessorInputs.spliterator(), false)
            .flatMap(input -> input.getIncludes().stream())
            .collect(Collectors.toUnmodifiableList());
    Iterable<String> includeArgs =
        CxxHeaders.getArgs(
            cxxHeaders,
            graphBuilder.getSourcePathResolver(),
            Optional.of(PathShortener.byRelativizingToWorkingDir(projectFilesystem.getRootPath())),
            preprocessor);
    for (String arg : includeArgs) {
      argBuilder.add(StringArg.of("-Xcc"), StringArg.of(arg));
    }

    return argBuilder.build();
  }

  private static Optional<SourcePath> getPlatformPathIfRequired(
      SwiftPlatform swiftPlatform, SwiftLibraryDescriptionArg args) {
    // If this library depends on any frameworks in the platform dir then we need to ensure the
    // target providing that path is a dependency of the compile rule. This is required for eg
    // the XCTest.swiftmodule.
    for (FrameworkPath frameworkPath : args.getFrameworks()) {
      if (frameworkPath.isPlatformDirFrameworkPath()) {
        return Optional.of(swiftPlatform.getPlatformPath());
      }
    }
    return Optional.empty();
  }

  private static String getModuleName(BuildTarget buildTarget, SwiftLibraryDescriptionArg args) {
    return args.getModuleName().orElse(buildTarget.getShortName());
  }

  public static boolean isSwiftTarget(BuildTarget buildTarget) {
    return buildTarget.getFlavors().contains(SWIFT_COMPANION_FLAVOR)
        || buildTarget.getFlavors().contains(SWIFT_COMPILE_FLAVOR);
  }

  private static ImmutableList<Arg> getSystemFrameworkSearchPaths(SwiftPlatform swiftPlatform) {
    return swiftPlatform.getAdditionalSystemFrameworkSearchPaths().stream()
        .map(path -> StringArg.of(path.toString()))
        .collect(ImmutableList.toImmutableList());
  }

  private FlavorDomain<UnresolvedCxxPlatform> getCxxPlatforms(
      TargetConfiguration toolchainTargetConfiguration) {
    return toolchainProvider
        .getByName(
            CxxPlatformsProvider.DEFAULT_NAME,
            toolchainTargetConfiguration,
            CxxPlatformsProvider.class)
        .getUnresolvedCxxPlatforms();
  }

  @RuleArg
  interface AbstractSwiftLibraryDescriptionArg
      extends BuildRuleArg, HasDeclaredDeps, HasSrcs, AbstractSwiftCxxCommonArg {
    ImmutableList<StringWithMacros> getCompilerFlags();

    Optional<String> getVersion();

    @Value.NaturalOrder
    ImmutableSortedSet<FrameworkPath> getFrameworks();

    @Value.NaturalOrder
    ImmutableSortedSet<FrameworkPath> getLibraries();

    Optional<Pattern> getSupportedPlatformsRegex();

    Optional<String> getSoname();

    Optional<SourcePath> getBridgingHeader();

    Optional<NativeLinkableGroup.Linkage> getPreferredLinkage();

    /**
     * The minimum OS version for which this target should be built. If set, this will override the
     * config-level option.
     */
    Optional<String> getTargetSdkVersion();

    @Value.Default
    default boolean getSerializeDebuggingOptions() {
      return true;
    }

    @Value.Default
    default boolean getEnableCxxInterop() {
      return false;
    }

    @Value.Default
    default boolean getUsesExplicitModules() {
      return false;
    }
  }
}

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

package com.facebook.buck.cxx;

import com.facebook.buck.core.cell.CellPathResolver;
import com.facebook.buck.core.model.BuildTarget;
import com.facebook.buck.core.model.Flavor;
import com.facebook.buck.core.model.FlavorConvertible;
import com.facebook.buck.core.model.FlavorDomain;
import com.facebook.buck.core.model.FlavorSet;
import com.facebook.buck.core.model.InternalFlavor;
import com.facebook.buck.core.rules.ActionGraphBuilder;
import com.facebook.buck.core.rules.BuildRule;
import com.facebook.buck.core.sourcepath.SourcePath;
import com.facebook.buck.core.util.graph.AbstractBreadthFirstTraversal;
import com.facebook.buck.cxx.config.CxxBuckConfig;
import com.facebook.buck.cxx.toolchain.CxxPlatform;
import com.facebook.buck.cxx.toolchain.HeaderSymlinkTree;
import com.facebook.buck.cxx.toolchain.HeaderVisibility;
import com.facebook.buck.cxx.toolchain.PicType;
import com.facebook.buck.downwardapi.config.DownwardApiConfig;
import com.facebook.buck.infer.InferConfig;
import com.facebook.buck.infer.InferPlatform;
import com.facebook.buck.io.filesystem.ProjectFilesystem;
import com.facebook.buck.util.stream.RichStream;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Multimaps;
import java.nio.file.Path;
import java.util.Optional;
import java.util.stream.Collectors;

/** Handles infer flavors for {@link CxxLibraryGroup} and {@link CxxBinary}. */
public final class CxxInferEnhancer {

  /** Basename of inter capture buildrules */
  static final String INFER_CAPTURE_BUILDRULE = "infer-capture";

  /** Flavors affixed to a library or binary rule to run infer. */
  public enum InferFlavors implements FlavorConvertible {
    /* Main flavor used to capture target and all its deps */
    INFER_CAPTURE_ALL(InternalFlavor.of("infer-capture-all")),
    /* Internal flavour added to transitive dependencies */
    INFER_CAPTURE_NO_DEPS(InternalFlavor.of("infer-capture-no-deps"));

    private final InternalFlavor flavor;

    InferFlavors(InternalFlavor flavor) {
      this.flavor = flavor;
    }

    @Override
    public InternalFlavor getFlavor() {
      return flavor;
    }

    private static BuildTarget targetWithoutAnyInferFlavor(BuildTarget target) {
      BuildTarget result = target;
      for (InferFlavors f : values()) {
        result = result.withoutFlavors(f.getFlavor());
      }
      return result;
    }

    private static void assertNoInferFlavors(ImmutableSet<Flavor> flavors) {
      Optional<Flavor> f = findSupportedFlavor(FlavorSet.copyOf(flavors));
      Preconditions.checkArgument(
          !f.isPresent(), "Unexpected infer-related flavor found: %s", f.toString());
    }

    public static Optional<Flavor> findSupportedFlavor(FlavorSet flavors) {
      for (InferFlavors f : InferFlavors.values()) {
        if (flavors.contains(f.getFlavor())) {
          return Optional.of(f.getFlavor());
        }
      }
      return Optional.empty();
    }
  }

  public static final FlavorDomain<InferFlavors> INFER_FLAVOR_DOMAIN =
      FlavorDomain.from("Infer flavors", InferFlavors.class);

  public static BuildRule requireInferRule(
      BuildTarget target,
      ProjectFilesystem filesystem,
      ActionGraphBuilder graphBuilder,
      CellPathResolver cellRoots,
      CxxBuckConfig cxxBuckConfig,
      DownwardApiConfig downwardApiConfig,
      CxxPlatform cxxPlatform,
      InferPlatform inferPlatform,
      CxxConstructorArg args,
      InferConfig inferConfig) {
    return new CxxInferEnhancer(
            graphBuilder, cxxBuckConfig, inferConfig, downwardApiConfig, cxxPlatform, inferPlatform)
        .requireInferRule(target, cellRoots, filesystem, args);
  }

  private final ActionGraphBuilder graphBuilder;
  private final CxxBuckConfig cxxBuckConfig;
  private final InferConfig inferConfig;
  private final DownwardApiConfig downwardApiConfig;
  private final CxxPlatform cxxPlatform;
  private final InferPlatform inferPlatform;

  private CxxInferEnhancer(
      ActionGraphBuilder graphBuilder,
      CxxBuckConfig cxxBuckConfig,
      InferConfig inferConfig,
      DownwardApiConfig downwardApiConfig,
      CxxPlatform cxxPlatform,
      InferPlatform inferPlatform) {
    this.graphBuilder = graphBuilder;
    this.cxxBuckConfig = cxxBuckConfig;
    this.inferConfig = inferConfig;
    this.downwardApiConfig = downwardApiConfig;
    this.cxxPlatform = cxxPlatform;
    this.inferPlatform = inferPlatform;
  }

  private BuildRule requireInferRule(
      BuildTarget buildTarget,
      CellPathResolver cellRoots,
      ProjectFilesystem filesystem,
      CxxConstructorArg args) {
    Optional<InferFlavors> inferFlavor = INFER_FLAVOR_DOMAIN.getValue(buildTarget);
    Preconditions.checkArgument(
        inferFlavor.isPresent(), "Expected BuildRuleParams to contain infer flavor.");
    switch (inferFlavor.get()) {
      case INFER_CAPTURE_ALL:
        return requireAllTransitiveCaptureBuildRules(buildTarget, cellRoots, filesystem, args);
      case INFER_CAPTURE_NO_DEPS:
        return requireInferCaptureAggregatorBuildRuleForCxxDescriptionArg(
            buildTarget, cellRoots, filesystem, args);
    }
    throw new IllegalStateException(
        "All InferFlavor cases should be handled, got: " + inferFlavor.get());
  }

  private BuildRule requireAllTransitiveCaptureBuildRules(
      BuildTarget target,
      CellPathResolver cellRoots,
      ProjectFilesystem filesystem,
      CxxConstructorArg args) {

    CxxInferCaptureRulesAggregator aggregator =
        requireInferCaptureAggregatorBuildRuleForCxxDescriptionArg(
            target, cellRoots, filesystem, args);

    ImmutableSet<CxxInferCaptureRule> captureRules = aggregator.getAllTransitiveCaptures();

    return graphBuilder.addToIndex(
        new CxxInferCaptureTransitiveRule(target, filesystem, graphBuilder, captureRules));
  }

  private CxxInferCaptureRulesAggregator requireInferCaptureAggregatorBuildRuleForCxxDescriptionArg(
      BuildTarget target,
      CellPathResolver cellRoots,
      ProjectFilesystem filesystem,
      CxxConstructorArg args) {

    Flavor inferCaptureNoDeps = InferFlavors.INFER_CAPTURE_NO_DEPS.getFlavor();

    return (CxxInferCaptureRulesAggregator)
        graphBuilder.computeIfAbsent(
            InferFlavors.targetWithoutAnyInferFlavor(target)
                .withAppendedFlavors(inferCaptureNoDeps),
            targetWithInferCaptureNoDepsFlavor -> {
              BuildTarget cleanTarget = InferFlavors.targetWithoutAnyInferFlavor(target);

              ImmutableMap<String, CxxSource> sources =
                  collectSources(cleanTarget, cellRoots, args);

              ImmutableSet<CxxInferCaptureRule> captureRules =
                  requireInferCaptureBuildRules(cleanTarget, cellRoots, filesystem, sources, args);

              ImmutableSet<CxxInferCaptureRulesAggregator> transitiveAggregatorRules =
                  requireTransitiveCaptureAndAggregatingRules(args, inferCaptureNoDeps);

              return new CxxInferCaptureRulesAggregator(
                  targetWithInferCaptureNoDepsFlavor,
                  filesystem,
                  captureRules,
                  transitiveAggregatorRules);
            });
  }

  private ImmutableSet<CxxInferCaptureRulesAggregator> requireTransitiveCaptureAndAggregatingRules(
      CxxConstructorArg args, Flavor requiredFlavor) {
    ImmutableSet<BuildRule> deps = args.getCxxDeps().get(graphBuilder, cxxPlatform);

    return requireTransitiveDependentLibraries(
        cxxPlatform, deps, requiredFlavor, CxxInferCaptureRulesAggregator.class);
  }

  private ImmutableMap<String, CxxSource> collectSources(
      BuildTarget buildTarget, CellPathResolver cellRoots, CxxConstructorArg args) {
    InferFlavors.assertNoInferFlavors(buildTarget.getFlavors().getSet());
    return CxxDescriptionEnhancer.parseCxxSources(
        buildTarget, cellRoots, graphBuilder, cxxPlatform, args);
  }

  private <T extends BuildRule> ImmutableSet<T> requireTransitiveDependentLibraries(
      CxxPlatform cxxPlatform,
      Iterable<? extends BuildRule> deps,
      Flavor requiredFlavor,
      Class<T> ruleClass) {
    ImmutableSet.Builder<T> depsBuilder = ImmutableSet.builder();
    new AbstractBreadthFirstTraversal<BuildRule>(deps) {
      @Override
      public Iterable<BuildRule> visit(BuildRule buildRule) {
        if (buildRule instanceof CxxLibraryGroup) {
          CxxLibraryGroup library = (CxxLibraryGroup) buildRule;
          depsBuilder.add(
              (ruleClass.cast(
                  library.requireBuildRule(
                      graphBuilder, requiredFlavor, cxxPlatform.getFlavor()))));
          return RichStream.from(
                  ((CxxLibraryGroup) buildRule).getCxxPreprocessorDeps(cxxPlatform, graphBuilder))
              .filter(BuildRule.class)
              .collect(Collectors.toList());
        }
        return ImmutableSet.of();
      }
    }.start();
    return depsBuilder.build();
  }

  private ImmutableList<CxxPreprocessorInput> computePreprocessorInputForCxxBinaryDescriptionArg(
      BuildTarget target,
      CellPathResolver cellRoots,
      CxxPlatform cxxPlatform,
      CxxBinaryDescription.CommonArg args,
      HeaderSymlinkTree headerSymlinkTree,
      ProjectFilesystem projectFilesystem) {
    ImmutableSet<BuildRule> deps = args.getCxxDeps().get(graphBuilder, cxxPlatform);
    return CxxDescriptionEnhancer.collectCxxPreprocessorInput(
        target,
        cxxPlatform,
        graphBuilder,
        deps,
        ImmutableListMultimap.copyOf(
            Multimaps.transformValues(
                CxxFlags.getLanguageFlagsWithMacros(
                    args.getPreprocessorFlags(),
                    args.getPlatformPreprocessorFlags(),
                    args.getLangPreprocessorFlags(),
                    args.getLangPlatformPreprocessorFlags(),
                    cxxPlatform),
                CxxDescriptionEnhancer.getStringWithMacrosArgsConverter(
                        target, cellRoots, graphBuilder, cxxPlatform)
                    ::convert)),
        ImmutableList.of(headerSymlinkTree),
        args.getFrameworks(),
        CxxPreprocessables.getTransitiveCxxPreprocessorInputFromDeps(
            cxxPlatform,
            graphBuilder,
            RichStream.from(deps).filter(CxxPreprocessorDep.class::isInstance).toImmutableList()),
        args.getRawHeaders(),
        args.getIncludeDirectories(),
        projectFilesystem);
  }

  private ImmutableSet<CxxInferCaptureRule> requireInferCaptureBuildRules(
      BuildTarget target,
      CellPathResolver cellRoots,
      ProjectFilesystem filesystem,
      ImmutableMap<String, CxxSource> sources,
      CxxConstructorArg args) {

    InferFlavors.assertNoInferFlavors(target.getFlavors().getSet());

    ImmutableMap<Path, SourcePath> headers =
        CxxDescriptionEnhancer.parseHeaders(
            target, graphBuilder, filesystem, Optional.of(cxxPlatform), args);

    // Setup the header symlink tree and combine all the preprocessor input from this rule
    // and all dependencies.

    boolean shouldCreateHeadersSymlinks = true;
    if (args instanceof CxxLibraryDescription.CommonArg) {
      shouldCreateHeadersSymlinks =
          ((CxxLibraryDescription.CommonArg) args)
              .getXcodePrivateHeadersSymlinks()
              .orElse(cxxPlatform.getPrivateHeadersSymlinksEnabled());
    }
    HeaderSymlinkTree headerSymlinkTree =
        CxxDescriptionEnhancer.requireHeaderSymlinkTree(
            target,
            filesystem,
            graphBuilder,
            cxxPlatform,
            headers,
            HeaderVisibility.PRIVATE,
            shouldCreateHeadersSymlinks);

    ImmutableList<CxxPreprocessorInput> preprocessorInputs;

    if (args instanceof CxxBinaryDescription.CommonArg) {
      preprocessorInputs =
          computePreprocessorInputForCxxBinaryDescriptionArg(
              target,
              cellRoots,
              cxxPlatform,
              (CxxBinaryDescription.CommonArg) args,
              headerSymlinkTree,
              filesystem);
    } else if (args instanceof CxxLibraryDescription.CommonArg) {
      preprocessorInputs =
          CxxLibraryDescription.getPreprocessorInputsForBuildingLibrarySources(
              cxxBuckConfig,
              graphBuilder,
              cellRoots,
              target,
              (CxxLibraryDescription.CommonArg) args,
              cxxPlatform,
              args.getCxxDeps().get(graphBuilder, cxxPlatform),
              CxxLibraryDescription.TransitiveCxxPreprocessorInputFunction.fromLibraryRule(),
              ImmutableList.of(headerSymlinkTree),
              filesystem);
    } else {
      throw new IllegalStateException("Only Binary and Library args supported.");
    }

    CxxSourceRuleFactory factory =
        CxxSourceRuleFactory.of(
            filesystem,
            target,
            graphBuilder,
            graphBuilder.getSourcePathResolver(),
            cxxBuckConfig,
            downwardApiConfig,
            cxxPlatform,
            preprocessorInputs,
            ImmutableMultimap.copyOf(
                Multimaps.transformValues(
                    CxxFlags.getLanguageFlagsWithMacros(
                        args.getCompilerFlags(),
                        args.getPlatformCompilerFlags(),
                        args.getLangCompilerFlags(),
                        args.getLangPlatformCompilerFlags(),
                        cxxPlatform),
                    CxxDescriptionEnhancer.getStringWithMacrosArgsConverter(
                            target, cellRoots, graphBuilder, cxxPlatform)
                        ::convert)),
            args.getPrefixHeader(),
            args.getPrecompiledHeader(),
            PicType.PDC);
    return factory.requireInferCaptureBuildRules(
        sources, inferConfig, downwardApiConfig, inferPlatform);
  }
}

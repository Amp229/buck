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

import com.facebook.buck.core.cell.nameresolver.CellNameResolver;
import com.facebook.buck.core.description.arg.BuildRuleArg;
import com.facebook.buck.core.description.arg.HasDeclaredDeps;
import com.facebook.buck.core.description.arg.HasSrcs;
import com.facebook.buck.core.description.attr.ImplicitDepsInferringDescription;
import com.facebook.buck.core.model.BuildTarget;
import com.facebook.buck.core.model.Flavor;
import com.facebook.buck.core.model.Flavored;
import com.facebook.buck.core.model.TargetConfiguration;
import com.facebook.buck.core.rules.BuildRule;
import com.facebook.buck.core.rules.BuildRuleCreationContextWithTargetGraph;
import com.facebook.buck.core.rules.BuildRuleParams;
import com.facebook.buck.core.rules.DescriptionWithTargetGraph;
import com.facebook.buck.core.toolchain.ToolchainProvider;
import com.facebook.buck.core.util.immutables.RuleArg;
import com.facebook.buck.cxx.toolchain.linker.Linker;
import com.facebook.buck.downwardapi.config.DownwardApiConfig;
import com.facebook.buck.rules.args.Arg;
import com.facebook.buck.rules.coercer.PatternMatchedCollection;
import com.facebook.buck.rules.macros.AbsoluteOutputMacroExpander;
import com.facebook.buck.rules.macros.LocationMacroExpander;
import com.facebook.buck.rules.macros.Macro;
import com.facebook.buck.rules.macros.MacroExpander;
import com.facebook.buck.rules.macros.StringWithMacros;
import com.facebook.buck.rules.macros.StringWithMacrosConverter;
import com.facebook.buck.versions.VersionRoot;
import com.google.common.base.Function;
import com.google.common.collect.ImmutableCollection.Builder;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import org.immutables.value.Value;

public class GoExportedLibraryDescription
    implements DescriptionWithTargetGraph<GoExportedLibraryDescriptionArg>,
        ImplicitDepsInferringDescription<
            GoExportedLibraryDescription.AbstractGoExportedLibraryDescriptionArg>,
        VersionRoot<GoExportedLibraryDescriptionArg>,
        Flavored {

  public static final ImmutableList<MacroExpander<? extends Macro, ?>> MACRO_EXPANDERS =
      ImmutableList.of(LocationMacroExpander.INSTANCE, AbsoluteOutputMacroExpander.INSTANCE);

  private final GoBuckConfig goBuckConfig;
  private final DownwardApiConfig downwardApiConfig;
  private final ToolchainProvider toolchainProvider;

  public GoExportedLibraryDescription(
      GoBuckConfig goBuckConfig,
      DownwardApiConfig downwardApiConfig,
      ToolchainProvider toolchainProvider) {
    this.goBuckConfig = goBuckConfig;
    this.downwardApiConfig = downwardApiConfig;
    this.toolchainProvider = toolchainProvider;
  }

  @Override
  public Class<GoExportedLibraryDescriptionArg> getConstructorArgType() {
    return GoExportedLibraryDescriptionArg.class;
  }

  @Override
  public boolean hasFlavors(
      ImmutableSet<Flavor> flavors, TargetConfiguration toolchainTargetConfiguration) {
    return getGoToolchain(toolchainTargetConfiguration)
        .getPlatformFlavorDomain()
        .containsAnyOf(flavors);
  }

  @Override
  public BuildRule createBuildRule(
      BuildRuleCreationContextWithTargetGraph context,
      BuildTarget buildTarget,
      BuildRuleParams params,
      GoExportedLibraryDescriptionArg args) {
    GoPlatform platform =
        GoDescriptors.getPlatformForRule(
                getGoToolchain(buildTarget.getTargetConfiguration()),
                this.goBuckConfig,
                buildTarget,
                args)
            .resolve(context.getActionGraphBuilder(), buildTarget.getTargetConfiguration());

    StringWithMacrosConverter macrosConverter =
        StringWithMacrosConverter.of(
            buildTarget,
            context.getCellPathResolver().getCellNameResolver(),
            context.getActionGraphBuilder(),
            MACRO_EXPANDERS);

    Function<ImmutableList<StringWithMacros>, ImmutableList<Arg>> expandMacros =
        l -> ImmutableList.copyOf(Iterables.transform(l, arg -> macrosConverter.convert(arg)));

    return GoDescriptors.createGoBinaryRule(
        buildTarget,
        context.getProjectFilesystem(),
        params,
        context.getActionGraphBuilder(),
        goBuckConfig,
        downwardApiConfig,
        args.getLinkStyle().orElse(Linker.LinkableDepType.STATIC_PIC),
        args.getBuildMode(),
        args.getLinkMode(),
        args.getSrcs(),
        args.getResources(),
        args.getCompilerFlags(),
        args.getAssemblerFlags(),
        expandMacros.apply(args.getLinkerFlags()),
        Iterables.concat(
            ImmutableList.<ImmutableList<Arg>>builder()
                .add(expandMacros.apply(args.getExternalLinkerFlags()))
                .addAll(
                    Iterables.transform(
                        args.getPlatformExternalLinkerFlags()
                            .getMatchingValues(platform.getFlavor().toString()),
                        expandMacros))
                .build()),
        platform);
  }

  @Override
  public void findDepsForTargetFromConstructorArgs(
      BuildTarget buildTarget,
      CellNameResolver cellRoots,
      GoExportedLibraryDescription.AbstractGoExportedLibraryDescriptionArg constructorArg,
      Builder<BuildTarget> extraDepsBuilder,
      Builder<BuildTarget> targetGraphOnlyDepsBuilder) {
    // Add the C/C++ linker parse time deps.
    UnresolvedGoPlatform platform =
        GoDescriptors.getPlatformForRule(
            getGoToolchain(buildTarget.getTargetConfiguration()),
            this.goBuckConfig,
            buildTarget,
            constructorArg);
    targetGraphOnlyDepsBuilder.addAll(
        platform.getParseTimeDeps(buildTarget.getTargetConfiguration()));
  }

  private GoToolchain getGoToolchain(TargetConfiguration toolchainTargetConfiguration) {
    return toolchainProvider.getByName(
        GoToolchain.DEFAULT_NAME, toolchainTargetConfiguration, GoToolchain.class);
  }

  @RuleArg
  interface AbstractGoExportedLibraryDescriptionArg
      extends BuildRuleArg, HasDeclaredDeps, HasSrcs, HasGoLinkable {

    GoLinkStep.BuildMode getBuildMode();

    @Value.Default
    default PatternMatchedCollection<ImmutableList<StringWithMacros>>
        getPlatformExternalLinkerFlags() {
      return PatternMatchedCollection.of();
    }
  }
}

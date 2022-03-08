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

package com.facebook.buck.features.rust;

import com.facebook.buck.core.description.arg.BuildRuleArg;
import com.facebook.buck.core.description.arg.HasDeclaredDeps;
import com.facebook.buck.core.filesystems.AbsPath;
import com.facebook.buck.core.model.BuildTarget;
import com.facebook.buck.core.rules.BuildRule;
import com.facebook.buck.core.rules.BuildRuleCreationContextWithTargetGraph;
import com.facebook.buck.core.rules.BuildRuleParams;
import com.facebook.buck.core.rules.DescriptionWithTargetGraph;
import com.facebook.buck.core.sourcepath.SourcePath;
import com.facebook.buck.core.sourcepath.resolver.SourcePathResolverAdapter;
import com.facebook.buck.core.util.immutables.RuleArg;
import com.facebook.buck.cxx.CxxDeps;
import com.facebook.buck.cxx.toolchain.linker.Linker;
import com.facebook.buck.cxx.toolchain.nativelink.NativeLinkableGroup;
import com.facebook.buck.io.filesystem.ProjectFilesystem;
import com.facebook.buck.rules.coercer.PatternMatchedCollection;
import com.facebook.buck.versions.VersionPropagator;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSortedSet;
import java.util.Optional;
import org.immutables.value.Value;

public class PrebuiltRustLibraryDescription
    implements DescriptionWithTargetGraph<PrebuiltRustLibraryDescriptionArg>,
        VersionPropagator<PrebuiltRustLibraryDescriptionArg> {
  private final RustBuckConfig rustBuckConfig;

  public PrebuiltRustLibraryDescription(RustBuckConfig rustBuckConfig) {
    this.rustBuckConfig = rustBuckConfig;
  }

  @Override
  public Class<PrebuiltRustLibraryDescriptionArg> getConstructorArgType() {
    return PrebuiltRustLibraryDescriptionArg.class;
  }

  @Override
  public PrebuiltRustLibrary createBuildRule(
      BuildRuleCreationContextWithTargetGraph context,
      BuildTarget buildTarget,
      BuildRuleParams params,
      PrebuiltRustLibraryDescriptionArg args) {
    CxxDeps allDeps =
        CxxDeps.builder().addDeps(args.getDeps()).addPlatformDeps(args.getPlatformDeps()).build();
    // TODO(cjhopman): This shouldn't be an anonymous class, it's capturing a ton of information
    // that isn't being reflected in rulekeys.
    return new PrebuiltRustLibrary(
        buildTarget, context.getProjectFilesystem(), params, args.getRlib()) {
      @Override
      public String getCrate() {
        return args.getCrate();
      }

      @Override
      public com.facebook.buck.rules.args.Arg getLinkerArg(
          Optional<BuildTarget> directDependent,
          ProjectFilesystem dependentFilesystem,
          CrateType crateType,
          RustPlatform rustPlatform,
          Linker.LinkableDepType depType,
          Optional<String> alias) {
        SourcePathResolverAdapter pathResolver =
            context.getActionGraphBuilder().getSourcePathResolver();
        AbsPath rlibAbsolutePath = pathResolver.getAbsolutePath(args.getRlib());
        return RustLibraryArg.of(
            buildTarget,
            alias.orElse(args.getCrate()),
            args.getRlib(),
            directDependent,
            dependentFilesystem.relativize(rlibAbsolutePath).toString(),
            rustBuckConfig.getExternLocations());
      }

      @Override
      public boolean isProcMacro() {
        return args.getProcMacro();
      }

      @Override
      public NativeLinkableGroup.Linkage getPreferredLinkage() {
        return NativeLinkableGroup.Linkage.STATIC;
      }

      @Override
      public ImmutableMap<String, SourcePath> getRustSharedLibraries(RustPlatform rustPlatform) {
        return ImmutableMap.of();
      }

      @Override
      public Iterable<BuildRule> getRustLinkableDeps(RustPlatform rustPlatform) {
        return allDeps.get(context.getActionGraphBuilder(), rustPlatform.getCxxPlatform());
      }
    };
  }

  @RuleArg
  interface AbstractPrebuiltRustLibraryDescriptionArg extends BuildRuleArg, HasDeclaredDeps {
    SourcePath getRlib();

    @Value.Default
    default String getCrate() {
      return getName();
    }

    Optional<Linker.LinkableDepType> getLinkStyle();

    @Value.Default
    default boolean getProcMacro() {
      return false;
    }

    @Value.Default
    default PatternMatchedCollection<ImmutableSortedSet<BuildTarget>> getPlatformDeps() {
      return PatternMatchedCollection.of();
    }
  }
}

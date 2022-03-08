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

package com.facebook.buck.apple;

import com.facebook.buck.core.description.arg.BuildRuleArg;
import com.facebook.buck.core.exceptions.HumanReadableException;
import com.facebook.buck.core.model.BuildTarget;
import com.facebook.buck.core.model.Flavor;
import com.facebook.buck.core.model.Flavored;
import com.facebook.buck.core.model.TargetConfiguration;
import com.facebook.buck.core.model.targetgraph.TargetNode;
import com.facebook.buck.core.rules.BuildRule;
import com.facebook.buck.core.rules.BuildRuleCreationContextWithTargetGraph;
import com.facebook.buck.core.rules.BuildRuleParams;
import com.facebook.buck.core.rules.BuildRuleResolver;
import com.facebook.buck.core.rules.DescriptionWithTargetGraph;
import com.facebook.buck.core.rules.impl.NoopBuildRuleWithDeclaredAndExtraDeps;
import com.facebook.buck.core.sourcepath.SourcePath;
import com.facebook.buck.core.util.immutables.RuleArg;
import com.facebook.buck.io.filesystem.ProjectFilesystem;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import java.util.Optional;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.immutables.value.Value;

/** Description for an apple_resource rule which copies resource files to the built bundle. */
public class AppleResourceDescription
    implements DescriptionWithTargetGraph<AppleResourceDescriptionArg>,
        Flavored,
        HasAppleBundleResourcesDescription<AppleResourceDescriptionArg> {

  @Override
  public Class<AppleResourceDescriptionArg> getConstructorArgType() {
    return AppleResourceDescriptionArg.class;
  }

  @Override
  public BuildRule createBuildRule(
      BuildRuleCreationContextWithTargetGraph context,
      BuildTarget buildTarget,
      BuildRuleParams params,
      AppleResourceDescriptionArg args) {
    return new NoopBuildRuleWithDeclaredAndExtraDeps(
        buildTarget, context.getProjectFilesystem(), params);
  }

  @Override
  public boolean hasFlavors(
      ImmutableSet<Flavor> flavors, TargetConfiguration toolchainTargetConfiguration) {
    return true;
  }

  @Override
  public void addAppleBundleResources(
      AppleBundleResources.Builder builder,
      TargetNode<AppleResourceDescriptionArg> targetNode,
      ProjectFilesystem filesystem,
      BuildRuleResolver resolver) {
    AppleResourceDescriptionArg appleResource = targetNode.getConstructorArg();
    AppleResourceBundleDestination destination =
        appleResource.getDestination().orElse(AppleResourceBundleDestination.defaultValue());
    Supplier<SortedSet<SourcePathWithAppleBundleDestination>> supplier = TreeSet::new;
    builder.addAllResourceDirs(
        appleResource.getDirs().stream()
            .map(
                sourcePath ->
                    SourcePathWithAppleBundleDestination.of(
                        sourcePath, destination.asGenericDestination(), false))
            .collect(Collectors.toCollection(supplier)));
    builder.addAllDirsContainingResourceDirs(
        appleResource.getContentDirs().stream()
            .map(
                sourcePath ->
                    SourcePathWithAppleBundleDestination.of(
                        sourcePath, destination.asGenericDestination(), false))
            .collect(Collectors.toCollection(supplier)));
    builder.addAllResourceFiles(
        appleResource.getFiles().stream()
            .map(
                sourcePath ->
                    SourcePathWithAppleBundleDestination.of(
                        sourcePath,
                        destination.asGenericDestination(),
                        appleResource.getCodesignOnCopy()))
            .collect(Collectors.toCollection(supplier)));
    ImmutableSet<SourcePath> variants = appleResource.getVariants();
    ImmutableMap<String, ImmutableSet<SourcePath>> namedVariants = appleResource.getNamedVariants();
    if ((!variants.isEmpty() || !namedVariants.isEmpty())
        && destination != AppleResourceBundleDestination.RESOURCES) {
      throw new HumanReadableException(
          String.format(
              ("Resource \"%s\" contains localization variants, but destination \"%s\" is "
                  + "non-standard. It should have standard \\\"resource\\\" destination\""),
              appleResource.getName(),
              destination));
    }
    builder.addAllResourceVariantFiles(variants);
    builder.putAllNamedResourceVariantFiles(namedVariants);
  }

  @RuleArg
  interface AbstractAppleResourceDescriptionArg extends BuildRuleArg {
    ImmutableSet<SourcePath> getDirs();

    ImmutableSet<SourcePath> getContentDirs();

    ImmutableSet<SourcePath> getFiles();

    ImmutableSet<SourcePath> getVariants();

    ImmutableMap<String, ImmutableSet<SourcePath>> getNamedVariants();

    ImmutableSet<BuildTarget> getResourcesFromDeps();

    Optional<AppleResourceBundleDestination> getDestination();

    @Value.Default
    default boolean getCodesignOnCopy() {
      return false;
    }
  }
}

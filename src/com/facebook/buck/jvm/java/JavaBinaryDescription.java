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

import com.facebook.buck.core.cell.nameresolver.CellNameResolver;
import com.facebook.buck.core.description.arg.BuildRuleArg;
import com.facebook.buck.core.description.arg.HasDeclaredDeps;
import com.facebook.buck.core.description.arg.HasTests;
import com.facebook.buck.core.description.attr.ImplicitDepsInferringDescription;
import com.facebook.buck.core.model.BuildTarget;
import com.facebook.buck.core.model.Flavor;
import com.facebook.buck.core.model.InternalFlavor;
import com.facebook.buck.core.model.TargetConfiguration;
import com.facebook.buck.core.rules.ActionGraphBuilder;
import com.facebook.buck.core.rules.BuildRule;
import com.facebook.buck.core.rules.BuildRuleCreationContextWithTargetGraph;
import com.facebook.buck.core.rules.BuildRuleParams;
import com.facebook.buck.core.rules.DescriptionWithTargetGraph;
import com.facebook.buck.core.sourcepath.SourcePath;
import com.facebook.buck.core.toolchain.ToolchainProvider;
import com.facebook.buck.core.toolchain.tool.Tool;
import com.facebook.buck.core.util.immutables.RuleArg;
import com.facebook.buck.cxx.toolchain.CxxPlatform;
import com.facebook.buck.cxx.toolchain.CxxPlatformsProvider;
import com.facebook.buck.cxx.toolchain.UnresolvedCxxPlatform;
import com.facebook.buck.cxx.toolchain.nativelink.NativeLinkableGroup;
import com.facebook.buck.cxx.toolchain.nativelink.NativeLinkableGroups;
import com.facebook.buck.cxx.toolchain.nativelink.NativeLinkables;
import com.facebook.buck.downwardapi.config.DownwardApiConfig;
import com.facebook.buck.io.filesystem.ProjectFilesystem;
import com.facebook.buck.jvm.core.CalculateAbi;
import com.facebook.buck.jvm.core.JavaLibrary;
import com.facebook.buck.jvm.java.toolchain.JavaCxxPlatformProvider;
import com.facebook.buck.jvm.java.toolchain.JavaOptionsProvider;
import com.facebook.buck.jvm.java.toolchain.JavacOptionsProvider;
import com.facebook.buck.versions.VersionRoot;
import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import java.nio.file.Path;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Pattern;
import org.immutables.value.Value;

public class JavaBinaryDescription
    implements DescriptionWithTargetGraph<JavaBinaryDescriptionArg>,
        ImplicitDepsInferringDescription<JavaBinaryDescription.AbstractJavaBinaryDescriptionArg>,
        VersionRoot<JavaBinaryDescriptionArg> {

  private static final Flavor FAT_JAR_INNER_JAR_FLAVOR = InternalFlavor.of("inner-jar");

  private final ToolchainProvider toolchainProvider;
  private final JavaBuckConfig javaBuckConfig;
  private final JavacFactory javacFactory;
  private final DownwardApiConfig downwardApiConfig;
  private final Function<TargetConfiguration, JavaOptions> javaOptions;

  public JavaBinaryDescription(
      ToolchainProvider toolchainProvider,
      JavaBuckConfig javaBuckConfig,
      DownwardApiConfig downwardApiConfig) {
    this.toolchainProvider = toolchainProvider;
    this.javaBuckConfig = javaBuckConfig;
    this.javaOptions = JavaOptionsProvider.getDefaultJavaOptions(toolchainProvider);
    this.javacFactory = JavacFactory.getDefault(toolchainProvider);
    this.downwardApiConfig = downwardApiConfig;
  }

  @Override
  public Class<JavaBinaryDescriptionArg> getConstructorArgType() {
    return JavaBinaryDescriptionArg.class;
  }

  private UnresolvedCxxPlatform getCxxPlatform(
      AbstractJavaBinaryDescriptionArg args, TargetConfiguration toolchainTargetConfiguration) {
    return args.getDefaultCxxPlatform()
        .map(
            toolchainProvider
                    .getByName(
                        CxxPlatformsProvider.DEFAULT_NAME,
                        toolchainTargetConfiguration,
                        CxxPlatformsProvider.class)
                    .getUnresolvedCxxPlatforms()
                ::getValue)
        .orElse(
            toolchainProvider
                .getByName(
                    JavaCxxPlatformProvider.DEFAULT_NAME,
                    toolchainTargetConfiguration,
                    JavaCxxPlatformProvider.class)
                .getDefaultJavaCxxPlatform());
  }

  @Override
  public BuildRule createBuildRule(
      BuildRuleCreationContextWithTargetGraph context,
      BuildTarget buildTarget,
      BuildRuleParams params,
      JavaBinaryDescriptionArg args) {

    ActionGraphBuilder graphBuilder = context.getActionGraphBuilder();
    TargetConfiguration targetConfiguration = buildTarget.getTargetConfiguration();

    ImmutableMap<String, SourcePath> nativeLibraries =
        getNativeLibraries(
            params.getBuildDeps(),
            getCxxPlatform(args, targetConfiguration).resolve(graphBuilder, targetConfiguration),
            graphBuilder);
    BuildTarget binaryBuildTarget = buildTarget;

    // If we're packaging native libraries, we'll build the binary JAR in a separate rule and
    // package it into the final fat JAR, so adjust it's params to use a flavored target.
    if (!nativeLibraries.isEmpty()) {
      binaryBuildTarget = binaryBuildTarget.withAppendedFlavors(FAT_JAR_INNER_JAR_FLAVOR);
    }

    ProjectFilesystem projectFilesystem = context.getProjectFilesystem();

    // Construct the build rule to build the binary JAR.
    ImmutableSet<JavaLibrary> transitiveClasspathDeps =
        JavaLibraryClasspathProvider.getClasspathDeps(params.getBuildDeps());
    ImmutableSet<SourcePath> transitiveClasspaths =
        JavaLibraryClasspathProvider.getClasspathsFromLibraries(transitiveClasspathDeps);
    Tool javaRuntime = javaOptions.apply(targetConfiguration).getJavaRuntime();

    JavaBinary javaBinary =
        new JavaBinary(
            binaryBuildTarget,
            projectFilesystem,
            params.copyAppendingExtraDeps(transitiveClasspathDeps),
            javaRuntime,
            args.getMainClass().orElse(null),
            args.getManifestFile().orElse(null),
            args.getMetaInfDirectory().orElse(null),
            args.getBlacklist(),
            transitiveClasspathDeps,
            transitiveClasspaths,
            javaBuckConfig.shouldCacheBinaries(),
            javaBuckConfig.getDuplicatesLogLevel(),
            args.getGenerateWrapper());

    if (nativeLibraries.isEmpty()) {
      return javaBinary;
    }

    // If we're packaging native libraries, construct the rule to build the fat JAR, which packages
    // up the original binary JAR and any required native libraries.
    graphBuilder.addToIndex(javaBinary);
    SourcePath innerJar = javaBinary.getSourcePathToOutput();
    JavacFactory javacFactory = JavacFactory.getDefault(toolchainProvider);
    BuildRuleParams buildRuleParams =
        params.copyAppendingExtraDeps(
            Suppliers.ofInstance(
                Iterables.concat(
                    graphBuilder.filterBuildRuleInputs(
                        Iterables.concat(ImmutableList.of(innerJar), nativeLibraries.values())),
                    javacFactory.getBuildDeps(graphBuilder, targetConfiguration))));
    Javac javac = javacFactory.create(graphBuilder, null, targetConfiguration);
    JavacOptions javacOptions =
        toolchainProvider
            .getByName(
                JavacOptionsProvider.DEFAULT_NAME, targetConfiguration, JavacOptionsProvider.class)
            .getJavacOptions();
    return new JarFattener(
        buildTarget,
        projectFilesystem,
        buildRuleParams,
        javac,
        javacOptions,
        innerJar,
        javaBinary,
        nativeLibraries,
        javaRuntime,
        downwardApiConfig.isEnabledForJava(),
        args.getGenerateWrapper());
  }

  /**
   * @return all the transitive native libraries a rule depends on, represented as a map from their
   *     system-specific library names to their {@link SourcePath} objects.
   */
  private ImmutableMap<String, SourcePath> getNativeLibraries(
      Iterable<BuildRule> deps, CxxPlatform cxxPlatform, ActionGraphBuilder graphBuilder) {
    // Allow the transitive walk to find NativeLinkables through the BuildRuleParams deps of a
    // JavaLibrary or CalculateAbi object. The deps may be either one depending if we're compiling
    // against ABI rules or full rules
    ImmutableMap<BuildTarget, NativeLinkableGroup> roots =
        NativeLinkableGroups.getNativeLinkableRoots(
            deps,
            r ->
                r instanceof JavaLibrary
                    ? Optional.of(((JavaLibrary) r).getDepsForTransitiveClasspathEntries())
                    : r instanceof CalculateAbi ? Optional.of(r.getBuildDeps()) : Optional.empty());
    return NativeLinkables.getTransitiveSharedLibraries(
        graphBuilder,
        roots.values().stream()
            .map(g -> g.getNativeLinkable(cxxPlatform, graphBuilder))
            .collect(ImmutableList.toImmutableList()),
        true);
  }

  @Override
  public void findDepsForTargetFromConstructorArgs(
      BuildTarget buildTarget,
      CellNameResolver cellRoots,
      AbstractJavaBinaryDescriptionArg constructorArg,
      ImmutableCollection.Builder<BuildTarget> extraDepsBuilder,
      ImmutableCollection.Builder<BuildTarget> targetGraphOnlyDepsBuilder) {
    targetGraphOnlyDepsBuilder.addAll(
        getCxxPlatform(constructorArg, buildTarget.getTargetConfiguration())
            .getParseTimeDeps(buildTarget.getTargetConfiguration()));
    javacFactory.addParseTimeDeps(
        targetGraphOnlyDepsBuilder, null, buildTarget.getTargetConfiguration());
  }

  @RuleArg
  interface AbstractJavaBinaryDescriptionArg extends BuildRuleArg, HasDeclaredDeps, HasTests {

    Optional<String> getMainClass();

    Optional<SourcePath> getManifestFile();

    Optional<Path> getMetaInfDirectory();

    ImmutableSet<Pattern> getBlacklist();

    @Value.Default
    default boolean getGenerateWrapper() {
      return false;
    }

    Optional<Flavor> getDefaultCxxPlatform();
  }
}

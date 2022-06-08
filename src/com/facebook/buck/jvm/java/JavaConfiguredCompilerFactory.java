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

import static com.facebook.buck.jvm.java.abi.AbiGenerationModeUtils.isNotClassAbi;
import static com.facebook.buck.jvm.java.abi.AbiGenerationModeUtils.usesDependencies;

import com.facebook.buck.cd.model.java.AbiGenerationMode;
import com.facebook.buck.core.model.TargetConfiguration;
import com.facebook.buck.core.rules.BuildRuleResolver;
import com.facebook.buck.core.toolchain.ToolchainProvider;
import com.facebook.buck.downwardapi.config.DownwardApiConfig;
import java.util.Optional;
import java.util.function.BiFunction;
import javax.annotation.Nullable;

public class JavaConfiguredCompilerFactory extends ConfiguredCompilerFactory {
  private final JavaBuckConfig javaBuckConfig;
  private final DownwardApiConfig downwardApiConfig;
  private final BiFunction<ToolchainProvider, TargetConfiguration, ExtraClasspathProvider>
      extraClasspathProviderSupplier;
  private final JavacFactory javacFactory;

  public JavaConfiguredCompilerFactory(
      JavaBuckConfig javaBuckConfig,
      DownwardApiConfig downwardApiConfig,
      JavacFactory javacFactory) {
    this(
        javaBuckConfig,
        downwardApiConfig,
        (toolchainProvider, toolchainTargetConfiguration) -> ExtraClasspathProvider.EMPTY,
        javacFactory);
  }

  public JavaConfiguredCompilerFactory(
      JavaBuckConfig javaBuckConfig,
      DownwardApiConfig downwardApiConfig,
      BiFunction<ToolchainProvider, TargetConfiguration, ExtraClasspathProvider>
          extraClasspathProviderSupplier,
      JavacFactory javacFactory) {
    this.javaBuckConfig = javaBuckConfig;
    this.downwardApiConfig = downwardApiConfig;
    this.extraClasspathProviderSupplier = extraClasspathProviderSupplier;
    this.javacFactory = javacFactory;
  }

  @Override
  public boolean trackClassUsage(JavacOptions javacOptions) {
    return javacOptions.trackClassUsage();
  }

  @Override
  public JavaBuckConfig.UnusedDependenciesConfig getUnusedDependenciesAction() {
    return javaBuckConfig.getUnusedDependenciesAction();
  }

  @Override
  public boolean shouldDesugarInterfaceMethods() {
    return javaBuckConfig.shouldDesugarInterfaceMethods();
  }

  @Override
  public boolean shouldCompileAgainstAbis() {
    return javaBuckConfig.shouldCompileAgainstAbis();
  }

  @Override
  public AbiGenerationMode getAbiGenerationMode() {
    return javaBuckConfig.getAbiGenerationMode();
  }

  @Override
  public boolean shouldGenerateSourceAbi() {
    return isNotClassAbi(javaBuckConfig.getAbiGenerationMode());
  }

  @Override
  public boolean shouldMigrateToSourceOnlyAbi() {
    return shouldGenerateSourceAbi();
  }

  @Override
  public boolean shouldGenerateSourceOnlyAbi() {
    return shouldGenerateSourceAbi() && !usesDependencies(javaBuckConfig.getAbiGenerationMode());
  }

  @Override
  public CompileToJarStepFactory<JavaExtraParams> configure(
      @Nullable JvmLibraryArg arg,
      JavacOptions javacOptions,
      BuildRuleResolver buildRuleResolver,
      TargetConfiguration targetConfiguration,
      ToolchainProvider toolchainProvider) {

    return new JavacToJarStepFactory(
        javacOptions,
        extraClasspathProviderSupplier.apply(toolchainProvider, targetConfiguration),
        downwardApiConfig.isEnabledForJava());
  }

  @Override
  public Optional<ExtraClasspathProvider> getExtraClasspathProvider(
      ToolchainProvider toolchainProvider, TargetConfiguration toolchainTargetConfiguration) {
    return Optional.of(
        extraClasspathProviderSupplier.apply(toolchainProvider, toolchainTargetConfiguration));
  }

  @Override
  public Javac getJavac(
      BuildRuleResolver resolver,
      @Nullable JvmLibraryArg arg,
      TargetConfiguration toolchainTargetConfiguration) {
    return javacFactory.create(resolver, arg, toolchainTargetConfiguration);
  }
}

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
import com.facebook.buck.core.model.BuildTarget;
import com.facebook.buck.core.model.TargetConfiguration;
import com.facebook.buck.core.rules.BuildRuleResolver;
import com.facebook.buck.core.toolchain.ToolchainProvider;
import com.facebook.buck.jvm.cd.params.DefaultRulesCDParams;
import com.facebook.buck.jvm.cd.params.RulesCDParams;
import com.google.common.collect.ImmutableCollection;
import java.util.Optional;
import java.util.function.Consumer;
import javax.annotation.Nullable;

public abstract class ConfiguredCompilerFactory {

  // TODO(jkeljo): args is not actually Nullable in all subclasses, but it is also not
  // straightforward to create a safe "empty" default value. Find a fix.
  public abstract CompileToJarStepFactory<?> configure(
      @Nullable JvmLibraryArg args,
      JavacOptions javacOptions,
      BuildRuleResolver buildRuleResolver,
      TargetConfiguration targetConfiguration,
      ToolchainProvider toolchainProvider);

  public abstract Javac getJavac(
      BuildRuleResolver resolver,
      @Nullable JvmLibraryArg arg,
      TargetConfiguration toolchainTargetConfiguration);

  public abstract Optional<ExtraClasspathProvider> getExtraClasspathProvider(
      ToolchainProvider toolchainProvider, TargetConfiguration toolchainTargetConfiguration);

  public boolean trackClassUsage(@SuppressWarnings("unused") JavacOptions javacOptions) {
    return false;
  }

  @Nullable
  public JavaBuckConfig.UnusedDependenciesConfig getUnusedDependenciesAction() {
    return JavaBuckConfig.UnusedDependenciesConfig.IGNORE;
  }

  /**
   * Get the compiler daemon parameters associated with this language and compiler. Defaults to a
   * sentinel value that disables compiler daemons.
   */
  public RulesCDParams getCDParams() {
    return DefaultRulesCDParams.DISABLED;
  }

  public boolean shouldDesugarInterfaceMethods() {
    return false;
  }

  public boolean shouldCompileAgainstAbis() {
    // Buck's ABI generation support was built for Java and hasn't been extended for other JVM
    // languages yet, so this is defaulted false.
    // See https://github.com/facebook/buck/issues/1386
    return false;
  }

  public AbiGenerationMode getAbiGenerationMode() {
    return AbiGenerationMode.CLASS;
  }

  public boolean shouldGenerateSourceAbi() {
    return false;
  }

  // In some configurations (Java) a rule can produce a source-only-abi
  // than it also can produce a source-abi
  // In other configurations (Kotlin) source-abi is not available
  public boolean sourceAbiIsAvailableIfSourceOnlyAbiIsAvailable() {
    return false;
  }

  public boolean shouldGenerateSourceOnlyAbi() {
    return false;
  }

  public boolean shouldMigrateToSourceOnlyAbi() {
    return false;
  }

  public void addTargetDeps(
      @SuppressWarnings("unused") TargetConfiguration targetConfiguration,
      @SuppressWarnings("unused") ImmutableCollection.Builder<BuildTarget> extraDepsBuilder,
      @SuppressWarnings("unused")
          ImmutableCollection.Builder<BuildTarget> targetGraphOnlyDepsBuilder) {}

  public void getNonProvidedClasspathDeps(
      @SuppressWarnings("unused") TargetConfiguration targetConfiguration,
      @SuppressWarnings("unused") Consumer<BuildTarget> depsConsumer) {}
}

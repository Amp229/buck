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

package com.facebook.buck.jvm.java.stepsbuilder.impl;

import com.facebook.buck.jvm.java.CompileToJarStepFactory;
import com.facebook.buck.jvm.java.stepsbuilder.AbiStepsBuilder;
import com.facebook.buck.jvm.java.stepsbuilder.JavaCompileStepsBuilder;
import com.facebook.buck.jvm.java.stepsbuilder.JavaCompileStepsBuilderFactory;
import com.facebook.buck.jvm.java.stepsbuilder.LibraryStepsBuilder;

/**
 * Factory that creates {@link JavaCompileStepsBuilder } builders instances that returns steps that
 * is ready to be executed in the current process.
 */
public class DefaultJavaCompileStepsBuilderFactory<T extends CompileToJarStepFactory.ExtraParams>
    implements JavaCompileStepsBuilderFactory {

  private final CompileToJarStepFactory<T> configuredCompiler;

  public DefaultJavaCompileStepsBuilderFactory(CompileToJarStepFactory<T> configuredCompiler) {
    this.configuredCompiler = configuredCompiler;
  }

  /** Creates an appropriate {@link LibraryStepsBuilder} instance */
  @Override
  public LibraryStepsBuilder getLibraryBuilder() {
    return new DefaultLibraryStepsBuilder<>(configuredCompiler);
  }

  /** Creates an appropriate {@link AbiStepsBuilder} instance */
  @Override
  public AbiStepsBuilder getAbiBuilder() {
    return new DefaultAbiStepsBuilder<>(configuredCompiler);
  }
}

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

package com.facebook.buck.jvm.cd;

import com.facebook.buck.event.BuckEventBus;
import com.facebook.buck.jvm.java.CompileToJarStepFactory;
import com.facebook.buck.step.isolatedsteps.IsolatedStep;
import com.google.common.collect.ImmutableList;
import java.util.Optional;

/** Creates a list of {@link IsolatedStep} that is ready for in process execute. */
abstract class DefaultCompileStepsBuilderBase<T extends CompileToJarStepFactory.ExtraParams>
    implements CompileStepsBuilder {

  protected final ImmutableList.Builder<IsolatedStep> stepsBuilder = ImmutableList.builder();
  protected final CompileToJarStepFactory<T> configuredCompiler;

  DefaultCompileStepsBuilderBase(CompileToJarStepFactory<T> configuredCompiler) {
    this.configuredCompiler = configuredCompiler;
  }

  @Override
  public final ImmutableList<IsolatedStep> buildIsolatedSteps(
      Optional<BuckEventBus> buckEventBusOptional) {
    return stepsBuilder.build();
  }
}

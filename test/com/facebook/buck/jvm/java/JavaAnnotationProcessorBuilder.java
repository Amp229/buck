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

import com.facebook.buck.core.model.BuildTarget;
import com.facebook.buck.core.model.targetgraph.AbstractNodeBuilder;

public class JavaAnnotationProcessorBuilder
    extends AbstractNodeBuilder<
        JavaAnnotationProcessorDescriptionArg.Builder,
        JavaAnnotationProcessorDescriptionArg,
        JavaAnnotationProcessorDescription,
        JavaAnnotationProcessor> {

  private JavaAnnotationProcessorBuilder(BuildTarget target) {
    super(new JavaAnnotationProcessorDescription(), target);
    setIsolateClassloader(true);
    setSupportsAbiGenerationFromSource(true);
    setDoesNotAffectAbi(true);
  }

  public static JavaAnnotationProcessorBuilder createBuilder(BuildTarget target) {
    return new JavaAnnotationProcessorBuilder(target);
  }

  public JavaAnnotationProcessorBuilder addDep(BuildTarget dep) {
    getArgForPopulating().addDeps(dep);
    return this;
  }

  public void setIsolateClassloader(boolean isolateClassloader) {
    getArgForPopulating().setIsolateClassLoader(isolateClassloader);
  }

  public void setSupportsAbiGenerationFromSource(boolean supportsAbiGenerationFromSource) {
    getArgForPopulating().setSupportsAbiGenerationFromSource(supportsAbiGenerationFromSource);
  }

  public void setDoesNotAffectAbi(boolean doesNotAffectAbi) {
    getArgForPopulating().setDoesNotAffectAbi(doesNotAffectAbi);
  }

  public JavaAnnotationProcessorBuilder setProcessorClass(String processorClass) {
    getArgForPopulating().setProcessorClass(processorClass);
    return this;
  }
}

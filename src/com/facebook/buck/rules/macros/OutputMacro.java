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

package com.facebook.buck.rules.macros;

import com.facebook.buck.core.model.TargetConfiguration;
import com.facebook.buck.core.model.TargetConfigurationResolver;
import com.facebook.buck.core.util.immutables.BuckStyleValue;
import com.google.common.collect.ComparisonChain;

/**
 * Macro used to denote the path of an output of a rule. Used when constructing command lines for
 * the rule, e.g. in {@code flags} fields of supporting rules.
 */
@BuckStyleValue
public abstract class OutputMacro implements Macro, UnconfiguredMacro {

  public static OutputMacro of(String outputName) {
    return ImmutableOutputMacro.ofImpl(outputName);
  }

  @Override
  public Class<? extends UnconfiguredMacro> getUnconfiguredMacroClass() {
    return OutputMacro.class;
  }

  @Override
  public Class<? extends Macro> getMacroClass() {
    return OutputMacro.class;
  }

  public abstract String getOutputName();

  @Override
  public int compareTo(Macro o) {
    int result = Macro.super.compareTo(o);
    if (result != 0) {
      return result;
    }
    OutputMacro other = (OutputMacro) o;
    return ComparisonChain.start().compare(getOutputName(), other.getOutputName()).result();
  }

  @Override
  public Macro configure(
      TargetConfiguration targetConfiguration,
      TargetConfigurationResolver hostConfigurationResolver) {
    return this;
  }
}

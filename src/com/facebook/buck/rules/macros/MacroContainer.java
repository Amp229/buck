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

import com.facebook.buck.core.util.immutables.BuckStyleValue;
import com.google.common.collect.ComparisonChain;
import org.immutables.value.Value;

/** Packages up a {@link Macro} along some configuration. */
@BuckStyleValue
public abstract class MacroContainer implements Comparable<MacroContainer> {

  public abstract Macro getMacro();

  @Value.Default
  public boolean isOutputToFile() {
    return false;
  }

  @Override
  public int compareTo(MacroContainer o) {
    return ComparisonChain.start()
        .compare(getMacro(), o.getMacro())
        .compareFalseFirst(isOutputToFile(), o.isOutputToFile())
        .result();
  }

  public static MacroContainer of(Macro macro, boolean outputToFile) {
    return ImmutableMacroContainer.ofImpl(macro, outputToFile);
  }

  public final MacroContainer withMacro(Macro value) {
    if (getMacro() == value) {
      return this;
    }
    return of(value, isOutputToFile());
  }
}

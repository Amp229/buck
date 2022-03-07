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

import com.facebook.buck.core.model.BuildTarget;
import com.facebook.buck.core.model.UnconfiguredBuildTarget;
import com.facebook.buck.core.util.immutables.BuckStyleValue;
import com.google.common.collect.ImmutableList;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.regex.Pattern;

/** Unconfigured version of {@link LdflagsSharedFilterMacro}. */
@BuckStyleValue
public abstract class UnconfiguredLdflagsSharedFilterMacro
    extends UnconfiguredCxxGenruleFilterAndTargetsMacro {

  public static UnconfiguredLdflagsSharedFilterMacro of(
      Optional<Pattern> pattern, ImmutableList<UnconfiguredBuildTarget> buildTargets) {
    return ImmutableUnconfiguredLdflagsSharedFilterMacro.ofImpl(pattern, buildTargets);
  }

  @Override
  public Class<? extends UnconfiguredMacro> getUnconfiguredMacroClass() {
    return UnconfiguredLdflagsSharedFilterMacro.class;
  }

  @Override
  public BiFunction<Optional<Pattern>, ImmutableList<BuildTarget>, CxxGenruleFilterAndTargetsMacro>
      getConfiguredFactory() {
    return LdflagsSharedFilterMacro::of;
  }
}

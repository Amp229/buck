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

package com.facebook.buck.features.rust;

import com.facebook.buck.core.description.arg.BuildRuleArg;
import com.facebook.buck.core.description.arg.HasDefaultPlatform;
import com.facebook.buck.core.description.arg.HasSrcs;
import com.facebook.buck.core.model.BuildTarget;
import com.facebook.buck.core.model.Flavor;
import com.facebook.buck.core.sourcepath.SourcePath;
import com.facebook.buck.rules.coercer.PatternMatchedCollection;
import com.facebook.buck.rules.macros.StringWithMacros;
import com.facebook.buck.util.types.Pair;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSortedMap;
import com.google.common.collect.ImmutableSortedSet;
import java.util.Optional;
import org.immutables.value.Value;

/** Common parameters for all Rust rules */
public interface RustCommonArgs
    extends BuildRuleArg, HasNamedDeclaredDeps, HasSrcs, HasDefaultPlatform {
  @Value.NaturalOrder
  ImmutableSortedMap<SourcePath, String> getMappedSrcs();

  @Value.NaturalOrder
  ImmutableSortedMap<String, StringWithMacros> getEnv();

  Optional<String> getEdition();

  @Value.NaturalOrder
  ImmutableSortedSet<String> getFeatures();

  ImmutableList<StringWithMacros> getRustcFlags();

  Optional<String> getCrate();

  Optional<String> getCrateRoot();

  @Value.NaturalOrder
  ImmutableSortedMap<Flavor, ImmutableList<StringWithMacros>> getPlatformRustcFlags();

  @Value.Default
  default PatternMatchedCollection<ImmutableSortedSet<BuildTarget>> getPlatformDeps() {
    return PatternMatchedCollection.of();
  }

  @Value.Default
  default ImmutableList<Pair<BuildTarget, ImmutableList<String>>> getFlaggedDeps() {
    return ImmutableList.of();
  }

  @Value.Default
  default PatternMatchedCollection<ImmutableList<Pair<BuildTarget, ImmutableList<String>>>>
      getPlatformFlaggedDeps() {
    return PatternMatchedCollection.of();
  }
}

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

package com.facebook.buck.cxx;

import com.facebook.buck.core.rules.BuildRule;
import com.facebook.buck.core.rules.SourcePathRuleFinder;
import com.facebook.buck.core.rules.common.BuildableSupport;
import com.facebook.buck.core.sourcepath.SourcePath;
import com.facebook.buck.rules.args.Arg;
import com.google.common.collect.ImmutableSet;

/** Builder suitable for generating the dependency list of a build rule. */
// TODO(cjhopman): Delete this.
public class DepsBuilder {
  private final ImmutableSet.Builder<BuildRule> builder = ImmutableSet.builder();
  private final SourcePathRuleFinder ruleFinder;

  public DepsBuilder(SourcePathRuleFinder ruleFinder) {
    this.ruleFinder = ruleFinder;
  }

  public ImmutableSet<BuildRule> build() {
    return builder.build();
  }

  public DepsBuilder add(CxxSource source) {
    return add(source.getPath());
  }

  public DepsBuilder add(SourcePath sourcePath) {
    builder.addAll(ruleFinder.filterBuildRuleInputs(sourcePath));
    return this;
  }

  public DepsBuilder add(BuildRule buildRule) {
    builder.add(buildRule);
    return this;
  }

  public DepsBuilder add(PreprocessorDelegate delegate) {
    for (Arg arg : delegate.getPreprocessorFlags().getOtherFlags().getSrcFlags()) {
      builder.addAll(BuildableSupport.getDepsCollection(arg, ruleFinder));
    }
    return this;
  }

  public DepsBuilder add(CompilerDelegate delegate) {
    builder.addAll(delegate.getDeps(ruleFinder));
    return this;
  }
}

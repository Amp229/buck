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

package com.facebook.buck.core.rules.attr;

import com.facebook.buck.core.model.OutputLabel;
import com.facebook.buck.core.rules.BuildRule;
import com.facebook.buck.core.sourcepath.SourcePath;
import com.google.common.collect.ImmutableSortedSet;
import javax.annotation.Nullable;

/** BuildRules which supports supplementary outputs. */
public interface HasSupplementaryOutputs extends BuildRule, HasMultipleOutputs {

  /** Returns a SourcePath to a named supplementary output, or null if it does not exist. */
  @Nullable
  SourcePath getSourcePathToSupplementaryOutput(String name);

  @Override
  @Nullable
  default ImmutableSortedSet<SourcePath> getSourcePathToOutput(OutputLabel outputLabel) {
    if (outputLabel.isDefault()) {
      return ImmutableSortedSet.of(getSourcePathToOutput());
    } else {
      SourcePath path = getSourcePathToSupplementaryOutput(outputLabel.toString());
      if (path != null) {
        return ImmutableSortedSet.of(path);
      }
      return null;
    }
  }
}

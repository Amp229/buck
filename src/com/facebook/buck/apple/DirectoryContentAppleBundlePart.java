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

package com.facebook.buck.apple;

import static com.facebook.buck.core.util.Optionals.compare;

import com.facebook.buck.core.rulekey.AddToRuleKey;
import com.facebook.buck.core.sourcepath.SourcePath;
import com.facebook.buck.core.util.immutables.BuckStyleValue;
import java.util.Optional;

/** Directory which content is copied to specific subdirectory in bundle. */
@BuckStyleValue
public abstract class DirectoryContentAppleBundlePart extends AppleBundlePart
    implements Comparable<DirectoryContentAppleBundlePart> {

  @Override
  @AddToRuleKey
  public abstract SourcePath getSourcePath();

  @Override
  @AddToRuleKey
  public abstract AppleBundleDestination getDestination();

  @AddToRuleKey
  public abstract Optional<SourcePath> getHashesMapSourcePath();

  public static DirectoryContentAppleBundlePart of(
      SourcePath sourcePath,
      AppleBundleDestination destination,
      Optional<SourcePath> maybeContentHashSourcePath) {
    return ImmutableDirectoryContentAppleBundlePart.ofImpl(
        sourcePath, destination, maybeContentHashSourcePath);
  }

  @Override
  public int compareTo(DirectoryContentAppleBundlePart o) {
    if (getHashesMapSourcePath() != o.getHashesMapSourcePath()) {
      return compare(getHashesMapSourcePath(), o.getHashesMapSourcePath());
    }
    return super.compareTo(o);
  }
}

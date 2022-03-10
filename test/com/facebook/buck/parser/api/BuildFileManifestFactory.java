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

package com.facebook.buck.parser.api;

import com.facebook.buck.parser.exceptions.ParsingError;
import com.facebook.buck.skylark.io.GlobSpecWithResult;
import com.facebook.buck.util.collect.TwoArraysImmutableHashMap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSortedSet;

public class BuildFileManifestFactory {

  private BuildFileManifestFactory() {}

  public static BuildFileManifest create(ImmutableMap<String, RawTargetNode> targets) {
    return create(
        targets,
        ImmutableSortedSet.of(),
        ImmutableMap.of(),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static BuildFileManifest create(
      ImmutableMap<String, RawTargetNode> targets,
      ImmutableSortedSet<String> includes,
      ImmutableMap<String, Object> configs,
      ImmutableList<GlobSpecWithResult> globManifest,
      ImmutableList<ParsingError> errors) {
    return BuildFileManifest.of(
        TwoArraysImmutableHashMap.copyOf(targets), includes, configs, globManifest, errors);
  }
}

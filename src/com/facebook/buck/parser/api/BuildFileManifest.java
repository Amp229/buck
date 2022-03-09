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

import com.facebook.buck.core.graph.transformation.model.ComputeResult;
import com.facebook.buck.core.util.immutables.BuckStyleValue;
import com.facebook.buck.parser.exceptions.ParsingError;
import com.facebook.buck.skylark.io.GlobSpecWithResult;
import com.facebook.buck.util.collect.TwoArraysImmutableHashMap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

/** Describes the content of a build file, which includes defined targets and their metadata. */
@BuckStyleValue
public abstract class BuildFileManifest implements ComputeResult, FileManifest {
  /** @return a list of targets defined in the build file. */
  public abstract TwoArraysImmutableHashMap<String, RawTargetNode> getTargets();

  @Override
  public abstract ImmutableSet<String> getIncludes();

  @Override
  public abstract ImmutableMap<String, Object> getReadConfigurationOptionsForTest();

  /** @return A list of the glob operations performed with their results. */
  public abstract ImmutableList<GlobSpecWithResult> getGlobManifest();

  @Override
  public abstract ImmutableList<ParsingError> getErrors();

  public static BuildFileManifest of(
      TwoArraysImmutableHashMap<String, RawTargetNode> targets,
      ImmutableSet<String> includes,
      ImmutableMap<String, Object> configs,
      ImmutableList<GlobSpecWithResult> globManifest,
      ImmutableList<ParsingError> errors) {
    return ImmutableBuildFileManifest.ofImpl(targets, includes, configs, globManifest, errors);
  }
}

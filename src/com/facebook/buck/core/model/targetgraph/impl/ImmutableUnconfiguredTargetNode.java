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

package com.facebook.buck.core.model.targetgraph.impl;

import com.facebook.buck.core.model.RuleType;
import com.facebook.buck.core.model.UnflavoredBuildTarget;
import com.facebook.buck.core.model.targetgraph.raw.UnconfiguredTargetNode;
import com.facebook.buck.core.util.immutables.BuckStylePrehashedValue;
import com.facebook.buck.rules.param.ParamName;
import com.facebook.buck.rules.visibility.VisibilityPattern;
import com.facebook.buck.util.collect.TwoArraysImmutableHashMap;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import java.util.Map;
import java.util.Optional;

/** Immutable implementation of {@link UnconfiguredTargetNode}. */
@BuckStylePrehashedValue
@JsonDeserialize
public abstract class ImmutableUnconfiguredTargetNode implements UnconfiguredTargetNode {
  @Override
  @JsonProperty("buildTarget")
  public abstract UnflavoredBuildTarget getBuildTarget();

  @Override
  @JsonProperty("ruleType")
  public abstract RuleType getRuleType();

  @Override
  @JsonProperty("attributes")
  public abstract TwoArraysImmutableHashMap<ParamName, Object> getAttributes();

  // Visibility patterns might not really serialize/deserialize well
  // TODO: should we move them out of UnconfiguredTargetNode to TargetNode ?

  @Override
  @JsonProperty("visibilityPatterns")
  public abstract ImmutableSet<VisibilityPattern> getVisibilityPatterns();

  @Override
  @JsonProperty("withinViewPatterns")
  public abstract ImmutableSet<VisibilityPattern> getWithinViewPatterns();

  @Override
  @JsonProperty("defaultTargetPlatform")
  public abstract Optional<UnflavoredBuildTarget> getDefaultTargetPlatform();

  @Override
  @JsonProperty("defaultHostPlatform")
  public abstract Optional<UnflavoredBuildTarget> getDefaultHostPlatform();

  @Override
  @JsonProperty("compatibleWith")
  public abstract ImmutableList<UnflavoredBuildTarget> getCompatibleWith();

  public static UnconfiguredTargetNode of(
      UnflavoredBuildTarget buildTarget,
      RuleType ruleType,
      TwoArraysImmutableHashMap<ParamName, Object> attributes,
      ImmutableSet<VisibilityPattern> visibilityPatterns,
      ImmutableSet<VisibilityPattern> withinViewPatterns,
      Optional<UnflavoredBuildTarget> defaultTargetPlatform,
      Optional<UnflavoredBuildTarget> defaultHostPlatform,
      ImmutableList<UnflavoredBuildTarget> compatibleWith) {
    return ImmutableImmutableUnconfiguredTargetNode.ofImpl(
        buildTarget,
        ruleType,
        attributes,
        visibilityPatterns,
        withinViewPatterns,
        defaultTargetPlatform,
        defaultHostPlatform,
        compatibleWith);
  }

  /** Kill me. */
  // TODO: kill me
  public static UnconfiguredTargetNode of(
      UnflavoredBuildTarget buildTarget,
      RuleType ruleType,
      ImmutableMap<String, Object> attributes,
      ImmutableSet<VisibilityPattern> visibilityPatterns,
      ImmutableSet<VisibilityPattern> withinViewPatterns,
      Optional<UnflavoredBuildTarget> defaultTargetPlatform,
      Optional<UnflavoredBuildTarget> defaultHostPlatform,
      ImmutableList<UnflavoredBuildTarget> compatibleWith) {
    return of(
        buildTarget,
        ruleType,
        attributes.entrySet().stream()
            .collect(
                TwoArraysImmutableHashMap.toMap(
                    e -> ParamName.bySnakeCase(e.getKey()), Map.Entry::getValue)),
        visibilityPatterns,
        withinViewPatterns,
        defaultTargetPlatform,
        defaultHostPlatform,
        compatibleWith);
  }
}

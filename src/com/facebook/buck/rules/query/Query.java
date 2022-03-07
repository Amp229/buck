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

package com.facebook.buck.rules.query;

import com.facebook.buck.core.model.BaseName;
import com.facebook.buck.core.model.BuildTarget;
import com.facebook.buck.core.model.TargetConfiguration;
import com.facebook.buck.core.util.immutables.BuckStyleValue;
import com.google.common.collect.ComparisonChain;
import com.google.common.collect.ImmutableSortedSet;
import javax.annotation.Nullable;
import org.immutables.value.Value;

@BuckStyleValue
public abstract class Query implements Comparable<Query> {

  public abstract String getQuery();

  public abstract TargetConfiguration getTargetConfiguration();

  public abstract BaseName getBaseName();

  @Override
  public int compareTo(Query o) {
    return ComparisonChain.start()
        .compare(getQuery(), o.getQuery())
        .compare(getTargetConfiguration(), o.getTargetConfiguration())
        .compare(getBaseName(), o.getBaseName())
        .result();
  }

  @Nullable
  @Value.NaturalOrder
  public abstract ImmutableSortedSet<BuildTarget> getResolvedQuery();

  public static Query of(String query, TargetConfiguration targetConfiguration, BaseName baseName) {
    return ImmutableQuery.ofImpl(query, targetConfiguration, baseName, null);
  }

  public Query withQuery(String query) {
    return ImmutableQuery.ofImpl(
        query, getTargetConfiguration(), getBaseName(), getResolvedQuery());
  }

  public Query withResolvedQuery(ImmutableSortedSet<BuildTarget> resolveDepQuery) {
    return ImmutableQuery.ofImpl(
        getQuery(), getTargetConfiguration(), getBaseName(), resolveDepQuery);
  }
}

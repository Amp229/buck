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

package com.facebook.buck.core.select;

import com.facebook.buck.core.config.BuckConfig;
import com.facebook.buck.core.exceptions.DependencyStack;
import com.facebook.buck.core.model.platform.Platform;
import com.facebook.buck.core.util.immutables.BuckStyleValue;
import com.google.common.collect.ImmutableList;

/** Selectable-like object which matches any of contained selectables. */
@BuckStyleValue
public abstract class AnySelectable {
  public abstract ImmutableList<ConfigSettingSelectable> getSelectables();

  /** @return <code>true</code> if this condition matches the platform. */
  public boolean matchesPlatform(
      Platform platform, BuckConfig buckConfig, DependencyStack dependencyStack) {

    // Optimization
    if (this == any()) {
      return true;
    }

    return getSelectables().stream()
        .anyMatch(s -> s.matchesPlatform(platform, buckConfig, dependencyStack));
  }

  /** Constructor. */
  public static AnySelectable of(ImmutableList<ConfigSettingSelectable> selectables) {
    return ImmutableAnySelectable.ofImpl(selectables);
  }

  private static class AnyHolder {
    private static final AnySelectable ANY =
        ImmutableAnySelectable.ofImpl(ImmutableList.of(ConfigSettingSelectable.any()));
    private static final AnySelectable NONE = ImmutableAnySelectable.ofImpl(ImmutableList.of());
  }

  public static AnySelectable any() {
    return AnyHolder.ANY;
  }

  public static AnySelectable none() {
    return AnyHolder.NONE;
  }
}

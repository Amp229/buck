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
import com.facebook.buck.core.model.platform.Platform;
import com.facebook.buck.core.util.immutables.BuckStyleValue;

/**
 * Contains context that can be accessed by {@link Selectable} to get information about the current
 * configuration.
 */
@BuckStyleValue
public abstract class SelectableConfigurationContext {

  public abstract BuckConfig getBuckConfig();

  public abstract Platform getPlatform();

  public SelectableConfigurationContext withPlatform(Platform platform) {
    return of(getBuckConfig(), platform);
  }

  public static SelectableConfigurationContext of(BuckConfig buckConfig, Platform platform) {
    return ImmutableSelectableConfigurationContext.ofImpl(buckConfig, platform);
  }
}

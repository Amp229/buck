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

import com.facebook.buck.core.config.FakeBuckConfig;
import com.facebook.buck.core.model.ConfigurationBuildTargetFactoryForTests;
import com.facebook.buck.core.model.platform.ConstraintSetting;
import com.facebook.buck.core.model.platform.ConstraintValue;
import com.facebook.buck.core.model.platform.Platform;
import com.facebook.buck.core.model.platform.impl.ConstraintBasedPlatform;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

public class TestSelectables {

  public static ConstraintSetting constraintSetting(String target) {
    return ConstraintSetting.of(ConfigurationBuildTargetFactoryForTests.newInstance(target));
  }

  public static ConstraintValue constraintValue(
      String target, ConstraintSetting constraintSetting) {
    return ConstraintValue.of(
        ConfigurationBuildTargetFactoryForTests.newInstance(target), constraintSetting);
  }

  public static ConstraintValue constraintValue(String target, String constraintSetting) {
    return constraintValue(target, constraintSetting(constraintSetting));
  }

  public static ConfigSettingSelectable configSetting(ConstraintValue... constraintValues) {
    return ConfigSettingSelectable.of(ImmutableMap.of(), ImmutableSet.copyOf(constraintValues));
  }

  public static SelectableConfigurationContext selectableConfigurationContext(
      ConstraintValue... constraintValues) {
    Platform platform = platform(constraintValues);
    return SelectableConfigurationContext.of(FakeBuckConfig.empty(), platform);
  }

  public static Platform platform(ConstraintValue... constraintValues) {
    return new ConstraintBasedPlatform(
        ConfigurationBuildTargetFactoryForTests.newInstance("//:p"),
        ImmutableSet.copyOf(constraintValues));
  }
}

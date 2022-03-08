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

package com.facebook.buck.core.rules.configsetting;

import com.facebook.buck.core.model.BuildTarget;
import com.facebook.buck.core.rules.config.AbstractConfigurationRule;
import com.facebook.buck.core.rules.platform.ConstraintValueRule;
import com.facebook.buck.core.select.BuckConfigKey;
import com.facebook.buck.core.select.ConfigSettingSelectable;
import com.facebook.buck.core.select.ProvidesSelectable;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

/** {@code config_setting} rule. */
public class ConfigSettingRule extends AbstractConfigurationRule implements ProvidesSelectable {

  private final ConfigSettingSelectable configSettingSelectable;

  public ConfigSettingRule(
      BuildTarget buildTarget,
      ImmutableMap<BuckConfigKey, String> values,
      ImmutableSet<ConstraintValueRule> constraintValueRules) {
    super(buildTarget);
    this.configSettingSelectable =
        ConfigSettingSelectable.of(
            values,
            constraintValueRules.stream()
                .map(ConstraintValueRule::getConstraintValue)
                .collect(ImmutableSet.toImmutableSet()));
  }

  @Override
  public ConfigSettingSelectable getSelectable() {
    return configSettingSelectable;
  }
}

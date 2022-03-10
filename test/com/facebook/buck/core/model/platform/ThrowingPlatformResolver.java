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

package com.facebook.buck.core.model.platform;

import com.facebook.buck.core.exceptions.DependencyStack;
import com.facebook.buck.core.model.TargetConfiguration;
import com.facebook.buck.core.model.UnconfiguredTargetConfiguration;
import com.facebook.buck.core.model.platform.impl.UnconfiguredPlatform;

/** Platform resolver for tests. */
public class ThrowingPlatformResolver implements TargetPlatformResolver {

  /** Unconditionally throw */
  @Override
  public Platform getTargetPlatform(
      TargetConfiguration targetConfiguration, DependencyStack dependencyStack) {
    if (targetConfiguration instanceof UnconfiguredTargetConfiguration) {
      return UnconfiguredPlatform.INSTANCE;
    }

    throw new UnsupportedOperationException(
        "attempt to resolve configuration: " + targetConfiguration);
  }
}

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

package com.facebook.buck.step.fs;

import com.facebook.buck.core.build.execution.context.StepExecutionContext;
import com.facebook.buck.core.filesystems.RelPath;
import com.facebook.buck.core.util.immutables.BuckStyleValue;
import com.facebook.buck.io.filesystem.BuildCellRelativePath;
import com.facebook.buck.step.isolatedsteps.common.MkdirIsolatedStep;
import com.google.common.annotations.VisibleForTesting;
import org.immutables.value.Value;

/** Command that runs equivalent command of {@code mkdir -p} on the specified directory. */
@BuckStyleValue
public abstract class MkdirStep extends DelegateStep<MkdirIsolatedStep> {

  abstract BuildCellRelativePath getPath();

  @VisibleForTesting
  @Value.Derived
  public RelPath getPathRelativeToBuildCellRoot() {
    return getPath().getPathRelativeToBuildCellRoot();
  }

  @Override
  protected String getShortNameSuffix() {
    return "mkdir";
  }

  @Override
  protected MkdirIsolatedStep createDelegate(StepExecutionContext context) {
    return MkdirIsolatedStep.of(toCellRootRelativePath(context, getPath()));
  }

  public static MkdirStep of(BuildCellRelativePath path) {
    return ImmutableMkdirStep.ofImpl(path);
  }
}

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

package com.facebook.buck.shell;

import com.facebook.buck.core.build.execution.context.StepExecutionContext;
import com.facebook.buck.core.filesystems.AbsPath;
import com.facebook.buck.util.environment.Platform;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public class DefaultShellStep extends ShellStep {

  private ImmutableMap<String, String> environment;
  private ImmutableList<String> args;

  public DefaultShellStep(
      AbsPath workingDirectory,
      boolean withDownwardApi,
      List<String> args,
      Map<String, String> environment) {
    this(workingDirectory.getPath(), withDownwardApi, args, environment);
  }

  public DefaultShellStep(
      Path workingDirectory,
      boolean withDownwardApi,
      List<String> args,
      Map<String, String> environment) {
    super(workingDirectory, withDownwardApi);
    this.args = ImmutableList.copyOf(args);
    this.environment = ImmutableMap.copyOf(environment);
  }

  public DefaultShellStep(Path workingDirectory, boolean withDownwardApi, List<String> args) {
    this(workingDirectory, withDownwardApi, args, ImmutableMap.of());
  }

  public DefaultShellStep(AbsPath workingDirectory, boolean withDownwardApi, List<String> args) {
    this(workingDirectory.getPath(), withDownwardApi, args);
  }

  @Override
  public String getShortName() {
    return args.get(0);
  }

  @Override
  protected ImmutableList<String> getShellCommandInternal(StepExecutionContext context) {
    return args;
  }

  @Override
  public ImmutableMap<String, String> getEnvironmentVariables(Platform platform) {
    return environment;
  }
}

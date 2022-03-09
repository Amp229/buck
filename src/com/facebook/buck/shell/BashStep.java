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

import com.facebook.buck.core.build.execution.context.IsolatedExecutionContext;
import com.facebook.buck.core.filesystems.AbsPath;
import com.facebook.buck.core.filesystems.RelPath;
import com.facebook.buck.step.isolatedsteps.shell.IsolatedShellStep;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import java.nio.file.Path;

/**
 * Command that makes it possible to run an arbitrary command in Bash. Whenever possible, a more
 * specific subclass of {@link IsolatedShellStep} should be preferred. BashCommand should be
 * reserved for cases where the expressiveness of Bash (often in the form of *-shell-expansion)
 * makes the command considerably easier to implement.
 */
public class BashStep extends IsolatedShellStep {

  private final String bashCommand;

  /**
   * @param bashCommand command to execute. For convenience, multiple arguments are supported and
   *     will be joined with space characters if more than one is present.
   */
  public BashStep(
      Path workingDirectory, RelPath cellPath, boolean withDownwardApi, String... bashCommand) {
    super(workingDirectory, cellPath, withDownwardApi);
    this.bashCommand = Joiner.on(' ').join(bashCommand);
  }

  public BashStep(
      AbsPath workingDirectory, RelPath cellPath, boolean withDownwardApi, String... bashCommand) {
    this(workingDirectory.getPath(), cellPath, withDownwardApi, bashCommand);
  }

  @Override
  public String getShortName() {
    return "bash";
  }

  @Override
  protected ImmutableList<String> getShellCommandInternal(IsolatedExecutionContext context) {
    return ImmutableList.of("bash", "-c", bashCommand);
  }
}

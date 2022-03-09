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

package com.facebook.buck.intellij.ideabuck.build;

import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import java.util.function.Consumer;
import javax.annotation.Nullable;

public class ResultCallbackBuckHandler extends BuckCommandHandler {
  private final Consumer<String> futureCallback;

  @Nullable private Runnable onBuckDaemonBusyCallback;
  private StringBuilder stdout;

  /** @deprecated Use {@link ResultCallbackBuckHandler(Project, BuckCommand, Consumer<String>)}. */
  @Deprecated
  public ResultCallbackBuckHandler(
      final Project project,
      final VirtualFile root,
      final BuckCommand command,
      final Consumer<String> futureCallback) {
    super(project, VfsUtil.virtualToIoFile(root), command, true);
    this.futureCallback = futureCallback;
    this.stdout = new StringBuilder();
  }

  public ResultCallbackBuckHandler(
      Project project, BuckCommand command, Consumer<String> futureCallback) {
    super(project, command, true);
    this.futureCallback = futureCallback;
    this.stdout = new StringBuilder();
  }

  public void setOnBuckDaemonBusyCallback(@Nullable Runnable onBuckDaemonBusyCallback) {
    this.onBuckDaemonBusyCallback = onBuckDaemonBusyCallback;
  }

  @Override
  protected void notifyLines(Key outputType, Iterable<String> lines) {
    super.notifyLines(outputType, lines);
    if (outputType == ProcessOutputTypes.STDOUT) {
      for (String line : lines) {
        stdout.append(line);
      }
    }
  }

  @Override
  protected boolean beforeCommand() {
    return true;
  }

  @Override
  protected void afterCommand() {
    if (!isCancelled()) {
      futureCallback.accept(stdout.toString());
    }
  }

  @Override
  protected void onBuckDaemonBusy() {
    if (onBuckDaemonBusyCallback != null) {
      onBuckDaemonBusyCallback.run();
    }
  }
}

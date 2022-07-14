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

package com.facebook.buck.intellij.ideabuck.configurations;

import com.facebook.buck.intellij.ideabuck.build.BuckBuildCommandHandler;
import com.facebook.buck.intellij.ideabuck.build.BuckCommand;
import com.facebook.buck.intellij.ideabuck.config.BuckModule;
import com.intellij.execution.DefaultExecutionResult;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.ExecutionResult;
import com.intellij.execution.Executor;
import com.intellij.execution.filters.TextConsoleBuilderFactory;
import com.intellij.execution.process.OSProcessHandler;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

class BuckRunExecutionState extends AbstractExecutionState<BuckRunConfiguration> {

  public BuckRunExecutionState(BuckRunConfiguration configuration, Project project) {
    super(configuration, project);
  }

  @Nullable
  @Override
  public ExecutionResult execute(Executor executor, @NotNull ProgramRunner runner)
      throws ExecutionException {
    ProcessHandler processHandler = runBuildCommand();
    ConsoleView console =
        TextConsoleBuilderFactory.getInstance().createBuilder(mProject).getConsole();
    console.attachToProcess(processHandler);
    return new DefaultExecutionResult(console, processHandler, AnAction.EMPTY_ARRAY);
  }

  private ProcessHandler runBuildCommand() {
    BuckModule buckModule = BuckModule.getInstance(mProject);
    String targets = mConfiguration.data.targets;
    String additionalParams = mConfiguration.data.additionalParams;
    String buckExecutablePath = mConfiguration.data.buckExecutablePath;
    String title = "Buck Run " + targets;

    buckModule.attach(targets);

    BuckBuildCommandHandler handler =
        new BuckBuildCommandHandler(
            mProject, BuckCommand.RUN, /* doStartNotify */ false, buckExecutablePath);
    if (!targets.isEmpty()) {
      handler.command().addParameters(parseParamsIntoList(targets));
    }
    if (!additionalParams.isEmpty()) {
      handler.command().addParameters(parseParamsIntoList(additionalParams));
    }
    handler.start();
    OSProcessHandler result = handler.getHandler();
    openBuckToolWindowPostExecution(result, title);
    return result;
  }
}

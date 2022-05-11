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

package com.facebook.buck.jvm.java.stepsbuilder.javacd.main;

import com.facebook.buck.core.build.execution.context.IsolatedExecutionContext;
import com.facebook.buck.core.build.execution.context.actionid.ActionId;
import com.facebook.buck.core.exceptions.HumanReadableException;
import com.facebook.buck.core.filesystems.AbsPath;
import com.facebook.buck.core.util.log.Logger;
import com.facebook.buck.downward.model.EventTypeMessage;
import com.facebook.buck.downward.model.EventTypeMessage.EventType;
import com.facebook.buck.downward.model.PipelineFinishedEvent;
import com.facebook.buck.downward.model.ResultEvent;
import com.facebook.buck.downwardapi.protocol.DownwardProtocol;
import com.facebook.buck.event.IsolatedEventBus;
import com.facebook.buck.step.StepExecutionResult;
import com.facebook.buck.step.isolatedsteps.IsolatedStep;
import com.facebook.buck.step.isolatedsteps.IsolatedStepsRunner;
import com.facebook.buck.util.ClassLoaderCache;
import com.facebook.buck.util.Console;
import com.facebook.buck.util.ProcessExecutor;
import com.facebook.buck.util.environment.Platform;
import com.facebook.buck.util.timing.Clock;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.protobuf.AbstractMessage;
import java.io.IOException;
import java.io.OutputStream;
import javax.annotation.Nonnull;

/** Common methods used by java and pipelining java command executors */
class StepExecutionUtils {

  private static final Logger LOG = Logger.get(StepExecutionUtils.class);

  private StepExecutionUtils() {}

  static StepExecutionResult executeSteps(
      ImmutableList<IsolatedStep> steps, IsolatedExecutionContext executionContext) {
    return IsolatedStepsRunner.executeWithDefaultExceptionHandling(steps, executionContext);
  }

  @Nonnull
  static IsolatedExecutionContext createExecutionContext(
      ClassLoaderCache classLoaderCache,
      IsolatedEventBus eventBus,
      Platform platform,
      ProcessExecutor processExecutor,
      Console console,
      Clock clock,
      ActionId actionId,
      AbsPath ruleCellRoot) {
    return IsolatedExecutionContext.of(
        classLoaderCache,
        eventBus,
        console,
        platform,
        processExecutor,
        ruleCellRoot,
        actionId,
        clock);
  }

  static ResultEvent getResultEvent(ActionId actionId, StepExecutionResult stepExecutionResult) {
    int exitCode = stepExecutionResult.getExitCode();
    ResultEvent.Builder resultEventBuilder =
        ResultEvent.newBuilder().setActionId(actionId.getValue()).setExitCode(exitCode);
    if (!stepExecutionResult.isSuccess()) {
      StringBuilder errorMessage = new StringBuilder();
      stepExecutionResult
          .getStderr()
          .ifPresent(
              stdErr ->
                  errorMessage.append("Std err: ").append(stdErr).append(System.lineSeparator()));
      stepExecutionResult
          .getCause()
          .ifPresent(
              cause -> {
                LOG.warn(cause, "%s failed with an exception.", actionId);
                String error;
                if (cause instanceof HumanReadableException) {
                  error = ((HumanReadableException) cause).getHumanReadableErrorMessage();
                } else {
                  error = Throwables.getStackTraceAsString(cause);
                }
                errorMessage.append("Cause: ").append(error);
              });
      if (errorMessage.length() > 0) {
        resultEventBuilder.setMessage(errorMessage.toString());
      }
    }

    return resultEventBuilder.build();
  }

  static void writeResultEvent(
      DownwardProtocol downwardProtocol, OutputStream eventsOutputStream, ResultEvent resultEvent)
      throws IOException {
    writeEvent(EventType.RESULT_EVENT, resultEvent, eventsOutputStream, downwardProtocol);
  }

  static void writePipelineFinishedEvent(
      DownwardProtocol downwardProtocol,
      OutputStream eventsOutputStream,
      ImmutableList<ActionId> actionIds)
      throws IOException {
    PipelineFinishedEvent.Builder pipelineFinishedEventBuilder = PipelineFinishedEvent.newBuilder();
    for (ActionId actionId : actionIds) {
      pipelineFinishedEventBuilder.addActionId(actionId.getValue());
    }
    writeEvent(
        EventType.PIPELINE_FINISHED_EVENT,
        pipelineFinishedEventBuilder.build(),
        eventsOutputStream,
        downwardProtocol);
  }

  private static void writeEvent(
      EventType eventType,
      AbstractMessage event,
      OutputStream eventsOutputStream,
      DownwardProtocol downwardProtocol)
      throws IOException {
    downwardProtocol.write(
        EventTypeMessage.newBuilder().setEventType(eventType).build(), event, eventsOutputStream);
  }

  static void sendResultEvent(
      StepExecutionResult stepExecutionResult,
      ActionId actionId,
      DownwardProtocol downwardProtocol,
      OutputStream eventsOutputStream)
      throws IOException {
    ResultEvent resultEvent = getResultEvent(actionId, stepExecutionResult);
    writeResultEvent(downwardProtocol, eventsOutputStream, resultEvent);
  }
}

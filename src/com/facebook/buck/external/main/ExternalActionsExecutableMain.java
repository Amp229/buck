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

package com.facebook.buck.external.main;

import com.facebook.buck.core.build.execution.context.IsolatedExecutionContext;
import com.facebook.buck.core.build.execution.context.actionid.ActionId;
import com.facebook.buck.core.util.log.Logger;
import com.facebook.buck.downwardapi.protocol.DownwardProtocol;
import com.facebook.buck.downwardapi.protocol.DownwardProtocolType;
import com.facebook.buck.event.IsolatedEventBus;
import com.facebook.buck.event.isolated.DefaultIsolatedEventBus;
import com.facebook.buck.external.log.ExternalLogHandler;
import com.facebook.buck.external.model.ExternalAction;
import com.facebook.buck.external.model.ParsedArgs;
import com.facebook.buck.external.parser.ExternalArgsParser;
import com.facebook.buck.external.parser.ParsedEnvVars;
import com.facebook.buck.external.utils.BuildStepsRetriever;
import com.facebook.buck.io.namedpipes.NamedPipeFactory;
import com.facebook.buck.io.namedpipes.NamedPipeWriter;
import com.facebook.buck.step.StepExecutionResult;
import com.facebook.buck.step.isolatedsteps.IsolatedStep;
import com.facebook.buck.step.isolatedsteps.IsolatedStepsRunner;
import com.facebook.buck.util.Ansi;
import com.facebook.buck.util.Console;
import com.facebook.buck.util.DefaultProcessExecutor;
import com.facebook.buck.util.ErrorLogger;
import com.facebook.buck.util.environment.EnvVariablesProvider;
import com.facebook.buck.util.environment.Platform;
import com.facebook.buck.util.timing.Clock;
import com.facebook.buck.util.timing.DefaultClock;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import java.io.OutputStream;

/**
 * Main entry point for executing {@link ExternalAction} instances.
 *
 * <p>Expected usage: {@code this_binary <external_action_class_name> <buildable_command_path>}. See
 * {@link ExternalArgsParser}.
 *
 * <p>This binary also expects some environment variables to be written before execution. See {@link
 * ParsedEnvVars}.
 */
public class ExternalActionsExecutableMain {

  private static final NamedPipeFactory NAMED_PIPE_FACTORY = NamedPipeFactory.getFactory();
  private static final DownwardProtocolType DOWNWARD_PROTOCOL_TYPE = DownwardProtocolType.BINARY;
  private static final DownwardProtocol DOWNWARD_PROTOCOL =
      DOWNWARD_PROTOCOL_TYPE.getDownwardProtocol();

  /** Main entrypoint of actions that can be built in a separate process from buck. */
  public static void main(String[] args) {
    // Note that creating if expected environment variables are not present, this will throw a
    // runtime exception
    ParsedEnvVars parsedEnvVars = ParsedEnvVars.parse(EnvVariablesProvider.getSystemEnv());
    ActionId actionId = parsedEnvVars.getActionId();
    Console console = createConsole(parsedEnvVars);
    try (NamedPipeWriter namedPipe =
            NAMED_PIPE_FACTORY.connectAsWriter(parsedEnvVars.getEventPipe());
        OutputStream outputStream = namedPipe.getOutputStream()) {
      DOWNWARD_PROTOCOL_TYPE.writeDelimitedTo(outputStream);

      Logger logger = Logger.get("");
      logger.cleanHandlers();
      logger.addHandler(new ExternalLogHandler(outputStream, DOWNWARD_PROTOCOL));

      StepExecutionResult stepExecutionResult =
          executeSteps(args, parsedEnvVars, console, outputStream);
      if (!stepExecutionResult.isSuccess()) {
        handleExceptionAndTerminate(actionId, console, getErrorMessage(stepExecutionResult));
      }
    } catch (Exception e) {
      handleExceptionAndTerminate(actionId, console, e);
    }
    System.exit(0);
  }

  private static Console createConsole(ParsedEnvVars parsedEnvVars) {
    return new Console(
        parsedEnvVars.getVerbosity(),
        System.out,
        System.err,
        new Ansi(parsedEnvVars.isAnsiTerminal()));
  }

  private static void handleExceptionAndTerminate(
      ActionId actionId, Console console, Throwable throwable) {
    handleExceptionAndTerminate(actionId, console, ErrorLogger.getUserFriendlyMessage(throwable));
  }

  private static void handleExceptionAndTerminate(
      ActionId actionId, Console console, String errorMessage) {
    // Remove an existing `ExternalLogHandler` handler that depend on the closed event pipe stream.
    Logger logger = Logger.get("");
    logger.cleanHandlers();

    // this method logs the message with log.warn that would be noop as all logger handlers have
    // been cleaned and prints the message into a std err.
    console.printErrorText(
        "Failed to execute external action: "
            + actionId
            + ". Thread: "
            + Thread.currentThread()
            + System.lineSeparator()
            + errorMessage);
    System.exit(1);
  }

  private static String getErrorMessage(StepExecutionResult stepExecutionResult) {
    StringBuilder errorMessage = new StringBuilder();
    stepExecutionResult
        .getStderr()
        .ifPresent(
            stdErr ->
                errorMessage.append("Std err: ").append(stdErr).append(System.lineSeparator()));
    stepExecutionResult
        .getCause()
        .ifPresent(
            cause ->
                errorMessage.append("Cause: ").append(Throwables.getStackTraceAsString(cause)));
    return errorMessage.toString();
  }

  private static StepExecutionResult executeSteps(
      String[] args, ParsedEnvVars parsedEnvVars, Console console, OutputStream outputStream)
      throws Exception {
    ParsedArgs parsedArgs = new ExternalArgsParser().parse(args);
    ImmutableList<IsolatedStep> stepsToExecute =
        BuildStepsRetriever.getStepsForBuildable(parsedArgs);

    // no need to measure thread CPU time as this is an external process and we do not pass thread
    // time back to buck with Downward API
    Clock clock = new DefaultClock(false);
    ActionId actionId = parsedEnvVars.getActionId();
    try (IsolatedEventBus eventBus =
            new DefaultIsolatedEventBus(
                parsedEnvVars.getBuildUuid(), outputStream, clock, DOWNWARD_PROTOCOL, actionId);
        IsolatedExecutionContext executionContext =
            IsolatedExecutionContext.of(
                eventBus,
                console,
                Platform.detect(),
                new DefaultProcessExecutor(console),
                parsedEnvVars.getRuleCellRoot(),
                actionId,
                clock)) {
      return IsolatedStepsRunner.execute(stepsToExecute, executionContext);
    }
  }
}

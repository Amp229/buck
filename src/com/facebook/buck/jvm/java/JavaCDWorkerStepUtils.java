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

package com.facebook.buck.jvm.java;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.facebook.buck.core.build.execution.context.IsolatedExecutionContext;
import com.facebook.buck.core.build.execution.context.actionid.ActionId;
import com.facebook.buck.core.filesystems.AbsPath;
import com.facebook.buck.downward.model.ResultEvent;
import com.facebook.buck.jvm.java.stepsbuilder.params.JavaCDParams;
import com.facebook.buck.step.StepExecutionResult;
import com.facebook.buck.step.StepExecutionResults;
import com.facebook.buck.util.env.BuckClasspath;
import com.facebook.buck.util.environment.CommonChildProcessParams;
import com.facebook.buck.util.function.ThrowingSupplier;
import com.facebook.buck.util.java.JavaRuntimeUtils;
import com.facebook.buck.worker.WorkerProcessPool;
import com.facebook.buck.workertool.WorkerToolExecutor;
import com.facebook.buck.workertool.WorkerToolLauncher;
import com.facebook.buck.workertool.impl.DefaultWorkerToolLauncher;
import com.facebook.buck.workertool.impl.WorkerToolPoolFactory;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** Collection of constants/methods used in JavaCD worker tool steps. */
public class JavaCDWorkerStepUtils {

  public static final String JAVACD_MAIN_CLASS =
      "com.facebook.buck.jvm.java.stepsbuilder.javacd.main.JavaCDWorkerToolMain";

  private JavaCDWorkerStepUtils() {}

  /** Creates {@link StepExecutionResult} from received from javacd {@link ResultEvent} */
  public static StepExecutionResult createStepExecutionResult(
      ImmutableList<String> executedCommand, ResultEvent resultEvent, ActionId actionId) {
    int exitCode = resultEvent.getExitCode();
    StepExecutionResult.Builder builder =
        StepExecutionResult.builder().setExitCode(exitCode).setExecutedCommand(executedCommand);

    if (exitCode != 0) {
      builder.setStderr(
          String.format(
              "javacd action id: %s%n%s",
              actionId, resultEvent.getMessage().replace("\\n", System.lineSeparator())));
    }
    return builder.build();
  }

  /** Creates failed {@link StepExecutionResult} from the occurred {@link Exception} */
  public static StepExecutionResult createFailStepExecutionResult(
      ImmutableList<String> executedCommand, ActionId actionId, ExecutionException e) {
    Exception causeException;
    if (e.getCause() instanceof Exception) {
      causeException = (Exception) e.getCause();
    } else {
      causeException = e;
    }
    return StepExecutionResult.builder()
        .setExitCode(StepExecutionResults.ERROR_EXIT_CODE)
        .setExecutedCommand(executedCommand)
        .setStderr(
            String.format("ActionId: %s. Caused by: %s", actionId, causeException.getMessage()))
        .setCause(causeException)
        .build();
  }

  /** Creates failed {@link StepExecutionResult} from the occurred {@link TimeoutException} */
  public static StepExecutionResult createFailStepExecutionResult(
      ImmutableList<String> executedCommand, ActionId actionId, TimeoutException e) {
    return StepExecutionResult.builder()
        .setExitCode(StepExecutionResults.ERROR_EXIT_CODE)
        .setExecutedCommand(executedCommand)
        .setStderr(
            String.format(
                "ActionId: %s. Caused by timeout while waiting for the result event from javacd: %s",
                actionId, e.getMessage()))
        .setCause(e)
        .build();
  }

  /** Returns the startup command for launching javacd process. */
  public static ImmutableList<String> getLaunchJavaCDCommand(
      JavaCDParams javaCDParams, AbsPath ruleCellRoot) {
    ImmutableList<String> startCommandOptions = javaCDParams.getStartCommandOptions();
    ImmutableList<String> commonJvmParams =
        getCommonJvmParams(ruleCellRoot.resolve(javaCDParams.getLogDirectory()));

    String classpath =
        Objects.requireNonNull(
            BuckClasspath.getBuckBootstrapClasspathFromEnvVarOrNull(),
            BuckClasspath.BOOTSTRAP_ENV_VAR_NAME + " env variable is not set");
    ImmutableList<String> command =
        ImmutableList.of("-cp", classpath, BuckClasspath.BOOTSTRAP_MAIN_CLASS, JAVACD_MAIN_CLASS);

    return ImmutableList.<String>builderWithExpectedSize(
            1 + commonJvmParams.size() + startCommandOptions.size() + command.size())
        .add(JavaRuntimeUtils.getBucksJavaBinCommand())
        .addAll(commonJvmParams)
        .addAll(startCommandOptions)
        .addAll(command)
        .build();
  }

  /** Returns common jvm params for javacd */
  @VisibleForTesting
  public static ImmutableList<String> getCommonJvmParams(AbsPath logDirectory) {
    return ImmutableList.of(
        "-Dfile.encoding=" + UTF_8.name(),
        "-Djava.io.tmpdir=" + System.getProperty("java.io.tmpdir"),
        "-XX:+HeapDumpOnOutOfMemoryError",
        "-XX:HeapDumpPath=" + logDirectory.toString(),
        // Directs the VM to refrain from setting the file descriptor limit to the default maximum.
        // https://stackoverflow.com/a/16535804/5208808
        "-XX:-MaxFDLimit");
  }

  /** Returns {@link WorkerProcessPool.BorrowedWorkerProcess} from the passed pool. */
  public static WorkerProcessPool.BorrowedWorkerProcess<WorkerToolExecutor>
      borrowWorkerToolWithTimeout(
          WorkerProcessPool<WorkerToolExecutor> workerToolPool, int borrowFromPoolTimeoutInSeconds)
          throws InterruptedException {
    return workerToolPool
        .borrowWorkerProcess(borrowFromPoolTimeoutInSeconds, TimeUnit.SECONDS)
        .orElseThrow(
            () ->
                new IllegalStateException(
                    "Cannot get a worker tool from a pool of the size: "
                        + workerToolPool.getCapacity()
                        + ". Time out of "
                        + borrowFromPoolTimeoutInSeconds
                        + " seconds passed."));
  }

  /** Returns {@link WorkerProcessPool} created for the passed {@code command} */
  public static WorkerProcessPool<WorkerToolExecutor> getWorkerToolPool(
      IsolatedExecutionContext context,
      ImmutableList<String> startupCommand,
      JavaCDParams javaCDParams) {
    return WorkerToolPoolFactory.getPool(
        context,
        startupCommand,
        getLaunchWorkerSupplier(
            context, startupCommand, javaCDParams.isIncludeAllBucksEnvVariables()),
        javaCDParams.getWorkerToolPoolSize(),
        javaCDParams.getWorkerToolMaxInstancesSize());
  }

  /** Returns {@link WorkerToolExecutor} created for the passed {@code command} */
  public static WorkerToolExecutor getLaunchedWorker(
      IsolatedExecutionContext context,
      ImmutableList<String> startupCommand,
      boolean pathAllEnvVariablesFromBuckProcess)
      throws IOException {
    return getLaunchWorkerSupplier(context, startupCommand, pathAllEnvVariablesFromBuckProcess)
        .get();
  }

  private static ThrowingSupplier<WorkerToolExecutor, IOException> getLaunchWorkerSupplier(
      IsolatedExecutionContext context,
      ImmutableList<String> startupCommand,
      boolean includeBucksEnvVariables) {
    return () -> {
      WorkerToolLauncher workerToolLauncher = new DefaultWorkerToolLauncher(context);
      return workerToolLauncher.launchWorker(
          startupCommand,
          CommonChildProcessParams.getCommonChildProcessEnvsIncludingBuckClasspath(
              includeBucksEnvVariables));
    };
  }
}

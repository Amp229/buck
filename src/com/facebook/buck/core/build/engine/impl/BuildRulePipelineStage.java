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

package com.facebook.buck.core.build.engine.impl;

import com.facebook.buck.core.build.engine.BuildResult;
import com.facebook.buck.core.rules.pipeline.RulePipelineState;
import com.facebook.buck.core.rules.pipeline.StateHolder;
import com.google.common.base.Preconditions;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.SettableFuture;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.function.BiFunction;
import javax.annotation.Nullable;

/**
 * Creates and runs the steps for a single build rule within a pipeline, cascading any failures to
 * rules later in the pipeline.
 */
class BuildRulePipelineStage<State extends RulePipelineState>
    implements RunnableWithFuture<Optional<BuildResult>> {

  private final SettableFuture<Optional<BuildResult>> future;

  @Nullable private BuildRulePipelineStage<State> nextStage;
  @Nullable private Throwable error = null;

  @Nullable
  private BiFunction<StateHolder<State>, Boolean, RunnableWithFuture<Optional<BuildResult>>>
      ruleStepRunnerFactory;

  @Nullable private RunnableWithFuture<Optional<BuildResult>> runner;
  private boolean executed = false;

  BuildRulePipelineStage() {
    this.future = SettableFuture.create();
    Futures.addCallback(
        future,
        new FutureCallback<Optional<BuildResult>>() {
          @Override
          public void onSuccess(Optional<BuildResult> result) {}

          @Override
          public void onFailure(Throwable t) {
            error = t;
          }
        },
        MoreExecutors.directExecutor());
  }

  public void setRuleStepRunnerFactory(
      BiFunction<StateHolder<State>, Boolean, RunnableWithFuture<Optional<BuildResult>>>
          ruleStepRunnerFactory) {
    Preconditions.checkState(this.ruleStepRunnerFactory == null);
    this.ruleStepRunnerFactory = ruleStepRunnerFactory;
  }

  public void init(StateHolder<State> stateHolder, boolean isFirst) {
    Preconditions.checkState(runner == null);
    Preconditions.checkNotNull(ruleStepRunnerFactory);

    runner = ruleStepRunnerFactory.apply(stateHolder, isFirst);
    ruleStepRunnerFactory = null;
  }

  public void setNextStage(BuildRulePipelineStage<State> nextStage) {
    Preconditions.checkState(this.nextStage == null);
    this.nextStage = nextStage;
  }

  @Nullable
  public BuildRulePipelineStage<State> getNextStage() {
    return nextStage;
  }

  public boolean isReady() {
    return executed || runner != null;
  }

  public void waitForResult() {
    // For now there's no cancel (cuz it's not hooked up at all), but we can at least wait
    try {
      future.get();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    } catch (ExecutionException e) { // NOPMD
      // Ignore; the future is hooked up elsewhere and that location will handle the exceptions
    }
  }

  @Nullable
  public Throwable getError() {
    return error;
  }

  @Override
  public SettableFuture<Optional<BuildResult>> getFuture() {
    return future;
  }

  @Override
  public void run() {
    Preconditions.checkState(!executed);
    Preconditions.checkNotNull(runner);

    try {
      future.setFuture(runner.getFuture());
      runner.run();
    } finally {
      executed = true;
      runner = null;
    }
  }

  public void abort(Throwable error) {
    future.setException(error);
  }
}

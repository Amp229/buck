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

package com.facebook.buck.workertool;

import com.facebook.buck.core.build.execution.context.actionid.ActionId;
import com.facebook.buck.downward.model.PipelineFinishedEvent;
import com.facebook.buck.downward.model.ResultEvent;
import com.facebook.buck.worker.WorkerProcess;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.SettableFuture;
import com.google.protobuf.AbstractMessage;
import java.io.IOException;

/**
 * Interface for WorkerTool executor that implements that WTv2 protocol. It executes commands by
 * sending it to worker tool using created command's named pipe file.
 */
public interface WorkerToolExecutor extends WorkerProcess {

  /** Send an execution command to a worker tool instance and wait till the command executed. */
  SettableFuture<ResultEvent> executeCommand(
      ActionId actionId, AbstractMessage executeCommandMessage) throws IOException;

  /**
   * Send an execution pipelining command to a worker tool instance and immediately return a list of
   * result event's futures that represent the result.
   */
  ImmutableList<SettableFuture<ResultEvent>> executePipeliningCommand(
      ImmutableList<ActionId> actionIds,
      AbstractMessage executeCommandMessage,
      SettableFuture<PipelineFinishedEvent> pipelineFinished)
      throws IOException;

  /**
   * Sends a signal to worker tool that it could start execution the next pipelining command.
   *
   * @param actionId - action id of the next pipelining command.
   */
  void startNextCommand(AbstractMessage startNextPipeliningCommand, ActionId actionId)
      throws IOException;
}

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

package com.facebook.buck.core.rules.pipeline;

import com.facebook.buck.core.build.execution.context.actionid.ActionId;
import com.facebook.buck.step.Step;
import com.google.protobuf.AbstractMessage;

/**
 * Interface for compilation daemon step (step that interacts with compilation daemon process). Step
 * could contain multiple commands that would be scheduled for execution at once.
 */
public interface CompilationDaemonStep extends Step {

  /** Appends existing compilation step with a new protobuf command. */
  void appendStepWithCommand(ActionId actionId, AbstractMessage command);

  /**
   * Closes compilation step after it has been executed.
   *
   * @param force if is set then close compilation step without waiting for any events from a
   *     daemon.
   *     <p>
   *     <p>For example: In case compilation daemon uses worker tool pool, the used worker tool
   *     instance is returned back to the pool for future reuse.
   */
  void close(boolean force);
}

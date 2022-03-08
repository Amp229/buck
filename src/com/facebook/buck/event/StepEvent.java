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

package com.facebook.buck.event;

import com.facebook.buck.event.external.events.StepEventExternalInterface;
import com.google.common.base.Objects;

/** Base class for events about steps. */
public abstract class StepEvent extends AbstractBuckEvent
    implements LeafEvent, StepEventExternalInterface, WorkAdvanceEvent {

  private final String shortName;
  private final String description;

  protected StepEvent(String shortName, String description, EventKey eventKey) {
    super(eventKey);
    this.shortName = shortName;
    this.description = description;
  }

  @Override
  public String getShortStepName() {
    return shortName;
  }

  @Override
  public String getDescription() {
    return description;
  }

  @Override
  public String getCategory() {
    return getShortStepName();
  }

  @Override
  protected String getValueString() {
    return getShortStepName();
  }

  public static Started started(String shortName, String description) {
    return new Started(shortName, description);
  }

  public static Finished finished(Started started, int exitCode) {
    return new Finished(started, exitCode);
  }

  public static class Started extends StepEvent {
    protected Started(String shortName, String description) {
      super(shortName, description, EventKey.unique());
    }

    @Override
    public String getEventName() {
      return STEP_STARTED;
    }
  }

  public static class Finished extends StepEvent {
    private final int exitCode;

    protected Finished(Started started, int exitCode) {
      super(started.getShortStepName(), started.getDescription(), started.getEventKey());
      this.exitCode = exitCode;
    }

    public int getExitCode() {
      return exitCode;
    }

    @Override
    public String getEventName() {
      return STEP_FINISHED;
    }

    @Override
    public boolean equals(Object o) {
      if (!super.equals(o)) {
        return false;
      }
      // Because super.equals compares the EventKey, getting here means that we've somehow managed
      // to create 2 Finished events for the same Started event.
      throw new UnsupportedOperationException("Multiple conflicting Finished events detected.");
    }

    @Override
    public int hashCode() {
      return Objects.hashCode(super.hashCode(), exitCode);
    }
  }
}

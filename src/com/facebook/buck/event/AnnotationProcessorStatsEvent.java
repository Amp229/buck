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

/** Event for sending Annotation Processing perf stats from the compilation step to be logged. */
public class AnnotationProcessorStatsEvent extends AbstractBuckEvent {

  private final String invokingRule;
  private final AnnotationProcessorPerfStats data;

  public AnnotationProcessorStatsEvent(String invokingRule, AnnotationProcessorPerfStats data) {
    super(EventKey.unique());
    this.invokingRule = invokingRule;
    this.data = data;
  }

  public AnnotationProcessorPerfStats getData() {
    return data;
  }

  public String getInvokingRule() {
    return invokingRule;
  }

  @Override
  protected String getValueString() {
    return "apStats";
  }

  @Override
  public String getEventName() {
    return "AnnotationProcessorStatsEvent";
  }

  @Override
  public String toString() {
    return "AnnotationProcessorStatsEvent{"
        + "invokingRule="
        + invokingRule
        + ", data="
        + data
        + '}';
  }
}

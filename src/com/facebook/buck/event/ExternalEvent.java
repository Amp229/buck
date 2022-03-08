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

import com.google.common.collect.ImmutableMap;

/**
 * An {@link ExternalEvent} is an event that is intended to be propagated to consumers outside of
 * Buck (currently via the Websocket). Tools are able to create this event so that arbitrary data
 * can be propagated to any custom listener of the Websocket. Buck itself does not process these
 * events in any way, and is unaware of its data.
 *
 * <p>Ex. idea plugin could receive compilation error events from java compiler.
 */
public class ExternalEvent extends AbstractBuckEvent {

  private final ImmutableMap<String, String> data;

  public ExternalEvent(ImmutableMap<String, String> data) {
    super(EventKey.unique());
    this.data = data;
  }

  @Override
  protected String getValueString() {
    return data.toString();
  }

  @Override
  public String getEventName() {
    return getClass().getSimpleName();
  }

  public ImmutableMap<String, String> getData() {
    return data;
  }
}

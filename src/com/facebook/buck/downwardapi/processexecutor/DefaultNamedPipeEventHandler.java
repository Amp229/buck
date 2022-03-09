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

package com.facebook.buck.downwardapi.processexecutor;

import com.facebook.buck.downward.model.EventTypeMessage;
import com.facebook.buck.downwardapi.processexecutor.context.DownwardApiExecutionContext;
import com.facebook.buck.downwardapi.processexecutor.handlers.EventHandler;
import com.facebook.buck.downwardapi.processexecutor.handlers.impl.EventHandlerUtils;
import com.facebook.buck.io.namedpipes.NamedPipeReader;
import com.facebook.buck.util.NamedPipeEventHandler;
import com.facebook.buck.util.NamedPipeEventHandlerFactory;
import com.google.protobuf.AbstractMessage;

/** Default implementation of {@link NamedPipeEventHandler} interface. */
public class DefaultNamedPipeEventHandler extends BaseNamedPipeEventHandler {

  public static final NamedPipeEventHandlerFactory FACTORY = DefaultNamedPipeEventHandler::new;

  public DefaultNamedPipeEventHandler(
      NamedPipeReader namedPipeReader, DownwardApiExecutionContext context) {
    super(namedPipeReader, context);
  }

  @Override
  protected void processEvent(EventTypeMessage.EventType eventType, AbstractMessage event) {
    EventHandler<AbstractMessage> eventHandler =
        EventHandlerUtils.getStandardEventHandler(eventType);
    eventHandler.handleEvent(getContext(), event);
  }
}

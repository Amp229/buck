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

package com.facebook.buck.downwardapi.protocol;

import com.facebook.buck.downward.model.EventTypeMessage;
import com.google.protobuf.AbstractMessage;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import javax.annotation.Nullable;

/** Downward API Protocol interface. */
public interface DownwardProtocol {

  /** Writes {@code message} into {@code outputStream}. */
  void write(EventTypeMessage eventType, AbstractMessage message, OutputStream outputStream)
      throws IOException;

  /** Reads {@code EventTypeMessage.EventType} from {@code inputStream}. */
  @Nullable
  EventTypeMessage.EventType readEventType(InputStream inputStream) throws IOException;

  /** Reads event correspondent to {@code eventType} from {@code inputStream}. */
  @Nullable
  <T extends AbstractMessage> T readEvent(
      InputStream inputStream, EventTypeMessage.EventType eventType) throws IOException;

  /** Returns the type of this protocol. */
  DownwardProtocolType getProtocolType();

  /** Returns the name of this protocol. */
  default String getProtocolName() {
    return getProtocolType().name().toLowerCase();
  }
}

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

/**
 * Exception representing an invalid {@link
 * com.facebook.buck.downwardapi.protocol.DownwardProtocol}.
 */
public class InvalidDownwardProtocolException extends IllegalStateException {

  public InvalidDownwardProtocolException(String message) {
    super(message);
  }

  public InvalidDownwardProtocolException(String message, Throwable cause) {
    super(message, cause);
  }
}

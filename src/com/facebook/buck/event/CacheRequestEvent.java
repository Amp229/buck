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

import java.net.InetAddress;

/** BuckEvent passing network information about ArtifactCache requests */
public class CacheRequestEvent extends AbstractBuckEvent {

  private final InetAddress localAddress;

  public CacheRequestEvent(InetAddress localAddress) {
    super(EventKey.unique());
    this.localAddress = localAddress;
  }

  @Override
  protected String getValueString() {
    return localAddress.getHostAddress();
  }

  @Override
  public String getEventName() {
    return "CacheRequestEvent";
  }

  public InetAddress getLocalAddress() {
    return localAddress;
  }
}

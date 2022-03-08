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

package com.facebook.buck.io.namedpipes.windows.handle;

import com.sun.jna.platform.win32.WinNT;

/** Factory interface that creates {@link WindowsHandle}. */
@FunctionalInterface
public interface WindowsHandleFactory {

  /** Creates {@link WindowsHandle}. */
  WindowsHandle create(WinNT.HANDLE handle, String description);
}

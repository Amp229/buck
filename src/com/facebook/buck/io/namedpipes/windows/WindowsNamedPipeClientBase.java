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

package com.facebook.buck.io.namedpipes.windows;

import com.facebook.buck.io.namedpipes.BaseNamedPipe;
import com.facebook.buck.io.namedpipes.windows.handle.WindowsHandle;
import com.facebook.buck.io.namedpipes.windows.handle.WindowsHandleFactory;
import com.sun.jna.platform.win32.WinBase;
import java.io.IOException;
import java.nio.file.Path;
import java.util.function.Consumer;

/**
 * Windows name pipe that operates over passed named pipe path. Named pipe should have already been
 * created.
 *
 * <p>Ported from {@link com.facebook.nailgun.NGWin32NamedPipeSocket}.
 */
abstract class WindowsNamedPipeClientBase extends BaseNamedPipe {

  /**
   * Handle to the named pipe that this client communicates to. Either the handle from the server's
   * {@link WindowsNamedPipeLibrary#CreateNamedPipe(String, int, int, int, int, int, int,
   * WinBase.SECURITY_ATTRIBUTES)} or the handle from the client's Kernel32#CreateFile.
   */
  private final WindowsHandle handle;

  private final Consumer<WindowsHandle> closeCallback;
  private final WindowsHandleFactory windowsHandleFactory;
  private boolean isClosed = false;

  WindowsNamedPipeClientBase(
      Path path,
      WindowsHandle handle,
      Consumer<WindowsHandle> closeCallback,
      WindowsHandleFactory windowsHandleFactory) {
    super(path);
    this.handle = handle;
    this.closeCallback = closeCallback;
    this.windowsHandleFactory = windowsHandleFactory;
  }

  @Override
  public void close() throws IOException {
    closeCallback.accept(handle);
    isClosed = true;
  }

  @Override
  public boolean isClosed() {
    return isClosed;
  }

  protected WindowsHandle getNamedPipeHandle() {
    return handle;
  }

  protected WindowsHandleFactory getWindowsHandleFactory() {
    return windowsHandleFactory;
  }
}

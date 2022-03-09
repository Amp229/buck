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

package com.facebook.buck.intellij.ideabuck.icons;

import com.facebook.buck.intellij.ideabuck.lang.BuckFileType;
import com.intellij.openapi.util.IconLoader;
import javax.swing.Icon;

public final class BuckIcons {

  private BuckIcons() {}

  private static Icon load(String path) {
    return IconLoader.getIcon(path, BuckIcons.class);
  }

  public static final Icon DEFAULT_BUCK_ICON = BuckFileType.DEFAULT_ICON; // 16x16
  public static final Icon BUCK_TOOL_WINDOW_ICON =
      load("/icons/buck_tool_window_icon.png"); // 13x13
  public static final Icon ACTION_DEBUG = load("/icons/actions/Debug.png");
  public static final Icon ACTION_FIND = load("/icons/actions/Find.png");
  public static final Icon ACTION_INSTALL = load("/icons/actions/Install.png");
  public static final Icon ACTION_STOP = load("/icons/actions/Stop.png");
  public static final Icon ACTION_PROJECT = load("/icons/actions/Project.png");
  public static final Icon ACTION_RUN = load("/icons/actions/Run.png");
  public static final Icon ACTION_TEST = load("/icons/actions/Test.png");
  public static final Icon ACTION_UNINSTALL = load("/icons/actions/Uninstall.png");

  public static final Icon BUCK_RUN = load("/icons/runConfigurations/BuckRun.png");
  public static final Icon BUCK_DEBUG = load("/icons/runConfigurations/BuckDebug.png");
  public static final Icon BUCK_BUILD = load("/icons/runConfigurations/BuckBuild.png");
  public static final Icon BUCK_TEST = load("/icons/runConfigurations/BuckTest.png");
  public static final Icon BUCK_INSTALL = load("/icons/runConfigurations/BuckInstall.png");
}

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

package com.facebook.buck.android;

import com.facebook.buck.android.toolchain.AndroidPlatformTarget;
import com.facebook.buck.core.toolchain.tool.impl.testutil.SimpleTool;
import com.facebook.buck.core.toolchain.toolprovider.impl.ConstantToolProvider;
import com.google.common.collect.ImmutableList;
import java.nio.file.Paths;

public class AndroidTestUtils {

  /**
   * Keep this variable in sync with repo configured value. In case of change: update all {@code
   * .buckconfig} files in test resources. Search for a configuration like this:
   *
   * <pre>
   *  [ndk]
   *    ndk_version = 17
   * </pre>
   */
  public static final String TARGET_NDK_VERSION = "17";

  private AndroidTestUtils() {}

  static AndroidPlatformTarget createAndroidPlatformTarget() {
    return AndroidPlatformTarget.of(
        "android",
        /* androidJar= */ Paths.get(""),
        /* bootclasspathEntries= */ ImmutableList.of(),
        /* aaptExecutable= */ () -> new SimpleTool(""),
        /* aapt2ToolProvider= */ new ConstantToolProvider(new SimpleTool("")),
        /* adbExecutable= */ Paths.get(""),
        /* aidlExecutable= */ Paths.get(""),
        /* zipalignToolProvider= */ new ConstantToolProvider(new SimpleTool("")),
        /* dxExecutable= */ Paths.get("/usr/bin/dx"),
        /* d8Executable= */ Paths.get("/usr/bin/d8"),
        /* androidFrameworkIdlFile= */ Paths.get(""),
        /* proguardJar= */ Paths.get(""),
        /* proguardConfig= */ Paths.get(""),
        /* optimizedProguardConfig= */ Paths.get(""));
  }
}

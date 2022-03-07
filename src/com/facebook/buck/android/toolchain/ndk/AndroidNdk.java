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

package com.facebook.buck.android.toolchain.ndk;

import com.facebook.buck.core.exceptions.HumanReadableException;
import com.facebook.buck.core.toolchain.ComparableToolchain;
import com.facebook.buck.core.util.immutables.BuckStyleValue;
import com.facebook.buck.io.ExecutableFinder;
import com.facebook.buck.util.environment.Platform;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import org.immutables.value.Value;

/** Part of Android toolchain that provides access to Android NDK */
@BuckStyleValue
public abstract class AndroidNdk implements ComparableToolchain {
  public static final String DEFAULT_NAME = "android-ndk-location";

  @Override
  public String getName() {
    return DEFAULT_NAME;
  }

  public abstract String getNdkVersion();

  public abstract Path getNdkRootPath();

  /** Escaping logic can be different and depends on the version of Android NDK. */
  public abstract boolean shouldEscapeCFlagsInDoubleQuotes();

  @Value.Auxiliary
  protected abstract ExecutableFinder getExecutableFinder();

  @Value.Lazy
  public Path getNdkBuildExecutable() {
    String executableName = "ndk-build";
    if (Platform.detect() == Platform.WINDOWS) {
      executableName = "ndk-build.cmd";
    }
    Optional<Path> ndkBuild =
        getExecutableFinder().getOptionalExecutable(Paths.get(executableName), getNdkRootPath());
    if (!ndkBuild.isPresent()) {
      throw new HumanReadableException(
          "Unable to find " + executableName + " in " + getNdkRootPath());
    }
    return ndkBuild.get();
  }

  public static AndroidNdk of(
      String ndkVersion,
      Path ndkRootPath,
      boolean shouldEscapeCFlagsInDoubleQuotes,
      ExecutableFinder executableFinder) {
    return ImmutableAndroidNdk.ofImpl(
        ndkVersion, ndkRootPath, shouldEscapeCFlagsInDoubleQuotes, executableFinder);
  }
}

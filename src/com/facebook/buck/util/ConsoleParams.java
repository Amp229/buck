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

package com.facebook.buck.util;

import com.facebook.buck.core.util.immutables.BuckStyleValue;

/** Parameters required to create a new console */
@BuckStyleValue
public abstract class ConsoleParams {

  abstract boolean ansiEscapeSequencesEnabled();

  abstract Verbosity verbosity();

  public String isAnsiEscapeSequencesEnabled() {
    return Boolean.toString(ansiEscapeSequencesEnabled());
  }

  public String getVerbosity() {
    return verbosity().toString();
  }

  public static ConsoleParams of(boolean ansiEscapeSequencesEnabled, Verbosity verbosity) {
    return ImmutableConsoleParams.ofImpl(ansiEscapeSequencesEnabled, verbosity);
  }
}

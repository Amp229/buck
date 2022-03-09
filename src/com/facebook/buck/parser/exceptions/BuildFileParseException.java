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

package com.facebook.buck.parser.exceptions;

import com.facebook.buck.core.exceptions.DependencyStack;
import com.facebook.buck.core.exceptions.HumanReadableException;
import java.io.IOException;
import java.nio.file.Path;
import javax.annotation.Nullable;

/**
 * Thrown if we encounter an unexpected, fatal condition while interacting with the build file
 * parser.
 */
public class BuildFileParseException extends HumanReadableException {

  public BuildFileParseException(@Nullable Throwable cause, String humanReadableErrorMessage) {
    super(cause, humanReadableErrorMessage);
  }

  public BuildFileParseException(DependencyStack dependencyStack, String message) {
    super(dependencyStack, message);
  }

  public BuildFileParseException(Throwable cause, DependencyStack dependencyStack, String message) {
    super(cause, dependencyStack, message);
  }

  protected BuildFileParseException(String message, Object... args) {
    super(message, args);
  }

  protected BuildFileParseException(String message) {
    super(message);
  }

  protected BuildFileParseException(
      DependencyStack dependencyStack, String message, Object... args) {
    super(dependencyStack, message, args);
  }

  public static BuildFileParseException createForUnknownParseError(String message, Object... args) {
    return new BuildFileParseException(message, args);
  }

  public static BuildFileParseException createForUnknownParseError(String message) {
    return new BuildFileParseException(message);
  }

  public static BuildFileParseException createForUnknownParseError(
      Throwable cause, String message) {
    return new BuildFileParseException(cause, message);
  }

  public static BuildFileParseException createForUnknownParseError(
      DependencyStack dependencyStack, String message, Object... args) {
    return new BuildFileParseException(dependencyStack, message, args);
  }

  public static BuildFileParseException createForUnknownParseError(
      Throwable cause, DependencyStack dependencyStack, String message, Object... args) {
    return new BuildFileParseException(cause, dependencyStack, String.format(message, args));
  }

  public static BuildFileParseException createForBuildFileParseError(
      Path buildFile, @Nullable IOException cause) {
    String causeMessage =
        cause != null && cause.getMessage() != null ? ":\n" + cause.getMessage() : "";
    return new BuildFileParseException("Buck wasn't able to parse %s%s", buildFile, causeMessage);
  }

  @Override
  public String getHumanReadableErrorMessage() {
    return getMessage();
  }
}

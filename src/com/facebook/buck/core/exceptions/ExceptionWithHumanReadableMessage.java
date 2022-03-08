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

package com.facebook.buck.core.exceptions;

import javax.annotation.Nullable;

public interface ExceptionWithHumanReadableMessage {

  /** @return a human-readable error message */
  String getHumanReadableErrorMessage();

  /** Get the dependency stack associated with this error */
  default DependencyStack getDependencyStack() {
    return DependencyStack.root();
  }

  /** Get dependency stack if the exception have some otherwise return an empty stack. */
  static DependencyStack getDependencyStack(@Nullable Throwable throwable) {
    if (throwable instanceof ExceptionWithHumanReadableMessage) {
      return ((ExceptionWithHumanReadableMessage) throwable).getDependencyStack();
    } else {
      return DependencyStack.root();
    }
  }
}

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

package com.facebook.buck.core.starlark.testutil;

import com.facebook.buck.core.starlark.compatible.BuckStarlark;
import net.starlark.java.eval.Mutability;
import net.starlark.java.eval.StarlarkThread;

public class TestStarlarkThread {

  public static StarlarkThread dummyStarlarkThread() {
    return new StarlarkThread(
        Mutability.create("dummyStarlarkThread"), BuckStarlark.BUCK_STARLARK_SEMANTICS);
  }
}

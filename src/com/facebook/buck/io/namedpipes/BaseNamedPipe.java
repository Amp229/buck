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

package com.facebook.buck.io.namedpipes;

import com.google.common.base.MoreObjects;
import java.nio.file.Path;

/** Base implementation of {@code NamedPipe} interface. */
public abstract class BaseNamedPipe implements NamedPipe {

  private final Path path;
  private final String namedPipeName;

  protected BaseNamedPipe(Path path) {
    this.path = path;
    this.namedPipeName = path.toString();
  }

  @Override
  public String getName() {
    return namedPipeName;
  }

  protected Path getPath() {
    return path;
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this).add("path", path).toString();
  }
}

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

package com.facebook.buck.rules.coercer;

import com.facebook.buck.core.cell.nameresolver.CellNameResolver;
import com.facebook.buck.core.filesystems.ForwardRelPath;
import com.facebook.buck.core.model.TargetConfiguration;
import com.facebook.buck.core.model.TargetConfigurationResolver;
import com.facebook.buck.io.filesystem.ProjectFilesystem;
import com.facebook.buck.rules.coercer.TypeCoercer.Traversal;
import com.facebook.buck.rules.macros.Macro;
import com.facebook.buck.rules.macros.UnconfiguredMacro;
import com.google.common.collect.ImmutableList;

/** Base class for coercing a macro name and arguments into a typed {@link Macro}. */
interface MacroTypeCoercer<U extends UnconfiguredMacro, T extends Macro> {

  boolean hasElementClass(Class<?>[] types);

  Class<U> getUnconfiguredOutputClass();

  Class<T> getOutputClass();

  void traverseUnconfigured(CellNameResolver cellRoots, U macro, Traversal traversal);

  void traverse(CellNameResolver cellRoots, T macro, Traversal traversal);

  U coerceToUnconfigured(
      CellNameResolver cellNameResolver,
      ProjectFilesystem filesystem,
      ForwardRelPath pathRelativeToProjectRoot,
      ImmutableList<String> args)
      throws CoerceFailedException;

  @SuppressWarnings("unchecked")
  default T coerceBoth(
      CellNameResolver cellNameResolver,
      ProjectFilesystem filesystem,
      ForwardRelPath pathRelativeToProjectRoot,
      TargetConfiguration targetConfiguration,
      TargetConfigurationResolver hostConfigurationResolver,
      ImmutableList<String> args)
      throws CoerceFailedException {
    U unconfigured =
        coerceToUnconfigured(cellNameResolver, filesystem, pathRelativeToProjectRoot, args);
    return (T) unconfigured.configure(targetConfiguration, hostConfigurationResolver);
  }
}

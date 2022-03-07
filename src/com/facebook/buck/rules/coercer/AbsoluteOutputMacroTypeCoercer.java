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
import com.facebook.buck.io.filesystem.ProjectFilesystem;
import com.facebook.buck.rules.coercer.TypeCoercer.Traversal;
import com.facebook.buck.rules.macros.AbsoluteOutputMacro;
import com.google.common.collect.ImmutableList;

/**
 * Handles '$(output ...)' macro.
 *
 * @see com.facebook.buck.rules.macros.OutputMacroExpander
 */
public class AbsoluteOutputMacroTypeCoercer
    implements MacroTypeCoercer<AbsoluteOutputMacro, AbsoluteOutputMacro> {

  @Override
  public boolean hasElementClass(Class<?>[] types) {
    for (Class<?> type : types) {
      if (type.isAssignableFrom(getOutputClass())) {
        return true;
      }
    }
    return false;
  }

  @Override
  public Class<AbsoluteOutputMacro> getUnconfiguredOutputClass() {
    return AbsoluteOutputMacro.class;
  }

  @Override
  public Class<AbsoluteOutputMacro> getOutputClass() {
    return AbsoluteOutputMacro.class;
  }

  @Override
  public void traverseUnconfigured(
      CellNameResolver cellRoots, AbsoluteOutputMacro macro, Traversal traversal) {
    traversal.traverse(macro);
  }

  @Override
  public void traverse(CellNameResolver cellRoots, AbsoluteOutputMacro macro, Traversal traversal) {
    traversal.traverse(macro);
  }

  @Override
  public AbsoluteOutputMacro coerceToUnconfigured(
      CellNameResolver cellNameResolver,
      ProjectFilesystem filesystem,
      ForwardRelPath pathRelativeToProjectRoot,
      ImmutableList<String> args)
      throws CoerceFailedException {
    if (args.size() != 1 || args.get(0).isEmpty()) {
      throw new CoerceFailedException(
          String.format("expected exactly one argument (found %d)", args.size()));
    }
    return AbsoluteOutputMacro.of(args.get(0));
  }
}

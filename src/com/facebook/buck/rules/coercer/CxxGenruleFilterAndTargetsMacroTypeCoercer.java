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
import com.facebook.buck.core.model.BuildTarget;
import com.facebook.buck.core.model.UnconfiguredBuildTarget;
import com.facebook.buck.io.filesystem.ProjectFilesystem;
import com.facebook.buck.rules.macros.CxxGenruleFilterAndTargetsMacro;
import com.facebook.buck.rules.macros.UnconfiguredCxxGenruleFilterAndTargetsMacro;
import com.google.common.collect.ImmutableList;
import java.util.ArrayDeque;
import java.util.Optional;
import java.util.Queue;
import java.util.function.BiFunction;
import java.util.regex.Pattern;

/** Coercer for <code>cxx_genrule</code> flag-based macros. */
public class CxxGenruleFilterAndTargetsMacroTypeCoercer<
        U extends UnconfiguredCxxGenruleFilterAndTargetsMacro,
        M extends CxxGenruleFilterAndTargetsMacro>
    implements MacroTypeCoercer<U, M> {

  private final Optional<TypeCoercer<Pattern, Pattern>> patternTypeCoercer;
  private final TypeCoercer<ImmutableList<UnconfiguredBuildTarget>, ImmutableList<BuildTarget>>
      buildTargetsTypeCoercer;
  private final Class<U> uClass;
  private final Class<M> mClass;
  private final BiFunction<Optional<Pattern>, ImmutableList<UnconfiguredBuildTarget>, U> factory;

  public CxxGenruleFilterAndTargetsMacroTypeCoercer(
      Optional<TypeCoercer<Pattern, Pattern>> patternTypeCoercer,
      TypeCoercer<ImmutableList<UnconfiguredBuildTarget>, ImmutableList<BuildTarget>>
          buildTargetsTypeCoercer,
      Class<U> uClass,
      Class<M> mClass,
      BiFunction<Optional<Pattern>, ImmutableList<UnconfiguredBuildTarget>, U> factory) {
    this.patternTypeCoercer = patternTypeCoercer;
    this.buildTargetsTypeCoercer = buildTargetsTypeCoercer;
    this.uClass = uClass;
    this.mClass = mClass;
    this.factory = factory;
  }

  @Override
  public Class<U> getUnconfiguredOutputClass() {
    return uClass;
  }

  @Override
  public Class<M> getOutputClass() {
    return mClass;
  }

  @Override
  public boolean hasElementClass(Class<?>[] types) {
    return buildTargetsTypeCoercer.hasElementClass(types);
  }

  @Override
  public void traverseUnconfigured(
      CellNameResolver cellRoots,
      UnconfiguredCxxGenruleFilterAndTargetsMacro macro,
      TypeCoercer.Traversal traversal) {
    patternTypeCoercer.ifPresent(
        coercer ->
            macro
                .getFilter()
                .ifPresent(filter -> coercer.traverseUnconfigured(cellRoots, filter, traversal)));
    buildTargetsTypeCoercer.traverseUnconfigured(cellRoots, macro.getTargets(), traversal);
  }

  @Override
  public void traverse(CellNameResolver cellRoots, M macro, TypeCoercer.Traversal traversal) {
    patternTypeCoercer.ifPresent(
        coercer ->
            macro.getFilter().ifPresent(filter -> coercer.traverse(cellRoots, filter, traversal)));
    buildTargetsTypeCoercer.traverse(cellRoots, macro.getTargets(), traversal);
  }

  @Override
  public U coerceToUnconfigured(
      CellNameResolver cellNameResolver,
      ProjectFilesystem filesystem,
      ForwardRelPath pathRelativeToProjectRoot,
      ImmutableList<String> args)
      throws CoerceFailedException {

    if (args.isEmpty() && patternTypeCoercer.isPresent()) {
      throw new CoerceFailedException(
          String.format("expected at least one argument (found %d)", args.size()));
    }

    Queue<String> mArgs = new ArrayDeque<>(args);

    // Parse filter arg.
    Optional<Pattern> filter = Optional.empty();
    if (patternTypeCoercer.isPresent()) {
      filter =
          Optional.of(
              patternTypeCoercer
                  .get()
                  .coerceToUnconfigured(
                      cellNameResolver, filesystem, pathRelativeToProjectRoot, mArgs.remove()));
    }

    // Parse build target args.
    ImmutableList<UnconfiguredBuildTarget> targets =
        buildTargetsTypeCoercer.coerceToUnconfigured(
            cellNameResolver, filesystem, pathRelativeToProjectRoot, mArgs);

    return factory.apply(filter, targets);
  }
}

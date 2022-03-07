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

import com.facebook.buck.apple.xcode.xcodeproj.PBXReference;
import com.facebook.buck.apple.xcode.xcodeproj.SourceTreePath;
import com.facebook.buck.core.cell.nameresolver.CellNameResolver;
import com.facebook.buck.core.exceptions.HumanReadableException;
import com.facebook.buck.core.filesystems.ForwardRelPath;
import com.facebook.buck.core.model.TargetConfiguration;
import com.facebook.buck.core.model.TargetConfigurationResolver;
import com.facebook.buck.core.sourcepath.SourcePath;
import com.facebook.buck.core.sourcepath.UnconfiguredSourcePath;
import com.facebook.buck.io.filesystem.ProjectFilesystem;
import com.facebook.buck.util.types.Unit;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.reflect.TypeToken;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;
import java.util.Optional;

/** Coerce to {@link com.facebook.buck.rules.coercer.FrameworkPath}. */
public class FrameworkPathTypeCoercer
    implements TypeCoercer<UnconfiguredFrameworkPath, FrameworkPath> {

  private final TypeCoercer<UnconfiguredSourcePath, SourcePath> sourcePathTypeCoercer;

  public FrameworkPathTypeCoercer(
      TypeCoercer<UnconfiguredSourcePath, SourcePath> sourcePathTypeCoercer) {
    this.sourcePathTypeCoercer = sourcePathTypeCoercer;
  }

  @Override
  public SkylarkSpec getSkylarkSpec() {
    return () -> "attr.string()";
  }

  @Override
  public TypeToken<FrameworkPath> getOutputType() {
    return TypeToken.of(FrameworkPath.class);
  }

  @Override
  public TypeToken<UnconfiguredFrameworkPath> getUnconfiguredType() {
    return TypeToken.of(UnconfiguredFrameworkPath.class);
  }

  @Override
  public boolean hasElementClass(Class<?>... types) {
    for (Class<?> type : types) {
      if (type.isAssignableFrom(SourceTreePath.class)) {
        return true;
      }
    }
    return sourcePathTypeCoercer.hasElementClass(types);
  }

  @Override
  public void traverseUnconfigured(
      CellNameResolver cellRoots, UnconfiguredFrameworkPath object, Traversal traversal) {
    object.match(
        new UnconfiguredFrameworkPath.Matcher<Unit>() {
          @Override
          public Unit sourcePath(UnconfiguredSourcePath sourcePath) {
            sourcePathTypeCoercer.traverseUnconfigured(cellRoots, sourcePath, traversal);
            return Unit.UNIT;
          }

          @Override
          public Unit sourceTreePath(SourceTreePath sourceTreePath) {
            traversal.traverse(sourceTreePath);
            return Unit.UNIT;
          }
        });
  }

  @Override
  public void traverse(CellNameResolver cellRoots, FrameworkPath object, Traversal traversal) {
    switch (object.getType()) {
      case SOURCE_TREE_PATH:
        traversal.traverse(object.getSourceTreePath().get());
        break;
      case SOURCE_PATH:
        sourcePathTypeCoercer.traverse(cellRoots, object.getSourcePath().get(), traversal);
        break;
      default:
        throw new RuntimeException("Unhandled type: " + object.getType());
    }
  }

  @Override
  public UnconfiguredFrameworkPath coerceToUnconfigured(
      CellNameResolver cellRoots,
      ProjectFilesystem filesystem,
      ForwardRelPath pathRelativeToProjectRoot,
      Object object)
      throws CoerceFailedException {
    if (object instanceof String) {
      Path path = Paths.get((String) object);

      String firstElement =
          Objects.requireNonNull(Iterables.getFirst(path, Paths.get(""))).toString();

      if (firstElement.startsWith("$")) { // NOPMD - length() > 0 && charAt(0) == '$' is ridiculous
        Optional<PBXReference.SourceTree> sourceTree =
            PBXReference.SourceTree.fromBuildSetting(firstElement);
        if (sourceTree.isPresent()) {
          int nameCount = path.getNameCount();
          if (nameCount < 2) {
            throw new HumanReadableException(
                "Invalid source tree path: '%s'. Should have at least one path component after"
                    + "'%s'.",
                path, firstElement);
          }
          return UnconfiguredFrameworkPath.ofSourceTreePath(
              new SourceTreePath(
                  sourceTree.get(), path.subpath(1, path.getNameCount()), Optional.empty()));
        } else {
          throw new HumanReadableException(
              "Unknown SourceTree: '%s'. Should be one of: %s",
              firstElement,
              Joiner.on(", ")
                  .join(
                      Iterables.transform(
                          ImmutableList.copyOf(PBXReference.SourceTree.values()),
                          input -> "$" + input)));
        }
      } else {
        return UnconfiguredFrameworkPath.ofSourcePath(
            sourcePathTypeCoercer.coerceToUnconfigured(
                cellRoots, filesystem, pathRelativeToProjectRoot, object));
      }
    }

    throw CoerceFailedException.simple(
        object, getOutputType(), "input should be either a source tree path or a source path");
  }

  @Override
  public FrameworkPath coerce(
      CellNameResolver cellRoots,
      ProjectFilesystem filesystem,
      ForwardRelPath pathRelativeToProjectRoot,
      TargetConfiguration targetConfiguration,
      TargetConfigurationResolver hostConfigurationResolver,
      UnconfiguredFrameworkPath object)
      throws CoerceFailedException {
    return object.match(
        new UnconfiguredFrameworkPath.Matcher<FrameworkPath>() {
          @Override
          public FrameworkPath sourcePath(UnconfiguredSourcePath sourcePath) {
            return FrameworkPath.ofSourcePath(
                sourcePath.configure(cellRoots, filesystem, targetConfiguration));
          }

          @Override
          public FrameworkPath sourceTreePath(SourceTreePath sourceTreePath) {
            return FrameworkPath.ofSourceTreePath(sourceTreePath);
          }
        });
  }
}

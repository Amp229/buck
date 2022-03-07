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
import com.facebook.buck.core.sourcepath.SourcePath;
import com.facebook.buck.core.sourcepath.UnconfiguredSourcePath;
import com.facebook.buck.io.filesystem.ProjectFilesystem;
import com.facebook.buck.util.types.Unit;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSortedMap;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.reflect.TypeToken;
import java.util.List;

public class SourceSortedSetTypeCoercer extends SourceSortedSetConcatable
    implements TypeCoercer<UnconfiguredSourceSortedSet, SourceSortedSet> {
  private final TypeCoercer<ImmutableList<UnconfiguredSourcePath>, ImmutableSortedSet<SourcePath>>
      unnamedHeadersTypeCoercer;
  private final TypeCoercer<
          ImmutableSortedMap<String, UnconfiguredSourcePath>,
          ImmutableSortedMap<String, SourcePath>>
      namedHeadersTypeCoercer;

  SourceSortedSetTypeCoercer(
      TypeCoercer<String, String> stringTypeCoercer,
      TypeCoercer<UnconfiguredSourcePath, SourcePath> sourcePathTypeCoercer) {
    this.unnamedHeadersTypeCoercer = new SortedSetTypeCoercer<>(sourcePathTypeCoercer);
    this.namedHeadersTypeCoercer =
        new SortedMapTypeCoercer<>(stringTypeCoercer, sourcePathTypeCoercer);
  }

  @Override
  public SkylarkSpec getSkylarkSpec() {
    return new SkylarkSpec() {
      @Override
      public String spec() {
        return "attr.named_set(attr.source(), sorted=True)";
      }

      @Override
      public String topLevelSpec() {
        return "attr.named_set(attr.source(), sorted=True, default=[])";
      }
    };
  }

  @Override
  public TypeToken<SourceSortedSet> getOutputType() {
    return TypeToken.of(SourceSortedSet.class);
  }

  @Override
  public TypeToken<UnconfiguredSourceSortedSet> getUnconfiguredType() {
    return TypeToken.of(UnconfiguredSourceSortedSet.class);
  }

  @Override
  public boolean hasElementClass(Class<?>... types) {
    return unnamedHeadersTypeCoercer.hasElementClass(types)
        || namedHeadersTypeCoercer.hasElementClass(types);
  }

  @Override
  public void traverseUnconfigured(
      CellNameResolver cellRoots, UnconfiguredSourceSortedSet object, Traversal traversal) {
    object.match(
        new UnconfiguredSourceSortedSet.Matcher<Unit>() {
          @Override
          public Unit named(ImmutableSortedMap<String, UnconfiguredSourcePath> named) {
            namedHeadersTypeCoercer.traverseUnconfigured(cellRoots, named, traversal);
            return Unit.UNIT;
          }

          @Override
          public Unit unnamed(ImmutableSortedSet<UnconfiguredSourcePath> unnamed) {
            // TODO(srice): The types here are wonky - why are we making `unnamed` into an
            // `ImmutableSortedSet` only to turn it back into a list for traversal?
            unnamedHeadersTypeCoercer.traverseUnconfigured(
                cellRoots, ImmutableList.copyOf(unnamed), traversal);
            return Unit.UNIT;
          }
        });
  }

  @Override
  public void traverse(CellNameResolver cellRoots, SourceSortedSet object, Traversal traversal) {
    object.match(
        new SourceSortedSet.Matcher<Unit>() {
          @Override
          public Unit named(ImmutableSortedMap<String, SourcePath> named) {
            namedHeadersTypeCoercer.traverse(cellRoots, named, traversal);
            return Unit.UNIT;
          }

          @Override
          public Unit unnamed(ImmutableSortedSet<SourcePath> unnamed) {
            unnamedHeadersTypeCoercer.traverse(cellRoots, unnamed, traversal);
            return Unit.UNIT;
          }
        });
  }

  @Override
  public UnconfiguredSourceSortedSet coerceToUnconfigured(
      CellNameResolver cellRoots,
      ProjectFilesystem filesystem,
      ForwardRelPath pathRelativeToProjectRoot,
      Object object)
      throws CoerceFailedException {
    if (object instanceof List) {
      return UnconfiguredSourceSortedSet.ofUnnamedSources(
          ImmutableSortedSet.copyOf(
              unnamedHeadersTypeCoercer.coerceToUnconfigured(
                  cellRoots, filesystem, pathRelativeToProjectRoot, object)));
    } else {
      return UnconfiguredSourceSortedSet.ofNamedSources(
          namedHeadersTypeCoercer.coerceToUnconfigured(
              cellRoots, filesystem, pathRelativeToProjectRoot, object));
    }
  }

  @Override
  public SourceSortedSet coerce(
      CellNameResolver cellRoots,
      ProjectFilesystem filesystem,
      ForwardRelPath pathRelativeToProjectRoot,
      TargetConfiguration targetConfiguration,
      TargetConfigurationResolver hostConfigurationResolver,
      UnconfiguredSourceSortedSet object)
      throws CoerceFailedException {
    return object.match(
        new UnconfiguredSourceSortedSet.MatcherWithException<
            SourceSortedSet, CoerceFailedException>() {
          @Override
          public SourceSortedSet named(ImmutableSortedMap<String, UnconfiguredSourcePath> named)
              throws CoerceFailedException {
            return SourceSortedSet.ofNamedSources(
                namedHeadersTypeCoercer.coerce(
                    cellRoots,
                    filesystem,
                    pathRelativeToProjectRoot,
                    targetConfiguration,
                    hostConfigurationResolver,
                    named));
          }

          @Override
          public SourceSortedSet unnamed(ImmutableSortedSet<UnconfiguredSourcePath> unnamed)
              throws CoerceFailedException {
            return SourceSortedSet.ofUnnamedSources(
                unnamedHeadersTypeCoercer.coerce(
                    cellRoots,
                    filesystem,
                    pathRelativeToProjectRoot,
                    targetConfiguration,
                    hostConfigurationResolver,
                    unnamed.asList()));
          }
        });
  }
}

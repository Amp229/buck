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

package com.facebook.buck.rules.query;

import com.facebook.buck.core.cell.nameresolver.CellNameResolver;
import com.facebook.buck.core.description.arg.ConstructorArg;
import com.facebook.buck.core.exceptions.HumanReadableException;
import com.facebook.buck.core.model.BuildTarget;
import com.facebook.buck.core.model.targetgraph.TargetNode;
import com.facebook.buck.core.sourcepath.BuildTargetSourcePath;
import com.facebook.buck.core.sourcepath.PathSourcePath;
import com.facebook.buck.core.sourcepath.SourcePath;
import com.facebook.buck.query.ConfiguredQueryBuildTarget;
import com.facebook.buck.query.ConfiguredQueryTarget;
import com.facebook.buck.query.QueryFileTarget;
import com.facebook.buck.rules.coercer.ParamInfo;
import com.facebook.buck.rules.coercer.TypeCoercerFactory;
import com.facebook.buck.rules.param.ParamName;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedSet;
import java.nio.file.Path;
import java.util.function.Predicate;

public class QueryTargetAccessor {

  private QueryTargetAccessor() {}

  /** Get targets in attribute. */
  public static <T extends ConstructorArg>
      ImmutableSet<ConfiguredQueryTarget> getTargetsInAttribute(
          TypeCoercerFactory typeCoercerFactory,
          TargetNode<T> node,
          ParamName attribute,
          CellNameResolver cellPathResolver) {
    ParamInfo<?> info =
        typeCoercerFactory.paramInfos(node.getConstructorArg()).getByName(attribute);
    if (info == null) {
      // Ignore if the field does not exist in this rule.
      return ImmutableSet.of();
    }
    ImmutableSet.Builder<ConfiguredQueryTarget> builder =
        new ImmutableSortedSet.Builder<>(ConfiguredQueryTarget::compare);
    info.traverse(
        cellPathResolver,
        value -> {
          if (value instanceof Path) {
            builder.add(QueryFileTarget.of(PathSourcePath.of(node.getFilesystem(), (Path) value)));
          } else if (value instanceof SourcePath) {
            builder.add(extractSourcePath((SourcePath) value));
          } else if (value instanceof BuildTarget) {
            builder.add(extractBuildTargetContainer((BuildTarget) value));
          }
        },
        node.getConstructorArg());
    return builder.build();
  }

  public static ConfiguredQueryTarget extractSourcePath(SourcePath sourcePath) {
    if (sourcePath instanceof PathSourcePath) {
      return QueryFileTarget.of((PathSourcePath) sourcePath);
    } else if (sourcePath instanceof BuildTargetSourcePath) {
      return ConfiguredQueryBuildTarget.of(((BuildTargetSourcePath) sourcePath).getTarget());
    }
    throw new HumanReadableException("Unsupported source path type: %s", sourcePath.getClass());
  }

  /** Filters the objects in the given attribute that satisfy the given predicate. */
  public static <T extends ConstructorArg> ImmutableSet<Object> filterAttributeContents(
      TypeCoercerFactory typeCoercerFactory,
      TargetNode<T> node,
      ParamName attribute,
      Predicate<Object> predicate,
      CellNameResolver cellNameResolver) {
    ParamInfo<?> info =
        typeCoercerFactory.paramInfos(node.getConstructorArg()).getByName(attribute);
    if (info == null) {
      // Ignore if the field does not exist in this rule.
      return ImmutableSet.of();
    }
    ImmutableSet.Builder<Object> builder = ImmutableSet.builder();
    info.traverse(
        cellNameResolver,
        value -> {
          if (predicate.test(value)) {
            builder.add(value);
          }
        },
        node.getConstructorArg());
    return builder.build();
  }

  public static ConfiguredQueryTarget extractBuildTargetContainer(
      BuildTarget buildTargetContainer) {
    return ConfiguredQueryBuildTarget.of(buildTargetContainer);
  }
}

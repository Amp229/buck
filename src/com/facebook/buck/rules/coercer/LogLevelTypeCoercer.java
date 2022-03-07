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
import com.google.common.collect.ImmutableList;
import com.google.common.reflect.TypeToken;
import java.util.List;
import java.util.logging.Level;

/** Coercer for {@link java.util.logging.Level}. */
public class LogLevelTypeCoercer extends LeafUnconfiguredOnlyCoercer<Level> {

  @Override
  public TypeToken<Level> getUnconfiguredType() {
    return TypeToken.of(Level.class);
  }

  enum LogLevel {
    OFF,
    SEVERE,
    WARNING,
    INFO,
    CONFIG,
    FINE,
    FINER,
    FINEST,
    ALL
  }

  @Override
  public SkylarkSpec getSkylarkSpec() {
    return new SkylarkSpec() {
      @Override
      public String spec() {
        return "attr.one_of(attr.enum(LogLevel), attr.int())";
      }

      @Override
      public List<Class<? extends Enum<?>>> enums() {
        return ImmutableList.of(LogLevel.class);
      }
    };
  }

  @Override
  public Level coerceToUnconfigured(
      CellNameResolver cellRoots,
      ProjectFilesystem filesystem,
      ForwardRelPath pathRelativeToProjectRoot,
      Object object)
      throws CoerceFailedException {
    if (object instanceof String) {
      try {
        return Level.parse((String) object);
      } catch (IllegalArgumentException ex) {
        throw CoerceFailedException.simple(object, getOutputType(), ex.getMessage());
      }
    }
    throw CoerceFailedException.simple(object, getOutputType());
  }
}

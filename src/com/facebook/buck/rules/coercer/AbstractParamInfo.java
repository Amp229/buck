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
import com.facebook.buck.core.description.arg.Hint;
import com.facebook.buck.core.filesystems.ForwardRelPath;
import com.facebook.buck.core.model.ConstantHostTargetConfigurationResolver;
import com.facebook.buck.core.model.TargetConfiguration;
import com.facebook.buck.core.model.TargetConfigurationResolver;
import com.facebook.buck.core.model.UnconfiguredTargetConfiguration;
import com.facebook.buck.io.filesystem.ProjectFilesystem;
import com.facebook.buck.rules.param.ParamName;
import com.google.common.base.Preconditions;
import javax.annotation.Nullable;

/**
 * Represents a single field that can be represented in buck build files. This base class implements
 * some common logic that is used by both all subclasses
 */
public abstract class AbstractParamInfo<T> implements ParamInfo<T> {

  private final ParamName name;
  private final TypeCoercer<?, T> typeCoercer;

  /** Create an instance of {@link AbstractParamInfo} */
  public AbstractParamInfo(ParamName name, TypeCoercer<?, T> typeCoercer) {
    this.name = name;
    this.typeCoercer = typeCoercer;
  }

  @Override
  public ParamName getName() {
    return name;
  }

  @Override
  public TypeCoercer<?, T> getTypeCoercer() {
    return typeCoercer;
  }

  @Override
  public boolean isDep() {
    Hint hint = getHint();
    if (hint != null) {
      return hint.isDep();
    }
    return Hint.DEFAULT_IS_DEP;
  }

  @Override
  public boolean isTargetGraphOnlyDep() {
    Hint hint = getHint();
    if (hint != null && hint.isTargetGraphOnlyDep()) {
      Preconditions.checkState(hint.isDep(), "Conditional deps are only applicable for deps.");
      return true;
    }
    return Hint.DEFAULT_IS_TARGET_GRAPH_ONLY_DEP;
  }

  @Override
  public boolean isInput() {
    Hint hint = getHint();
    if (hint != null) {
      return hint.isInput();
    }
    return Hint.DEFAULT_IS_INPUT;
  }

  @Override
  public boolean isConfigurable() {
    Hint hint = getHint();
    if (hint != null) {
      return hint.isConfigurable();
    }
    return Hint.DEFAULT_IS_CONFIGURABLE;
  }

  @Override
  public boolean splitConfiguration() {
    Hint hint = getHint();
    if (hint != null) {
      return hint.splitConfiguration();
    }
    return Hint.DEFAULT_SPLIT_CONFIGURATION;
  }

  @Override
  public boolean execConfiguration() {
    Hint hint = getHint();
    if (hint != null) {
      return hint.execConfiguration();
    }
    return Hint.DEFAULT_EXEC_CONFIGURATION;
  }

  @Override
  public Hint.PathsMustBe pathsMustBe() {
    Hint hint = getHint();
    if (hint != null) {
      return hint.pathsMustBe();
    }
    return Hint.PathsMustBe.ANY;
  }

  @Override
  public Class<?> getResultClass() {
    return typeCoercer.getOutputType().getRawType();
  }

  @Override
  public void traverse(CellNameResolver cellNameResolver, Traversal traversal, Object dto) {
    traverseHelper(cellNameResolver, typeCoercer, traversal, dto);
  }

  private void traverseHelper(
      CellNameResolver cellPathResolver,
      TypeCoercer<?, T> typeCoercer,
      Traversal traversal,
      Object dto) {
    T object = get(dto);
    if (object != null) {
      typeCoercer.traverse(cellPathResolver, object, traversal);
    }
  }

  @Override
  public void set(
      CellNameResolver cellNameResolver,
      ProjectFilesystem filesystem,
      ForwardRelPath pathRelativeToProjectRoot,
      TargetConfiguration targetConfiguration,
      TargetConfigurationResolver hostConfigurationResolver,
      Object dto,
      @Nullable Object value)
      throws ParamInfoException {
    if (value == null) {
      return;
    }
    try {
      setCoercedValue(
          dto,
          typeCoercer.coerceBoth(
              cellNameResolver,
              filesystem,
              pathRelativeToProjectRoot,
              targetConfiguration,
              new ConstantHostTargetConfigurationResolver(UnconfiguredTargetConfiguration.INSTANCE),
              value));
    } catch (CoerceFailedException e) {
      throw new ParamInfoException(name.getSnakeCase(), e.getMessage(), e);
    }
  }

  @Override
  public boolean hasElementTypes(Class<?>... types) {
    return typeCoercer.hasElementClass(types);
  }
}

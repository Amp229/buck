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
import com.facebook.buck.core.description.arg.DataTransferObject;
import com.facebook.buck.core.filesystems.ForwardRelPath;
import com.facebook.buck.core.model.TargetConfiguration;
import com.facebook.buck.core.model.TargetConfigurationResolver;
import com.facebook.buck.io.filesystem.ProjectFilesystem;
import com.google.common.reflect.TypeToken;
import java.util.Map;

/** A coercer for Immutables using the same flow as Description's args */
public class ImmutableTypeCoercer<T extends DataTransferObject> implements TypeCoercer<Object, T> {

  private final DataTransferObjectDescriptor<T> constructorArgDescriptor;
  private final ParamsInfo paramsInfo;

  ImmutableTypeCoercer(DataTransferObjectDescriptor<T> constructorArgDescriptor) {
    this.constructorArgDescriptor = constructorArgDescriptor;
    // Translate keys from lowerCamel to lower_hyphen
    this.paramsInfo = constructorArgDescriptor.getParamsInfo();
  }

  @Override
  public SkylarkSpec getSkylarkSpec() {
    // Luckily, this appears to only be used for AppleAssetCatalogsCompilationOptions which doesn't
    // have any interesting fields.
    return new SkylarkSpec() {
      @Override
      public String spec() {
        return "attr.dict(key=attr.string(), value=attr.any())";
      }

      @Override
      public String topLevelSpec() {
        return "attr.dict(key=attr.string(), value=attr.any(), default={})";
      }
    };
  }

  @Override
  public TypeToken<T> getOutputType() {
    return TypeToken.of(constructorArgDescriptor.objectClass());
  }

  @Override
  public TypeToken<Object> getUnconfiguredType() {
    return TypeToken.of(Object.class);
  }

  @Override
  public boolean hasElementClass(Class<?>... types) {
    return paramsInfo.getParamInfosSorted().stream()
        .anyMatch(paramInfo -> paramInfo.hasElementTypes(types));
  }

  @Override
  public void traverseUnconfigured(CellNameResolver cellRoots, Object object, Traversal traversal) {
    // TODO(srice): `coerceToUnconfigured` isn't fully implemented for this class, so our
    //  `traverseUnconfigured` is incorrect as well
    traversal.traverse(object);
  }

  @Override
  public void traverse(CellNameResolver cellRoots, T object, Traversal traversal) {
    traversal.traverse(object);
    for (ParamInfo<?> paramInfo : paramsInfo.getParamInfosSorted()) {
      @SuppressWarnings("unchecked")
      TypeCoercer<Object, Object> paramTypeCoercer =
          (TypeCoercer<Object, Object>) paramInfo.getTypeCoercer();
      Object fieldValue = paramInfo.get(object);
      paramTypeCoercer.traverse(cellRoots, fieldValue, traversal);
    }
  }

  @Override
  public Object coerceToUnconfigured(
      CellNameResolver cellRoots,
      ProjectFilesystem filesystem,
      ForwardRelPath pathRelativeToProjectRoot,
      Object object)
      throws CoerceFailedException {
    return object;
  }

  @Override
  public T coerce(
      CellNameResolver cellRoots,
      ProjectFilesystem filesystem,
      ForwardRelPath pathRelativeToProjectRoot,
      TargetConfiguration targetConfiguration,
      TargetConfigurationResolver hostConfigurationResolver,
      Object object)
      throws CoerceFailedException {

    Object builder = constructorArgDescriptor.getBuilderFactory().get();
    if (!(object instanceof Map)) {
      throw CoerceFailedException.simple(object, getOutputType(), "expected a dict");
    }
    Map<?, ?> map = (Map<?, ?>) object;
    for (Map.Entry<?, ?> entry : map.entrySet()) {
      Object key = entry.getKey();
      if (!(key instanceof String)) {
        throw CoerceFailedException.simple(object, getOutputType(), "keys should be strings");
      }
      ParamInfo<?> paramInfo = paramsInfo.getByStarlarkName((String) key);
      if (paramInfo == null) {
        throw CoerceFailedException.simple(
            object,
            getOutputType(),
            String.format(
                "parameter '%s' not found on %s", key, paramsInfo.getParamStarlarkNames()));
      }
      try {
        paramInfo.set(
            cellRoots,
            filesystem,
            pathRelativeToProjectRoot,
            targetConfiguration,
            hostConfigurationResolver,
            builder,
            entry.getValue());
      } catch (ParamInfoException e) {
        throw new CoerceFailedException(e.getMessage(), e.getCause());
      }
    }
    return constructorArgDescriptor.build(builder, builder.getClass().getSimpleName());
  }
}

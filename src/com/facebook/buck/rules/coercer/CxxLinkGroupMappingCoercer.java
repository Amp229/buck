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
import com.facebook.buck.core.linkgroup.CxxLinkGroupMapping;
import com.facebook.buck.core.linkgroup.CxxLinkGroupMappingTarget;
import com.facebook.buck.core.linkgroup.UnconfiguredCxxLinkGroupMapping;
import com.facebook.buck.core.linkgroup.UnconfiguredCxxLinkGroupMappingTarget;
import com.facebook.buck.core.model.TargetConfiguration;
import com.facebook.buck.core.model.TargetConfigurationResolver;
import com.facebook.buck.io.filesystem.ProjectFilesystem;
import com.facebook.buck.util.types.Pair;
import com.google.common.collect.ImmutableList;
import com.google.common.reflect.TypeToken;
import java.util.Collection;
import java.util.List;

/**
 * {@link TypeCoercer} for {@link CxxLinkGroupMapping}.
 *
 * <p>This {@link TypeCoercer} is used to convert a single link group mapping entry (i.e., single
 * element in the list of <code>link_group_map</code> to a {@link CxxLinkGroupMapping}.
 */
public class CxxLinkGroupMappingCoercer
    implements TypeCoercer<UnconfiguredCxxLinkGroupMapping, CxxLinkGroupMapping> {
  private final TypeCoercer<String, String> linkGroupTypeCoercer;
  private final TypeCoercer<
          ImmutableList<UnconfiguredCxxLinkGroupMappingTarget>,
          ImmutableList<CxxLinkGroupMappingTarget>>
      mappingTargetsCoercer;
  private final TypeCoercer<
          Pair<String, ImmutableList<UnconfiguredCxxLinkGroupMappingTarget>>,
          Pair<String, ImmutableList<CxxLinkGroupMappingTarget>>>
      buildTargetWithTraversalTypeCoercer;

  public CxxLinkGroupMappingCoercer(
      TypeCoercer<String, String> linkGroupTypeCoercer,
      TypeCoercer<
              ImmutableList<UnconfiguredCxxLinkGroupMappingTarget>,
              ImmutableList<CxxLinkGroupMappingTarget>>
          mappingTargetCoercer) {
    this.linkGroupTypeCoercer = linkGroupTypeCoercer;
    this.mappingTargetsCoercer = mappingTargetCoercer;
    this.buildTargetWithTraversalTypeCoercer =
        new PairTypeCoercer<>(this.linkGroupTypeCoercer, this.mappingTargetsCoercer);
  }

  @Override
  public SkylarkSpec getSkylarkSpec() {
    return new SkylarkSpec() {
      @Override
      public String spec() {
        return "attr.tuple(attr.string(), attr.list(attr.tuple(attr.label(), attr.enum(Traversal), attr.option(attr.string()))))";
      }

      @Override
      public List<Class<? extends Enum<?>>> enums() {
        return ImmutableList.of(CxxLinkGroupMappingTarget.Traversal.class);
      }
    };
  }

  @Override
  public TypeToken<CxxLinkGroupMapping> getOutputType() {
    return TypeToken.of(CxxLinkGroupMapping.class);
  }

  @Override
  public TypeToken<UnconfiguredCxxLinkGroupMapping> getUnconfiguredType() {
    return TypeToken.of(UnconfiguredCxxLinkGroupMapping.class);
  }

  @Override
  public boolean hasElementClass(Class<?>... types) {
    return linkGroupTypeCoercer.hasElementClass(types)
        || mappingTargetsCoercer.hasElementClass(types);
  }

  @Override
  public void traverseUnconfigured(
      CellNameResolver cellRoots, UnconfiguredCxxLinkGroupMapping object, Traversal traversal) {
    linkGroupTypeCoercer.traverseUnconfigured(cellRoots, object.getLinkGroup(), traversal);
    mappingTargetsCoercer.traverseUnconfigured(cellRoots, object.getMappingTargets(), traversal);
  }

  @Override
  public void traverse(
      CellNameResolver cellRoots, CxxLinkGroupMapping object, Traversal traversal) {
    linkGroupTypeCoercer.traverse(cellRoots, object.getLinkGroup(), traversal);
    mappingTargetsCoercer.traverse(cellRoots, object.getMappingTargets(), traversal);
  }

  @Override
  public UnconfiguredCxxLinkGroupMapping coerceToUnconfigured(
      CellNameResolver cellRoots,
      ProjectFilesystem filesystem,
      ForwardRelPath pathRelativeToProjectRoot,
      Object object)
      throws CoerceFailedException {
    if (object instanceof Collection<?> && ((Collection<?>) object).size() == 2) {
      Pair<String, ImmutableList<UnconfiguredCxxLinkGroupMappingTarget>>
          linkGroupWithMappingTargets =
              buildTargetWithTraversalTypeCoercer.coerceToUnconfigured(
                  cellRoots, filesystem, pathRelativeToProjectRoot, object);
      return UnconfiguredCxxLinkGroupMapping.of(
          linkGroupWithMappingTargets.getFirst(), linkGroupWithMappingTargets.getSecond());
    }

    throw CoerceFailedException.simple(
        object,
        getOutputType(),
        "input should be pair of a link group and list of mapping targets");
  }

  @Override
  public CxxLinkGroupMapping coerce(
      CellNameResolver cellRoots,
      ProjectFilesystem filesystem,
      ForwardRelPath pathRelativeToProjectRoot,
      TargetConfiguration targetConfiguration,
      TargetConfigurationResolver hostConfigurationResolver,
      UnconfiguredCxxLinkGroupMapping object)
      throws CoerceFailedException {
    return CxxLinkGroupMapping.of(
        object.getLinkGroup(),
        mappingTargetsCoercer.coerce(
            cellRoots,
            filesystem,
            pathRelativeToProjectRoot,
            targetConfiguration,
            hostConfigurationResolver,
            object.getMappingTargets()));
  }
}

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
import com.facebook.buck.core.model.UnconfiguredBuildTarget;
import com.facebook.buck.core.model.UnconfiguredBuildTargetWithOutputs;
import com.facebook.buck.core.parser.buildtargetparser.BuildTargetOutputLabelParser;
import com.facebook.buck.io.filesystem.ProjectFilesystem;
import com.google.common.reflect.TypeToken;

/** Coercer for {@link UnconfiguredBuildTarget} instances that can optionally have output labels. */
public class UnconfiguredBuildTargetWithOutputsTypeCoercer
    extends LeafUnconfiguredOnlyCoercer<UnconfiguredBuildTargetWithOutputs> {

  private final TypeCoercer<UnconfiguredBuildTarget, UnconfiguredBuildTarget>
      buildTargetTypeCoercer;

  public UnconfiguredBuildTargetWithOutputsTypeCoercer(
      TypeCoercer<UnconfiguredBuildTarget, UnconfiguredBuildTarget> buildTargetTypeCoercer) {
    this.buildTargetTypeCoercer = buildTargetTypeCoercer;
  }

  @Override
  public TypeToken<UnconfiguredBuildTargetWithOutputs> getUnconfiguredType() {
    return TypeToken.of(UnconfiguredBuildTargetWithOutputs.class);
  }

  @Override
  public SkylarkSpec getSkylarkSpec() {
    throw new UnsupportedOperationException(
        String.format(
            "%s can't be used in a context that requires a starlark spec.",
            getClass().getSimpleName()));
  }

  @Override
  public UnconfiguredBuildTargetWithOutputs coerceToUnconfigured(
      CellNameResolver cellRoots,
      ProjectFilesystem filesystem,
      ForwardRelPath pathRelativeToProjectRoot,
      Object object)
      throws CoerceFailedException {
    if (!(object instanceof String)) {
      throw CoerceFailedException.simple(object, getOutputType());
    }

    BuildTargetOutputLabelParser.TargetWithOutputLabel targetWithOutputLabel;
    try {
      targetWithOutputLabel =
          BuildTargetOutputLabelParser.getBuildTargetNameWithOutputLabel((String) object);
    } catch (Exception e) {
      throw new CoerceFailedException(e.getMessage(), e);
    }

    UnconfiguredBuildTarget coerced =
        buildTargetTypeCoercer.coerceToUnconfigured(
            cellRoots,
            filesystem,
            pathRelativeToProjectRoot,
            targetWithOutputLabel.getTargetName());
    return UnconfiguredBuildTargetWithOutputs.of(coerced, targetWithOutputLabel.getOutputLabel());
  }
}

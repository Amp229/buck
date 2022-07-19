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

package com.facebook.buck.jvm.java;

import com.facebook.buck.cd.model.java.AbiGenerationMode;
import com.facebook.buck.cd.model.java.AbiJarCommand;
import com.facebook.buck.cd.model.java.BaseCommandParams.SpoolMode;
import com.facebook.buck.cd.model.java.BaseJarCommand;
import com.facebook.buck.cd.model.java.FilesystemParams;
import com.facebook.buck.core.build.buildable.context.BuildableContext;
import com.facebook.buck.core.cell.name.CanonicalCellName;
import com.facebook.buck.core.filesystems.AbsPath;
import com.facebook.buck.core.filesystems.RelPath;
import com.facebook.buck.jvm.cd.AbiStepsBuilder;
import com.facebook.buck.jvm.cd.params.CDParams;
import com.facebook.buck.jvm.cd.serialization.java.JarParametersSerializer;
import com.facebook.buck.jvm.core.BaseJavaAbiInfo;
import com.facebook.buck.jvm.core.BuildTargetValue;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSortedSet;
import javax.annotation.Nullable;

/** Default implementation of {@link AbiStepsBuilder} */
class JavaCDAbiStepsBuilder extends JavaCDStepsBuilderBase<AbiJarCommand>
    implements AbiStepsBuilder {

  private final AbiJarCommand.Builder builder = AbiJarCommand.newBuilder();

  JavaCDAbiStepsBuilder(
      boolean hasAnnotationProcessing,
      SpoolMode spoolMode,
      boolean withDownwardApi,
      CDParams cdParams) {
    super(hasAnnotationProcessing, spoolMode, withDownwardApi, Type.ABI_JAR, cdParams);
  }

  @Override
  public void addBuildStepsForAbi(
      AbiGenerationMode abiCompatibilityMode,
      AbiGenerationMode abiGenerationMode,
      boolean isRequiredForSourceOnlyAbi,
      boolean trackClassUsage,
      boolean trackJavacPhaseEvents,
      boolean withDownwardApi,
      FilesystemParams filesystemParams,
      BuildableContext buildableContext,
      BuildTargetValue buildTargetValue,
      CompilerOutputPathsValue compilerOutputPathsValue,
      ImmutableSortedSet<RelPath> compileTimeClasspathPaths,
      ImmutableSortedSet<RelPath> javaSrcs,
      ImmutableList<BaseJavaAbiInfo> fullJarInfos,
      ImmutableList<BaseJavaAbiInfo> abiJarInfos,
      ImmutableMap<RelPath, RelPath> resourcesMap,
      ImmutableMap<CanonicalCellName, RelPath> cellToPathMappings,
      @Nullable JarParameters abiJarParameters,
      @Nullable JarParameters libraryJarParameters,
      AbsPath buildCellRootPath,
      ResolvedJavac resolvedJavac,
      CompileToJarStepFactory.ExtraParams extraParams) {

    BaseJarCommand baseJarCommand =
        buildBaseJarCommand(
            abiCompatibilityMode,
            abiGenerationMode,
            isRequiredForSourceOnlyAbi,
            trackClassUsage,
            trackJavacPhaseEvents,
            filesystemParams,
            buildTargetValue,
            compilerOutputPathsValue,
            compileTimeClasspathPaths,
            javaSrcs,
            fullJarInfos,
            abiJarInfos,
            resourcesMap,
            cellToPathMappings,
            libraryJarParameters,
            buildCellRootPath,
            resolvedJavac,
            extraParams);

    builder.setBaseJarCommand(baseJarCommand);
    if (abiJarParameters != null) {
      builder.setAbiJarParameters(JarParametersSerializer.serialize(abiJarParameters));
    }

    recordArtifacts(
        buildableContext,
        compilerOutputPathsValue,
        buildTargetValue,
        javaSrcs,
        trackClassUsage,
        abiJarParameters);
  }

  @Override
  protected AbiJarCommand buildCommand() {
    return builder.build();
  }
}

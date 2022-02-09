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

package com.facebook.buck.jvm.java.stepsbuilder.javacd;

import com.facebook.buck.cd.model.java.AbiGenerationMode;
import com.facebook.buck.cd.model.java.BaseCommandParams.SpoolMode;
import com.facebook.buck.cd.model.java.BaseJarCommand;
import com.facebook.buck.cd.model.java.FilesystemParams;
import com.facebook.buck.cd.model.java.LibraryJarBaseCommand;
import com.facebook.buck.cd.model.java.LibraryJarCommand;
import com.facebook.buck.cd.model.java.UnusedDependenciesParams;
import com.facebook.buck.core.build.buildable.context.BuildableContext;
import com.facebook.buck.core.cell.name.CanonicalCellName;
import com.facebook.buck.core.filesystems.AbsPath;
import com.facebook.buck.core.filesystems.RelPath;
import com.facebook.buck.jvm.cd.serialization.RelPathSerializer;
import com.facebook.buck.jvm.core.BaseJavaAbiInfo;
import com.facebook.buck.jvm.core.BuildTargetValue;
import com.facebook.buck.jvm.java.CompileToJarStepFactory;
import com.facebook.buck.jvm.java.CompilerOutputPathsValue;
import com.facebook.buck.jvm.java.JarParameters;
import com.facebook.buck.jvm.java.ResolvedJavac;
import com.facebook.buck.jvm.java.stepsbuilder.LibraryStepsBuilder;
import com.facebook.buck.jvm.java.stepsbuilder.params.JavaCDParams;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSortedSet;
import java.util.Optional;
import javax.annotation.Nullable;

/** JavaCD implementation of {@link LibraryStepsBuilder} interface. */
public class JavaCDLibraryStepsBuilder extends JavaCDStepsBuilderBase<LibraryJarCommand>
    implements LibraryStepsBuilder {

  private final LibraryJarCommand.Builder commandBuilder = LibraryJarCommand.newBuilder();

  public JavaCDLibraryStepsBuilder(
      boolean hasAnnotationProcessing,
      SpoolMode spoolMode,
      boolean withDownwardApi,
      JavaCDParams javaCDParams) {
    super(hasAnnotationProcessing, spoolMode, withDownwardApi, Type.LIBRARY_JAR, javaCDParams);
  }

  @Override
  public void addBuildStepsForLibrary(
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
      RelPath pathToClassHashes,
      ImmutableSortedSet<RelPath> compileTimeClasspathPaths,
      ImmutableSortedSet<RelPath> javaSrcs,
      ImmutableList<BaseJavaAbiInfo> fullJarInfos,
      ImmutableList<BaseJavaAbiInfo> abiJarInfos,
      ImmutableMap<RelPath, RelPath> resourcesMap,
      ImmutableMap<CanonicalCellName, RelPath> cellToPathMappings,
      @Nullable JarParameters libraryJarParameters,
      AbsPath buildCellRootPath,
      Optional<RelPath> pathToClasses,
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

    commandBuilder.setBaseJarCommand(baseJarCommand);

    LibraryJarBaseCommand.Builder libraryJarBaseCommandBuilder =
        commandBuilder.getLibraryJarBaseCommandBuilder();
    pathToClasses
        .map(RelPathSerializer::serialize)
        .ifPresent(libraryJarBaseCommandBuilder::setPathToClasses);

    recordArtifacts(
        buildableContext,
        compilerOutputPathsValue,
        buildTargetValue,
        javaSrcs,
        trackClassUsage,
        libraryJarParameters);
  }

  @Override
  public void addUnusedDependencyStep(
      UnusedDependenciesParams unusedDependenciesParams,
      ImmutableMap<CanonicalCellName, RelPath> cellToPathMappings,
      String buildTargetFullyQualifiedName) {
    LibraryJarBaseCommand.Builder libraryJarBaseCommandBuilder =
        commandBuilder.getLibraryJarBaseCommandBuilder();
    libraryJarBaseCommandBuilder.setUnusedDependenciesParams(unusedDependenciesParams);
  }

  @Override
  public void addMakeMissingOutputsStep(
      RelPath rootOutput, RelPath pathToClassHashes, RelPath annotationsPath) {
    LibraryJarBaseCommand.Builder libraryJarBaseCommandBuilder =
        commandBuilder.getLibraryJarBaseCommandBuilder();
    libraryJarBaseCommandBuilder.setRootOutput(RelPathSerializer.serialize(rootOutput));
    libraryJarBaseCommandBuilder.setPathToClassHashes(
        RelPathSerializer.serialize(pathToClassHashes));
    libraryJarBaseCommandBuilder.setAnnotationsPath(RelPathSerializer.serialize(annotationsPath));
  }

  @Override
  protected LibraryJarCommand buildCommand() {
    return commandBuilder.build();
  }
}

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

import com.facebook.buck.core.build.context.BuildContext;
import com.facebook.buck.core.filesystems.AbsPath;
import com.facebook.buck.core.filesystems.RelPath;
import com.facebook.buck.core.model.BuildTarget;
import com.facebook.buck.core.rulekey.AddToRuleKey;
import com.facebook.buck.core.sourcepath.resolver.SourcePathResolverAdapter;
import com.facebook.buck.io.filesystem.BuckPaths;
import com.facebook.buck.io.filesystem.ProjectFilesystem;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;
import com.google.common.collect.ImmutableList;
import java.util.Optional;

/** Factory that creates Java related compile build steps. */
public class JavacToJarStepFactory extends DaemonJavacToJarStepFactory
    implements CompileToJarStepFactory.CreatesExtraParams<JavaExtraParams> {

  @AddToRuleKey private final JavacOptions javacOptions;
  @AddToRuleKey private final ExtraClasspathProvider extraClasspathProvider;

  public JavacToJarStepFactory(
      JavacOptions javacOptions,
      ExtraClasspathProvider extraClasspathProvider,
      boolean withDownwardApi) {
    super(
        javacOptions.getSpoolMode(),
        CompileToJarStepFactory.hasAnnotationProcessing(javacOptions),
        withDownwardApi);
    this.javacOptions = javacOptions;
    this.extraClasspathProvider = extraClasspathProvider;
  }

  @Override
  protected Optional<String> getBootClasspath() {
    return getBuildTimeOptions().getBootclasspath();
  }

  @Override
  public ImmutableList<RelPath> getDepFilePaths(
      ProjectFilesystem filesystem, BuildTarget buildTarget) {
    BuckPaths buckPaths = filesystem.getBuckPaths();
    RelPath outputPath = CompilerOutputPaths.of(buildTarget, buckPaths).getOutputJarDirPath();
    return ImmutableList.of(CompilerOutputPaths.getJavaDepFilePath(outputPath));
  }

  @VisibleForTesting
  public JavacOptions getJavacOptions() {
    return javacOptions;
  }

  private JavacOptions getBuildTimeOptions() {
    return javacOptions.withBootclasspathFromContext(extraClasspathProvider);
  }

  /** Creates {@link JavaExtraParams}. */
  @Override
  public JavaExtraParams createExtraParams(BuildContext context, AbsPath rootPath) {
    SourcePathResolverAdapter resolver = context.getSourcePathResolver();
    JavacOptions buildTimeOptions = getBuildTimeOptions();
    ResolvedJavacOptions resolvedJavacOptions =
        ResolvedJavacOptions.of(buildTimeOptions, resolver, rootPath);
    return JavaExtraParams.of(resolvedJavacOptions);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    JavacToJarStepFactory that = (JavacToJarStepFactory) o;
    return Objects.equal(javacOptions, that.javacOptions)
        && Objects.equal(extraClasspathProvider, that.extraClasspathProvider);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(javacOptions, extraClasspathProvider);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("javacOptions", javacOptions)
        .add("extraClasspathProvider", extraClasspathProvider)
        .toString();
  }
}

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

import com.facebook.buck.cd.model.java.BuildTargetValue.Type;
import com.facebook.buck.core.model.BuildTarget;
import com.facebook.buck.core.util.immutables.BuckStyleValue;
import com.facebook.buck.io.filesystem.BuckPaths;
import com.facebook.buck.jvm.core.JavaAbis;

/**
 * Value object that contains {@link CompilerOutputPaths} for library, source abi and source only
 * abi targets as well as library target fully qualified name.
 */
@BuckStyleValue
public abstract class CompilerOutputPathsValue {

  public abstract String getLibraryTargetFullyQualifiedName();

  public abstract CompilerOutputPaths getLibraryCompilerOutputPath();

  public abstract CompilerOutputPaths getSourceAbiCompilerOutputPath();

  public abstract CompilerOutputPaths getSourceOnlyAbiCompilerOutputPath();

  /** Returns {@link CompilerOutputPaths} by given {@code type} */
  public CompilerOutputPaths getByType(Type type) {
    switch (type) {
      case LIBRARY:
        return getLibraryCompilerOutputPath();

      case SOURCE_ABI:
        return getSourceAbiCompilerOutputPath();

      case SOURCE_ONLY_ABI:
        return getSourceOnlyAbiCompilerOutputPath();

      case UNKNOWN:
      case UNRECOGNIZED:
      default:
        throw new IllegalStateException(type + " is not supported");
    }
  }

  /** Creates {@link CompilerOutputPathsValue} */
  public static CompilerOutputPathsValue of(
      String libraryTargetFullyQualifiedName,
      CompilerOutputPaths libraryCompilerOutputPath,
      CompilerOutputPaths sourceAbiCompilerOutputPath,
      CompilerOutputPaths sourceOnlyAbiCompilerOutputPath) {
    return ImmutableCompilerOutputPathsValue.ofImpl(
        libraryTargetFullyQualifiedName,
        libraryCompilerOutputPath,
        sourceAbiCompilerOutputPath,
        sourceOnlyAbiCompilerOutputPath);
  }

  /** Creates {@link CompilerOutputPathsValue} */
  public static CompilerOutputPathsValue of(BuckPaths buckPaths, BuildTarget buildTarget) {

    BuildTarget libraryTarget =
        JavaAbis.isLibraryTarget(buildTarget)
            ? buildTarget
            : JavaAbis.getLibraryTarget(buildTarget);

    CompilerOutputPaths libraryCompilerOutputPaths =
        CompilerOutputPaths.of(libraryTarget, buckPaths);
    CompilerOutputPaths sourceAbiCompilerOutputPaths =
        CompilerOutputPaths.of(JavaAbis.getSourceAbiJar(libraryTarget), buckPaths);
    CompilerOutputPaths sourceOnlyAbiCompilerOutputPaths =
        CompilerOutputPaths.of(JavaAbis.getSourceOnlyAbiJar(libraryTarget), buckPaths);
    return of(
        libraryTarget.getFullyQualifiedName(),
        libraryCompilerOutputPaths,
        sourceAbiCompilerOutputPaths,
        sourceOnlyAbiCompilerOutputPaths);
  }
}

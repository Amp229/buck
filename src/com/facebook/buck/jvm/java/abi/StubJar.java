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

package com.facebook.buck.jvm.java.abi;

import com.facebook.buck.cd.model.java.AbiGenerationMode;
import com.facebook.buck.core.filesystems.AbsPath;
import com.facebook.buck.jvm.java.abi.kotlin.InlineFunctionScope;
import com.facebook.buck.jvm.java.lang.model.ElementsExtended;
import com.facebook.buck.util.zip.JarBuilder;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import javax.annotation.processing.Messager;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.util.Types;

public class StubJar {
  private final Supplier<LibraryReader> libraryReaderSupplier;
  @Nullable private AbiGenerationMode compatibilityMode = null;
  @Nullable private AbsPath existingAbiDir = null;

  public StubJar(AbsPath jarPath) {
    libraryReaderSupplier = () -> LibraryReader.of(jarPath.getPath());
  }

  /**
   * @param targetVersion the class file version to output, expressed as the corresponding Java
   *     source version
   */
  public StubJar(
      SourceVersion targetVersion,
      ElementsExtended elements,
      Types types,
      Messager messager,
      Iterable<Element> topLevelElements,
      boolean includeParameterMetadata) {
    libraryReaderSupplier =
        () ->
            LibraryReader.of(
                targetVersion,
                elements,
                types,
                messager,
                topLevelElements,
                includeParameterMetadata);
  }

  /**
   * Filters the stub jar through {@link SourceAbiCompatibleVisitor}. See that class for details.
   */
  public StubJar setCompatibilityMode(AbiGenerationMode compatibilityMode) {
    this.compatibilityMode = compatibilityMode;
    return this;
  }

  /** Specify a directory of existing abi we need to inherit */
  public StubJar setExistingAbiDir(AbsPath existingAbiDir) {
    this.existingAbiDir = existingAbiDir;
    return this;
  }

  /** Writes output into the passed absolute path. */
  public void writeTo(AbsPath outputAbsPath) throws IOException {
    // The order of these declarations is important -- FilesystemStubJarWriter actually uses
    // the LibraryReader in its close method, and try-with-resources closes the items in the
    // opposite order of their creation.
    try (LibraryReader input = libraryReaderSupplier.get();
        StubJarWriter writer = new FilesystemStubJarWriter(outputAbsPath)) {
      writeTo(input, writer);
    }
  }

  public void writeTo(JarBuilder jarBuilder) throws IOException {
    try (LibraryReader input = libraryReaderSupplier.get();
        StubJarWriter writer = new JarBuilderStubJarWriter(jarBuilder)) {
      writeTo(input, writer);
    }
  }

  private void writeTo(LibraryReader input, StubJarWriter writer) throws IOException {
    List<Path> relativePaths = input.getRelativePaths();
    Comparator<Path> visitOuterClassesFirst = Comparator.comparing(StubJar::pathWithoutClassSuffix);
    List<Path> paths =
        relativePaths.stream().sorted(visitOuterClassesFirst).collect(Collectors.toList());

    InlineFunctionScope inlineFunctionScope =
        isKotlinModule(relativePaths) ? new InlineFunctionScope() : null;

    for (Path path : paths) {
      StubJarEntry entry = StubJarEntry.of(input, path, compatibilityMode, inlineFunctionScope);
      if (entry == null) {
        continue;
      }
      entry.write(writer);
      if (inlineFunctionScope != null) {
        String pathNoSuffix = pathWithoutClassSuffix(path);
        inlineFunctionScope.createScopes(pathNoSuffix, entry.getInlineFunctions());
        if (entry.extendsInlineFunctionScope()) {
          inlineFunctionScope.extendScope(pathNoSuffix);
        }
      }
    }
  }

  private boolean isKotlinModule(List<Path> relativePaths) {
    return relativePaths.stream().anyMatch(path -> path.toString().endsWith(".kotlin_module"));
  }

  static String pathWithoutClassSuffix(Path path) {
    final String pathString = path.toString();
    return pathString.endsWith(".class")
        ? pathString.substring(0, pathString.length() - ".class".length())
        : pathString;
  }
}

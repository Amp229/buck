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

package com.facebook.buck.features.python;

import com.facebook.buck.core.model.BuildTarget;
import com.facebook.buck.core.rules.ActionGraphBuilder;
import com.facebook.buck.core.rules.BuildRule;
import com.facebook.buck.core.rules.BuildRuleResolver;
import com.facebook.buck.core.rules.attr.HasRuntimeDeps;
import com.facebook.buck.core.rules.impl.NoopBuildRule;
import com.facebook.buck.cxx.toolchain.CxxPlatform;
import com.facebook.buck.features.python.toolchain.PythonPlatform;
import com.facebook.buck.io.file.MorePaths;
import com.facebook.buck.io.filesystem.ProjectFilesystem;
import com.google.common.collect.ImmutableSortedMap;
import com.google.common.collect.ImmutableSortedSet;
import java.nio.file.Path;
import java.util.Optional;
import java.util.SortedSet;
import java.util.function.Supplier;
import java.util.stream.Stream;

public class PythonLibrary extends NoopBuildRule implements PythonPackagable, HasRuntimeDeps {

  private final Supplier<? extends SortedSet<BuildRule>> declareDeps;
  private Optional<Boolean> zipSafe;
  private boolean excludeDepsFromOmnibus;

  PythonLibrary(
      BuildTarget buildTarget,
      ProjectFilesystem projectFilesystem,
      Supplier<? extends SortedSet<BuildRule>> declareDeps,
      Optional<Boolean> zipSafe,
      boolean excludeDepsFromOmnibus) {
    super(buildTarget, projectFilesystem);
    this.declareDeps = declareDeps;
    this.zipSafe = zipSafe;
    this.excludeDepsFromOmnibus = excludeDepsFromOmnibus;
  }

  private <T> T getMetadata(
      PythonPlatform pythonPlatform,
      CxxPlatform cxxPlatform,
      ActionGraphBuilder graphBuilder,
      PythonLibraryDescription.MetadataType type,
      Class<T> clazz) {
    return graphBuilder
        .requireMetadata(
            getBuildTarget()
                .withAppendedFlavors(
                    type.getFlavor(), pythonPlatform.getFlavor(), cxxPlatform.getFlavor()),
            clazz)
        .orElseThrow(IllegalStateException::new);
  }

  @Override
  @SuppressWarnings("unchecked")
  public Iterable<BuildRule> getPythonPackageDeps(
      PythonPlatform pythonPlatform, CxxPlatform cxxPlatform, ActionGraphBuilder graphBuilder) {
    return getMetadata(
        pythonPlatform,
        cxxPlatform,
        graphBuilder,
        PythonLibraryDescription.MetadataType.PACKAGE_DEPS,
        ImmutableSortedSet.class);
  }

  @Override
  @SuppressWarnings("unchecked")
  public Optional<PythonMappedComponents> getPythonModules(
      PythonPlatform pythonPlatform, CxxPlatform cxxPlatform, ActionGraphBuilder graphBuilder) {
    return getMetadata(
        pythonPlatform,
        cxxPlatform,
        graphBuilder,
        PythonLibraryDescription.MetadataType.MODULES,
        Optional.class);
  }

  @Override
  @SuppressWarnings("unchecked")
  public Optional<PythonMappedComponents> getPythonResources(
      PythonPlatform pythonPlatform, CxxPlatform cxxPlatform, ActionGraphBuilder graphBuilder) {
    return getMetadata(
        pythonPlatform,
        cxxPlatform,
        graphBuilder,
        PythonLibraryDescription.MetadataType.RESOURCES,
        Optional.class);
  }

  @SuppressWarnings("unchecked")
  private Optional<PythonMappedComponents> getPythonSources(
      PythonPlatform pythonPlatform, CxxPlatform cxxPlatform, ActionGraphBuilder graphBuilder) {
    return getMetadata(
        pythonPlatform,
        cxxPlatform,
        graphBuilder,
        PythonLibraryDescription.MetadataType.SOURCES,
        Optional.class);
  }

  @Override
  @SuppressWarnings("unchecked")
  public Optional<? extends PythonComponents> getPythonModulesForTyping(
      PythonPlatform pythonPlatform, CxxPlatform cxxPlatform, ActionGraphBuilder graphBuilder) {
    return getMetadata(
        pythonPlatform,
        cxxPlatform,
        graphBuilder,
        PythonLibraryDescription.MetadataType.MODULES_FOR_TYPING,
        Optional.class);
  }

  @Override
  public Optional<PythonComponents> getPythonBytecode(
      PythonPlatform pythonPlatform, CxxPlatform cxxPlatform, ActionGraphBuilder graphBuilder) {
    return getPythonSources(pythonPlatform, cxxPlatform, graphBuilder)
        .map(
            sources -> {
              PythonCompileRule compileRule =
                  (PythonCompileRule)
                      graphBuilder.requireRule(
                          getBuildTarget()
                              .withAppendedFlavors(
                                  pythonPlatform.getFlavor(),
                                  cxxPlatform.getFlavor(),
                                  PythonLibraryDescription.LibraryType.COMPILE.getFlavor()));
              return compileRule.getCompiledSources();
            });
  }

  @Override
  public Optional<Boolean> isPythonZipSafe() {
    return zipSafe;
  }

  @Override
  public Stream<BuildTarget> getRuntimeDeps(BuildRuleResolver buildRuleResolver) {
    return declareDeps.get().stream().map(BuildRule::getBuildTarget);
  }

  @Override
  public boolean doesPythonPackageDisallowOmnibus(
      PythonPlatform pythonPlatform, CxxPlatform cxxPlatform, ActionGraphBuilder graphBuilder) {
    if (excludeDepsFromOmnibus) {
      return true;
    }

    // In some cases, Python library rules package prebuilt native extensions, in which case, we
    // can't support library merging (since we can't re-link these extensions).
    for (Path module :
        getPythonModules(pythonPlatform, cxxPlatform, graphBuilder)
            .map(PythonMappedComponents::getComponents)
            .orElse(ImmutableSortedMap.of())
            .keySet()) {
      if (PythonUtil.isNativeExt(MorePaths.getFileExtension(module))) {
        return true;
      }
    }

    return false;
  }
}

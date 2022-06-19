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

package com.facebook.buck.features.project.intellij;

import com.facebook.buck.core.model.BuildTarget;
import com.facebook.buck.core.model.targetgraph.TargetGraph;
import com.facebook.buck.features.project.intellij.aggregation.DefaultAggregationModuleFactory;
import com.facebook.buck.features.project.intellij.depsquery.IjDepsQueryResolver;
import com.facebook.buck.features.project.intellij.lang.java.ParsingJavaPackageFinder;
import com.facebook.buck.features.project.intellij.model.IjLibraryFactory;
import com.facebook.buck.features.project.intellij.model.IjModuleFactoryResolver;
import com.facebook.buck.features.project.intellij.model.IjProjectConfig;
import com.facebook.buck.io.filesystem.ProjectFilesystem;
import com.facebook.buck.jvm.core.JavaPackageFinder;
import com.facebook.buck.jvm.java.JavaFileParser;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import java.io.IOException;
import java.util.Optional;
import java.util.Set;

/** Top-level class for IntelliJ project generation. */
public class IjProject {

  private final TargetGraph targetGraph;
  private final JavaPackageFinder javaPackageFinder;
  private final JavaFileParser javaFileParser;
  private final ProjectFilesystem projectFilesystem;
  private final IjProjectConfig projectConfig;
  private final ProjectFilesystem outFilesystem;
  private final IJProjectCleaner cleaner;
  private final IjDepsQueryResolver depsQueryResolver;

  public IjProject(
      TargetGraph targetGraph,
      JavaPackageFinder javaPackageFinder,
      JavaFileParser javaFileParser,
      ProjectFilesystem projectFilesystem,
      IjProjectConfig projectConfig,
      ProjectFilesystem outFilesystem,
      IjDepsQueryResolver depsQueryResolver) {
    this.targetGraph = targetGraph;
    this.javaPackageFinder = javaPackageFinder;
    this.javaFileParser = javaFileParser;
    this.projectFilesystem = projectFilesystem;
    this.projectConfig = projectConfig;
    this.outFilesystem = outFilesystem;
    this.depsQueryResolver = depsQueryResolver;
    cleaner = new IJProjectCleaner(outFilesystem);
  }

  /**
   * Write the project to disk.
   *
   * @return set of {@link BuildTarget}s which should be built in order for the project to index
   *     correctly.
   * @throws IOException
   */
  public ImmutableSet<BuildTarget> write() throws IOException {
    final ImmutableSet<BuildTarget> buildTargets = performWriteOrUpdate(false);
    clean();
    return buildTargets;
  }

  /**
   * Write a subset of the project to disk, specified by the targets passed on the command line.
   * Does not perform a clean of the project space after updating.
   *
   * @return set of {@link BuildTarget}s which should be built in to allow indexing
   * @throws IOException
   */
  public ImmutableSet<BuildTarget> update() throws IOException {
    return performWriteOrUpdate(true);
  }

  /**
   * Perform the write to disk.
   *
   * @param updateOnly whether to write all modules in the graph to disk, or only the ones which
   *     correspond to the listed targets
   * @return set of {@link BuildTarget}s which should be built in order for the project to index
   *     correctly.
   * @throws IOException
   */
  private ImmutableSet<BuildTarget> performWriteOrUpdate(boolean updateOnly) throws IOException {
    final Optional<Set<BuildTarget>> requiredBuildTargets =
        projectConfig.isSkipBuildEnabled()
            ? Optional.empty()
            : Optional.of(Sets.newConcurrentHashSet());
    IjProjectSourcePathResolver sourcePathResolver = new IjProjectSourcePathResolver(targetGraph);
    TargetInfoMapManager targetInfoMapManager =
        new TargetInfoMapManager(targetGraph, projectConfig, outFilesystem, updateOnly);
    IjLibraryFactory libraryFactory =
        new DefaultIjLibraryFactory(
            targetGraph,
            new DefaultIjLibraryFactoryResolver(
                projectFilesystem, sourcePathResolver, requiredBuildTargets));
    IjModuleFactoryResolver moduleFactoryResolver =
        new DefaultIjModuleFactoryResolver(
            sourcePathResolver, projectFilesystem, requiredBuildTargets, targetGraph);
    SupportedTargetTypeRegistry typeRegistry =
        new SupportedTargetTypeRegistry(
            projectFilesystem,
            moduleFactoryResolver,
            depsQueryResolver,
            projectConfig,
            javaPackageFinder);
    IjModuleGraph moduleGraph =
        IjModuleGraphFactory.from(
            projectFilesystem,
            projectConfig,
            targetGraph,
            libraryFactory,
            new DefaultIjModuleFactory(projectFilesystem, projectConfig, typeRegistry),
            new DefaultAggregationModuleFactory(typeRegistry),
            targetInfoMapManager,
            typeRegistry);
    JavaPackageFinder parsingJavaPackageFinder =
        ParsingJavaPackageFinder.preparse(
            javaFileParser,
            projectFilesystem,
            IjProjectTemplateDataPreparer.createPackageLookupPathSet(moduleGraph),
            javaPackageFinder);
    IjProjectTemplateDataPreparer templateDataPreparer =
        new IjProjectTemplateDataPreparer(
            parsingJavaPackageFinder, moduleGraph, projectFilesystem, projectConfig);
    IntellijModulesListParser modulesParser = new IntellijModulesListParser();
    BuckOutPathConverter buckOutPathConverter = new BuckOutPathConverter(projectConfig);
    IjProjectWriter writer =
        new IjProjectWriter(
            templateDataPreparer,
            projectConfig,
            projectFilesystem,
            modulesParser,
            cleaner,
            outFilesystem,
            buckOutPathConverter);

    if (updateOnly) {
      writer.update();
    } else {
      writer.write();
    }

    if (projectConfig.isGeneratingTargetInfoMapEnabled()) {
      targetInfoMapManager.write(
          templateDataPreparer.getModulesToBeWritten(),
          templateDataPreparer.getAllLibraries(),
          buckOutPathConverter,
          cleaner);
    }

    if (projectConfig.isGeneratingBinaryTargetInfoEnabled()) {
      targetInfoMapManager.writeBinaryInfo(
          templateDataPreparer.getModulesToBeWritten(),
          templateDataPreparer.getAllLibraries(),
          buckOutPathConverter,
          cleaner);
    }

    PregeneratedCodeWriter pregeneratedCodeWriter =
        new PregeneratedCodeWriter(templateDataPreparer, projectConfig, outFilesystem, cleaner);
    pregeneratedCodeWriter.write();

    TargetConfigurationInfoManager targetConfigInfoManager =
        new TargetConfigurationInfoManager(projectConfig, outFilesystem, updateOnly);

    if (projectConfig.isGeneratingTargetConfigurationMapEnabled()) {
      targetConfigInfoManager.write(
          templateDataPreparer.getModulesToBeWritten(),
          templateDataPreparer.getAllLibraries(),
          cleaner);
    }

    if (projectConfig.getGeneratedFilesListFilename().isPresent()) {
      cleaner.writeFilesToKeepToFile(projectConfig.getGeneratedFilesListFilename().get());
    }

    return requiredBuildTargets.map(ImmutableSet::copyOf).orElse(ImmutableSet.of());
  }

  /**
   * Run the cleaner after a successful call to write(). This removes stale project files from
   * previous runs.
   */
  private void clean() {
    cleaner.clean(
        projectConfig.getBuckConfig(),
        projectConfig.getProjectPaths().getIdeaConfigDir(),
        projectConfig.getProjectPaths().getLibrariesDir(),
        projectConfig.isCleanerEnabled(),
        projectConfig.isRemovingUnusedLibrariesEnabled());
  }
}

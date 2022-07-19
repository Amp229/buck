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

import com.facebook.buck.core.description.arg.BuildRuleArg;
import com.facebook.buck.core.model.BuildTarget;
import com.facebook.buck.core.model.targetgraph.TargetGraph;
import com.facebook.buck.core.model.targetgraph.TargetNode;
import com.facebook.buck.features.project.intellij.aggregation.AggregationModule;
import com.facebook.buck.features.project.intellij.aggregation.AggregationModuleFactory;
import com.facebook.buck.features.project.intellij.aggregation.AggregationTree;
import com.facebook.buck.features.project.intellij.model.DependencyType;
import com.facebook.buck.features.project.intellij.model.IjLibrary;
import com.facebook.buck.features.project.intellij.model.IjLibraryFactory;
import com.facebook.buck.features.project.intellij.model.IjModule;
import com.facebook.buck.features.project.intellij.model.IjModuleFactory;
import com.facebook.buck.features.project.intellij.model.IjModuleRule;
import com.facebook.buck.features.project.intellij.model.IjModuleType;
import com.facebook.buck.features.project.intellij.model.IjProjectConfig;
import com.facebook.buck.features.project.intellij.model.IjProjectElement;
import com.facebook.buck.features.project.intellij.model.LibraryBuildContext;
import com.facebook.buck.io.filesystem.ProjectFilesystem;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

public final class IjModuleGraphFactory {
  /**
   * Create all the modules we are capable of representing in IntelliJ from the supplied graph.
   *
   * @param targetGraph graph whose nodes will be converted to {@link IjModule}s.
   * @return map which for every BuildTarget points to the corresponding IjModule. Multiple
   *     BuildTarget can point to one IjModule (many:one mapping), the BuildTargetPaths which can't
   *     be prepresented in IntelliJ are missing from this mapping.
   */
  @SuppressWarnings({"unchecked", "rawtypes"})
  private static ImmutableMap<BuildTarget, IjModule> createModules(
      ProjectFilesystem projectFilesystem,
      IjProjectConfig projectConfig,
      TargetGraph targetGraph,
      IjModuleFactory moduleFactory,
      AggregationModuleFactory aggregationModuleFactory,
      int minimumPathDepth,
      ImmutableSet<String> ignoredTargetLabels,
      SupportedTargetTypeRegistry typeRegistry) {

    Stream<TargetNode<?>> nodes =
        targetGraph.getNodes().stream()
            .map(
                targetNode ->
                    IjAliasHelper.isAliasNode(targetNode)
                        ? IjAliasHelper.resolveAliasNode(targetGraph, targetNode)
                        : targetNode)
            .filter(
                input ->
                    SupportedTargetTypeRegistry.isTargetTypeSupported(
                        input.getDescription().getClass()))
            .filter(
                targetNode -> {
                  BuildRuleArg arg = (BuildRuleArg) targetNode.getConstructorArg();
                  return !arg.labelsContainsAnyOf(ignoredTargetLabels);
                })
            // Experimental support for generating modules outside the project root
            .filter(
                targetNode ->
                    projectConfig.isMultiCellModuleSupportEnabled()
                        || isInRootCell(projectFilesystem, targetNode));

    ImmutableListMultimap<Path, TargetNode<?>> baseTargetPathMultimap =
        (projectConfig.getProjectRoot().isEmpty()
                ? nodes
                : nodes.filter(
                    targetNode ->
                        shouldConvertToIjModule(
                            projectFilesystem, projectConfig.getProjectRoot(), targetNode)))
            .collect(
                ImmutableListMultimap.toImmutableListMultimap(
                    targetNode -> {
                      Path modulePath =
                          IjProjectPaths.getModulePathForNode(targetNode, projectFilesystem);
                      IjModuleRule<?> rule =
                          typeRegistry.getModuleRuleByTargetNodeType(
                              targetNode.getDescription().getClass());
                      return rule != null
                          ? rule.adjustModulePath((TargetNode) targetNode, modulePath)
                          : modulePath;
                    },
                    targetNode -> targetNode));

    AggregationTree aggregationTree =
        createAggregationTree(projectConfig, aggregationModuleFactory, baseTargetPathMultimap);

    aggregationTree.aggregateModules(minimumPathDepth);

    ImmutableMap.Builder<BuildTarget, IjModule> moduleByBuildTarget = new ImmutableMap.Builder<>();

    aggregationTree
        .getModules()
        .parallelStream()
        .filter(aggregationModule -> !aggregationModule.getTargets().isEmpty())
        .forEach(
            aggregationModule -> {
              IjModule module =
                  moduleFactory.createModule(
                      aggregationModule.getModuleBasePath(),
                      aggregationModule.getTargets(),
                      aggregationModule.getExcludes());
              synchronized (moduleByBuildTarget) {
                module
                    .getTargets()
                    .forEach(buildTarget -> moduleByBuildTarget.put(buildTarget, module));
              }
            });

    return moduleByBuildTarget.build();
  }

  private static boolean shouldConvertToIjModule(
      ProjectFilesystem projectFilesystem, String projectRoot, TargetNode<?> targetNode) {
    return targetNode
        .getBuildTarget()
        .getCellRelativeBasePath()
        .getPath()
        .toPath(projectFilesystem.getFileSystem())
        .startsWith(projectRoot);
  }

  private static AggregationTree createAggregationTree(
      IjProjectConfig projectConfig,
      AggregationModuleFactory aggregationModuleFactory,
      ImmutableListMultimap<Path, TargetNode<?>> targetNodesByBasePath) {
    Map<Path, AggregationModule> pathToAggregationModuleMap =
        targetNodesByBasePath.asMap().entrySet().stream()
            .collect(
                ImmutableMap.toImmutableMap(
                    Map.Entry::getKey,
                    pathWithTargetNode ->
                        aggregationModuleFactory.createAggregationModule(
                            pathWithTargetNode.getKey(),
                            ImmutableSet.copyOf(pathWithTargetNode.getValue()))));

    Path rootPath = Paths.get("");

    AggregationModule rootAggregationModule = pathToAggregationModuleMap.get(rootPath);
    if (rootAggregationModule == null) {
      rootAggregationModule =
          aggregationModuleFactory.createAggregationModule(rootPath, ImmutableSet.of());
    }

    AggregationTree aggregationTree = new AggregationTree(projectConfig, rootAggregationModule);

    pathToAggregationModuleMap.entrySet().stream()
        .filter(e -> !rootPath.equals(e.getKey()))
        .forEach(e -> aggregationTree.addModule(e.getKey(), e.getValue()));

    return aggregationTree;
  }

  private static ImmutableSet<IjProjectElement> getProjectElementFromBuildTargets(
      TargetGraph targetGraph,
      IjLibraryFactory libraryFactory,
      ImmutableMap<BuildTarget, IjModule> rulesToModules,
      IjModule module,
      int depCount,
      Stream<BuildTarget> buildTargetStream) {

    ImmutableSet.Builder<IjProjectElement> result = ImmutableSet.builderWithExpectedSize(depCount);
    Iterator<BuildTarget> i = buildTargetStream.iterator();
    while (i.hasNext()) {
      BuildTarget target = i.next();
      if (module.getTargets().contains(target)) {
        continue;
      }

      IjModule depModule = rulesToModules.get(target);
      if (depModule != null) {
        result.add(depModule);
      }
      if (depModule == null || depModule.getNonSourceBuildTargets().contains(target)) {
        // all BuildTarget's are merged into IJModule
        // if a BuildTarget is not built from Java sources, it will also be added as a
        // library
        TargetNode<?> targetNode = targetGraph.get(target);
        Optional<LibraryBuildContext> lib = libraryFactory.getLibrary(targetNode);

        lib.ifPresent(result::add);
      }
    }
    return result.build();
  }

  /**
   * @param projectConfig the project config used
   * @param targetGraph input graph.
   * @param libraryFactory library factory.
   * @param moduleFactory module factory.
   * @return module graph corresponding to the supplied {@link TargetGraph}. Multiple targets from
   *     the same base path are mapped to a single module, therefore an IjModuleGraph edge exists
   *     between two modules (Ma, Mb) if a TargetGraph edge existed between a pair of nodes (Ta, Tb)
   *     and Ma contains Ta and Mb contains Tb.
   */
  public static IjModuleGraph from(
      ProjectFilesystem projectFilesystem,
      IjProjectConfig projectConfig,
      TargetGraph targetGraph,
      IjLibraryFactory libraryFactory,
      IjModuleFactory moduleFactory,
      AggregationModuleFactory aggregationModuleFactory,
      TargetInfoMapManager targetInfoMapManager,
      SupportedTargetTypeRegistry typeRegistry) {
    ImmutableSet<String> ignoredTargetLabels = projectConfig.getIgnoredTargetLabels();
    ImmutableMap<BuildTarget, IjModule> rulesToModules =
        createModules(
            projectFilesystem,
            projectConfig,
            targetGraph,
            moduleFactory,
            aggregationModuleFactory,
            projectConfig.getAggregationMode().getGraphMinimumDepth(targetGraph.getNodes().size()),
            ignoredTargetLabels,
            typeRegistry);
    ExportedDepsClosureResolver exportedDepsClosureResolver =
        new ExportedDepsClosureResolver(targetGraph, ignoredTargetLabels);
    TransitiveDepsClosureResolver transitiveDepsClosureResolver =
        new TransitiveDepsClosureResolver(targetGraph, ignoredTargetLabels);
    Map<IjModule, Map<IjProjectElement, DependencyType>> moduleDepGraph = new HashMap<>();
    ImmutableMap.Builder<IjProjectElement, ImmutableMap<IjProjectElement, DependencyType>>
        depsBuilder = ImmutableMap.builder();
    Map<String, Integer> libraryReferenceCounter = new HashMap<>();
    Optional<Path> extraCompileOutputRootPath = projectConfig.getExtraCompilerOutputModulesPath();

    Set<IjModule> seenModules = new HashSet<>();
    for (IjModule module : rulesToModules.values()) {
      if (!seenModules.add(module)) {
        continue;
      }

      Map<IjProjectElement, DependencyType> moduleDeps = new LinkedHashMap<>();

      module
          .getExtraLibraryDependencies()
          .forEach(
              library -> moduleDeps.put(new LibraryBuildContext(library), DependencyType.PROD));

      if (extraCompileOutputRootPath.isPresent()
          && !module.getExtraModuleDependencies().isEmpty()) {
        IjModule extraModule =
            createExtraModuleForCompilerOutput(module, extraCompileOutputRootPath.get());
        moduleDeps.put(extraModule, DependencyType.PROD);
        depsBuilder.put(extraModule, ImmutableMap.of());
      }

      for (Map.Entry<BuildTarget, DependencyType> entry : module.getDependencies().entrySet()) {
        BuildTarget depBuildTarget = entry.getKey();
        TargetNode<?> depTargetNode = targetGraph.get(depBuildTarget);

        BuildRuleArg arg = (BuildRuleArg) depTargetNode.getConstructorArg();
        if (arg.labelsContainsAnyOf(ignoredTargetLabels)) {
          continue;
        }

        DependencyType depType = entry.getValue();
        ImmutableSet<IjProjectElement> depElements;
        ImmutableSet<IjProjectElement> transitiveDepElements = ImmutableSet.of();

        if (depType.equals(DependencyType.COMPILED_SHADOW)) {
          Optional<LibraryBuildContext> library = libraryFactory.getLibrary(depTargetNode);
          if (library.isPresent()) {
            depElements = ImmutableSet.of(library.get());
          } else {
            depElements = ImmutableSet.of();
          }
        } else {
          ImmutableSet<BuildTarget> exportedDeps =
              exportedDepsClosureResolver.getExportedDepsClosure(depBuildTarget);

          depElements =
              getProjectElementFromBuildTargets(
                  targetGraph,
                  libraryFactory,
                  rulesToModules,
                  module,
                  exportedDeps.size() + 1,
                  Stream.concat(exportedDeps.stream(), Stream.of(depBuildTarget)));
          if (projectConfig.isIncludeTransitiveDependency()) {
            ImmutableSet<BuildTarget> transitiveDeps =
                transitiveDepsClosureResolver.getTransitiveDepsClosure(depBuildTarget);
            transitiveDepElements =
                getProjectElementFromBuildTargets(
                    targetGraph,
                    libraryFactory,
                    rulesToModules,
                    module,
                    transitiveDeps.size() + 1,
                    Stream.concat(transitiveDeps.stream(), Stream.of(depBuildTarget)));
          }
        }

        for (IjProjectElement depElement : transitiveDepElements) {
          Preconditions.checkState(!depElement.equals(module));
          DependencyType.putWithMerge(moduleDeps, depElement, DependencyType.RUNTIME);
        }
        for (IjProjectElement depElement : depElements) {
          Preconditions.checkState(!depElement.equals(module));
          DependencyType.putWithMerge(moduleDeps, depElement, depType);
        }
      }

      moduleDeps.keySet().stream()
          .filter(dep -> dep instanceof LibraryBuildContext)
          .map(library -> (LibraryBuildContext) library)
          .forEach(
              library ->
                  libraryReferenceCounter.put(
                      library.getName(),
                      libraryReferenceCounter.getOrDefault(library.getName(), 0) + 1));

      moduleDepGraph.put(module, moduleDeps);
    }

    Set<IjLibrary> referencedLibraries =
        addModuleDepsAndGetReferencedLibraries(
            moduleDepGraph,
            libraryReferenceCounter,
            depsBuilder,
            libraryFactory,
            targetInfoMapManager,
            projectConfig);

    referencedLibraries.forEach(library -> depsBuilder.put(library, ImmutableMap.of()));

    return new IjModuleGraph(depsBuilder.build());
  }

  private static IjModule createExtraModuleForCompilerOutput(
      IjModule module, Path extraCompileOutputRootPath) {
    return IjModule.builder()
        .setModuleBasePath(extraCompileOutputRootPath.resolve(module.getModuleBasePath()))
        .setTargets(ImmutableSet.of())
        .addAllFolders(ImmutableSet.of())
        .putAllDependencies(ImmutableMap.of())
        .setLanguageLevel(module.getLanguageLevel())
        .setModuleType(IjModuleType.ANDROID_MODULE)
        .setCompilerOutputPath(module.getExtraModuleDependencies().asList().get(0))
        .build();
  }

  private static boolean isInRootCell(
      ProjectFilesystem projectFilesystem, TargetNode<?> targetNode) {
    return targetNode.getFilesystem().equals(projectFilesystem);
  }

  private static Set<IjLibrary> addModuleDepsAndGetReferencedLibraries(
      Map<IjModule, Map<IjProjectElement, DependencyType>> moduleDepGraph,
      Map<String, Integer> libraryReferenceCounter,
      ImmutableMap.Builder<IjProjectElement, ImmutableMap<IjProjectElement, DependencyType>>
          depsBuilder,
      IjLibraryFactory libraryFactory,
      TargetInfoMapManager targetInfoMapManager,
      IjProjectConfig projectConfig) {
    Set<IjLibrary> referencedLibraries = new HashSet<>();
    boolean canConvertToModuleLibrary =
        projectConfig.isModuleLibraryEnabled() && projectConfig.getModuleLibraryThreshold() > 0;
    moduleDepGraph.forEach(
        (module, depElementToDependencyTypeMap) -> {
          depsBuilder.put(
              module,
              depElementToDependencyTypeMap.entrySet().stream()
                  .collect(
                      ImmutableMap.toImmutableMap(
                          moduleDepEntry -> {
                            IjProjectElement depElement;
                            if (canConvertToModuleLibrary
                                && moduleDepEntry.getKey() instanceof LibraryBuildContext) {
                              depElement =
                                  convertLibraryIfNecessary(
                                      libraryReferenceCounter,
                                      libraryFactory,
                                      targetInfoMapManager,
                                      projectConfig,
                                      (LibraryBuildContext) moduleDepEntry.getKey());
                            } else {
                              depElement = moduleDepEntry.getKey();
                            }
                            if (depElement instanceof LibraryBuildContext) {
                              // Create the aggregated immutable library
                              IjLibrary library =
                                  ((LibraryBuildContext) depElement).getAggregatedLibrary();
                              referencedLibraries.add(library);
                              return library;
                            }
                            return depElement;
                          },
                          Map.Entry::getValue)));
        });
    return referencedLibraries;
  }

  private static LibraryBuildContext convertLibraryIfNecessary(
      Map<String, Integer> libraryReferenceCounter,
      IjLibraryFactory libraryFactory,
      TargetInfoMapManager targetInfoMapManager,
      IjProjectConfig projectConfig,
      LibraryBuildContext library) {
    Integer referenceCount = libraryReferenceCounter.getOrDefault(library.getName(), null);
    if (referenceCount != null
        && referenceCount <= projectConfig.getModuleLibraryThreshold()
        && library.getLevel() != IjLibrary.Level.MODULE
        && !targetInfoMapManager.isProjectLibrary(library.getName())) {
      return libraryFactory.getOrConvertToModuleLibrary(library).orElse(library);
    } else {
      return library;
    }
  }

  private IjModuleGraphFactory() {}
}

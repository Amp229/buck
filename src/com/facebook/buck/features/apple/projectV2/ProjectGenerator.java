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

package com.facebook.buck.features.apple.projectV2;

import com.dd.plist.NSDictionary;
import com.facebook.buck.apple.AppleConfig;
import com.facebook.buck.apple.AppleDependenciesCache;
import com.facebook.buck.apple.XCodeDescriptions;
import com.facebook.buck.apple.xcode.AbstractPBXObjectFactory;
import com.facebook.buck.apple.xcode.GidGenerator;
import com.facebook.buck.apple.xcode.XcodeprojSerializer;
import com.facebook.buck.apple.xcode.xcodeproj.PBXContainerItemProxy;
import com.facebook.buck.apple.xcode.xcodeproj.PBXNativeTarget;
import com.facebook.buck.apple.xcode.xcodeproj.PBXProject;
import com.facebook.buck.apple.xcode.xcodeproj.PBXTarget;
import com.facebook.buck.apple.xcode.xcodeproj.XCBuildConfiguration;
import com.facebook.buck.core.build.context.BuildContext;
import com.facebook.buck.core.cell.Cell;
import com.facebook.buck.core.cell.Cells;
import com.facebook.buck.core.exceptions.HumanReadableException;
import com.facebook.buck.core.model.BuildTarget;
import com.facebook.buck.core.model.Flavor;
import com.facebook.buck.core.model.targetgraph.TargetGraph;
import com.facebook.buck.core.model.targetgraph.TargetNode;
import com.facebook.buck.core.rules.ActionGraphBuilder;
import com.facebook.buck.core.sourcepath.BuildTargetSourcePath;
import com.facebook.buck.core.sourcepath.SourcePath;
import com.facebook.buck.core.sourcepath.resolver.SourcePathResolverAdapter;
import com.facebook.buck.core.util.log.Logger;
import com.facebook.buck.cxx.config.CxxBuckConfig;
import com.facebook.buck.cxx.toolchain.UnresolvedCxxPlatform;
import com.facebook.buck.event.BuckEventBus;
import com.facebook.buck.event.ProjectGenerationEvent;
import com.facebook.buck.event.SimplePerfEvent;
import com.facebook.buck.features.halide.HalideBuckConfig;
import com.facebook.buck.io.file.MorePaths;
import com.facebook.buck.io.file.MorePosixFilePermissions;
import com.facebook.buck.io.filesystem.MoreProjectFilesystems;
import com.facebook.buck.io.filesystem.ProjectFilesystem;
import com.facebook.buck.rules.keys.config.RuleKeyConfiguration;
import com.facebook.buck.swift.SwiftBuckConfig;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.UncheckedExecutionException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/** Generates an Xcode project and writes the output to disk. */
public class ProjectGenerator {
  private static final Logger LOG = Logger.get(ProjectGenerator.class);

  // TODO(chatatap): This is the same as REPO_ROOT, which can probably be dropped/consolidated.
  private static final String BUCK_CELL_RELATIVE_PATH = "BUCK_CELL_RELATIVE_PATH";

  private final XCodeDescriptions xcodeDescriptions;
  private final TargetGraph targetGraph;
  private final AppleDependenciesCache dependenciesCache;
  private final ProjectGenerationStateCache projGenerationStateCache;
  private final Cells cells;
  private final Cell projectCell;
  private final ProjectFilesystem projectFilesystem;
  private final ImmutableSet<BuildTarget> projectTargets;

  private final String buildFileName;
  private final ProjectGeneratorOptions options;

  private final ActionGraphBuilder actionGraphBuilder;
  private final SourcePathResolverAdapter defaultPathResolver;
  private final BuckEventBus buckEventBus;

  private final ImmutableSet<Flavor> appleCxxFlavors;
  private final HalideBuckConfig halideBuckConfig;
  private final CxxBuckConfig cxxBuckConfig;
  private final SwiftBuckConfig swiftBuckConfig;
  private final AppleConfig appleConfig;
  private final BuildTarget workspaceTarget;
  private final ImmutableSet<BuildTarget> targetsInRequiredProjects;

  private final SwiftAttributeParser swiftAttributeParser;
  private final ProjectSourcePathResolver projectSourcePathResolver;
  private final RuleKeyConfiguration ruleKeyConfiguration;

  private final ProjectExcludeResolver projectExcludeResolver;
  private final ProjectExcludeResolver projectExcludeFromBuildResolver;

  /**
   * Mapping from an apple_library target to the associated apple_bundle which names it as its
   * 'binary'
   */
  private final Optional<ImmutableMap<BuildTarget, TargetNode<?>>> sharedLibraryToBundle;

  private final BuildContext buildContext;
  private final UnresolvedCxxPlatform unresolvedCxxPlatform;

  public ProjectGenerator(
      XCodeDescriptions xcodeDescriptions,
      TargetGraph targetGraph,
      AppleDependenciesCache dependenciesCache,
      ProjectGenerationStateCache projGenerationStateCache,
      Cells cells,
      Set<BuildTarget> projectTargets,
      Cell cell,
      String buildFileName,
      ProjectGeneratorOptions options,
      RuleKeyConfiguration ruleKeyConfiguration,
      BuildTarget workspaceTarget,
      ImmutableSet<BuildTarget> targetsInRequiredProjects,
      FocusedTargetMatcher excludedTargetMatcher,
      FocusedTargetMatcher excludedFromBuildTargetMatcher,
      UnresolvedCxxPlatform unresolvedCxxPlatform,
      ImmutableSet<Flavor> appleCxxFlavors,
      ActionGraphBuilder actionGraphBuilder,
      BuildContext buildContext,
      BuckEventBus buckEventBus,
      HalideBuckConfig halideBuckConfig,
      CxxBuckConfig cxxBuckConfig,
      AppleConfig appleConfig,
      SwiftBuckConfig swiftBuckConfig,
      Optional<ImmutableMap<BuildTarget, TargetNode<?>>> sharedLibraryToBundle) {

    this.xcodeDescriptions = xcodeDescriptions;
    this.targetGraph = targetGraph;
    this.dependenciesCache = dependenciesCache;
    this.projGenerationStateCache = projGenerationStateCache;
    this.cells = cells;
    this.projectTargets = ImmutableSet.copyOf(projectTargets);
    this.projectCell = cell;
    this.projectFilesystem = cell.getFilesystem();
    this.buildFileName = buildFileName;
    this.options = options;
    this.workspaceTarget = workspaceTarget;
    this.targetsInRequiredProjects = targetsInRequiredProjects;
    this.unresolvedCxxPlatform = unresolvedCxxPlatform;
    this.appleCxxFlavors = appleCxxFlavors;
    this.actionGraphBuilder = actionGraphBuilder;
    this.buildContext = buildContext;
    this.ruleKeyConfiguration = ruleKeyConfiguration;
    this.defaultPathResolver = actionGraphBuilder.getSourcePathResolver();
    this.buckEventBus = buckEventBus;

    this.projectSourcePathResolver =
        new ProjectSourcePathResolver(
            projectCell, defaultPathResolver, targetGraph, actionGraphBuilder);

    this.sharedLibraryToBundle = sharedLibraryToBundle;

    this.halideBuckConfig = halideBuckConfig;
    this.cxxBuckConfig = cxxBuckConfig;
    this.appleConfig = appleConfig;
    this.swiftBuckConfig = swiftBuckConfig;

    this.swiftAttributeParser =
        new SwiftAttributeParser(swiftBuckConfig, projGenerationStateCache, projectFilesystem);

    this.projectExcludeResolver =
        new ProjectExcludeResolver(
            targetGraph, appleConfig.getProjectExcludeLabels(), excludedTargetMatcher);
    this.projectExcludeFromBuildResolver =
        new ProjectExcludeResolver(
            targetGraph, appleConfig.getProjectExcludeLabels(), excludedFromBuildTargetMatcher);
  }

  /** The output from generating an Xcode project. */
  public static class Result {
    public final PBXProject generatedProject;
    public final ImmutableMap<BuildTarget, PBXTarget> buildTargetsToGeneratedTargetMap;
    public final ImmutableSet<BuildTarget> requiredBuildTargets;
    public final ImmutableSet<Path> xcconfigPaths;
    public final ImmutableList<Path> headerSymlinkTrees;
    public final ImmutableList<BuildTargetSourcePath> sourcePathsToBuild;

    public Result(
        PBXProject generatedProject,
        ImmutableMap<BuildTarget, PBXTarget> buildTargetsToGeneratedTargetMap,
        ImmutableSet<BuildTarget> requiredBuildTargets,
        ImmutableSet<Path> xcconfigPaths,
        ImmutableList<Path> headerSymlinkTrees,
        ImmutableList<BuildTargetSourcePath> sourcePathsToBuild) {
      this.generatedProject = generatedProject;
      this.buildTargetsToGeneratedTargetMap = buildTargetsToGeneratedTargetMap;
      this.requiredBuildTargets = requiredBuildTargets;
      this.xcconfigPaths = xcconfigPaths;
      this.headerSymlinkTrees = headerSymlinkTrees;
      this.sourcePathsToBuild = sourcePathsToBuild;
    }
  }

  /**
   * Creates an xcode project.
   *
   * @return A result containing the data about that project.
   * @throws IOException An IO exception occurred while trying to write to disk.
   */
  public ProjectGenerator.Result createXcodeProject(
      XcodeProjectWriteOptions xcodeProjectWriteOptions,
      ListeningExecutorService listeningExecutorService)
      throws IOException, InterruptedException {
    LOG.debug("Creating projects for %d targets", projectTargets.size());

    try (SimplePerfEvent.Scope scope =
        SimplePerfEvent.scope(
            buckEventBus.isolated(),
            SimplePerfEvent.PerfEventTitle.of("xcode_project_generation"),
            ImmutableMap.of("Path", xcodeProjectWriteOptions.xcodeProjPath()))) {

      PathRelativizer pathRelativizer =
          new PathRelativizer(
              xcodeProjectWriteOptions.sourceRoot(), projectSourcePathResolver::resolveSourcePath);
      HeaderSearchPaths headerSearchPaths =
          new HeaderSearchPaths(
              cells,
              projectCell,
              cxxBuckConfig,
              unresolvedCxxPlatform,
              ruleKeyConfiguration,
              xcodeDescriptions,
              targetGraph,
              actionGraphBuilder,
              buildContext,
              dependenciesCache,
              projectSourcePathResolver,
              pathRelativizer,
              swiftAttributeParser,
              appleConfig);

      FlagParser flagParser =
          new FlagParser(
              projectFilesystem,
              cells,
              projectCell,
              appleConfig,
              swiftBuckConfig,
              cxxBuckConfig,
              appleCxxFlavors,
              xcodeDescriptions,
              targetGraph,
              actionGraphBuilder,
              dependenciesCache,
              defaultPathResolver,
              headerSearchPaths);

      XcodeNativeTargetGenerator targetGenerator =
          new XcodeNativeTargetGenerator(
              xcodeDescriptions,
              targetGraph,
              dependenciesCache,
              projGenerationStateCache,
              projectCell.getFilesystem(),
              xcodeProjectWriteOptions.sourceRoot(),
              buildFileName,
              pathRelativizer,
              defaultPathResolver,
              projectSourcePathResolver,
              options,
              unresolvedCxxPlatform,
              appleCxxFlavors,
              actionGraphBuilder,
              halideBuckConfig,
              headerSearchPaths,
              cxxBuckConfig,
              appleConfig,
              swiftBuckConfig,
              swiftAttributeParser,
              flagParser,
              sharedLibraryToBundle,
              xcodeProjectWriteOptions.objectFactory());

      ImmutableList.Builder<XcodeNativeTargetGenerator.Result> generationResultsBuilder =
          ImmutableList.builder();

      // Handle the workspace target if it's in the project. This ensures the
      // workspace target isn't filtered later by loading it first.
      final TargetNode<?> workspaceTargetNode = targetGraph.get(workspaceTarget);
      XcodeNativeTargetGenerator.Result workspaceTargetResult =
          targetGenerator.generateTarget(workspaceTargetNode);
      generationResultsBuilder.add(workspaceTargetResult);

      try {
        /*
         * Process flavored nodes before unflavored ones.
         *
         * It is possible we have the same bundle node twice (e.g. as a test target and a dep). In
         * that instance, one may be unflavored, so we need to prioritize the flavored version first
         * in order to properly get the target out during schema generation.
         */
        List<XcodeNativeTargetGenerator.Result> flavoredTargetResults =
            Futures.allAsList(
                    projectTargets.stream()
                        .filter(
                            buildTarget ->
                                buildTarget.isFlavored()
                                    && !projectExcludeResolver.excludeTarget(buildTarget))
                        .map(targetGraph::get)
                        .filter(targetNode -> !targetNode.equals(workspaceTargetNode))
                        .map(
                            target ->
                                listeningExecutorService.submit(
                                    () -> targetGenerator.generateTarget(target)))
                        .collect(Collectors.toList()))
                .get();

        generationResultsBuilder.addAll(flavoredTargetResults);

        List<XcodeNativeTargetGenerator.Result> unflavoredTargetResults =
            Futures.allAsList(
                    projectTargets.stream()
                        .filter(
                            buildTarget ->
                                !buildTarget.isFlavored()
                                    && !projectExcludeResolver.excludeTarget(buildTarget))
                        .map(targetGraph::get)
                        .filter(targetNode -> !targetNode.equals(workspaceTargetNode))
                        .map(
                            target ->
                                listeningExecutorService.submit(
                                    () -> targetGenerator.generateTarget(target)))
                        .collect(Collectors.toList()))
                .get();

        generationResultsBuilder.addAll(unflavoredTargetResults);
      } catch (ExecutionException e) {
        Throwables.throwIfInstanceOf(e.getCause(), IOException.class);
        Throwables.throwIfUnchecked(e.getCause());
        throw new IllegalStateException("Unexpected exception: ", e);
      }

      if (options.shouldMergeTargets()) {
        ImmutableSet<XcodeNativeTargetGenerator.Result> mergedTargetsResults =
            targetGenerator.generateMergedTargetDependencies(
                projectTargets, projectExcludeResolver);
        generationResultsBuilder.addAll(mergedTargetsResults);
      }

      ImmutableSet.Builder<BuildTarget> requiredBuildTargetsBuilder = ImmutableSet.builder();
      ImmutableSet.Builder<Path> xcconfigPathsBuilder = ImmutableSet.builder();
      ImmutableSet.Builder<String> targetConfigNamesBuilder = ImmutableSet.builder();
      ImmutableList.Builder<Path> headerSymlinkTreesBuilder = ImmutableList.builder();

      ImmutableMap.Builder<TargetNode<?>, PBXNativeTarget>
          targetNodeToGeneratedProjectTargetBuilder = ImmutableMap.builder();

      ImmutableList<XcodeNativeTargetGenerator.Result> generationResults =
          generationResultsBuilder.build();

      for (XcodeNativeTargetGenerator.Result result : generationResults) {
        requiredBuildTargetsBuilder.addAll(
            result.requiredBuildTargets.stream()
                .filter(buildTarget -> !projectExcludeFromBuildResolver.excludeTarget(buildTarget))
                .collect(Collectors.toSet()));
        xcconfigPathsBuilder.addAll(result.xcconfigPaths);
        targetConfigNamesBuilder.addAll(result.targetConfigNames);

        if (!appleConfig.getProjectGeneratorIndexViaCompileArgs()) {
          result.headerSearchPathAttributes.ifPresent(
              headerSearchPathAttributes ->
                  headerSearchPaths.createHeaderSearchPaths(
                      headerSearchPathAttributes, headerSymlinkTreesBuilder));
        }

        XCodeNativeTargetAttributes nativeTargetAttributes = result.targetAttributes;
        XcodeNativeTargetProjectWriter nativeTargetProjectWriter =
            new XcodeNativeTargetProjectWriter(
                pathRelativizer,
                sourcePath -> projectSourcePathResolver.resolveSourcePath(sourcePath).getPath(),
                projectExcludeResolver,
                options.shouldUseShortNamesForTargets(),
                projectCell.getNewCellPathResolver(),
                xcodeProjectWriteOptions.objectFactory());
        XcodeNativeTargetProjectWriter.Result targetWriteResult =
            nativeTargetProjectWriter.writeTargetToProject(
                nativeTargetAttributes, xcodeProjectWriteOptions.project());

        targetWriteResult
            .getTarget()
            .ifPresent(
                target -> {
                  targetNodeToGeneratedProjectTargetBuilder.put(result.targetNode, target);
                });
      }

      ImmutableMap<TargetNode<?>, PBXNativeTarget> targetNodeToGeneratedProjectTarget =
          targetNodeToGeneratedProjectTargetBuilder.build();

      for (XcodeNativeTargetGenerator.Result result : generationResults) {
        Optional<PBXNativeTarget> nativeTarget =
            targetNodeToGeneratedProjectTarget.containsKey(result.targetNode)
                ? Optional.of(targetNodeToGeneratedProjectTarget.get(result.targetNode))
                : Optional.empty();
        nativeTarget.ifPresent(
            target -> {
              for (BuildTarget dep : result.dependencies) {
                addPBXTargetDependency(
                    xcodeProjectWriteOptions.project(),
                    target,
                    xcodeProjectWriteOptions.objectFactory(),
                    dep,
                    targetNodeToGeneratedProjectTarget);
              }
            });
      }

      buckEventBus.post(ProjectGenerationEvent.processed());

      ImmutableList<BuildTargetSourcePath> buildTargetSourcePathsToBuild;

      // We don't need to add additional build targets nor create merged header map if we're using
      // index with compile args.
      if (appleConfig.getProjectGeneratorIndexViaCompileArgs()) {
        buildTargetSourcePathsToBuild = ImmutableList.of();
      } else {
        ImmutableSet<BuildTarget> headerMapTargets =
            ImmutableSet.<BuildTarget>builder()
                .addAll(targetsInRequiredProjects)
                .addAll(
                    StreamSupport.stream(targetGraph.getAll(projectTargets).spliterator(), false)
                        .filter(
                            targetNode ->
                                // Generate header maps for headers included in any Apple targets
                                NodeHelper.getAppleNativeNode(targetGraph, targetNode).isPresent())
                        .map(targetNode -> targetNode.getBuildTarget())
                        .collect(Collectors.toSet()))
                .build();

        ImmutableList<SourcePath> sourcePathsToBuild =
            headerSearchPaths.createMergedHeaderMap(headerMapTargets);

        buildTargetSourcePathsToBuild =
            sourcePathsToBuild.stream()
                .map(Utils::sourcePathTryIntoBuildTargetSourcePath)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(ImmutableList.toImmutableList());
      }

      PBXProject project = xcodeProjectWriteOptions.project();
      for (String configName : targetConfigNamesBuilder.build()) {
        XCBuildConfiguration outputConfig =
            project
                .getBuildConfigurationList()
                .getBuildConfigurationsByName()
                .getUnchecked(configName);

        NSDictionary projectBuildSettings = new NSDictionary();

        // Set the cell root relative to the source root for each configuration.
        Path cellRootRelativeToSourceRoot =
            MorePaths.relativizeWithDotDotSupport(
                projectCell.getRoot().getPath().resolve(xcodeProjectWriteOptions.sourceRoot()),
                projectCell.getRoot().getPath());
        projectBuildSettings.put(
            BUCK_CELL_RELATIVE_PATH, cellRootRelativeToSourceRoot.normalize().toString());

        outputConfig.setBuildSettings(projectBuildSettings);
      }

      writeProjectFile(xcodeProjectWriteOptions);

      ImmutableMap.Builder<BuildTarget, PBXTarget> buildTargetToPbxTargetMap =
          ImmutableMap.builder();
      for (TargetNode<?> targetNode : targetNodeToGeneratedProjectTarget.keySet()) {
        buildTargetToPbxTargetMap.put(
            targetNode.getBuildTarget(), targetNodeToGeneratedProjectTarget.get(targetNode));
      }

      return new ProjectGenerator.Result(
          project,
          buildTargetToPbxTargetMap.build(),
          requiredBuildTargetsBuilder.build(),
          xcconfigPathsBuilder.build(),
          headerSymlinkTreesBuilder.build(),
          buildTargetSourcePathsToBuild);
    } catch (UncheckedExecutionException e) {
      // if any code throws an exception, they tend to get wrapped in LoadingCache's
      // UncheckedExecutionException. Unwrap it if its cause is HumanReadable.
      UncheckedExecutionException originalException = e;
      while (e.getCause() instanceof UncheckedExecutionException) {
        e = (UncheckedExecutionException) e.getCause();
      }
      if (e.getCause() instanceof HumanReadableException) {
        throw (HumanReadableException) e.getCause();
      } else {
        throw originalException;
      }
    }
  }

  /** Create the project bundle structure and write {@code project.pbxproj}. */
  private void writeProjectFile(XcodeProjectWriteOptions xcodeProjectWriteOptions)
      throws IOException {
    GidGenerator gidGenerator = new GidGenerator();
    PBXProject project = xcodeProjectWriteOptions.project();

    XcodeprojSerializer serializer = new XcodeprojSerializer(gidGenerator, project);
    NSDictionary rootObject = serializer.toPlist();
    projectFilesystem.mkdirs(xcodeProjectWriteOptions.xcodeProjPath());
    Path serializedProject = xcodeProjectWriteOptions.projectFilePath();
    String contentsToWrite = rootObject.toXMLPropertyList();
    // Before we write any files, check if the file contents have changed.
    if (MoreProjectFilesystems.fileContentsDiffer(
        new ByteArrayInputStream(contentsToWrite.getBytes(StandardCharsets.UTF_8)),
        serializedProject,
        projectFilesystem)) {
      LOG.debug("Regenerating project at %s", serializedProject);
      if (options.shouldGenerateReadOnlyFiles()) {
        projectFilesystem.writeContentsToPath(
            contentsToWrite, serializedProject, MorePosixFilePermissions.READ_ONLY_FILE_ATTRIBUTE);
      } else {
        projectFilesystem.writeContentsToPath(contentsToWrite, serializedProject);
      }
    } else {
      LOG.debug("Not regenerating project at %s (contents have not changed)", serializedProject);
    }
  }

  private void addPBXTargetDependency(
      PBXProject project,
      PBXNativeTarget pbxTarget,
      AbstractPBXObjectFactory objectFactory,
      BuildTarget dependency,
      ImmutableMap<TargetNode<?>, ? extends PBXTarget> targetNodeToProjectTarget) {
    // Xcode appears to only support target dependencies if both targets are within the same
    // project.
    // If the desired target dependency is not in the same project, then just ignore it.
    // Not sure if we still need this if we're only ever considering targets in projectTargets,
    // but for saftey's sake, let's keep it for now (@cjjones)
    if (!projectTargets.contains(dependency)) {
      return;
    }

    PBXTarget dependencyPBXTarget = targetNodeToProjectTarget.get(targetGraph.get(dependency));
    if (dependencyPBXTarget != null) {
      PBXContainerItemProxy dependencyProxy =
          objectFactory.createContainerItemProxy(
              project,
              dependencyPBXTarget.getGlobalID(),
              PBXContainerItemProxy.ProxyType.TARGET_REFERENCE);

      pbxTarget.getDependencies().add(objectFactory.createTargetDependency(dependencyProxy));
    }
  }
}

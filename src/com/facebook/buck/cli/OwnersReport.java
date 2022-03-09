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

package com.facebook.buck.cli;

import com.facebook.buck.core.cell.Cell;
import com.facebook.buck.core.filesystems.AbsPath;
import com.facebook.buck.core.filesystems.ForwardRelPath;
import com.facebook.buck.core.filesystems.RelPath;
import com.facebook.buck.core.model.BuildFileTree;
import com.facebook.buck.core.model.targetgraph.TargetNode;
import com.facebook.buck.core.model.targetgraph.raw.UnconfiguredTargetNode;
import com.facebook.buck.core.util.log.Logger;
import com.facebook.buck.io.file.MorePaths;
import com.facebook.buck.parser.config.ParserConfig;
import com.facebook.buck.util.stream.RichStream;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.SetMultimap;
import com.google.common.collect.Sets;
import com.google.common.collect.TreeMultimap;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/** Used to determine owners of specific files */
final class OwnersReport<N extends Comparable<N>> {
  final ImmutableSetMultimap<N, Path> owners;
  final ImmutableSet<Path> inputsWithNoOwners;
  final ImmutableSet<String> nonExistentInputs;
  final ImmutableSet<String> nonFileInputs;

  private static final Logger LOG = Logger.get(OwnersReport.class);

  private OwnersReport(
      ImmutableSetMultimap<N, Path> owners,
      ImmutableSet<Path> inputsWithNoOwners,
      ImmutableSet<String> nonExistentInputs,
      ImmutableSet<String> nonFileInputs) {
    this.owners = owners;
    this.inputsWithNoOwners = inputsWithNoOwners;
    this.nonExistentInputs = nonExistentInputs;
    this.nonFileInputs = nonFileInputs;
  }

  /** Get the set of files that were requested that did not have an owning rule */
  public ImmutableSet<Path> getInputsWithNoOwners() {
    return inputsWithNoOwners;
  }

  /** Get the set of inputs specified in a build rule that do not exist on disk */
  public ImmutableSet<String> getNonExistentInputs() {
    return nonExistentInputs;
  }

  /** Get inputs to a build rule that do not appear to be regular files */
  public ImmutableSet<String> getNonFileInputs() {
    return nonFileInputs;
  }

  private static <N extends Comparable<N>> OwnersReport<N> emptyReport() {
    return new OwnersReport<N>(
        ImmutableSetMultimap.of(), ImmutableSet.of(), ImmutableSet.of(), ImmutableSet.of());
  }

  private boolean isEmpty() {
    return owners.isEmpty()
        && inputsWithNoOwners.isEmpty()
        && nonExistentInputs.isEmpty()
        && nonFileInputs.isEmpty();
  }

  @VisibleForTesting
  OwnersReport<N> updatedWith(OwnersReport<N> other) {
    // If either this or other are empty, the intersection below for missing files will get
    // screwed up. This mostly is just so that when we do a fold elsewhere in the class against
    // a default empty object, we don't obliterate inputsWithNoOwners
    if (this.isEmpty()) {
      return other;
    } else if (other.isEmpty()) {
      return this;
    }

    SetMultimap<N, Path> updatedOwners = TreeMultimap.create(owners);
    updatedOwners.putAll(other.owners);

    return new OwnersReport<>(
        ImmutableSetMultimap.copyOf(updatedOwners),
        Sets.intersection(inputsWithNoOwners, other.inputsWithNoOwners).immutableCopy(),
        Sets.union(nonExistentInputs, other.nonExistentInputs).immutableCopy(),
        Sets.union(nonFileInputs, other.nonFileInputs).immutableCopy());
  }

  @VisibleForTesting
  static <N extends Comparable<N>> OwnersReport<N> generateOwnersReport(
      Function<N, ImmutableSet<ForwardRelPath>> inputsFunction,
      Cell rootCell,
      N targetNode,
      String filePath) {
    AbsPath file = rootCell.getFilesystem().getPathForRelativePath(filePath);
    if (!Files.exists(file.getPath())) {
      return new OwnersReport<N>(
          ImmutableSetMultimap.of(),
          ImmutableSet.of(),
          ImmutableSet.of(filePath),
          ImmutableSet.of());
    } else if (!Files.isRegularFile(file.getPath())) {
      return new OwnersReport<N>(
          ImmutableSetMultimap.of(),
          ImmutableSet.of(),
          ImmutableSet.of(),
          ImmutableSet.of(filePath));
    } else {
      Path commandInput = rootCell.getFilesystem().getPath(filePath);
      ImmutableSet<ForwardRelPath> ruleInputs = inputsFunction.apply(targetNode);
      ImmutableSet<Path> ruleInputPaths =
          ruleInputs.stream()
              .map(p -> p.toPath(commandInput.getFileSystem()))
              .collect(ImmutableSet.toImmutableSet());
      Predicate<Path> startsWith =
          input -> !commandInput.equals(input) && commandInput.startsWith(input);
      if (ruleInputPaths.contains(commandInput) || ruleInputPaths.stream().anyMatch(startsWith)) {
        return new OwnersReport<>(
            ImmutableSetMultimap.of(targetNode, commandInput),
            ImmutableSet.of(),
            ImmutableSet.of(),
            ImmutableSet.of());
      } else {
        return new OwnersReport<N>(
            ImmutableSetMultimap.of(),
            ImmutableSet.of(commandInput),
            ImmutableSet.of(),
            ImmutableSet.of());
      }
    }
  }

  static Builder<UnconfiguredTargetNode> builderForUnconfigured(
      Cell rootCell, Path clientWorkingDir, UnconfiguredTargetGraph targetGraph) {
    return new Builder<>(
        rootCell,
        clientWorkingDir,
        targetGraph::getAllNodesInBuildFile,
        targetGraph::getInputPathsForNode);
  }

  static Builder<TargetNode<?>> builderForConfigured(
      Cell rootCell, Path clientWorkingDir, TargetUniverse targetUniverse) {
    return new Builder<>(
        rootCell,
        clientWorkingDir,
        (cell, path) -> targetUniverse.getAllTargetNodesInBuildFile(cell, path),
        TargetNode::getInputs);
  }

  /** Class that can create {@code OwnerReport}s for a set of modified files */
  static final class Builder<N extends Comparable<N>> {
    private final Cell rootCell;
    private final Path clientWorkingDir;
    private final BiFunction<Cell, AbsPath, ImmutableList<N>> buildfileNodesFunction;
    private final Function<N, ImmutableSet<ForwardRelPath>> inputsFunction;

    private Builder(
        Cell rootCell,
        Path clientWorkingDir,
        BiFunction<Cell, AbsPath, ImmutableList<N>> buildfileNodesFunction,
        Function<N, ImmutableSet<ForwardRelPath>> inputsFunction) {
      this.rootCell = rootCell;
      this.clientWorkingDir = clientWorkingDir;
      this.buildfileNodesFunction = buildfileNodesFunction;
      this.inputsFunction = inputsFunction;
    }

    private OwnersReport<N> getReportForBasePath(
        Map<AbsPath, ImmutableList<N>> map, Cell cell, RelPath basePath, RelPath cellRelativePath) {
      AbsPath buckFile =
          cell.getFilesystem()
              .resolve(basePath)
              .resolve(cell.getBuckConfigView(ParserConfig.class).getBuildFileName());
      ImmutableList<N> targetNodes =
          map.computeIfAbsent(buckFile, basePath1 -> buildfileNodesFunction.apply(cell, basePath1));
      return targetNodes.stream()
          .map(
              targetNode ->
                  generateOwnersReport(
                      inputsFunction, cell, targetNode, cellRelativePath.toString()))
          .reduce(OwnersReport.emptyReport(), OwnersReport::updatedWith);
    }

    private ImmutableSet<RelPath> getAllBasePathsForPath(
        BuildFileTree buildFileTree, RelPath cellRelativePath) {
      if (rootCell
              .getBuckConfigView(ParserConfig.class)
              .getPackageBoundaryEnforcementPolicy(cellRelativePath.getPath())
          == ParserConfig.PackageBoundaryEnforcement.ENFORCE) {
        return buildFileTree
            .getBasePathOfAncestorTarget(cellRelativePath)
            .map(ImmutableSet::of)
            .orElse(ImmutableSet.of());
      }
      ImmutableSet.Builder<RelPath> resultBuilder =
          ImmutableSet.builderWithExpectedSize(cellRelativePath.getPath().getNameCount());
      for (int i = 1; i < cellRelativePath.getPath().getNameCount(); i++) {
        buildFileTree
            .getBasePathOfAncestorTarget(cellRelativePath.subpath(0, i))
            .ifPresent(resultBuilder::add);
      }
      return resultBuilder.build();
    }

    OwnersReport<N> build(
        ImmutableMap<Cell, BuildFileTree> buildFileTrees, Iterable<String> arguments) {

      // Order cells by cell path length so that nested cells will resolve to the most specific
      // cell.
      List<Cell> cellsByRootLength =
          RichStream.from(buildFileTrees.keySet())
              .sorted(
                  Comparator.comparing((Cell cell) -> cell.getRoot().toString().length())
                      .reversed())
              .toImmutableList();

      Map<Optional<Cell>, List<Path>> argumentsByCell =
          RichStream.from(arguments)
              // Filter out any non-existent paths.
              .flatMap(
                  (path) -> {
                    // Assume paths given are relative to client's working directory.
                    Path resolvedPath = clientWorkingDir.resolve(path);
                    if (Files.exists(resolvedPath)) {
                      return RichStream.of(resolvedPath);
                    } else {
                      LOG.warn(
                          "path %s doesn't exist when resolved against the current working dir",
                          path);
                      return RichStream.empty();
                    }
                  })
              // Resolve them all to absolute paths.
              .map(
                  pathString -> {
                    try {
                      return pathString.toRealPath();
                    } catch (IOException e) {
                      throw new RuntimeException(e);
                    }
                  })
              // Group them into cells that they belong to.
              .collect(
                  Collectors.groupingBy(
                      path -> {
                        for (Cell c : cellsByRootLength) {
                          if (path.startsWith(c.getRoot().getPath())) {
                            return Optional.of(c);
                          }
                        }
                        return Optional.empty();
                      }));
      ImmutableSet<String> missingFiles =
          RichStream.from(arguments)
              .filter(f -> !Files.exists(clientWorkingDir.resolve(f)))
              .map(MorePaths::pathWithPlatformSeparators)
              .toImmutableSet();

      ImmutableSet.Builder<Path> inputWithNoOwners = ImmutableSet.builder();
      OwnersReport<N> report = OwnersReport.emptyReport();
      // Process every cell's files independently.
      for (Map.Entry<Optional<Cell>, List<Path>> entry : argumentsByCell.entrySet()) {
        if (!entry.getKey().isPresent()) {
          inputWithNoOwners.addAll(entry.getValue());
          continue;
        }

        Cell cell = entry.getKey().get();
        BuildFileTree buildFileTree =
            Objects.requireNonNull(
                buildFileTrees.get(cell),
                "cell is be derived from buildFileTree keys, so should be present");

        // Path from buck file to target nodes. We keep our own cache here since the manner that we
        // are calling the parser does not make use of its internal caches.
        Map<AbsPath, ImmutableList<N>> map = new HashMap<>();
        for (Path absolutePath : entry.getValue()) {
          RelPath cellRelativePath = cell.getFilesystem().relativize(absolutePath);
          ImmutableSet<RelPath> basePaths = getAllBasePathsForPath(buildFileTree, cellRelativePath);
          if (basePaths.isEmpty()) {
            inputWithNoOwners.add(absolutePath);
            continue;
          }
          report =
              basePaths.stream()
                  .map(basePath -> getReportForBasePath(map, cell, basePath, cellRelativePath))
                  .reduce(report, OwnersReport::updatedWith);
        }
      }

      return report.updatedWith(
          new OwnersReport<N>(
              ImmutableSetMultimap.of(),
              /* inputWithNoOwners */ inputWithNoOwners.build(),
              /* nonExistentInputs */ missingFiles,
              ImmutableSet.of()));
    }
  }
}

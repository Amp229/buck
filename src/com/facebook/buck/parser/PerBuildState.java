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

package com.facebook.buck.parser;

import com.facebook.buck.core.cell.Cell;
import com.facebook.buck.core.exceptions.DependencyStack;
import com.facebook.buck.core.filesystems.AbsPath;
import com.facebook.buck.core.filesystems.ForwardRelPath;
import com.facebook.buck.core.filesystems.RelPath;
import com.facebook.buck.core.model.BuildTarget;
import com.facebook.buck.core.model.TargetConfiguration;
import com.facebook.buck.core.model.UnconfiguredBuildTarget;
import com.facebook.buck.core.model.targetgraph.TargetNode;
import com.facebook.buck.core.model.targetgraph.TargetNodeMaybeIncompatible;
import com.facebook.buck.core.model.targetgraph.raw.UnconfiguredTargetNode;
import com.facebook.buck.core.rules.config.registry.ConfigurationRuleRegistry;
import com.facebook.buck.core.select.SelectorListResolver;
import com.facebook.buck.core.select.impl.SelectorListFactory;
import com.facebook.buck.parser.api.BuildFileManifest;
import com.facebook.buck.parser.exceptions.BuildFileParseException;
import com.facebook.buck.parser.exceptions.BuildTargetException;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import java.util.Optional;

public class PerBuildState implements AutoCloseable {

  private final CellManager cellManager;
  private final BuildFileRawNodeParsePipeline buildFileRawNodeParsePipeline;
  private final UnconfiguredTargetNodePipeline unconfiguredTargetNodeParsePipeline;
  private final UnconfiguredTargetNodeToTargetNodeParsePipeline targetNodeParsePipeline;
  private final ParsingContext parsingContext;
  private final SelectorListResolver selectorListResolver;
  private final SelectorListFactory selectorListFactory;
  private final ConfigurationRuleRegistry configurationRuleRegistry;

  PerBuildState(
      CellManager cellManager,
      BuildFileRawNodeParsePipeline buildFileRawNodeParsePipeline,
      UnconfiguredTargetNodePipeline unconfiguredTargetNodeParsePipeline,
      UnconfiguredTargetNodeToTargetNodeParsePipeline targetNodeParsePipeline,
      ParsingContext parsingContext,
      SelectorListResolver selectorListResolver,
      SelectorListFactory selectorListFactory,
      ConfigurationRuleRegistry configurationRuleRegistry) {
    this.cellManager = cellManager;
    this.buildFileRawNodeParsePipeline = buildFileRawNodeParsePipeline;
    this.unconfiguredTargetNodeParsePipeline = unconfiguredTargetNodeParsePipeline;
    this.targetNodeParsePipeline = targetNodeParsePipeline;
    this.parsingContext = parsingContext;
    this.selectorListResolver = selectorListResolver;
    this.selectorListFactory = selectorListFactory;
    this.configurationRuleRegistry = configurationRuleRegistry;
  }

  TargetNodeMaybeIncompatible getTargetNode(BuildTarget target, DependencyStack dependencyStack)
      throws BuildFileParseException {
    Cell owningCell = cellManager.getCell(target.getCell());

    return targetNodeParsePipeline.getNode(owningCell, target, dependencyStack);
  }

  TargetNode<?> getTargetNodeAssertCompatible(BuildTarget target, DependencyStack dependencyStack) {
    Cell owningCell = cellManager.getCell(target.getCell());

    return targetNodeParsePipeline
        .getNode(owningCell, target, dependencyStack)
        .assertGetTargetNode(dependencyStack);
  }

  ListenableFuture<TargetNode<?>> getTargetNodeJobAssertCompatible(
      BuildTarget target, DependencyStack dependencyStack) throws BuildTargetException {
    Cell owningCell = cellManager.getCell(target.getCell());

    return Futures.transform(
        targetNodeParsePipeline.getNodeJob(owningCell, target, dependencyStack),
        targetNodeMaybeIncompatible ->
            targetNodeMaybeIncompatible.assertGetTargetNode(dependencyStack));
  }

  ImmutableList<TargetNodeMaybeIncompatible> getAllTargetNodes(
      Cell cell, AbsPath buildFile, Optional<TargetConfiguration> targetConfiguration)
      throws BuildFileParseException {

    ForwardRelPath buildFileRel = ForwardRelPath.ofRelPath(buildFile.removePrefix(cell.getRoot()));

    return targetNodeParsePipeline.getAllRequestedTargetNodes(
        cell, buildFileRel, targetConfiguration);
  }

  ImmutableList<TargetNodeMaybeIncompatible> getAllTargetNodes(
      Cell cell, ForwardRelPath buildFile, Optional<TargetConfiguration> targetConfiguration)
      throws BuildFileParseException {
    return targetNodeParsePipeline.getAllRequestedTargetNodes(cell, buildFile, targetConfiguration);
  }

  ListenableFuture<TargetNodeMaybeIncompatible> getRequestedTargetNodeJob(
      UnconfiguredBuildTarget target, Optional<TargetConfiguration> targetConfiguration) {
    Cell owningCell = cellManager.getCell(target.getCell());

    return targetNodeParsePipeline.getRequestedTargetNodeJob(
        owningCell, target, targetConfiguration);
  }

  ListenableFuture<TargetNode<?>> getRequestedTargetNodeJobAssertCompatible(
      UnconfiguredBuildTarget target, Optional<TargetConfiguration> targetConfiguration) {
    Cell owningCell = cellManager.getCell(target.getCell());

    return Futures.transform(
        targetNodeParsePipeline.getRequestedTargetNodeJob(owningCell, target, targetConfiguration),
        node -> node.assertGetTargetNode(DependencyStack.top(target)),
        MoreExecutors.directExecutor());
  }

  ListenableFuture<ImmutableList<TargetNodeMaybeIncompatible>> getRequestedTargetNodesJob(
      Cell cell, AbsPath buildFile, Optional<TargetConfiguration> targetConfiguration)
      throws BuildTargetException {
    ForwardRelPath buildFileRel = ForwardRelPath.ofRelPath(buildFile.removePrefix(cell.getRoot()));

    return targetNodeParsePipeline.getAllRequestedTargetNodesJob(
        cell, buildFileRel, targetConfiguration);
  }

  ListenableFuture<ImmutableList<UnconfiguredTargetNode>> getAllUnconfiguredTargetNodesJobs(
      Cell cell, AbsPath buildFile) throws BuildFileParseException {

    ForwardRelPath buildFileRel = ForwardRelPath.ofRelPath(buildFile.removePrefix(cell.getRoot()));

    return unconfiguredTargetNodeParsePipeline.getAllNodesJob(cell, buildFileRel);
  }

  ListenableFuture<UnconfiguredTargetNode> getUnconfiguredTargetNodeJob(
      UnconfiguredBuildTarget target, DependencyStack dependencyStack)
      throws BuildFileParseException {
    Cell owningCell = cellManager.getCell(target.getCell());

    return unconfiguredTargetNodeParsePipeline.getNodeJob(owningCell, target, dependencyStack);
  }

  public BuildFileManifest getBuildFileManifest(Cell cell, AbsPath buildFile)
      throws BuildFileParseException {
    RelPath buildFileRelative = buildFile.removePrefix(cell.getRoot());
    return getBuildFileManifest(cell, ForwardRelPath.ofRelPath(buildFileRelative));
  }

  public BuildFileManifest getBuildFileManifest(Cell cell, ForwardRelPath buildFile)
      throws BuildFileParseException {
    return buildFileRawNodeParsePipeline.getFile(cell, buildFile);
  }

  ListenableFuture<BuildFileManifest> getBuildFileManifestJob(Cell cell, AbsPath buildFile)
      throws BuildFileParseException {
    RelPath buildFileRelative = buildFile.removePrefix(cell.getRoot());
    return buildFileRawNodeParsePipeline.getFileJob(
        cell, ForwardRelPath.ofRelPath(buildFileRelative));
  }

  ListenableFuture<BuildFileManifest> getBuildFileManifestJob(Cell cell, ForwardRelPath buildFile)
      throws BuildFileParseException {
    return buildFileRawNodeParsePipeline.getFileJob(cell, buildFile);
  }

  public ParsingContext getParsingContext() {
    return parsingContext;
  }

  SelectorListResolver getSelectorListResolver() {
    return selectorListResolver;
  }

  SelectorListFactory getSelectorListFactory() {
    return selectorListFactory;
  }

  ConfigurationRuleRegistry getConfigurationRuleRegistry() {
    return configurationRuleRegistry;
  }

  @Override
  public void close() {
    targetNodeParsePipeline.close();
    buildFileRawNodeParsePipeline.close();
    cellManager.close();
  }
}

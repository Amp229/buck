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
import com.facebook.buck.core.exceptions.HumanReadableException;
import com.facebook.buck.core.filesystems.AbsPath;
import com.facebook.buck.core.model.BuildTarget;
import com.facebook.buck.core.model.TargetConfiguration;
import com.facebook.buck.core.model.UnconfiguredBuildTarget;
import com.facebook.buck.core.model.targetgraph.TargetGraph;
import com.facebook.buck.core.model.targetgraph.TargetGraphCreationResult;
import com.facebook.buck.core.model.targetgraph.TargetNode;
import com.facebook.buck.core.model.targetgraph.TargetNodeMaybeIncompatible;
import com.facebook.buck.core.model.targetgraph.raw.UnconfiguredTargetNode;
import com.facebook.buck.core.util.graph.AcyclicDepthFirstPostOrderTraversalWithPayloadAndDependencyStack;
import com.facebook.buck.core.util.graph.CycleException;
import com.facebook.buck.core.util.graph.DirectedAcyclicGraph;
import com.facebook.buck.core.util.graph.GraphTraversableWithPayloadAndDependencyStack;
import com.facebook.buck.event.BuckEventBus;
import com.facebook.buck.event.SimplePerfEvent;
import com.facebook.buck.parser.api.BuildFileManifest;
import com.facebook.buck.parser.exceptions.BuildFileParseException;
import com.facebook.buck.parser.exceptions.BuildTargetException;
import com.facebook.buck.parser.spec.TargetNodeSpec;
import com.facebook.buck.parser.temporarytargetuniquenesschecker.TemporaryUnconfiguredTargetToTargetUniquenessChecker;
import com.facebook.buck.util.MoreMaps;
import com.facebook.buck.util.types.Pair;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.ListenableFuture;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Evaluates build files using one of the supported interpreters and provides information about
 * build targets defined in them.
 *
 * <p>Computed targets are cached but are automatically invalidated if Watchman reports any
 * filesystem changes that may affect computed results.
 */
abstract class AbstractParser implements Parser {

  protected final PerBuildStateFactory perBuildStateFactory;
  protected final DaemonicParserState permState;
  protected final BuckEventBus eventBus;

  AbstractParser(
      DaemonicParserState daemonicParserState,
      PerBuildStateFactory perBuildStateFactory,
      BuckEventBus eventBus) {
    this.perBuildStateFactory = perBuildStateFactory;
    this.permState = daemonicParserState;
    this.eventBus = eventBus;
  }

  @Override
  public DaemonicParserState getPermState() {
    return permState;
  }

  @Override
  public PerBuildStateFactory getPerBuildStateFactory() {
    return perBuildStateFactory;
  }

  @VisibleForTesting
  static BuildFileManifest getTargetNodeRawAttributes(
      PerBuildState state, Cell cell, AbsPath buildFile) throws BuildFileParseException {
    return state.getBuildFileManifest(cell, buildFile);
  }

  @Override
  public ImmutableList<TargetNodeMaybeIncompatible> getAllTargetNodes(
      PerBuildState perBuildState,
      Cell cell,
      AbsPath buildFile,
      Optional<TargetConfiguration> targetConfiguration)
      throws BuildFileParseException {
    return perBuildState.getAllTargetNodes(cell, buildFile, targetConfiguration);
  }

  @Override
  public TargetNode<?> getTargetNodeAssertCompatible(
      ParsingContext parsingContext, BuildTarget target, DependencyStack dependencyStack)
      throws BuildFileParseException {
    try (PerBuildState state = perBuildStateFactory.create(parsingContext, permState)) {
      return state.getTargetNodeAssertCompatible(target, dependencyStack);
    }
  }

  @Override
  public TargetNode<?> getTargetNodeAssertCompatible(
      PerBuildState perBuildState, BuildTarget target, DependencyStack dependencyStack)
      throws BuildFileParseException {
    return perBuildState.getTargetNodeAssertCompatible(target, dependencyStack);
  }

  @Override
  public ListenableFuture<TargetNode<?>> getTargetNodeJobAssertCompatible(
      PerBuildState perBuildState, BuildTarget target, DependencyStack dependencyStack)
      throws BuildTargetException {
    return perBuildState.getTargetNodeJobAssertCompatible(target, dependencyStack);
  }

  @Override
  public ListenableFuture<UnconfiguredTargetNode> getUnconfiguredTargetNodeJob(
      PerBuildState perBuildState,
      UnconfiguredBuildTarget unconfiguredBuildTarget,
      DependencyStack dependencyStack)
      throws BuildFileParseException {
    return perBuildState.getUnconfiguredTargetNodeJob(unconfiguredBuildTarget, dependencyStack);
  }

  private RuntimeException propagateRuntimeCause(RuntimeException e)
      throws IOException, InterruptedException, BuildFileParseException {
    Throwables.throwIfInstanceOf(e, HumanReadableException.class);

    Throwable t = e.getCause();
    if (t != null) {
      Throwables.throwIfInstanceOf(t, IOException.class);
      Throwables.throwIfInstanceOf(t, InterruptedException.class);
      Throwables.throwIfInstanceOf(t, BuildFileParseException.class);
      Throwables.throwIfInstanceOf(t, BuildTargetException.class);
    }
    return e;
  }

  @Override
  public TargetGraphCreationResult buildTargetGraph(
      ParsingContext parsingContext, ImmutableSet<BuildTarget> toExplore)
      throws IOException, InterruptedException, BuildFileParseException {
    AtomicLong processedBytes = new AtomicLong();
    try (PerBuildState state = perBuildStateFactory.create(parsingContext, permState)) {
      return buildTargetGraph(state, toExplore, processedBytes);
    }
  }

  private TargetGraphCreationResult buildTargetGraph(
      PerBuildState state, ImmutableSet<BuildTarget> toExplore, AtomicLong processedBytes)
      throws IOException, InterruptedException, BuildFileParseException {

    if (toExplore.isEmpty()) {
      return TargetGraphCreationResult.of(TargetGraph.EMPTY, toExplore);
    }

    ParseEvent.Started parseStart = ParseEvent.started(toExplore);
    eventBus.post(parseStart);

    GraphTraversableWithPayloadAndDependencyStack<BuildTarget, TargetNode<?>> traversable =
        (target, dependencyStack) -> {
          TargetNode<?> node;
          try {
            TargetNodeMaybeIncompatible nodeMaybe = state.getTargetNode(target, dependencyStack);
            node = nodeMaybe.assertGetTargetNode(dependencyStack);
          } catch (BuildFileParseException e) {
            throw new RuntimeException(e);
          } catch (HumanReadableException e) {
            eventBus.post(ParseEvent.finished(parseStart, processedBytes.get(), Optional.empty()));
            throw e;
          }

          // this second lookup loop may *seem* pointless, but it allows us to report which node is
          // referring to a node we can't find - something that's very difficult in this Traversable
          // visitor pattern otherwise.
          // it's also work we need to do anyways. the getTargetNode() result is cached, so that
          // when we come around and re-visit that node there won't actually be any work performed.
          for (BuildTarget dep : node.getTotalDepsFastWithDuplicates()) {
            try {
              state.getTargetNode(dep, dependencyStack.child(dep));
            } catch (BuildFileParseException e) {
              throw ParserMessages.createReadableExceptionWithWhenSuffix(target, dep, e);
            } catch (HumanReadableException e) {
              if (e.getDependencyStack().isEmpty()) {
                // we don't have a proper stack, use simple message as fallback
                throw ParserMessages.createReadableExceptionWithWhenSuffix(target, dep, e);
              } else {
                throw e;
              }
            }
          }
          return new Pair<>(node, node.getTotalDepsFastWithDuplicates().iterator());
        };

    AcyclicDepthFirstPostOrderTraversalWithPayloadAndDependencyStack<BuildTarget, TargetNode<?>>
        targetNodeTraversal =
            new AcyclicDepthFirstPostOrderTraversalWithPayloadAndDependencyStack<>(
                traversable, DependencyStack::child);

    TargetGraph targetGraph = null;
    try (SimplePerfEvent.Scope scope =
        SimplePerfEvent.scope(eventBus.isolated(), "parser.create_target_graph")) {

      DirectedAcyclicGraph.Builder<TargetNode<?>> graph = DirectedAcyclicGraph.serialBuilder();
      Map<BuildTarget, TargetNode<?>> index = new HashMap<>();
      TemporaryUnconfiguredTargetToTargetUniquenessChecker checker =
          new TemporaryUnconfiguredTargetToTargetUniquenessChecker();

      for (Map.Entry<BuildTarget, Pair<TargetNode<?>, DependencyStack>> targetAndNode :
          targetNodeTraversal.traverse(toExplore).entrySet()) {
        BuildTarget target = targetAndNode.getKey();
        TargetNode<?> targetNode = targetAndNode.getValue().getFirst();
        DependencyStack dependencyStack = targetAndNode.getValue().getSecond();

        graph.addNode(targetNode);
        MoreMaps.putCheckEquals(index, target, targetNode);
        checker.addTarget(targetNode, dependencyStack);
        if (target.isFlavored()) {
          BuildTarget unflavoredTarget = target.withoutFlavors();
          MoreMaps.putCheckEquals(
              index,
              unflavoredTarget,
              state.getTargetNodeAssertCompatible(unflavoredTarget, dependencyStack));
          // NOTE: do not used uniqueness checked for unflavored target
          // because `target.withoutFlavors()` does not switch unconfigured target
        }
        for (BuildTarget dep : targetNode.getParseDeps()) {
          graph.addEdge(
              targetNode, state.getTargetNodeAssertCompatible(dep, dependencyStack.child(dep)));
        }
      }

      targetGraph = new TargetGraph(graph.build(), ImmutableMap.copyOf(index));
      return TargetGraphCreationResult.of(targetGraph, toExplore);
    } catch (CycleException e) {
      throw new HumanReadableException(e.getMessage());
    } catch (RuntimeException e) {
      throw propagateRuntimeCause(e);
    } finally {
      eventBus.post(
          ParseEvent.finished(parseStart, processedBytes.get(), Optional.ofNullable(targetGraph)));
    }
  }

  @Override
  public synchronized TargetGraphCreationResult buildTargetGraphWithoutTopLevelConfigurationTargets(
      ParsingContext parsingContext,
      Iterable<? extends TargetNodeSpec> targetNodeSpecs,
      Optional<TargetConfiguration> targetConfiguration)
      throws BuildFileParseException, IOException, InterruptedException {
    return buildTargetGraphForTargetNodeSpecs(
        parsingContext, targetNodeSpecs, targetConfiguration, true);
  }

  @Override
  public synchronized TargetGraphCreationResult buildTargetGraphWithTopLevelConfigurationTargets(
      ParsingContext parsingContext,
      Iterable<? extends TargetNodeSpec> targetNodeSpecs,
      Optional<TargetConfiguration> targetConfiguration)
      throws BuildFileParseException, IOException, InterruptedException {
    return buildTargetGraphForTargetNodeSpecs(
        parsingContext, targetNodeSpecs, targetConfiguration, false);
  }

  private synchronized TargetGraphCreationResult buildTargetGraphForTargetNodeSpecs(
      ParsingContext parsingContext,
      Iterable<? extends TargetNodeSpec> targetNodeSpecs,
      Optional<TargetConfiguration> targetConfiguration,
      boolean excludeConfigurationTargets)
      throws BuildFileParseException, IOException, InterruptedException {

    AtomicLong processedBytes = new AtomicLong();
    try (PerBuildState state = perBuildStateFactory.create(parsingContext, permState)) {

      ImmutableSet<BuildTarget> buildTargets =
          collectBuildTargetsFromTargetNodeSpecs(
              parsingContext,
              state,
              targetNodeSpecs,
              targetConfiguration,
              excludeConfigurationTargets);
      return buildTargetGraph(state, buildTargets, processedBytes);
    }
  }

  protected abstract ImmutableSet<BuildTarget> collectBuildTargetsFromTargetNodeSpecs(
      ParsingContext parsingContext,
      PerBuildState state,
      Iterable<? extends TargetNodeSpec> targetNodeSpecs,
      Optional<TargetConfiguration> targetConfiguration,
      boolean excludeConfigurationTargets)
      throws InterruptedException;

  @Override
  public String toString() {
    return permState.toString();
  }
}

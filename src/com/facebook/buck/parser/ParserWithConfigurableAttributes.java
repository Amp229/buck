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
import com.facebook.buck.core.cell.Cells;
import com.facebook.buck.core.cell.nameresolver.CellNameResolver;
import com.facebook.buck.core.description.attr.ImplicitFlavorsInferringDescription;
import com.facebook.buck.core.exceptions.DependencyStack;
import com.facebook.buck.core.exceptions.HumanReadableException;
import com.facebook.buck.core.filesystems.AbsPath;
import com.facebook.buck.core.model.BuildTarget;
import com.facebook.buck.core.model.Flavor;
import com.facebook.buck.core.model.HasDefaultFlavors;
import com.facebook.buck.core.model.TargetConfiguration;
import com.facebook.buck.core.model.UnflavoredBuildTarget;
import com.facebook.buck.core.model.targetgraph.TargetNode;
import com.facebook.buck.core.model.targetgraph.TargetNodeMaybeIncompatible;
import com.facebook.buck.core.model.targetgraph.raw.UnconfiguredTargetNode;
import com.facebook.buck.core.select.SelectableConfigurationContext;
import com.facebook.buck.core.select.SelectorList;
import com.facebook.buck.core.select.SelectorListResolved;
import com.facebook.buck.core.select.SelectorListResolver;
import com.facebook.buck.core.select.impl.SelectorListFactory;
import com.facebook.buck.core.util.log.Logger;
import com.facebook.buck.event.BuckEventBus;
import com.facebook.buck.event.ConsoleEvent;
import com.facebook.buck.parser.TargetSpecResolver.TargetNodeFilterForSpecResolver;
import com.facebook.buck.parser.api.BuildFileManifest;
import com.facebook.buck.parser.api.RawTargetNode;
import com.facebook.buck.parser.config.ParserConfig;
import com.facebook.buck.parser.exceptions.BuildFileParseException;
import com.facebook.buck.parser.spec.TargetNodeSpec;
import com.facebook.buck.parser.syntax.ListWithSelects;
import com.facebook.buck.rules.coercer.CoerceFailedException;
import com.facebook.buck.rules.coercer.concat.JsonTypeConcatenatingCoercer;
import com.facebook.buck.rules.coercer.concat.JsonTypeConcatenatingCoercerFactory;
import com.facebook.buck.rules.coercer.concat.SingleElementJsonTypeConcatenatingCoercer;
import com.facebook.buck.rules.param.CommonParamNames;
import com.facebook.buck.rules.param.ParamName;
import com.facebook.buck.rules.param.ParamNameOrSpecial;
import com.facebook.buck.rules.param.SpecialAttr;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.UncheckedExecutionException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.ExecutionException;
import java.util.stream.Stream;
import javax.annotation.Nullable;

/**
 * An implementation of {@link Parser} that supports attributes with configurable values (i.e.
 * defined using {@code select} keyword).
 *
 * <p>This implementation also supports notion of configuration rules which are used to resolve
 * conditions in {@code select} statements.
 */
class ParserWithConfigurableAttributes extends AbstractParser {

  private static final Logger LOG = Logger.get(ParserWithConfigurableAttributes.class);

  private final TargetSpecResolver targetSpecResolver;

  ParserWithConfigurableAttributes(
      DaemonicParserState daemonicParserState,
      PerBuildStateFactory perBuildStateFactory,
      TargetSpecResolver targetSpecResolver,
      BuckEventBus eventBus) {
    super(daemonicParserState, perBuildStateFactory, eventBus);
    this.targetSpecResolver = targetSpecResolver;
  }

  @VisibleForTesting
  static BuildTarget applyDefaultFlavors(
      BuildTarget target,
      TargetNodeMaybeIncompatible targetNodeMaybeIncompatible,
      TargetNodeSpec.TargetType targetType,
      ParserConfig.ApplyDefaultFlavorsMode applyDefaultFlavorsMode) {
    Optional<TargetNode<?>> targetNodeOptional =
        targetNodeMaybeIncompatible.getTargetNodeOptional();
    if (!targetNodeOptional.isPresent()) {
      return target;
    }
    TargetNode<?> targetNode = targetNodeOptional.get();
    if (target.isFlavored()
        || (targetType == TargetNodeSpec.TargetType.MULTIPLE_TARGETS
            && applyDefaultFlavorsMode == ParserConfig.ApplyDefaultFlavorsMode.SINGLE)
        || applyDefaultFlavorsMode == ParserConfig.ApplyDefaultFlavorsMode.DISABLED) {
      return target;
    }

    ImmutableSortedSet<Flavor> defaultFlavors = ImmutableSortedSet.of();
    if (targetNode.getConstructorArg() instanceof HasDefaultFlavors) {
      defaultFlavors = ((HasDefaultFlavors) targetNode.getConstructorArg()).getDefaultFlavors();
      LOG.debug("Got default flavors %s from args of %s", defaultFlavors, target);
    }

    if (targetNode.getDescription() instanceof ImplicitFlavorsInferringDescription) {
      defaultFlavors =
          ((ImplicitFlavorsInferringDescription) targetNode.getDescription())
              .addImplicitFlavors(defaultFlavors, target.getTargetConfiguration());
      LOG.debug("Got default flavors %s from description of %s", defaultFlavors, target);
    }

    return target.withFlavors(defaultFlavors);
  }

  /**
   * This implementation collects raw attributes of a target node and resolves configurable
   * attributes.
   */
  @Override
  @Nullable
  public SortedMap<ParamNameOrSpecial, Object> getTargetNodeRawAttributes(
      PerBuildState state, Cells cells, TargetNode<?> targetNode, DependencyStack dependencyStack)
      throws BuildFileParseException {
    BuildTarget buildTarget = targetNode.getBuildTarget();
    Cell cell = cells.getCell(buildTarget.getCell());
    BuildFileManifest buildFileManifest =
        getTargetNodeRawAttributes(
            state,
            cell,
            cell.getBuckConfigView(ParserConfig.class)
                .getAbsolutePathToBuildFile(
                    cell,
                    buildTarget.getUnconfiguredBuildTarget(),
                    dependencyStack.child(buildTarget)));
    return getTargetFromManifest(state, cell, targetNode, dependencyStack, buildFileManifest);
  }

  @Override
  public ListenableFuture<SortedMap<ParamNameOrSpecial, Object>> getTargetNodeRawAttributesJob(
      PerBuildState state, Cells cells, TargetNode<?> targetNode, DependencyStack dependencyStack)
      throws BuildFileParseException {
    Cell cell = cells.getCell(targetNode.getBuildTarget().getCell());
    ListenableFuture<BuildFileManifest> buildFileManifestFuture =
        state.getBuildFileManifestJob(
            cell,
            cell.getBuckConfigView(ParserConfig.class)
                .getAbsolutePathToBuildFile(
                    cell,
                    targetNode.getBuildTarget().getUnconfiguredBuildTarget(),
                    dependencyStack.child(targetNode.getBuildTarget())));
    return Futures.transform(
        buildFileManifestFuture,
        buildFileManifest ->
            getTargetFromManifest(state, cell, targetNode, dependencyStack, buildFileManifest),
        MoreExecutors.directExecutor());
  }

  @Nullable
  private SortedMap<ParamNameOrSpecial, Object> getTargetFromManifest(
      PerBuildState state,
      Cell cell,
      TargetNode<?> targetNode,
      DependencyStack dependencyStack,
      BuildFileManifest buildFileManifest) {
    BuildTarget buildTarget = targetNode.getBuildTarget();
    String shortName = buildTarget.getShortName();

    if (!buildFileManifest.getTargets().containsKey(shortName)) {
      return null;
    }

    RawTargetNode attributes = buildFileManifest.getTargets().get(shortName);

    SortedMap<ParamNameOrSpecial, Object> convertedAttributes =
        new TreeMap<>(ParamNameOrSpecial.COMPARATOR);
    convertedAttributes.putAll(
        new TreeMap<ParamNameOrSpecial, Object>(
            copyWithResolvingConfigurableAttributes(
                state, cell, buildTarget, attributes, dependencyStack)));

    convertedAttributes.put(
        SpecialAttr.DIRECT_DEPENDENCIES,
        targetNode.getParseDeps().stream()
            .map(Object::toString)
            .collect(ImmutableList.toImmutableList()));
    convertedAttributes.put(
        SpecialAttr.BASE_PATH, targetNode.getBuildTarget().getBaseName().getPath().toString());
    convertedAttributes.put(SpecialAttr.BUCK_TYPE, targetNode.getRuleType().getName());

    // NOTE: We are explicitly not using `attributes.getVisibility()` or
    // `attributes.getWithinView()` here as they don't take into account things like `PACKAGE`
    // files.
    List<String> computedVisibility =
        targetNode.getVisibilityPatterns().stream()
            .map(visibilityPattern -> visibilityPattern.getRepresentation())
            .collect(ImmutableList.toImmutableList());
    if (!computedVisibility.isEmpty()) {
      convertedAttributes.put(CommonParamNames.VISIBILITY, computedVisibility);
    }

    List<String> computedWithinView =
        targetNode.getWithinViewPatterns().stream()
            .map(visibilityPattern -> visibilityPattern.getRepresentation())
            .collect(ImmutableList.toImmutableList());
    if (!computedWithinView.isEmpty()) {
      convertedAttributes.put(CommonParamNames.WITHIN_VIEW, computedWithinView);
    }

    return convertedAttributes;
  }

  private SortedMap<ParamName, Object> copyWithResolvingConfigurableAttributes(
      PerBuildState state,
      Cell cell,
      BuildTarget buildTarget,
      RawTargetNode attributes,
      DependencyStack dependencyStack) {
    SelectableConfigurationContext configurationContext =
        SelectableConfigurationContext.of(
            cell.getBuckConfig(),
            state
                .getConfigurationRuleRegistry()
                .getTargetPlatformResolver()
                .getTargetPlatform(
                    buildTarget.getTargetConfiguration(),
                    dependencyStack.child(buildTarget.getTargetConfiguration())));

    SortedMap<ParamName, Object> convertedAttributes = new TreeMap<>();

    for (Map.Entry<ParamName, Object> attribute : attributes.getAttrs().entrySet()) {
      ParamName attributeName = attribute.getKey();
      try {
        Object resolvedAttribute =
            resolveConfigurableAttributes(
                state.getSelectorListResolver(),
                configurationContext,
                cell.getCellNameResolver(),
                buildTarget,
                state.getSelectorListFactory(),
                attributeName,
                attribute.getValue(),
                dependencyStack);

        // non-null attribute can be resolved to null
        if (resolvedAttribute == null) {
          continue;
        }
        convertedAttributes.put(attributeName, resolvedAttribute);
      } catch (CoerceFailedException e) {
        throw e.withAttrResolutionContext(
            attributeName, buildTarget.toStringWithConfiguration(), dependencyStack);
      } catch (HumanReadableException e) {
        throw new HumanReadableException(
            e,
            dependencyStack,
            "When resolving attribute %s of %s: %s",
            attributeName,
            buildTarget,
            e.getMessage());
      }
    }

    return convertedAttributes;
  }

  @Nullable
  private Object resolveConfigurableAttributes(
      SelectorListResolver selectorListResolver,
      SelectableConfigurationContext configurationContext,
      CellNameResolver cellNameResolver,
      BuildTarget buildTarget,
      SelectorListFactory selectorListFactory,
      ParamName attributeName,
      Object jsonObject,
      DependencyStack dependencyStack)
      throws CoerceFailedException {
    if (!(jsonObject instanceof ListWithSelects)) {
      return jsonObject;
    }

    SelectorList<Object> selectorList =
        selectorListFactory.create(
            cellNameResolver,
            buildTarget.getCellRelativeBasePath().getPath(),
            (ListWithSelects) jsonObject);

    JsonTypeConcatenatingCoercer coercer =
        JsonTypeConcatenatingCoercerFactory.createForType(((ListWithSelects) jsonObject).getType());

    if (((ListWithSelects) jsonObject).getElements().size() != 1) {
      if (coercer instanceof SingleElementJsonTypeConcatenatingCoercer) {
        throw new HumanReadableException(
            "type '%s' doesn't support select concatenation",
            ((ListWithSelects) jsonObject).getType().getName());
      }
    }

    String attributeName1 = attributeName.getSnakeCase();
    SelectorListResolved<Object> selectorListResolved =
        selectorListResolver.resolveSelectorList(selectorList, dependencyStack);

    // We do not validate `select` keys against `compatible_with` here
    // because we already did that during configured target graph construction
    // and this code is invoked by query after the configured target graph construction.

    return selectorListResolved.eval(
        configurationContext, coercer, buildTarget, attributeName1, dependencyStack);
  }

  @Override
  public ImmutableList<TargetNode<?>> getAllTargetNodesWithTargetCompatibilityFiltering(
      PerBuildState state,
      Cell cell,
      AbsPath buildFile,
      Optional<TargetConfiguration> targetConfiguration)
      throws BuildFileParseException {
    return filterIncompatibleTargetNodes(
            getAllTargetNodes(state, cell, buildFile, targetConfiguration).stream())
        .collect(ImmutableList.toImmutableList());
  }

  @Override
  public ImmutableList<UnconfiguredTargetNode> getAllUnconfiguredTargetNodes(
      PerBuildState state, Cell cell, AbsPath buildFile) throws BuildFileParseException {
    try {
      // TODO(srice) Yeah it's not great that we're randomly blocking in this code, we should really
      // be making this async. This just matches what we do for configured nodes though.
      return state.getAllUnconfiguredTargetNodesJobs(cell, buildFile).get();
    } catch (ExecutionException e) {
      throw new UncheckedExecutionException(e);
    } catch (InterruptedException e) {
      throw new RuntimeException("Interrupted while getting all nodes for " + buildFile, e);
    }
  }

  private Stream<TargetNode<?>> filterIncompatibleTargetNodes(
      Stream<TargetNodeMaybeIncompatible> targetNodes) {
    return targetNodes
        .map(targetNodeMaybeIncompatible -> targetNodeMaybeIncompatible.getTargetNodeOptional())
        .filter(targetNodeOptional -> targetNodeOptional.isPresent())
        .map(targetNodeOptional -> targetNodeOptional.get());
  }

  @Override
  public ImmutableList<ImmutableSet<BuildTarget>> resolveTargetSpecs(
      ParsingContext parsingContext,
      Iterable<? extends TargetNodeSpec> specs,
      Optional<TargetConfiguration> targetConfiguration)
      throws BuildFileParseException, InterruptedException {

    try (PerBuildState state = perBuildStateFactory.create(parsingContext, permState)) {
      return resolveTargetSpecs(state, specs, targetConfiguration);
    }
  }

  @Override
  public ImmutableList<ImmutableSet<BuildTarget>> resolveTargetSpecs(
      PerBuildState state,
      Iterable<? extends TargetNodeSpec> specs,
      Optional<TargetConfiguration> targetConfiguration)
      throws BuildFileParseException, InterruptedException {

    ParsingContext parsingContext = state.getParsingContext();
    TargetNodeFilterForSpecResolver<BuildTarget, TargetNodeMaybeIncompatible> targetNodeFilter =
        TargetNodeSpec::filter;

    ImmutableList<ImmutableSet<BuildTarget>> buildTargets =
        targetSpecResolver.resolveTargetSpecs(
            parsingContext.getCells(),
            specs,
            targetConfiguration,
            (buildTarget, targetNode, targetType) ->
                applyDefaultFlavors(
                    buildTarget,
                    targetNode,
                    targetType,
                    parsingContext.getApplyDefaultFlavorsMode()),
            state,
            targetNodeFilter);

    return buildTargets.stream()
        .map(
            targets ->
                filterIncompatibleTargetNodes(
                        targets.stream()
                            .map(
                                (BuildTarget target) ->
                                    state.getTargetNode(target, DependencyStack.top(target))))
                    .map(TargetNode::getBuildTarget)
                    .collect(ImmutableSet.toImmutableSet()))
        .collect(ImmutableList.toImmutableList());
  }

  @Override
  public ImmutableList<ImmutableSet<UnflavoredBuildTarget>> resolveTargetSpecsUnconfigured(
      PerBuildState perBuildState, Iterable<? extends TargetNodeSpec> specs)
      throws BuildFileParseException, InterruptedException {
    ParsingContext parsingContext = perBuildState.getParsingContext();
    TargetNodeFilterForSpecResolver<UnflavoredBuildTarget, UnconfiguredTargetNode>
        targetNodeFilter = TargetNodeSpec::filterUnconfigured;

    return targetSpecResolver.resolveTargetSpecsUnconfigured(
        parsingContext.getCells(), specs, perBuildState, targetNodeFilter);
  }

  @Override
  protected ImmutableSet<BuildTarget> collectBuildTargetsFromTargetNodeSpecs(
      ParsingContext parsingContext,
      PerBuildState state,
      Iterable<? extends TargetNodeSpec> targetNodeSpecs,
      Optional<TargetConfiguration> targetConfiguration,
      boolean excludeConfigurationTargets)
      throws InterruptedException {

    TargetNodeFilterForSpecResolver<BuildTarget, TargetNodeMaybeIncompatible> targetNodeFilter =
        TargetNodeSpec::filter;

    if (excludeConfigurationTargets) {
      targetNodeFilter =
          new TargetNodeFilterForSpecResolverWithNodeFiltering<>(
              targetNodeFilter, ParserWithConfigurableAttributes::filterOutNonBuildTargets);
    }

    ImmutableList<ImmutableSet<BuildTarget>> buildTargets =
        targetSpecResolver.resolveTargetSpecs(
            parsingContext.getCells(),
            targetNodeSpecs,
            targetConfiguration,
            (buildTarget, targetNode, targetType) ->
                applyDefaultFlavors(
                    buildTarget,
                    targetNode,
                    targetType,
                    parsingContext.getApplyDefaultFlavorsMode()),
            state,
            targetNodeFilter);
    long totalTargets = buildTargets.stream().mapToInt(targets -> targets.size()).sum();
    ImmutableSet<BuildTarget> filteredBuildTargets =
        filterIncompatibleTargetNodes(
                buildTargets.stream()
                    .flatMap(ImmutableSet::stream)
                    .map(
                        (BuildTarget target) ->
                            state.getTargetNode(target, DependencyStack.top(target))))
            .map(TargetNode::getBuildTarget)
            .collect(ImmutableSet.toImmutableSet());
    long skippedTargets = totalTargets - filteredBuildTargets.size();
    if (skippedTargets > 0) {
      this.eventBus.post(
          ConsoleEvent.warning(
              String.format(
                  "%d target%s skipped due to incompatibility with target configuration",
                  skippedTargets, skippedTargets > 1 ? "s" : "")));
    }
    return filteredBuildTargets;
  }

  private static boolean filterOutNonBuildTargets(
      TargetNodeMaybeIncompatible targetNodeMaybeIncompatible) {
    Optional<TargetNode<?>> targetNodeOptional =
        targetNodeMaybeIncompatible.getTargetNodeOptional();
    return !targetNodeOptional.isPresent() || targetNodeOptional.get().getRuleType().isBuildRule();
  }
}

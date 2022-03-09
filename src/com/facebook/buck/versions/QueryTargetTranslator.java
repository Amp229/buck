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

package com.facebook.buck.versions;

import com.facebook.buck.core.cell.name.CanonicalCellName;
import com.facebook.buck.core.cell.nameresolver.CellNameResolver;
import com.facebook.buck.core.model.BaseName;
import com.facebook.buck.core.model.BuildTarget;
import com.facebook.buck.core.parser.buildtargetparser.UnconfiguredBuildTargetViewFactory;
import com.facebook.buck.query.QueryException;
import com.facebook.buck.rules.query.Query;
import com.facebook.buck.rules.query.QueryUtils;
import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class QueryTargetTranslator implements TargetTranslator<Query> {

  private final UnconfiguredBuildTargetViewFactory unconfiguredBuildTargetFactory;

  public QueryTargetTranslator(UnconfiguredBuildTargetViewFactory unconfiguredBuildTargetFactory) {
    this.unconfiguredBuildTargetFactory = unconfiguredBuildTargetFactory;
  }

  @Override
  public Class<Query> getTranslatableClass() {
    return Query.class;
  }

  @Override
  public Optional<Query> translateTargets(
      CellNameResolver cellNameResolver,
      BaseName targetBaseName,
      TargetNodeTranslator translator,
      Query query) {

    // Extract all build targets from the original query string.
    ImmutableList<BuildTarget> targets;
    try {
      targets =
          QueryUtils.extractBuildTargets(cellNameResolver, targetBaseName, query)
              .collect(ImmutableList.toImmutableList());
    } catch (QueryException e) {
      throw new RuntimeException("Error parsing/executing query from deps", e);
    }

    // If there's no targets, bail early.
    if (targets.isEmpty()) {
      return Optional.empty();
    }

    List<String> patterns = new ArrayList<>();
    // Match all fully qualified targets.
    targets.stream()
        .map(Object::toString)
        .map(Pattern::quote)
        // Use a positive look-behind assertion to avoid matching other targets.
        .map(p -> p + "(?=[) '\",]|$)")
        .forEach(patterns::add);
    if (!cellNameResolver.getCurrentCellName().equals(CanonicalCellName.rootCell())) {
      targets.stream()
          .filter(t -> t.getCell().equals(cellNameResolver.getCurrentCellName()))
          .map(t -> "//" + t.getFullyQualifiedName().split("//")[1])
          .map(Pattern::quote)
          // Use a positive look-behind assertion to avoid matching other targets.
          .map(p -> p + "(?=[) '\",]|$)")
          .forEach(patterns::add);
    }
    // Match all short name targets (e.g. `:foo`) for targets matching the top-level target
    // basename.
    targets.stream()
        .filter(t -> t.getBaseName().equals(targetBaseName))
        .map(t -> ":" + t.getShortNameAndFlavorPostfix())
        .map(Pattern::quote)
        // Use a positive look-behind assertion to avoid matching in a fully qualified target.
        .map(p -> "((?<=[( \"',]|^)" + p + "(?=[) '\",]|$))")
        .forEach(patterns::add);

    // A pattern matching all of the build targets in the query string.
    Pattern targetsPattern = Pattern.compile(patterns.stream().collect(Collectors.joining("|")));

    // Build a new query string from the original by translating all build targets.
    String queryString = query.getQuery();
    Matcher matcher = targetsPattern.matcher(queryString);
    StringBuilder builder = new StringBuilder();
    int lastEnd = 0;
    while (matcher.find()) {
      builder.append(queryString, lastEnd, matcher.start());
      BuildTarget target =
          unconfiguredBuildTargetFactory
              .createForBaseName(targetBaseName, matcher.group(), cellNameResolver)
              .configure(query.getTargetConfiguration());
      Optional<BuildTarget> translated =
          translator.translate(cellNameResolver, targetBaseName, target);
      builder.append(
          translated
              .map(BuildTarget::getFullyQualifiedName)
              .orElse(queryString.substring(matcher.start(), matcher.end())));
      lastEnd = matcher.end();
    }
    builder.append(queryString, lastEnd, queryString.length());
    String newQuery = builder.toString();

    return queryString.equals(newQuery) ? Optional.empty() : Optional.of(query.withQuery(newQuery));
  }
}

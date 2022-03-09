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

package com.facebook.buck.query;

import com.facebook.buck.query.QueryEnvironment.Argument;
import com.facebook.buck.query.QueryEnvironment.ArgumentType;
import com.facebook.buck.query.QueryEnvironment.QueryFunction;
import com.facebook.buck.rules.param.ParamName;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * A attrregexfilter(attribute, pattern, argument) filter expression, which computes the subset of
 * nodes in 'argument' whose 'attribute' matches the given pattern.
 *
 * <pre>expr ::= ATTRREGEXFILTER '(' WORD ',' WORD ',' expr ')'</pre>
 */
public class AttrRegexFilterFunction<NODE_TYPE> implements QueryFunction<NODE_TYPE> {

  private static final ImmutableList<ArgumentType> ARGUMENT_TYPES =
      ImmutableList.of(ArgumentType.WORD, ArgumentType.WORD, ArgumentType.EXPRESSION);

  public AttrRegexFilterFunction() {}

  @Override
  public String getName() {
    return "attrregexfilter";
  }

  @Override
  public int getMandatoryArguments() {
    return 3;
  }

  @Override
  public ImmutableList<ArgumentType> getArgumentTypes() {
    return ARGUMENT_TYPES;
  }

  @Override
  public Set<NODE_TYPE> eval(
      QueryEvaluator<NODE_TYPE> evaluator,
      QueryEnvironment<NODE_TYPE> env,
      ImmutableList<Argument<NODE_TYPE>> args)
      throws QueryException {
    QueryExpression<NODE_TYPE> argument = args.get(args.size() - 1).getExpression();
    ParamName attr = ParamName.bySnakeCase(args.get(0).getWord());

    String attrValue = args.get(1).getWord();
    Pattern compiledPattern;
    try {
      compiledPattern = Pattern.compile(attrValue);
    } catch (IllegalArgumentException e) {
      throw new QueryException(
          String.format("Illegal pattern regexp '%s': %s", attrValue, e.getMessage()));
    }
    // filterAttributeContents() below will traverse the entire type hierarchy of each attr (see the
    // various type coercers). Collection types are (1) very common (2) expensive to convert to
    // string and (3) we shouldn't apply the filter to the stringified form, and so we have a fast
    // path to ignore them.
    Predicate<Object> predicate =
        input ->
            !(input instanceof Collection || input instanceof Map)
                && compiledPattern.matcher(input.toString()).find();

    ImmutableSet.Builder<NODE_TYPE> result = new ImmutableSet.Builder<>();
    Set<NODE_TYPE> targets = evaluator.eval(argument, env);
    for (NODE_TYPE target : targets) {
      Set<Object> matchingObjects = env.filterAttributeContents(target, attr, predicate);
      if (!matchingObjects.isEmpty()) {
        result.add(target);
      }
    }
    return result.build();
  }
}

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

import static org.junit.Assert.assertEquals;

import com.facebook.buck.core.model.BuildTargetFactory;
import com.facebook.buck.rules.macros.StringWithMacros;
import com.facebook.buck.rules.param.ParamName;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import java.util.Collections;
import java.util.Set;
import java.util.function.Predicate;
import org.junit.Test;

public class AttrFilterFunctionTest {
  private QueryEnvironment<ConfiguredQueryTarget> queryEnvironment;
  private final ConfiguredQueryBuildTarget onlyTarget =
      ConfiguredQueryBuildTarget.of(BuildTargetFactory.newInstance("//x:y"));

  @Test
  public void singleValue() throws QueryException {
    queryEnvironment = new TestQueryEnvironment("a", "b");
    assertQuery("attrfilter('a', 'b', //x:y)", ImmutableSet.of(onlyTarget));
    assertQuery("attrfilter('a', 'z', //x:y)", ImmutableSet.of());
  }

  @Test
  public void singleStringWithMacroValue() throws QueryException {
    queryEnvironment = new TestQueryEnvironment("a", StringWithMacros.ofConstantString("b"));
    assertQuery("attrfilter('a', 'b', //x:y)", ImmutableSet.of(onlyTarget));
    assertQuery("attrfilter('a', 'z', //x:y)", ImmutableSet.of());
  }

  @Test
  public void listValue() throws QueryException {
    queryEnvironment = new TestQueryEnvironment("a", ImmutableList.of("b", "c"));
    assertQuery("attrfilter('a', 'b', //x:y)", ImmutableSet.of(onlyTarget));
    assertQuery("attrfilter('a', 'c', //x:y)", ImmutableSet.of(onlyTarget));
    assertQuery("attrfilter('a', 'e', //x:y)", ImmutableSet.of());
  }

  @Test
  public void listOfStringWithMacrosValue() throws QueryException {
    queryEnvironment =
        new TestQueryEnvironment(
            "a",
            ImmutableList.of(
                StringWithMacros.ofConstantString("b"), StringWithMacros.ofConstantString("c")));
    assertQuery("attrfilter('a', 'b', //x:y)", ImmutableSet.of(onlyTarget));
    assertQuery("attrfilter('a', 'c', //x:y)", ImmutableSet.of(onlyTarget));
    assertQuery("attrfilter('a', 'e', //x:y)", ImmutableSet.of());
  }

  private void assertQuery(String query, Set<ConfiguredQueryBuildTarget> expected)
      throws QueryException {
    QueryExpression<ConfiguredQueryTarget> queryExpr =
        QueryParser.parse(query, queryEnvironment.getQueryParserEnv());

    Set<ConfiguredQueryTarget> result =
        queryExpr.eval(new NoopQueryEvaluator<>(), queryEnvironment);
    assertEquals(expected, result);
  }

  private class TestQueryEnvironment extends BaseTestQueryEnvironment<ConfiguredQueryTarget> {
    private final ParamName attributeName;
    private final Object attributeValue;

    public TestQueryEnvironment(String attributeName, Object attributeValue) {
      this.attributeName = ParamName.bySnakeCase(attributeName);
      this.attributeValue = attributeValue;
    }

    @Override
    public Iterable<QueryFunction<ConfiguredQueryTarget>> getFunctions() {
      return ImmutableList.of(new AttrFilterFunction<>());
    }

    @Override
    public TargetEvaluator getTargetEvaluator() {
      return new TargetEvaluator() {
        @Override
        public Set<ConfiguredQueryTarget> evaluateTarget(String target) {
          if (target.equals(onlyTarget.toString())) {
            return Collections.singleton(onlyTarget);
          }
          return Collections.emptySet();
        }

        @Override
        public Type getType() {
          return Type.IMMEDIATE;
        }
      };
    }

    @Override
    public Set<Object> filterAttributeContents(
        ConfiguredQueryTarget target, ParamName attribute, Predicate<Object> predicate) {

      if (target == onlyTarget && attribute.equals(this.attributeName)) {
        if (predicate.test(attributeValue)) {
          return ImmutableSet.of(onlyTarget);
        }
      }
      return ImmutableSet.of();
    }
  }
}

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

import static org.easymock.EasyMock.createNiceMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.replay;
import static org.hamcrest.MatcherAssert.assertThat;

import com.facebook.buck.core.model.BuildTargetFactory;
import com.facebook.buck.core.model.targetgraph.TargetGraph;
import com.facebook.buck.core.model.targetgraph.TargetGraphFactory;
import com.facebook.buck.core.model.targetgraph.TargetNode;
import com.facebook.buck.jvm.java.JavaLibraryBuilder;
import com.facebook.buck.query.QueryEnvironment.Argument;
import com.facebook.buck.util.stream.RichStream;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import java.util.Set;
import org.easymock.Capture;
import org.easymock.EasyMock;
import org.hamcrest.Matchers;
import org.junit.Test;

public class DepsFunctionTest {

  private static final DepsFunction DEPS_FUNCTION = new DepsFunction();
  private static final QueryEnvironment.Argument FIRST_ORDER_DEPS =
      QueryEnvironment.Argument.of(
          new FunctionExpression(new DepsFunction.FirstOrderDepsFunction(), ImmutableList.of()));
  private static final QueryEnvironment.Argument DEPTH = QueryEnvironment.Argument.of(10);

  @Test
  public void testFilterArgumentDoesNotLimitDeps() throws Exception {
    TargetNode<?> b =
        JavaLibraryBuilder.createBuilder(BuildTargetFactory.newInstance("//:b")).build();
    TargetNode<?> a =
        JavaLibraryBuilder.createBuilder(BuildTargetFactory.newInstance("//:a"))
            .addDep(b.getBuildTarget())
            .build();
    TargetGraph targetGraph = TargetGraphFactory.newInstance(a, b);
    QueryEnvironment queryEnvironment = makeFakeQueryEnvironment(targetGraph);
    Set<?> result =
        DEPS_FUNCTION.eval(
            new NoopQueryEvaluator(),
            queryEnvironment,
            ImmutableList.of(
                Argument.of(TargetLiteral.of(a.getBuildTarget().getFullyQualifiedName())),
                DEPTH,
                FIRST_ORDER_DEPS));
    assertThat(
        result,
        Matchers.containsInAnyOrder(
            ConfiguredQueryBuildTarget.of(a.getBuildTarget()),
            ConfiguredQueryBuildTarget.of(b.getBuildTarget())));
  }

  @Test
  public void testFilterArgumentLimitsDeps() throws Exception {
    TargetNode<?> c =
        JavaLibraryBuilder.createBuilder(BuildTargetFactory.newInstance("//foo:c")).build();
    TargetNode<?> b =
        JavaLibraryBuilder.createBuilder(BuildTargetFactory.newInstance("//bar:b"))
            .addDep(c.getBuildTarget())
            .build();
    TargetNode<?> a =
        JavaLibraryBuilder.createBuilder(BuildTargetFactory.newInstance("//foo:a"))
            .addDep(b.getBuildTarget())
            .build();
    TargetGraph targetGraph = TargetGraphFactory.newInstance(a, b, c);
    QueryEnvironment queryEnvironment = makeFakeQueryEnvironment(targetGraph);
    FunctionExpression expression =
        new FunctionExpression(
            new FilterFunction(), ImmutableList.of(Argument.of("//foo.*"), FIRST_ORDER_DEPS));
    assertThat(
        (Iterable<ConfiguredQueryBuildTarget>)
            DEPS_FUNCTION.eval(
                new NoopQueryEvaluator(),
                queryEnvironment,
                ImmutableList.of(
                    QueryEnvironment.Argument.of(
                        TargetLiteral.of(a.getBuildTarget().getFullyQualifiedName())),
                    DEPTH,
                    QueryEnvironment.Argument.of(expression))),
        Matchers.contains(ConfiguredQueryBuildTarget.of(a.getBuildTarget())));
  }

  private QueryEnvironment<ConfiguredQueryBuildTarget> makeFakeQueryEnvironment(
      TargetGraph targetGraph) throws Exception {
    QueryEnvironment env = createNiceMock(QueryEnvironment.class);

    expect(env.getTargetEvaluator())
        .andReturn(
            new QueryEnvironment.TargetEvaluator() {
              @Override
              public Set evaluateTarget(String target) {
                return ImmutableSet.of(
                    ConfiguredQueryBuildTarget.of(BuildTargetFactory.newInstance(target)));
              }

              @Override
              public Type getType() {
                return Type.IMMEDIATE;
              }
            });

    Capture<Iterable<ConfiguredQueryBuildTarget>> targetsCapture = Capture.newInstance();
    expect(env.getFwdDeps(EasyMock.capture(targetsCapture)))
        .andStubAnswer(
            () ->
                RichStream.from(targetsCapture.getValue())
                    .map(ConfiguredQueryBuildTarget.class::cast)
                    .map(ConfiguredQueryBuildTarget::getBuildTarget)
                    .map(targetGraph::get)
                    .flatMap(n -> targetGraph.getOutgoingNodesFor(n).stream())
                    .map(TargetNode::getBuildTarget)
                    .map(ConfiguredQueryBuildTarget::of)
                    .toImmutableSet());

    replay(env);

    return env;
  }
}

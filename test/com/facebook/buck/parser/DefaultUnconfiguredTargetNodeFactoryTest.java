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

import static org.junit.Assert.assertEquals;

import com.facebook.buck.core.cell.Cells;
import com.facebook.buck.core.cell.TestCellBuilder;
import com.facebook.buck.core.exceptions.DependencyStack;
import com.facebook.buck.core.filesystems.ForwardRelPath;
import com.facebook.buck.core.model.ConfigurationBuildTargetFactoryForTests;
import com.facebook.buck.core.model.RuleType;
import com.facebook.buck.core.model.UnconfiguredBuildTarget;
import com.facebook.buck.core.model.UnconfiguredBuildTargetFactoryForTests;
import com.facebook.buck.core.model.targetgraph.impl.Package;
import com.facebook.buck.core.model.targetgraph.raw.UnconfiguredTargetNode;
import com.facebook.buck.core.parser.buildtargetparser.ParsingUnconfiguredBuildTargetViewFactory;
import com.facebook.buck.core.parser.buildtargetpattern.UnconfiguredBuildTargetParser;
import com.facebook.buck.core.plugin.impl.BuckPluginManagerFactory;
import com.facebook.buck.core.rules.knowntypes.TestKnownRuleTypesProvider;
import com.facebook.buck.core.rules.knowntypes.provider.KnownRuleTypesProvider;
import com.facebook.buck.core.select.Selector;
import com.facebook.buck.core.select.SelectorKey;
import com.facebook.buck.core.select.SelectorList;
import com.facebook.buck.core.select.impl.SelectorFactory;
import com.facebook.buck.core.select.impl.SelectorListFactory;
import com.facebook.buck.core.sourcepath.UnconfiguredSourcePathFactoryForTests;
import com.facebook.buck.parser.api.PackageMetadata;
import com.facebook.buck.parser.api.RawTargetNode;
import com.facebook.buck.parser.syntax.ListWithSelects;
import com.facebook.buck.parser.syntax.SelectorValue;
import com.facebook.buck.rules.coercer.DefaultTypeCoercerFactory;
import com.facebook.buck.rules.param.ParamName;
import com.facebook.buck.util.collect.TwoArraysImmutableHashMap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import java.util.Map;
import java.util.Optional;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class DefaultUnconfiguredTargetNodeFactoryTest {

  private DefaultUnconfiguredTargetNodeFactory factory;
  private Cells cell;

  @Rule public ExpectedException thrown = ExpectedException.none();

  @Before
  public void setUp() {
    KnownRuleTypesProvider knownRuleTypesProvider =
        TestKnownRuleTypesProvider.create(BuckPluginManagerFactory.createPluginManager());

    cell = new TestCellBuilder().build();

    factory =
        new DefaultUnconfiguredTargetNodeFactory(
            knownRuleTypesProvider,
            new BuiltTargetVerifier(),
            cell,
            new SelectorListFactory(
                new SelectorFactory(new ParsingUnconfiguredBuildTargetViewFactory())),
            new DefaultTypeCoercerFactory());
  }

  @Test
  public void createPopulatesNode() {
    UnconfiguredBuildTarget buildTarget =
        UnconfiguredBuildTargetFactoryForTests.newInstance("//a/b:c");

    ImmutableMap<String, Object> inputAttributes =
        ImmutableMap.<String, Object>builder()
            .put("name", "c")
            .put("deps", ImmutableList.of("//a/b:d", "//a/b:e"))
            .put(
                "resources",
                ListWithSelects.of(
                    ImmutableList.of(
                        SelectorValue.of(
                            ImmutableMap.of(
                                "//c:a",
                                ImmutableList.of("//a/b:file1", "//a/b:file2"),
                                "//c:b",
                                ImmutableList.of("//a/b:file3", "//a/b:file4")),
                            "")),
                    ImmutableList.class))
            .build();

    ImmutableMap<String, Object> expectAttributes =
        ImmutableMap.<String, Object>builder()
            .put("name", "c")
            .put(
                "deps",
                ImmutableList.of(
                    UnconfiguredBuildTargetParser.parse("//a/b:d"),
                    UnconfiguredBuildTargetParser.parse("//a/b:e")))
            .put(
                "resources",
                new SelectorList<>(
                    ImmutableList.of(
                        new Selector<>(
                            ImmutableMap.of(
                                new SelectorKey(
                                    ConfigurationBuildTargetFactoryForTests.newInstance("//c:a")),
                                ImmutableList.of(
                                    UnconfiguredSourcePathFactoryForTests.unconfiguredSourcePath(
                                        "//a/b:file1"),
                                    UnconfiguredSourcePathFactoryForTests.unconfiguredSourcePath(
                                        "//a/b:file2")),
                                new SelectorKey(
                                    ConfigurationBuildTargetFactoryForTests.newInstance("//c:b")),
                                ImmutableList.of(
                                    UnconfiguredSourcePathFactoryForTests.unconfiguredSourcePath(
                                        "//a/b:file3"),
                                    UnconfiguredSourcePathFactoryForTests.unconfiguredSourcePath(
                                        "//a/b:file4"))),
                            ImmutableSet.of(),
                            ""))))
            .build();

    UnconfiguredTargetNode unconfiguredTargetNode =
        factory.create(
            cell.getRootCell(),
            cell.getRootCell().getRoot().resolve("a/b/BUCK"),
            buildTarget,
            DependencyStack.root(),
            RawTargetNode.copyOf(
                ForwardRelPath.of("a/b"),
                "java_library",
                ImmutableList.of("//a/..."),
                ImmutableList.of("//b/..."),
                TwoArraysImmutableHashMap.copyOf(inputAttributes)),
            getPackage());

    assertEquals(
        RuleType.of("java_library", RuleType.Kind.BUILD), unconfiguredTargetNode.getRuleType());
    assertEquals(buildTarget.getUnflavoredBuildTarget(), unconfiguredTargetNode.getBuildTarget());

    assertEquals(
        expectAttributes.entrySet().stream()
            .collect(
                ImmutableMap.toImmutableMap(
                    e -> ParamName.bySnakeCase(e.getKey()), Map.Entry::getValue)),
        unconfiguredTargetNode.getAttributes());

    assertEquals(
        "//a/...",
        Iterables.getOnlyElement(unconfiguredTargetNode.getVisibilityPatterns())
            .getRepresentation());

    assertEquals(
        "//b/...",
        Iterables.getOnlyElement(unconfiguredTargetNode.getWithinViewPatterns())
            .getRepresentation());
  }

  @Test
  public void createWithVisibilityEntriesAndExcludePackage() {
    UnconfiguredBuildTarget buildTarget =
        UnconfiguredBuildTargetFactoryForTests.newInstance("//a/b:c");

    ImmutableMap<String, Object> inputAttributes =
        ImmutableMap.<String, Object>builder().put("name", "c").build();

    UnconfiguredTargetNode unconfiguredTargetNode =
        factory.create(
            cell.getRootCell(),
            cell.getRootCell().getRoot().resolve("a/b/BUCK"),
            buildTarget,
            DependencyStack.root(),
            RawTargetNode.copyOf(
                ForwardRelPath.of("a/b"),
                "java_library",
                ImmutableList.of("//a/..."),
                ImmutableList.of("//b/..."),
                TwoArraysImmutableHashMap.copyOf(inputAttributes)),
            getPackage());

    assertEquals(
        "//a/...",
        Iterables.getOnlyElement(unconfiguredTargetNode.getVisibilityPatterns())
            .getRepresentation());

    assertEquals(
        "//b/...",
        Iterables.getOnlyElement(unconfiguredTargetNode.getWithinViewPatterns())
            .getRepresentation());
  }

  @Test
  public void createWithVisibilityEntriesAndIncludePackage() {
    UnconfiguredBuildTarget buildTarget =
        UnconfiguredBuildTargetFactoryForTests.newInstance("//a/b:c");

    ImmutableMap<String, Object> inputAttributes =
        ImmutableMap.<String, Object>builder().put("name", "c").build();

    UnconfiguredTargetNode unconfiguredTargetNode =
        factory.create(
            cell.getRootCell(),
            cell.getRootCell().getRoot().resolve("a/b/BUCK"),
            buildTarget,
            DependencyStack.root(),
            RawTargetNode.copyOf(
                ForwardRelPath.of("a/b"),
                "java_library",
                ImmutableList.of("//a/..."),
                ImmutableList.of("//b/...", "EXTEND_PACKAGE"),
                TwoArraysImmutableHashMap.copyOf(inputAttributes)),
            getPackage());

    assertEquals(
        "//a/...",
        Iterables.getOnlyElement(unconfiguredTargetNode.getVisibilityPatterns())
            .getRepresentation());

    assertEquals(unconfiguredTargetNode.getWithinViewPatterns().size(), 2);
    assertEquals(
        "//b/...",
        Iterables.get(unconfiguredTargetNode.getWithinViewPatterns(), 0).getRepresentation());
    assertEquals(
        "//d/...",
        Iterables.get(unconfiguredTargetNode.getWithinViewPatterns(), 1).getRepresentation());
  }

  Package getPackage() {
    PackageMetadata pkg =
        PackageMetadata.of(false, ImmutableList.of("//a/..."), ImmutableList.of("//d/..."));

    return PackageFactory.create(
        cell.getRootCell(), ForwardRelPath.of("a/b/PACKAGE"), pkg, Optional.empty());
  }
}

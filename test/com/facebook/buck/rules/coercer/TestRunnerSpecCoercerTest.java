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

package com.facebook.buck.rules.coercer;

import static org.junit.Assert.assertEquals;

import com.facebook.buck.core.cell.TestCellPathResolver;
import com.facebook.buck.core.cell.nameresolver.CellNameResolver;
import com.facebook.buck.core.filesystems.ForwardRelPath;
import com.facebook.buck.core.model.ConstantHostTargetConfigurationResolver;
import com.facebook.buck.core.model.UnconfiguredTargetConfiguration;
import com.facebook.buck.core.test.rule.TestRunnerSpec;
import com.facebook.buck.io.filesystem.ProjectFilesystem;
import com.facebook.buck.io.filesystem.impl.FakeProjectFilesystem;
import com.facebook.buck.rules.macros.StringWithMacrosUtils;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class TestRunnerSpecCoercerTest {

  @Rule public ExpectedException expectedException = ExpectedException.none();

  private final StringWithMacrosTypeCoercer stringWithMacrosTypeCoercer =
      StringWithMacrosTypeCoercer.builder()
          .put(
              "test",
              StringWithMacrosTypeCoercerTest.TestMacro.class,
              new StringWithMacrosTypeCoercerTest.TestMacroTypeCoercer())
          .build();

  private final TestRunnerSpecCoercer coercer =
      new TestRunnerSpecCoercer(stringWithMacrosTypeCoercer);

  private final ProjectFilesystem filesystem = new FakeProjectFilesystem();
  private final CellNameResolver cellNameResolver =
      TestCellPathResolver.get(filesystem).getCellNameResolver();
  private final ForwardRelPath basePath = ForwardRelPath.of("");

  @Test
  public void coerceMapWithMacros() throws CoerceFailedException {
    TestRunnerSpec spec =
        coercer.coerceBoth(
            cellNameResolver,
            filesystem,
            basePath,
            UnconfiguredTargetConfiguration.INSTANCE,
            new ConstantHostTargetConfigurationResolver(UnconfiguredTargetConfiguration.INSTANCE),
            ImmutableMap.of("$(test arg)", "foo"));

    assertEquals(
        TestRunnerSpec.ofMap(
            ImmutableMap.of(
                StringWithMacrosUtils.format(
                    "%s", new StringWithMacrosTypeCoercerTest.TestMacro(ImmutableList.of("arg"))),
                TestRunnerSpec.ofStringWithMacros(StringWithMacrosUtils.format("foo")))),
        spec);
  }

  @Test
  public void coerceListWithMacros() throws CoerceFailedException {
    TestRunnerSpec spec =
        coercer.coerceBoth(
            cellNameResolver,
            filesystem,
            basePath,
            UnconfiguredTargetConfiguration.INSTANCE,
            new ConstantHostTargetConfigurationResolver(UnconfiguredTargetConfiguration.INSTANCE),
            ImmutableList.of("$(test arg)", "foo"));

    assertEquals(
        TestRunnerSpec.ofList(
            ImmutableList.of(
                TestRunnerSpec.ofStringWithMacros(
                    StringWithMacrosUtils.format(
                        "%s",
                        new StringWithMacrosTypeCoercerTest.TestMacro(ImmutableList.of("arg")))),
                TestRunnerSpec.ofStringWithMacros(StringWithMacrosUtils.format("foo")))),
        spec);
  }

  @Test
  public void coerceNestedWithMacros() throws CoerceFailedException {
    TestRunnerSpec spec =
        coercer.coerceBoth(
            cellNameResolver,
            filesystem,
            basePath,
            UnconfiguredTargetConfiguration.INSTANCE,
            new ConstantHostTargetConfigurationResolver(UnconfiguredTargetConfiguration.INSTANCE),
            ImmutableMap.of("a", ImmutableList.of("foo", "some $(test arg2)")));

    assertEquals(
        TestRunnerSpec.ofMap(
            ImmutableMap.of(
                StringWithMacrosUtils.format("a"),
                TestRunnerSpec.ofList(
                    ImmutableList.of(
                        TestRunnerSpec.ofStringWithMacros(StringWithMacrosUtils.format("foo")),
                        TestRunnerSpec.ofStringWithMacros(
                            StringWithMacrosUtils.format(
                                "some %s",
                                new StringWithMacrosTypeCoercerTest.TestMacro(
                                    ImmutableList.of("arg2")))))))),
        spec);
  }

  @Test
  public void coerceNumbers() throws CoerceFailedException {
    TestRunnerSpec spec =
        coercer.coerceBoth(
            cellNameResolver,
            filesystem,
            basePath,
            UnconfiguredTargetConfiguration.INSTANCE,
            new ConstantHostTargetConfigurationResolver(UnconfiguredTargetConfiguration.INSTANCE),
            ImmutableMap.of("a", 1.0, "b", 2));

    assertEquals(
        TestRunnerSpec.ofMap(
            ImmutableMap.of(
                StringWithMacrosUtils.format("a"),
                TestRunnerSpec.ofNumber(1.0),
                StringWithMacrosUtils.format("b"),
                TestRunnerSpec.ofNumber(2))),
        spec);
  }

  @Test
  public void coerceBooleans() throws CoerceFailedException {
    TestRunnerSpec spec =
        coercer.coerceBoth(
            cellNameResolver,
            filesystem,
            basePath,
            UnconfiguredTargetConfiguration.INSTANCE,
            new ConstantHostTargetConfigurationResolver(UnconfiguredTargetConfiguration.INSTANCE),
            ImmutableMap.of("bb", true, "bby", false));

    assertEquals(
        TestRunnerSpec.ofMap(
            ImmutableMap.of(
                StringWithMacrosUtils.format("bb"),
                TestRunnerSpec.ofBoolean(true),
                StringWithMacrosUtils.format("bby"),
                TestRunnerSpec.ofBoolean(false))),
        spec);
  }

  @Test
  public void coerceFailsWhenMapKeysNotStringWithMacros() throws CoerceFailedException {
    expectedException.expect(CoerceFailedException.class);

    coercer.coerceBoth(
        cellNameResolver,
        filesystem,
        basePath,
        UnconfiguredTargetConfiguration.INSTANCE,
        new ConstantHostTargetConfigurationResolver(UnconfiguredTargetConfiguration.INSTANCE),
        ImmutableMap.of(ImmutableList.of(), "foo"));
  }

  @Test
  public void coerceFailsWhenMapKeysAreInt() throws CoerceFailedException {
    expectedException.expect(CoerceFailedException.class);

    coercer.coerceBoth(
        cellNameResolver,
        filesystem,
        basePath,
        UnconfiguredTargetConfiguration.INSTANCE,
        new ConstantHostTargetConfigurationResolver(UnconfiguredTargetConfiguration.INSTANCE),
        ImmutableMap.of(1, "foo"));
  }
}

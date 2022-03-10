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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.facebook.buck.core.cell.TestCellPathResolver;
import com.facebook.buck.core.cell.nameresolver.CellNameResolver;
import com.facebook.buck.core.filesystems.ForwardRelPath;
import com.facebook.buck.core.model.ConstantHostTargetConfigurationResolver;
import com.facebook.buck.core.model.TargetConfiguration;
import com.facebook.buck.core.model.TargetConfigurationResolver;
import com.facebook.buck.core.model.UnconfiguredTargetConfiguration;
import com.facebook.buck.io.filesystem.ProjectFilesystem;
import com.facebook.buck.io.filesystem.impl.FakeProjectFilesystem;
import com.facebook.buck.rules.coercer.TypeCoercer.Traversal;
import com.facebook.buck.rules.macros.Macro;
import com.facebook.buck.rules.macros.MacroContainer;
import com.facebook.buck.rules.macros.StringWithMacros;
import com.facebook.buck.rules.macros.StringWithMacrosUtils;
import com.facebook.buck.rules.macros.UnconfiguredMacro;
import com.facebook.buck.util.types.Either;
import com.google.common.collect.Comparators;
import com.google.common.collect.ComparisonChain;
import com.google.common.collect.ImmutableList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import org.hamcrest.Matchers;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class StringWithMacrosTypeCoercerTest {

  @Rule public ExpectedException thrown = ExpectedException.none();

  private final ProjectFilesystem filesystem = new FakeProjectFilesystem();
  private final CellNameResolver cellNameResolver =
      TestCellPathResolver.get(filesystem).getCellNameResolver();
  private final ForwardRelPath basePath = ForwardRelPath.of("");

  @Test
  public void plainString() throws CoerceFailedException {
    StringWithMacrosTypeCoercer coercer = StringWithMacrosTypeCoercer.builder().build();
    assertThat(
        coercer.coerceBoth(
            cellNameResolver,
            filesystem,
            basePath,
            UnconfiguredTargetConfiguration.INSTANCE,
            new ConstantHostTargetConfigurationResolver(UnconfiguredTargetConfiguration.INSTANCE),
            "test string"),
        Matchers.equalTo(StringWithMacrosUtils.format("test string")));
  }

  @Test
  public void embeddedMacro() throws CoerceFailedException {
    StringWithMacrosTypeCoercer coercer =
        StringWithMacrosTypeCoercer.builder()
            .put("test", TestMacro.class, new TestMacroTypeCoercer())
            .build();
    assertThat(
        coercer.coerceBoth(
            cellNameResolver,
            filesystem,
            basePath,
            UnconfiguredTargetConfiguration.INSTANCE,
            new ConstantHostTargetConfigurationResolver(UnconfiguredTargetConfiguration.INSTANCE),
            "string with $(test arg) macro"),
        Matchers.equalTo(
            StringWithMacrosUtils.format(
                "string with %s macro", new TestMacro(ImmutableList.of("arg")))));
    assertThat(
        coercer.coerceBoth(
            cellNameResolver,
            filesystem,
            basePath,
            UnconfiguredTargetConfiguration.INSTANCE,
            new ConstantHostTargetConfigurationResolver(UnconfiguredTargetConfiguration.INSTANCE),
            "string with $(test arg)"),
        Matchers.equalTo(
            StringWithMacrosUtils.format(
                "string with %s", new TestMacro(ImmutableList.of("arg")))));
    assertThat(
        coercer.coerceBoth(
            cellNameResolver,
            filesystem,
            basePath,
            UnconfiguredTargetConfiguration.INSTANCE,
            new ConstantHostTargetConfigurationResolver(UnconfiguredTargetConfiguration.INSTANCE),
            "$(test arg) macro"),
        Matchers.equalTo(
            StringWithMacrosUtils.format("%s macro", new TestMacro(ImmutableList.of("arg")))));
    assertThat(
        coercer.coerceBoth(
            cellNameResolver,
            filesystem,
            basePath,
            UnconfiguredTargetConfiguration.INSTANCE,
            new ConstantHostTargetConfigurationResolver(UnconfiguredTargetConfiguration.INSTANCE),
            "$(test arg)"),
        Matchers.equalTo(
            StringWithMacrosUtils.format("%s", new TestMacro(ImmutableList.of("arg")))));
  }

  @Test
  public void failedMacroNotFound() throws CoerceFailedException {
    StringWithMacrosTypeCoercer coercer =
        StringWithMacrosTypeCoercer.builder()
            .put("test", TestMacro.class, new TestMacroTypeCoercer())
            .build();
    thrown.expect(CoerceFailedException.class);
    thrown.expectMessage(
        containsString("Macro 'testnotfound' not found when expanding '$(testnotfound arg)'"));
    coercer.coerceBoth(
        cellNameResolver,
        filesystem,
        basePath,
        UnconfiguredTargetConfiguration.INSTANCE,
        new ConstantHostTargetConfigurationResolver(UnconfiguredTargetConfiguration.INSTANCE),
        "string with $(testnotfound arg) macro");
  }

  @Test
  public void failedMacroArgument() throws CoerceFailedException {
    StringWithMacrosTypeCoercer coercer =
        StringWithMacrosTypeCoercer.builder()
            .put("test", TestMacro.class, new TestFailMacroTypeCoercer())
            .build();
    thrown.expect(CoerceFailedException.class);
    thrown.expectMessage(containsString("The macro '$(test arg)' could not be expanded:\nfailed"));
    coercer.coerceBoth(
        cellNameResolver,
        filesystem,
        basePath,
        UnconfiguredTargetConfiguration.INSTANCE,
        new ConstantHostTargetConfigurationResolver(UnconfiguredTargetConfiguration.INSTANCE),
        "string with $(test arg) macro");
  }

  @Test
  public void multipleMacros() throws CoerceFailedException {
    StringWithMacrosTypeCoercer coercer =
        StringWithMacrosTypeCoercer.builder()
            .put("test1", TestMacro.class, new TestMacroTypeCoercer())
            .put("test2", Test2Macro.class, new Test2MacroTypeCoercer())
            .build();
    assertThat(
        coercer.coerceBoth(
            cellNameResolver,
            filesystem,
            basePath,
            UnconfiguredTargetConfiguration.INSTANCE,
            new ConstantHostTargetConfigurationResolver(UnconfiguredTargetConfiguration.INSTANCE),
            "first $(test1 arg1) second $(test2 arg2)"),
        Matchers.equalTo(
            StringWithMacrosUtils.format(
                "first %s second %s",
                new TestMacro(ImmutableList.of("arg1")),
                new Test2Macro(ImmutableList.of("arg2")))));
  }

  @Test
  public void outputToFile() throws CoerceFailedException {
    StringWithMacrosTypeCoercer coercer =
        StringWithMacrosTypeCoercer.builder()
            .put("test", TestMacro.class, new TestMacroTypeCoercer())
            .build();
    assertThat(
        coercer.coerceBoth(
            cellNameResolver,
            filesystem,
            basePath,
            UnconfiguredTargetConfiguration.INSTANCE,
            new ConstantHostTargetConfigurationResolver(UnconfiguredTargetConfiguration.INSTANCE),
            "string with $(@test arg) macro"),
        Matchers.equalTo(
            StringWithMacrosUtils.format(
                "string with %s macro",
                MacroContainer.of(new TestMacro(ImmutableList.of("arg")), true))));
  }

  @Test
  public void escaping() throws CoerceFailedException {
    StringWithMacrosTypeCoercer coercer =
        StringWithMacrosTypeCoercer.builder()
            .put("test", TestMacro.class, new TestMacroTypeCoercer())
            .build();
    assertThat(
        coercer.coerceBoth(
            cellNameResolver,
            filesystem,
            basePath,
            UnconfiguredTargetConfiguration.INSTANCE,
            new ConstantHostTargetConfigurationResolver(UnconfiguredTargetConfiguration.INSTANCE),
            "string with \\$(test arg) macro"),
        Matchers.equalTo(
            StringWithMacros.of(
                ImmutableList.of(
                    Either.ofLeft("string with "),
                    Either.ofLeft("$(test arg)"),
                    Either.ofLeft(" macro")))));
  }

  @Test
  public void testConcatCombinesStrings() throws CoerceFailedException {
    StringWithMacrosTypeCoercer coercer =
        StringWithMacrosTypeCoercer.builder()
            .put("test", TestMacro.class, new TestMacroTypeCoercer())
            .build();

    StringWithMacros string1 =
        coercer.coerceBoth(
            cellNameResolver,
            filesystem,
            basePath,
            UnconfiguredTargetConfiguration.INSTANCE,
            new ConstantHostTargetConfigurationResolver(UnconfiguredTargetConfiguration.INSTANCE),
            "string with $(test arg) macro");
    StringWithMacros string2 =
        coercer.coerceBoth(
            cellNameResolver,
            filesystem,
            basePath,
            UnconfiguredTargetConfiguration.INSTANCE,
            new ConstantHostTargetConfigurationResolver(UnconfiguredTargetConfiguration.INSTANCE),
            " + another string with $(test arg) macro");
    StringWithMacros string3 =
        coercer.coerceBoth(
            cellNameResolver,
            filesystem,
            basePath,
            UnconfiguredTargetConfiguration.INSTANCE,
            new ConstantHostTargetConfigurationResolver(UnconfiguredTargetConfiguration.INSTANCE),
            " + string");
    StringWithMacros string4 =
        coercer.coerceBoth(
            cellNameResolver,
            filesystem,
            basePath,
            UnconfiguredTargetConfiguration.INSTANCE,
            new ConstantHostTargetConfigurationResolver(UnconfiguredTargetConfiguration.INSTANCE),
            " + string + $(test arg)");
    StringWithMacros string5 =
        coercer.coerceBoth(
            cellNameResolver,
            filesystem,
            basePath,
            UnconfiguredTargetConfiguration.INSTANCE,
            new ConstantHostTargetConfigurationResolver(UnconfiguredTargetConfiguration.INSTANCE),
            "$(test arg)");
    StringWithMacros string6 =
        coercer.coerceBoth(
            cellNameResolver,
            filesystem,
            basePath,
            UnconfiguredTargetConfiguration.INSTANCE,
            new ConstantHostTargetConfigurationResolver(UnconfiguredTargetConfiguration.INSTANCE),
            "+ string");
    StringWithMacros string7 =
        coercer.coerceBoth(
            cellNameResolver,
            filesystem,
            basePath,
            UnconfiguredTargetConfiguration.INSTANCE,
            new ConstantHostTargetConfigurationResolver(UnconfiguredTargetConfiguration.INSTANCE),
            "$(test arg)");
    StringWithMacros string8 =
        coercer.coerceBoth(
            cellNameResolver,
            filesystem,
            basePath,
            UnconfiguredTargetConfiguration.INSTANCE,
            new ConstantHostTargetConfigurationResolver(UnconfiguredTargetConfiguration.INSTANCE),
            "+ another string");
    StringWithMacros result =
        coercer.coerceBoth(
            cellNameResolver,
            filesystem,
            basePath,
            UnconfiguredTargetConfiguration.INSTANCE,
            new ConstantHostTargetConfigurationResolver(UnconfiguredTargetConfiguration.INSTANCE),
            "string with $(test arg) macro + another string with $(test arg) macro"
                + " + string + string + $(test arg)$(test arg)+ string$(test arg)+ another string");

    assertEquals(
        result,
        coercer.concat(
            Arrays.asList(string1, string2, string3, string4, string5, string6, string7, string8)));
  }

  @Test
  public void testConcatCombinesTwoStrings() throws CoerceFailedException {
    StringWithMacrosTypeCoercer coercer =
        StringWithMacrosTypeCoercer.builder()
            .put("test", TestMacro.class, new TestMacroTypeCoercer())
            .build();

    StringWithMacros string1 =
        coercer.coerceBoth(
            cellNameResolver,
            filesystem,
            basePath,
            UnconfiguredTargetConfiguration.INSTANCE,
            new ConstantHostTargetConfigurationResolver(UnconfiguredTargetConfiguration.INSTANCE),
            "string1");
    StringWithMacros string2 =
        coercer.coerceBoth(
            cellNameResolver,
            filesystem,
            basePath,
            UnconfiguredTargetConfiguration.INSTANCE,
            new ConstantHostTargetConfigurationResolver(UnconfiguredTargetConfiguration.INSTANCE),
            "string2");
    StringWithMacros result =
        coercer.coerceBoth(
            cellNameResolver,
            filesystem,
            basePath,
            UnconfiguredTargetConfiguration.INSTANCE,
            new ConstantHostTargetConfigurationResolver(UnconfiguredTargetConfiguration.INSTANCE),
            "string1string2");

    assertEquals(result, coercer.concat(Arrays.asList(string1, string2)));
  }

  @Test
  public void testConcatOfOneStringReturnsTheSameString() throws CoerceFailedException {
    StringWithMacrosTypeCoercer coercer =
        StringWithMacrosTypeCoercer.builder()
            .put("test", TestMacro.class, new TestMacroTypeCoercer())
            .build();

    StringWithMacros string1 =
        coercer.coerceBoth(
            cellNameResolver,
            filesystem,
            basePath,
            UnconfiguredTargetConfiguration.INSTANCE,
            new ConstantHostTargetConfigurationResolver(UnconfiguredTargetConfiguration.INSTANCE),
            "string1");
    StringWithMacros result =
        coercer.coerceBoth(
            cellNameResolver,
            filesystem,
            basePath,
            UnconfiguredTargetConfiguration.INSTANCE,
            new ConstantHostTargetConfigurationResolver(UnconfiguredTargetConfiguration.INSTANCE),
            "string1");

    assertEquals(result, coercer.concat(Collections.singleton(string1)));
  }

  @Test
  public void testConcatOfEmptyStringReturnsEmptyString() {
    StringWithMacrosTypeCoercer coercer = StringWithMacrosTypeCoercer.builder().build();

    assertTrue(
        coercer
            .concat(Collections.singleton(StringWithMacros.ofConstantString("")))
            .getParts()
            .isEmpty());
  }

  @Test
  public void testConcatCombinesStringInTheMiddle() throws CoerceFailedException {
    StringWithMacrosTypeCoercer coercer =
        StringWithMacrosTypeCoercer.builder()
            .put("test", TestMacro.class, new TestMacroTypeCoercer())
            .build();

    StringWithMacros string1 =
        coercer.coerceBoth(
            cellNameResolver,
            filesystem,
            basePath,
            UnconfiguredTargetConfiguration.INSTANCE,
            new ConstantHostTargetConfigurationResolver(UnconfiguredTargetConfiguration.INSTANCE),
            "$(test arg) string1");
    StringWithMacros string2 =
        coercer.coerceBoth(
            cellNameResolver,
            filesystem,
            basePath,
            UnconfiguredTargetConfiguration.INSTANCE,
            new ConstantHostTargetConfigurationResolver(UnconfiguredTargetConfiguration.INSTANCE),
            "string2");
    StringWithMacros string3 =
        coercer.coerceBoth(
            cellNameResolver,
            filesystem,
            basePath,
            UnconfiguredTargetConfiguration.INSTANCE,
            new ConstantHostTargetConfigurationResolver(UnconfiguredTargetConfiguration.INSTANCE),
            "$(test arg)");
    StringWithMacros result =
        coercer.coerceBoth(
            cellNameResolver,
            filesystem,
            basePath,
            UnconfiguredTargetConfiguration.INSTANCE,
            new ConstantHostTargetConfigurationResolver(UnconfiguredTargetConfiguration.INSTANCE),
            "$(test arg) string1string2$(test arg)");

    assertEquals(result, coercer.concat(Arrays.asList(string1, string2, string3)));
  }

  static class TestMacro implements Macro, UnconfiguredMacro {

    private final ImmutableList<String> args;

    TestMacro(ImmutableList<String> args) {
      this.args = args;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }

      TestMacro testMacro = (TestMacro) o;

      return args.equals(testMacro.args);
    }

    @Override
    public int compareTo(Macro o) {
      int result = Macro.super.compareTo(o);
      if (result != 0) {
        return result;
      }
      TestMacro other = (TestMacro) o;
      return ComparisonChain.start()
          .compare(args, other.args, Comparators.lexicographical(Comparator.<String>naturalOrder()))
          .result();
    }

    @Override
    public Class<? extends UnconfiguredMacro> getUnconfiguredMacroClass() {
      return TestMacro.class;
    }

    @Override
    public Class<? extends Macro> getMacroClass() {
      return TestMacro.class;
    }

    @Override
    public int hashCode() {
      return args.hashCode();
    }

    @Override
    public Macro configure(
        TargetConfiguration targetConfiguration,
        TargetConfigurationResolver hostConfigurationResolver) {
      return this;
    }
  }

  static class Test2Macro implements Macro, UnconfiguredMacro {
    private final ImmutableList<String> args;

    Test2Macro(ImmutableList<String> args) {
      this.args = args;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }

      Test2Macro testMacro = (Test2Macro) o;

      return args.equals(testMacro.args);
    }

    @Override
    public int compareTo(Macro o) {
      int result = Macro.super.compareTo(o);
      if (result != 0) {
        return result;
      }
      Test2Macro other = (Test2Macro) o;
      return ComparisonChain.start()
          .compare(args, other.args, Comparators.lexicographical(Comparator.<String>naturalOrder()))
          .result();
    }

    @Override
    public Class<? extends UnconfiguredMacro> getUnconfiguredMacroClass() {
      return TestMacro.class;
    }

    @Override
    public Class<? extends Macro> getMacroClass() {
      return TestMacro.class;
    }

    @Override
    public int hashCode() {
      return args.hashCode();
    }

    @Override
    public Macro configure(
        TargetConfiguration targetConfiguration,
        TargetConfigurationResolver hostConfigurationResolver) {
      return this;
    }
  }

  static class TestMacroTypeCoercer implements MacroTypeCoercer<TestMacro, TestMacro> {

    @Override
    public boolean hasElementClass(Class<?>[] types) {
      return false;
    }

    @Override
    public Class<TestMacro> getUnconfiguredOutputClass() {
      return TestMacro.class;
    }

    @Override
    public Class<TestMacro> getOutputClass() {
      return TestMacro.class;
    }

    @Override
    public void traverseUnconfigured(
        CellNameResolver cellRoots, TestMacro macro, Traversal traversal) {}

    @Override
    public void traverse(CellNameResolver cellRoots, TestMacro macro, Traversal traversal) {}

    @Override
    public TestMacro coerceToUnconfigured(
        CellNameResolver cellNameResolver,
        ProjectFilesystem filesystem,
        ForwardRelPath pathRelativeToProjectRoot,
        ImmutableList<String> args) {
      return new TestMacro(args);
    }
  }

  static class Test2MacroTypeCoercer implements MacroTypeCoercer<Test2Macro, Test2Macro> {

    @Override
    public boolean hasElementClass(Class<?>[] types) {
      return false;
    }

    @Override
    public Class<Test2Macro> getUnconfiguredOutputClass() {
      return Test2Macro.class;
    }

    @Override
    public Class<Test2Macro> getOutputClass() {
      return Test2Macro.class;
    }

    @Override
    public void traverseUnconfigured(
        CellNameResolver cellRoots, Test2Macro macro, Traversal traversal) {}

    @Override
    public void traverse(CellNameResolver cellRoots, Test2Macro macro, Traversal traversal) {}

    @Override
    public Test2Macro coerceToUnconfigured(
        CellNameResolver cellNameResolver,
        ProjectFilesystem filesystem,
        ForwardRelPath pathRelativeToProjectRoot,
        ImmutableList<String> args) {
      return new Test2Macro(args);
    }
  }

  private static class TestFailMacroTypeCoercer implements MacroTypeCoercer<TestMacro, TestMacro> {

    @Override
    public boolean hasElementClass(Class<?>[] types) {
      return false;
    }

    @Override
    public Class<TestMacro> getUnconfiguredOutputClass() {
      return TestMacro.class;
    }

    @Override
    public Class<TestMacro> getOutputClass() {
      return TestMacro.class;
    }

    @Override
    public void traverseUnconfigured(
        CellNameResolver cellRoots, TestMacro macro, Traversal traversal) {}

    @Override
    public void traverse(CellNameResolver cellRoots, TestMacro macro, Traversal traversal) {}

    @Override
    public TestMacro coerceToUnconfigured(
        CellNameResolver cellNameResolver,
        ProjectFilesystem filesystem,
        ForwardRelPath pathRelativeToProjectRoot,
        ImmutableList<String> args)
        throws CoerceFailedException {
      throw new CoerceFailedException("failed");
    }
  }
}

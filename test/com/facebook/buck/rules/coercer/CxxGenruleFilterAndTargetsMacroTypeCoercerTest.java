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

import static com.facebook.buck.core.cell.TestCellBuilder.createCellRoots;
import static org.hamcrest.MatcherAssert.assertThat;

import com.facebook.buck.core.filesystems.ForwardRelPath;
import com.facebook.buck.core.model.BuildTargetFactory;
import com.facebook.buck.core.model.ConstantHostTargetConfigurationResolver;
import com.facebook.buck.core.model.UnconfiguredTargetConfiguration;
import com.facebook.buck.core.parser.buildtargetparser.ParsingUnconfiguredBuildTargetViewFactory;
import com.facebook.buck.io.filesystem.ProjectFilesystem;
import com.facebook.buck.io.filesystem.impl.FakeProjectFilesystem;
import com.facebook.buck.rules.macros.CppFlagsMacro;
import com.facebook.buck.rules.macros.LdflagsStaticMacro;
import com.facebook.buck.rules.macros.UnconfiguredCppFlagsMacro;
import com.facebook.buck.rules.macros.UnconfiguredLdflagsStaticMacro;
import com.google.common.collect.ImmutableList;
import java.util.Optional;
import java.util.regex.Pattern;
import org.hamcrest.Matchers;
import org.junit.Test;

public class CxxGenruleFilterAndTargetsMacroTypeCoercerTest {

  @Test
  public void testNoPattern() throws CoerceFailedException {
    ForwardRelPath basePath = ForwardRelPath.of("java/com/facebook/buck/example");
    ProjectFilesystem filesystem = new FakeProjectFilesystem();
    CxxGenruleFilterAndTargetsMacroTypeCoercer<UnconfiguredCppFlagsMacro, CppFlagsMacro> coercer =
        new CxxGenruleFilterAndTargetsMacroTypeCoercer<>(
            Optional.empty(),
            new ListTypeCoercer<>(
                new BuildTargetTypeCoercer(
                    new UnconfiguredBuildTargetTypeCoercer(
                        new ParsingUnconfiguredBuildTargetViewFactory()))),
            UnconfiguredCppFlagsMacro.class,
            CppFlagsMacro.class,
            UnconfiguredCppFlagsMacro::of);
    CppFlagsMacro result =
        coercer.coerceBoth(
            createCellRoots(filesystem).getCellNameResolver(),
            filesystem,
            basePath,
            UnconfiguredTargetConfiguration.INSTANCE,
            new ConstantHostTargetConfigurationResolver(UnconfiguredTargetConfiguration.INSTANCE),
            ImmutableList.of("//:a"));
    assertThat(
        result,
        Matchers.equalTo(
            CppFlagsMacro.of(
                Optional.empty(), ImmutableList.of(BuildTargetFactory.newInstance("//:a")))));
  }

  @Test
  public void testPattern() throws CoerceFailedException {
    ForwardRelPath basePath = ForwardRelPath.of("java/com/facebook/buck/example");
    ProjectFilesystem filesystem = new FakeProjectFilesystem();
    CxxGenruleFilterAndTargetsMacroTypeCoercer<UnconfiguredLdflagsStaticMacro, LdflagsStaticMacro>
        coercer =
            new CxxGenruleFilterAndTargetsMacroTypeCoercer<>(
                Optional.of(new PatternTypeCoercer()),
                new ListTypeCoercer<>(
                    new BuildTargetTypeCoercer(
                        new UnconfiguredBuildTargetTypeCoercer(
                            new ParsingUnconfiguredBuildTargetViewFactory()))),
                UnconfiguredLdflagsStaticMacro.class,
                LdflagsStaticMacro.class,
                UnconfiguredLdflagsStaticMacro::of);
    LdflagsStaticMacro result =
        coercer.coerceBoth(
            createCellRoots(filesystem).getCellNameResolver(),
            filesystem,
            basePath,
            UnconfiguredTargetConfiguration.INSTANCE,
            new ConstantHostTargetConfigurationResolver(UnconfiguredTargetConfiguration.INSTANCE),
            ImmutableList.of("hello", "//:a"));
    assertThat(result.getFilter().map(Pattern::pattern), Matchers.equalTo(Optional.of("hello")));
    assertThat(
        result.getTargets(),
        Matchers.equalTo(ImmutableList.of(BuildTargetFactory.newInstance("//:a"))));
  }
}

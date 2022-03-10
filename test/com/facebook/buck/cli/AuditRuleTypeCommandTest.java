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

package com.facebook.buck.cli;

import static com.facebook.buck.util.MoreStringsForTests.equalToIgnoringPlatformNewlines;
import static org.hamcrest.MatcherAssert.assertThat;

import com.facebook.buck.core.description.arg.BuildRuleArg;
import com.facebook.buck.core.model.BuildTarget;
import com.facebook.buck.core.rules.BuildRule;
import com.facebook.buck.core.rules.BuildRuleCreationContextWithTargetGraph;
import com.facebook.buck.core.rules.BuildRuleParams;
import com.facebook.buck.core.rules.DescriptionWithTargetGraph;
import com.facebook.buck.core.rules.knowntypes.KnownNativeRuleTypes;
import com.facebook.buck.core.util.immutables.RuleArg;
import com.facebook.buck.rules.coercer.DefaultTypeCoercerFactory;
import com.facebook.buck.testutil.TestConsole;
import com.google.common.collect.ImmutableList;
import java.util.Optional;
import org.junit.Test;

public class AuditRuleTypeCommandTest {
  static class BuildRuleDescription implements DescriptionWithTargetGraph<BuildRuleDescriptionArg> {

    @Override
    public Class<BuildRuleDescriptionArg> getConstructorArgType() {
      return BuildRuleDescriptionArg.class;
    }

    @Override
    public BuildRule createBuildRule(
        BuildRuleCreationContextWithTargetGraph context,
        BuildTarget buildTarget,
        BuildRuleParams params,
        BuildRuleDescriptionArg args) {
      return null;
    }

    @RuleArg
    interface AbstractBuildRuleDescriptionArg extends BuildRuleArg {
      Optional<String> getOptionalString();
    }
  }

  private static final DescriptionWithTargetGraph<?> DESCRIPTION = new BuildRuleDescription();

  @Test
  public void buildRuleTypePrintedAsAPythonFunction() {
    TestConsole console = new TestConsole();

    KnownNativeRuleTypes knownTypes =
        KnownNativeRuleTypes.of(
            ImmutableList.of(DESCRIPTION), ImmutableList.of(), ImmutableList.of());
    AuditRuleTypeCommand.printPythonFunction(
        console, knownTypes.getDescriptorByName("build_rule"), new DefaultTypeCoercerFactory());

    assertThat(
        console.getTextWrittenToStdOut(),
        equalToIgnoringPlatformNewlines(
            "def build_rule (\n"
                + "    name,\n"
                + "    compatible_with = None,\n"
                + "    contacts = None,\n"
                + "    default_host_platform = None,\n"
                + "    default_target_platform = None,\n"
                + "    labels = None,\n"
                + "    licenses = None,\n"
                + "    optional_string = None,\n"
                + "):\n"
                + "    ...\n"));
  }
}

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

package com.facebook.buck.cli.endtoend;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.facebook.buck.testutil.ProcessResult;
import com.facebook.buck.testutil.endtoend.EndToEndEnvironment;
import com.facebook.buck.testutil.endtoend.EndToEndRunner;
import com.facebook.buck.testutil.endtoend.EndToEndTestDescriptor;
import com.facebook.buck.testutil.endtoend.EndToEndWorkspace;
import com.facebook.buck.testutil.endtoend.Environment;
import com.facebook.buck.testutil.endtoend.ToggleState;
import com.facebook.buck.util.ExitCode;
import com.facebook.buck.util.json.ObjectMappers;
import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Streams;
import java.nio.file.Path;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(EndToEndRunner.class)
public class RunEndToEndTest {

  @Environment
  public static EndToEndEnvironment getBaseEnvironment() {
    return new EndToEndEnvironment()
        .withCommand("run")
        .withBuckdToggled(ToggleState.ON)
        .addTemplates("cli");
  }

  @Test
  public void shouldRunBuiltBinaries(EndToEndTestDescriptor test, EndToEndWorkspace workspace)
      throws Throwable {
    ProcessResult result =
        workspace.runBuckCommand(
            test.getBuckdEnabled(),
            ImmutableMap.copyOf(test.getVariableMap()),
            test.getTemplateSet(),
            "run",
            "-c",
            "user.exit_code=0",
            "//run/simple_bin:main");
    result.assertSuccess();

    result =
        workspace.runBuckCommand(
            test.getBuckdEnabled(),
            ImmutableMap.copyOf(test.getVariableMap()),
            test.getTemplateSet(),
            "run",
            "-c",
            "user.exit_code=" + ExitCode.TEST_NOTHING.getCode(),
            "//run/simple_bin:main");
    result.assertExitCode(ExitCode.TEST_NOTHING);
  }

  @Test
  public void shouldUseBuildErrorCodeOnBuildFailure(
      EndToEndTestDescriptor test, EndToEndWorkspace workspace) throws Throwable {
    ProcessResult result =
        workspace.runBuckCommand(
            test.getBuckdEnabled(),
            ImmutableMap.copyOf(test.getVariableMap()),
            test.getTemplateSet(),
            "run",
            "//run/simple_bin:broken");
    result.assertExitCode(ExitCode.BUILD_ERROR);
  }

  @Test
  public void shouldPrintRunDetailsIfRequested(
      EndToEndTestDescriptor test, EndToEndWorkspace workspace) throws Exception {
    String expectedCwd = workspace.getDestPath().toAbsolutePath().toString();

    Path outputPath =
        workspace
            .getPath(
                workspace
                    .buildAndReturnOutputs(
                        test.getBuckdEnabled(),
                        ImmutableMap.copyOf(test.getVariableMap()),
                        test.getTemplateSet(),
                        "//run/simple_bin:main")
                    .get("//run/simple_bin:main"))
            .toAbsolutePath();

    ProcessResult result =
        workspace.runBuckCommand(
            test.getBuckdEnabled(),
            ImmutableMap.copyOf(test.getVariableMap()),
            test.getTemplateSet(),
            "run",
            "--print-command",
            "//run/simple_bin:main");
    ProcessResult resultWithArgs =
        workspace.runBuckCommand(
            test.getBuckdEnabled(),
            ImmutableMap.copyOf(test.getVariableMap()),
            test.getTemplateSet(),
            "run",
            "--print-command",
            "//run/simple_bin:main",
            "--",
            "arg1",
            "arg2");

    JsonNode js = ObjectMappers.READER.readTree(result.assertSuccess().getStdout());
    JsonNode jsWithArgs = ObjectMappers.READER.readTree(resultWithArgs.assertSuccess().getStdout());

    ImmutableList<String> args1 =
        Streams.stream(js.get("args"))
            .map(JsonNode::asText)
            .collect(ImmutableList.toImmutableList());
    ImmutableList<String> args2 =
        Streams.stream(jsWithArgs.get("args"))
            .map(JsonNode::asText)
            .collect(ImmutableList.toImmutableList());

    assertEquals(ImmutableList.of(outputPath.toString()), args1);
    assertTrue(js.get("env").has("BUCK_BUILD_ID"));
    assertEquals(expectedCwd, js.get("cwd").asText());

    assertEquals(ImmutableList.of(outputPath.toString(), "arg1", "arg2"), args2);
    assertTrue(jsWithArgs.get("env").has("BUCK_BUILD_ID"));
    assertEquals(expectedCwd, jsWithArgs.get("cwd").asText());
  }
}

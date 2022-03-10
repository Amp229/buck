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

import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertEquals;

import com.facebook.buck.testutil.TemporaryPaths;
import com.facebook.buck.testutil.endtoend.EndToEndEnvironment;
import com.facebook.buck.testutil.endtoend.EndToEndRunner;
import com.facebook.buck.testutil.endtoend.EndToEndTestDescriptor;
import com.facebook.buck.testutil.endtoend.EndToEndWorkspace;
import com.facebook.buck.testutil.endtoend.Environment;
import com.facebook.buck.util.environment.Platform;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.Assume;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(EndToEndRunner.class)
public class CommandsEndToEndTest {

  @Rule public TemporaryPaths tmp = new TemporaryPaths();

  @Environment
  public static EndToEndEnvironment getBaseEnvironment() {
    return new EndToEndEnvironment().withCommand("run").addTemplates("cli");
  }

  @Test
  public void shouldNotHaveProblemsParsingFlagsPassedByWrapperScript(
      EndToEndTestDescriptor test, EndToEndWorkspace workspace) throws Exception {
    // Get a healthy mix of AbstractCommand and AbstractContainerCommand invocations.
    // This mostly makes sure that the implicit args passed from the buck python
    // wrapper are understood by the java side of things.
    ImmutableList<String[]> commands =
        ImmutableList.of(
            new String[] {"--help"},
            new String[] {"audit", "--help"},
            new String[] {"audit", "config", "--help"},
            new String[] {"build", "--help"},
            new String[] {"cache", "--help"},
            new String[] {"perf", "--help"},
            new String[] {"perf", "rk", "--help"},
            new String[] {"run", "--help"},
            new String[] {"test", "--help"},
            new String[] {"kill"},
            new String[] {"--version"});

    for (String[] command : commands) {
      workspace
          .runBuckCommand(
              true, ImmutableMap.copyOf(test.getVariableMap()), test.getTemplateSet(), command)
          .assertSuccess();
    }

    for (String[] command : commands) {
      workspace
          .runBuckCommand(
              false, ImmutableMap.copyOf(test.getVariableMap()), test.getTemplateSet(), command)
          .assertSuccess();
    }
  }

  @Test
  public void shouldUseTheInterpreterSpecifiedInTheEnvironment(
      EndToEndTestDescriptor test, EndToEndWorkspace workspace) throws Throwable {
    Assume.assumeThat(Platform.detect(), not(Platform.WINDOWS));

    for (String s : test.getTemplateSet()) {
      workspace.addPremadeTemplate(s);
    }
    workspace.setup();

    Path scriptPath = workspace.getPath(Paths.get("test.py"));
    Files.write(scriptPath, ImmutableList.of("echo --config", "echo foo.bar=baz"));

    String stdout =
        workspace
            .runBuckCommand(
                ImmutableMap.of("BUCK_WRAPPER_PYTHON_BIN", "/bin/bash"),
                "audit",
                "config",
                "--tab",
                "@" + scriptPath.toAbsolutePath().toString(),
                "foo.bar")
            .assertSuccess()
            .getStdout()
            .trim();

    assertEquals("foo.bar\tbaz", stdout);
  }
}

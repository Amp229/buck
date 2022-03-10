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

package com.facebook.buck.infer;

import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import com.facebook.buck.infer.toolchain.InferPlatformFactory;
import com.facebook.buck.io.ExecutableFinder;
import com.facebook.buck.testutil.ProcessResult;
import com.facebook.buck.testutil.TemporaryPaths;
import com.facebook.buck.testutil.integration.ProjectWorkspace;
import com.facebook.buck.testutil.integration.TestDataHelper;
import com.facebook.buck.util.environment.Platform;
import com.facebook.buck.util.json.ObjectMappers;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.Assume;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;

public class InferJavaIntegrationTest {
  @Rule public TemporaryPaths tmp = new TemporaryPaths();

  public static void assumeInferIsConfigured(ProjectWorkspace workspace) throws IOException {
    Assume.assumeThat(Platform.detect(), not(Platform.WINDOWS));

    Optional<UnresolvedInferPlatform> unresolvedPlatform =
        InferPlatformFactory.getBasedOnConfigAndPath(
            InferConfig.of(workspace.asCell().getBuckConfig()), new ExecutableFinder());

    assumeTrue("infer is not available", unresolvedPlatform.isPresent());
  }

  @Test
  public void captureJavaLibrarySmokeTest() throws IOException {
    Assume.assumeThat(Platform.detect(), not(Platform.WINDOWS));

    ProjectWorkspace workspace =
        TestDataHelper.createProjectWorkspaceForScenario(this, "several_libraries", tmp);
    workspace.setUp();
    assumeInferIsConfigured(workspace);

    Path output = workspace.buildAndReturnOutput("//:java-smoke-test#infer-java-capture");
    assertTrue(Files.isRegularFile(output.resolve("results.db")));
  }

  @Test
  public void nullsafeJavaLibrarySmokeTest() throws IOException {
    Assume.assumeThat(Platform.detect(), not(Platform.WINDOWS));

    ProjectWorkspace workspace =
        TestDataHelper.createProjectWorkspaceForScenario(this, "several_libraries", tmp);
    workspace.setUp();
    assumeInferIsConfigured(workspace);

    Path output = workspace.buildAndReturnOutput("//:java-smoke-test#nullsafe");
    JsonNode issues = ObjectMappers.READER.readTree(workspace.getFileContents(output));
    assertEquals("ERADICATE_RETURN_NOT_NULLABLE", issues.path(0).path("bug_type").asText());
  }

  @Test
  public void nullsafeEmptySourcesTest() throws IOException {
    Assume.assumeThat(Platform.detect(), not(Platform.WINDOWS));

    ProjectWorkspace workspace =
        TestDataHelper.createProjectWorkspaceForScenario(this, "several_libraries", tmp);
    workspace.setUp();
    assumeInferIsConfigured(workspace);

    workspace.runBuckBuild("//:empty-srcs-test#nullsafe").assertSuccess();
  }

  @Test
  public void nullsafeProvidedDepTest() throws IOException {
    Assume.assumeThat(Platform.detect(), not(Platform.WINDOWS));

    ProjectWorkspace workspace =
        TestDataHelper.createProjectWorkspaceForScenario(this, "several_libraries", tmp);
    workspace.setUp();
    assumeInferIsConfigured(workspace);

    Path output = workspace.buildAndReturnOutput("//:java-provided-dep-test#nullsafe");
    JsonNode issues = ObjectMappers.READER.readTree(workspace.getFileContents(output));
    assertEquals(0, issues.size());
  }

  @Test
  public void inferExportedProvidedDepTest() throws IOException {
    Assume.assumeThat(Platform.detect(), not(Platform.WINDOWS));

    ProjectWorkspace workspace =
        TestDataHelper.createProjectWorkspaceForScenario(this, "several_libraries", tmp);
    workspace.setUp();
    assumeInferIsConfigured(workspace);

    Path output = workspace.buildAndReturnOutput("//:java-exported-provided-dep-test#nullsafe");
    JsonNode issues = ObjectMappers.READER.readTree(workspace.getFileContents(output));
    assertEquals(0, issues.size());
  }

  @Test
  public void inferFromDistTest() throws IOException {
    Assume.assumeThat(Platform.detect(), not(Platform.WINDOWS));

    ProjectWorkspace workspace =
        TestDataHelper.createProjectWorkspaceForScenario(this, "dist", tmp);
    workspace.setUp();
    assumeInferIsConfigured(workspace);

    // Although here we use #nullsafe flavor this testcase is not nullsafe specific as it tests
    // how InferJava handles infer.dist config option (note "dist" workspace scenario above).
    Path output = workspace.buildAndReturnOutput("//:l1#nullsafe");
    String content = workspace.getFileContents(output);
    assertEquals("fake infer results\n", content);
  }

  @Test
  @Ignore(
      "TODO: `buck test` fails with unknown android-platform-target toolchain (but works in Idea)")
  public void nullsafeAndroidLibrarySmokeTest() throws IOException {
    ProjectWorkspace workspace =
        TestDataHelper.createProjectWorkspaceForScenario(this, "several_libraries", tmp);
    workspace.setUp();

    ProcessResult result = workspace.runBuckBuild("//:android-smoke-test#nullsafe");
    result.assertSuccess();
  }
}

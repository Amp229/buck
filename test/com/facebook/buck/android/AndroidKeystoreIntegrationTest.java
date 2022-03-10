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

package com.facebook.buck.android;

import static org.junit.Assert.assertEquals;

import com.facebook.buck.testutil.TemporaryPaths;
import com.facebook.buck.testutil.integration.ProjectWorkspace;
import com.facebook.buck.testutil.integration.TestDataHelper;
import java.io.IOException;
import java.nio.file.Path;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public class AndroidKeystoreIntegrationTest {

  @Rule public TemporaryPaths tmpFolder = new TemporaryPaths();
  private ProjectWorkspace workspace;

  @Before
  public void setUp() throws IOException {
    workspace =
        TestDataHelper.createProjectWorkspaceForScenario(
            new AndroidKeystoreIntegrationTest(), "android_project", tmpFolder);
    workspace.setUp();
  }

  @Test
  public void testKeystoreOutputUsingNamedOutput() throws IOException {
    Path output = workspace.buildAndReturnOutput("//keystores:copy_keystore");
    String copyOutput = workspace.getFileContents(output);
    String source = workspace.getFileContents("keystores/debug.keystore");
    assertEquals(source, copyOutput);
  }

  @Test
  public void testKeystorePropertiesOutputUsingNamedOutput() throws IOException {
    Path output = workspace.buildAndReturnOutput("//keystores:copy_keystore_properties");
    String copyOutput = workspace.getFileContents(output);
    String source = workspace.getFileContents("keystores/debug.keystore.properties");
    assertEquals(source, copyOutput);
  }
}

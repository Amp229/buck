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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.iterableWithSize;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.facebook.buck.jvm.java.version.utils.JavaVersionUtils;
import com.facebook.buck.testutil.TemporaryPaths;
import com.facebook.buck.testutil.integration.ProjectWorkspace;
import com.facebook.buck.testutil.integration.TestDataHelper;
import com.facebook.buck.util.json.ObjectMappers;
import com.fasterxml.jackson.core.type.TypeReference;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSortedSet;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.hamcrest.Matchers;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public class RobolectricTestRuleIntegrationTest {

  @Rule public TemporaryPaths tmpFolder = new TemporaryPaths();

  private ProjectWorkspace workspace;

  @Before
  public void setUp() {
    // TODO(T47912516): Remove once we can upgrade our Robolectric libraries and run this on Java
    //                  11.
    Assume.assumeThat(JavaVersionUtils.getMajorVersion(), Matchers.lessThanOrEqualTo(8));
  }

  @Test
  public void testRobolectricTestAddsRequiredPath() throws IOException {
    workspace =
        TestDataHelper.createProjectWorkspaceForScenario(this, "android_project", tmpFolder);
    workspace.setUp();
    AssumeAndroidPlatform.get(workspace).assumeSdkIsAvailable();
    workspace.addBuckConfigLocalOption("test", "use_relative_paths_in_classpath_file", "true");
    workspace.addBuckConfigLocalOption("test", "external_runner", "echo");
    workspace.addBuckConfigLocalOption("test", "java_for_tests_version", "11");
    workspace.runBuckTest("//java/com/sample/lib:test").assertSuccess();

    Path specOutput =
        workspace.getPath(
            workspace.getBuckPaths().getScratchDir().resolve("external_runner_specs.json"));
    ImmutableList<ImmutableMap<String, Object>> specs =
        ObjectMappers.readValue(
            specOutput, new TypeReference<ImmutableList<ImmutableMap<String, Object>>>() {});
    assertThat(specs, iterableWithSize(1));
    ImmutableMap<String, Object> spec = specs.get(0);
    assertThat(spec, hasKey("required_paths"));
    //noinspection unchecked
    ImmutableSortedSet<String> requiredPaths =
        ImmutableSortedSet.<String>naturalOrder()
            .addAll((Iterable<String>) spec.get("required_paths"))
            .build();

    ImmutableList<String> robolectricBinaryResourcesApkPath =
        requiredPaths.stream()
            .filter(path -> path.contains("merged.assets.ap_"))
            .collect(ImmutableList.toImmutableList());
    assertEquals(1, robolectricBinaryResourcesApkPath.size());

    ImmutableList<String> robolectricManifestPath =
        requiredPaths.stream()
            .filter(path -> path.contains("TestAndroidManifest.xml"))
            .collect(ImmutableList.toImmutableList());
    assertEquals(1, robolectricManifestPath.size());

    ImmutableList<String> robolectricRuntimeDepsDirEntries =
        requiredPaths.stream()
            .filter(path -> path.contains("robolectric_dir"))
            .collect(ImmutableList.toImmutableList());
    assertTrue(robolectricRuntimeDepsDirEntries.size() > 0);

    ImmutableList<String> androidJarEntries =
        requiredPaths.stream()
            .filter(path -> path.contains("android.jar"))
            .collect(ImmutableList.toImmutableList());
    assertFalse(androidJarEntries.isEmpty());

    ImmutableList<String> robolectricResourcesPaths =
        requiredPaths.stream()
            .filter(path -> path.contains("some_file.txt"))
            .collect(ImmutableList.toImmutableList());
    assertEquals(1, robolectricResourcesPaths.size());

    // The classpath arg file should use relative paths except for the bootclasspath, which uses an
    // absolute path.
    ImmutableList<String> classpathArgfile =
        requiredPaths.stream()
            .filter(path -> path.contains("classpath-argfile"))
            .collect(ImmutableList.toImmutableList());
    assertEquals(1, classpathArgfile.size());
    Path classpathArgFilePath = Paths.get(classpathArgfile.get(0));
    for (String line : workspace.getProjectFileSystem().readLines(classpathArgFilePath)) {
      // Last line ends with a quote
      line = line.endsWith("\"") ? line.substring(0, line.length() - 1) : line;
      if (line.equals("-classpath") || line.contains("ant-out")) {
        continue;
      }
      assertSame(
          "Only paths containing android_sdk should be absolute, path is: " + line,
          Paths.get(line).isAbsolute(),
          line.contains("android") && line.contains("sdk"));
    }
  }

  @Test
  public void testNoBootClasspathInRequiredPath() throws IOException {
    workspace =
        TestDataHelper.createProjectWorkspaceForScenario(this, "android_project", tmpFolder);
    workspace.setUp();
    AssumeAndroidPlatform.get(workspace).assumeSdkIsAvailable();
    workspace.addBuckConfigLocalOption("test", "external_runner", "echo");
    workspace.addBuckConfigLocalOption("test", "include_boot_classpath_in_required_paths", "false");
    workspace.runBuckTest("//java/com/sample/lib:test").assertSuccess();

    Path specOutput =
        workspace.getPath(
            workspace.getBuckPaths().getScratchDir().resolve("external_runner_specs.json"));
    ImmutableList<ImmutableMap<String, Object>> specs =
        ObjectMappers.readValue(
            specOutput, new TypeReference<ImmutableList<ImmutableMap<String, Object>>>() {});
    assertThat(specs, iterableWithSize(1));
    ImmutableMap<String, Object> spec = specs.get(0);
    assertThat(spec, hasKey("required_paths"));
    //noinspection unchecked
    ImmutableSortedSet<String> requiredPaths =
        ImmutableSortedSet.<String>naturalOrder()
            .addAll((Iterable<String>) spec.get("required_paths"))
            .build();

    ImmutableList<String> androidJarEntries =
        requiredPaths.stream()
            .filter(path -> path.contains("android.jar"))
            .collect(ImmutableList.toImmutableList());
    assertTrue(androidJarEntries.isEmpty());
  }

  @Test
  public void testRobolectricTestWithExternalRunnerWithPassingDirectoriesInArgument()
      throws IOException {
    workspace =
        TestDataHelper.createProjectWorkspaceForScenario(this, "android_project", tmpFolder);
    workspace.setUp();
    AssumeAndroidPlatform.get(workspace).assumeSdkIsAvailable();
    workspace.runBuckTest("//java/com/sample/lib:test").assertSuccess();
  }

  @Test
  public void testRobolectricTestWithExternalRunnerWithPassingDirectoriesInFile()
      throws IOException {
    workspace =
        TestDataHelper.createProjectWorkspaceForScenario(this, "android_project", tmpFolder);
    workspace.setUp();
    AssumeAndroidPlatform.get(workspace).assumeSdkIsAvailable();
    workspace.runBuckTest("//java/com/sample/lib:test").assertSuccess();
  }

  @Test
  public void testRobolectricTestWithExternalRunnerWithRobolectricRuntimeDependencyArgument()
      throws IOException {
    workspace =
        TestDataHelper.createProjectWorkspaceForScenario(this, "android_project", tmpFolder);
    workspace.setUp();
    AssumeAndroidPlatform.get(workspace).assumeSdkIsAvailable();

    workspace.runBuckTest("//java/com/sample/lib:test_robolectric_runtime_dep").assertSuccess();
  }

  @Test
  public void robolectricTestBuildsWithBinaryResources() throws IOException {
    workspace =
        TestDataHelper.createProjectWorkspaceForScenario(this, "android_project", tmpFolder);
    workspace.setUp();
    AssumeAndroidPlatform.get(workspace).assumeSdkIsAvailable();
    workspace.runBuckTest("//java/com/sample/lib:test").assertSuccess();
  }

  @Test
  public void testRobolectricTestSpecifiesCorrectClasses() throws IOException {
    workspace =
        TestDataHelper.createProjectWorkspaceForScenario(this, "android_project", tmpFolder);
    workspace.setUp();
    AssumeAndroidPlatform.get(workspace).assumeSdkIsAvailable();
    workspace.addBuckConfigLocalOption("test", "external_runner", "echo");
    workspace.addBuckConfigLocalOption("test", "java_for_tests_version", "11");
    workspace.runBuckTest("//java/com/sample/lib:test").assertSuccess();

    Path specOutput =
        workspace.getPath(
            workspace.getBuckPaths().getScratchDir().resolve("external_runner_specs.json"));
    ImmutableList<ImmutableMap<String, Object>> specs =
        ObjectMappers.readValue(
            specOutput, new TypeReference<ImmutableList<ImmutableMap<String, Object>>>() {});
    assertThat(specs, iterableWithSize(1));
    ImmutableMap<String, Object> spec = specs.get(0);
    assertThat(spec, hasKey("command"));
    //noinspection unchecked
    ImmutableList<String> commandArgs =
        ImmutableList.<String>builder().addAll((Iterable<String>) spec.get("command")).build();

    assertTrue(commandArgs.contains("com.facebook.sample.BinaryResourcesTest"));
    assertFalse(commandArgs.contains("com.sample.R"));
  }

  @Test
  public void robolectricTestBuildsWithNoResources() throws IOException {
    workspace =
        TestDataHelper.createProjectWorkspaceForScenario(this, "android_project", tmpFolder);
    workspace.setUp();
    AssumeAndroidPlatform.get(workspace).assumeSdkIsAvailable();
    workspace.runBuckTest("//java/com/sample/lib:test_with_no_resources").assertSuccess();
  }

  @Test
  public void testRobolectricTestWithDependencyOrderClasspath() throws IOException {
    workspace =
        TestDataHelper.createProjectWorkspaceForScenario(this, "android_project", tmpFolder);
    workspace.setUp();
    AssumeAndroidPlatform.get(workspace).assumeSdkIsAvailable();

    workspace.addBuckConfigLocalOption("java", "use_dependency_order_classpath_for_tests", "true");

    workspace.runBuckTest("//java/com/sample/lib:test").assertSuccess();
  }

  @Test
  public void testRobolectricTestWithDependencyOrderClasspathAsRuleOption() throws IOException {
    workspace =
        TestDataHelper.createProjectWorkspaceForScenario(this, "android_project", tmpFolder);
    workspace.setUp();
    AssumeAndroidPlatform.get(workspace).assumeSdkIsAvailable();
    workspace
        .runBuckTest("//java/com/sample/lib:test_with_dependency_order_classpath")
        .assertSuccess();
  }

  @Test
  public void testRobolectricTestWithRuntimeDependencies() throws IOException {
    workspace =
        TestDataHelper.createProjectWorkspaceForScenario(this, "android_project", tmpFolder);
    workspace.setUp();
    AssumeAndroidPlatform.get(workspace).assumeSdkIsAvailable();
    workspace.runBuckTest("//java/com/sample/lib:test_with_runtime_dependencies").assertSuccess();
  }

  @Test
  public void testRobolectricTestAddsRuntimeDependenciesToRequiredPath() throws IOException {
    workspace =
        TestDataHelper.createProjectWorkspaceForScenario(this, "android_project", tmpFolder);
    workspace.setUp();
    AssumeAndroidPlatform.get(workspace).assumeSdkIsAvailable();
    workspace.addBuckConfigLocalOption("test", "use_relative_paths_in_classpath_file", "true");
    workspace.addBuckConfigLocalOption("test", "external_runner", "echo");
    workspace.addBuckConfigLocalOption("test", "java_for_tests_version", "11");
    workspace.runBuckTest("//java/com/sample/lib:test_with_runtime_dependencies").assertSuccess();

    Path specOutput =
        workspace.getPath(
            workspace.getBuckPaths().getScratchDir().resolve("external_runner_specs.json"));
    ImmutableList<ImmutableMap<String, Object>> specs =
        ObjectMappers.readValue(
            specOutput, new TypeReference<ImmutableList<ImmutableMap<String, Object>>>() {});
    assertThat(specs, iterableWithSize(1));
    ImmutableMap<String, Object> spec = specs.get(0);
    assertThat(spec, hasKey("required_paths"));
    //noinspection unchecked
    ImmutableSortedSet<String> requiredPaths =
        ImmutableSortedSet.<String>naturalOrder()
            .addAll((Iterable<String>) spec.get("required_paths"))
            .build();

    ImmutableList<String> robolectricRuntimeJarEntries =
        requiredPaths.stream()
            .filter(
                path ->
                    path.contains(
                        "third-party/java/robolectric/__robolectric-jar__/robolectric-3.3.2.jar"))
            .collect(ImmutableList.toImmutableList());
    assertEquals(1, robolectricRuntimeJarEntries.size());

    ImmutableList<String> robolectricRuntimeJarSymlinkDirEntries =
        requiredPaths.stream()
            .filter(path -> path.contains("robolectric_runtime_dependencies/runtime_jars"))
            .collect(ImmutableList.toImmutableList());
    assertEquals(1, robolectricRuntimeJarSymlinkDirEntries.size());
  }
}

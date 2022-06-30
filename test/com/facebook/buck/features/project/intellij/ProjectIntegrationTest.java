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

package com.facebook.buck.features.project.intellij;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasXPath;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeThat;

import com.facebook.buck.android.AssumeAndroidPlatform;
import com.facebook.buck.core.filesystems.AbsPath;
import com.facebook.buck.core.filesystems.RelPath;
import com.facebook.buck.testutil.ProcessResult;
import com.facebook.buck.testutil.TemporaryPaths;
import com.facebook.buck.testutil.integration.ProjectWorkspace;
import com.facebook.buck.testutil.integration.TestDataHelper;
import com.facebook.buck.util.environment.Platform;
import com.facebook.buck.util.xml.XmlDomParser;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Streams;
import com.google.common.io.Files;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.w3c.dom.Node;

public class ProjectIntegrationTest {
  @Rule public TemporaryPaths temporaryFolder = new TemporaryPaths();
  @Rule public TemporaryPaths temporaryFolder2 = new TemporaryPaths();

  @Before
  public void setUp() {
    // These tests consistently fail on Windows due to path separator issues.
    assumeThat(Platform.detect(), not(Platform.WINDOWS));
  }

  @Test
  public void testAndroidLibraryProject() throws IOException {
    runBuckProjectAndVerify("android_library");
  }

  @Test
  public void testAndroidBinaryProject() throws IOException {
    runBuckProjectAndVerify("android_binary");
  }

  @Test
  public void testVersion2BuckProject() throws IOException {
    runBuckProjectAndVerify("project1");
  }

  @Test
  public void testVersion2BuckProjectWithoutAutogeneratingSources() throws IOException {
    runBuckProjectAndVerify("project_without_autogeneration");
  }

  @Test
  public void testVersion2BuckProjectSlice() throws IOException {
    runBuckProjectAndVerify("project_slice", "--without-tests", "modules/dep1:dep1");
  }

  @Test
  public void testVersion2BuckProjectSourceMerging() throws IOException {
    runBuckProjectAndVerify("aggregation");
  }

  @Test
  public void testBuckProjectWithCustomAndroidSdks() throws IOException {
    runBuckProjectAndVerify("project_with_custom_android_sdks");
  }

  @Test
  public void testBuckProjectWithCustomJavaSdks() throws IOException {
    runBuckProjectAndVerify("project_with_custom_java_sdks");
  }

  @Test
  public void testBuckProjectWithIntellijSdk() throws IOException {
    runBuckProjectAndVerify("project_with_intellij_sdk");
  }

  @Test
  public void testVersion2BuckProjectWithProjectSettings() throws IOException {
    runBuckProjectAndVerify("project_with_project_settings");
  }

  @Test
  public void testVersion2BuckProjectWithScripts() throws IOException {
    runBuckProjectAndVerify("project_with_scripts", "//modules/dep1:dep1");
  }

  @Test
  public void testVersion2BuckProjectWithUnusedLibraries() throws IOException {
    ProjectWorkspace workspace =
        TestDataHelper.createProjectWorkspaceForScenario(
            this, "project_with_unused_libraries", temporaryFolder);
    workspace.setUp();

    ProcessResult result = workspace.runBuckCommand("project");
    result.assertSuccess("buck project should exit cleanly");

    assertFalse(workspace.resolve(".idea/libraries/library_libs_jsr305.xml").toFile().exists());
  }

  @Test
  public void testVersion2BuckProjectWithExcludedResources() throws IOException {
    runBuckProjectAndVerify("project_with_excluded_resources");
  }

  @Test
  public void testVersion2BuckProjectWithAssets() throws IOException {
    runBuckProjectAndVerify("project_with_assets");
  }

  @Test
  public void testVersion2BuckProjectWithLanguageLevel() throws IOException {
    runBuckProjectAndVerify("project_with_language_level");
  }

  @Test
  public void testVersion2BuckProjectWithOutputUrl() throws IOException {
    runBuckProjectAndVerify("project_with_output_url");
  }

  @Test
  public void testVersion2BuckProjectWithJavaResources() throws IOException {
    runBuckProjectAndVerify("project_with_java_resources");
  }

  @Test
  public void testVersion2BuckProjectWithExtraModuleLibraries() throws IOException {
    runBuckProjectAndVerify("project_with_extra_module_libraries");
  }

  @Test
  public void testVersion2BuckProjectWithExtraOutputModules() throws IOException {
    runBuckProjectAndVerify("project_with_extra_output_modules");
  }

  @Test
  public void testVersion2BuckProjectWithGeneratedSources() throws IOException {
    runBuckProjectAndVerify("project_with_generated_sources");
  }

  @Test
  public void testBuckProjectWithSubdirGlobResources() throws IOException {
    runBuckProjectAndVerify("project_with_subdir_glob_resources");
  }

  @Test
  public void testRobolectricTestRule() throws IOException {
    runBuckProjectAndVerify("robolectric_test");
  }

  @Test
  public void testJavaTestRule() throws IOException {
    runBuckProjectAndVerify("java_test");
  }

  @Test
  public void testAndroidBuildConfigInDependencies() throws IOException {
    runBuckProjectAndVerify("project_with_android_build_config");
  }

  @Test
  public void testAndroidResourcesInDependencies() throws IOException {
    runBuckProjectAndVerify("project_with_android_resources");
  }

  @Test
  public void testPrebuiltJarWithJavadoc() throws IOException {
    runBuckProjectAndVerify("project_with_prebuilt_jar");
  }

  @Test
  public void testZipFile() throws IOException {
    runBuckProjectAndVerify("project_with_zipfile");
  }

  @Test
  public void testAndroidResourcesAndLibraryInTheSameFolder() throws IOException {
    runBuckProjectAndVerify("android_resources_in_the_same_folder");
  }

  @Test
  public void testAndroidResourcesWithPackagesAtTheSameLocation() throws IOException {
    runBuckProjectAndVerify("project_with_multiple_resources_with_package_names");
  }

  @Test
  public void testCxxLibrary() throws IOException {
    runBuckProjectAndVerify("project_with_cxx_library");
  }

  @Test
  public void testAggregatingCxxLibrary() throws IOException {
    runBuckProjectAndVerify("aggregation_with_cxx_library");
  }

  @Test
  public void testCxxTest() throws IOException {
    runBuckProjectAndVerify("project_with_cxx_test");
  }

  @Test
  public void testAggregatingCxxTest() throws IOException {
    runBuckProjectAndVerify("aggregation_with_cxx_test");
  }

  @Test
  public void testSavingGeneratedFilesList() throws IOException {
    runBuckProjectAndVerify(
        "save_generated_files_list",
        "--file-with-list-of-generated-files",
        ".idea/generated-files.txt");
  }

  @Test
  public void testMultipleLibraries() throws IOException {
    runBuckProjectAndVerify("project_with_multiple_libraries");
  }

  @Test
  public void testProjectWithIgnoredTargets() throws IOException {
    runBuckProjectAndVerify("project_with_ignored_targets");
  }

  @Test
  public void testProjectWithCustomPackages() throws IOException {
    runBuckProjectAndVerify("aggregation_with_custom_packages");
  }

  @Test
  public void testAndroidResourceAggregation() throws IOException {
    runBuckProjectAndVerify("android_resource_aggregation");
  }

  @Test
  public void testAndroidResourceAggregationWithLimit() throws IOException {
    runBuckProjectAndVerify("android_resource_aggregation_with_limit");
  }

  @Test
  public void testProjectIncludesTestsByDefault() throws IOException {
    runBuckProjectAndVerify("project_with_tests_by_default", "//modules/lib:lib");
  }

  @Test
  public void testProjectExcludesTestsWhenRequested() throws IOException {
    runBuckProjectAndVerify("project_without_tests", "--without-tests", "//modules/lib:lib");
  }

  @Test
  public void testProjectExcludesDepTestsWhenRequested() throws IOException {
    runBuckProjectAndVerify(
        "project_without_dep_tests", "--without-dependencies-tests", "//modules/lib:lib");
  }

  @Test
  public void testUpdatingExistingWorkspace() throws IOException {
    runBuckProjectAndVerify("update_existing_workspace");
  }

  @Test
  public void testCreateNewWorkspace() throws IOException {
    runBuckProjectAndVerify("create_new_workspace");
  }

  @Test
  public void testUpdateMalformedWorkspace() throws IOException {
    runBuckProjectAndVerify("update_malformed_workspace");
  }

  @Test
  public void testUpdateWorkspaceWithoutIgnoredNodes() throws IOException {
    runBuckProjectAndVerify("update_workspace_without_ignored_nodes");
  }

  @Test
  public void testUpdateWorkspaceWithoutManagerNode() throws IOException {
    runBuckProjectAndVerify("update_workspace_without_manager_node");
  }

  @Test
  public void testUpdateWorkspaceWithoutProjectNode() throws IOException {
    runBuckProjectAndVerify("update_workspace_without_project_node");
  }

  @Test
  public void testProjectWthPackageBoundaryException() throws IOException {
    runBuckProjectAndVerify("project_with_package_boundary_exception", "//project2:lib");
  }

  @Test
  public void testProjectWithPrebuiltJarExportedDeps() throws IOException {
    runBuckProjectAndVerify("project_with_prebuilt_exported_deps", "//a:a");
  }

  @Test
  public void testProjectWithProjectRoot() throws IOException {
    runBuckProjectAndVerify(
        "project_with_project_root",
        "--intellij-project-root",
        "project1",
        "--intellij-include-transitive-dependencies",
        "--intellij-module-group-name",
        "",
        "//project1/lib:lib");
  }

  @Test
  public void testProjectWithBinaryInputs() throws IOException {
    runBuckProjectAndVerify("project_with_binary_inputs");
  }

  @Test
  public void testGeneratingAndroidManifest() throws IOException {
    runBuckProjectAndVerify("generate_android_manifest");
  }

  @Test
  public void testGeneratingAndroidManifestWithHashedPath() throws IOException {
    runBuckProjectAndVerify("generate_android_manifest_with_hashed_path");
  }

  @Test
  public void testGeneratingAndroidManifestWithMinSdkWithDifferentVersionsFromManifest()
      throws IOException {
    runBuckProjectAndVerify("min_sdk_version_different_from_manifests");
  }

  @Test
  public void testGeneratingAndroidManifestWithMinSdkParameterized() throws IOException {
    runBuckProjectAndVerify("min_sdk_parameterized");
  }

  @Test
  public void testGeneratingAndroidManifestWithMinSdkFromBinaryManifest() throws IOException {
    runBuckProjectAndVerify("min_sdk_version_from_binary_manifest");
  }

  @Test
  public void testGeneratingAndroidManifestWithMinSdkFromBuckConfig() throws IOException {
    runBuckProjectAndVerify("min_sdk_version_from_buck_config");
  }

  @Test
  public void testGeneratingAndroidManifestWithNoMinSdkConfig() throws IOException {
    runBuckProjectAndVerify("min_sdk_version_with_no_config");
  }

  @Test
  public void testPreprocessScript() throws IOException {
    ProcessResult result = runBuckProjectAndVerify("preprocess_script_test");

    assertEquals("intellij", result.getStdout().trim());
  }

  @Test
  public void testScalaProject() throws IOException {
    runBuckProjectAndVerify("scala_project");
  }

  @Test
  public void testIgnoredPathAddedToExcludedFolders() throws IOException {
    runBuckProjectAndVerify("ignored_excluded");
  }

  @Test
  public void testImlsInIdea() throws IOException {
    runBuckProjectAndVerify("imls_in_idea");
  }

  @Test
  public void testPythonLibrary() throws IOException {
    runBuckProjectAndVerify("python_library");
  }

  @Test
  public void testRustRules() throws IOException {
    runBuckProjectAndVerify("rust_rules");
  }

  @Test
  public void testTwoConfigurations() throws IOException {
    runBuckProjectAndVerify("two_configurations");
  }

  @Test
  public void testDepsQuery() throws IOException {
    runBuckProjectAndVerify("deps_query");
  }

  @Test
  public void testOutputDir() throws IOException {
    ProjectWorkspace workspace =
        TestDataHelper.createProjectWorkspaceForScenario(
            this, "min_sdk_version_from_binary_manifest", temporaryFolder);
    workspace.setUp();
    AssumeAndroidPlatform.get(workspace).assumeSdkIsAvailable();

    workspace.runBuckCommand("project").assertSuccess("buck project should exit cleanly");
    AbsPath outPath = temporaryFolder2.getRoot();
    workspace
        .runBuckCommand("project", "--output-dir", outPath.toString())
        .assertSuccess("buck project should exit cleanly");

    // Check every file in output-dir matches one in project
    for (File outFile : Files.fileTraverser().breadthFirst(outPath.toFile())) {
      RelPath relativePath = outPath.relativize(Paths.get(outFile.getPath()));
      File projFile = temporaryFolder.getRoot().resolve(relativePath).toFile();
      assertTrue(projFile.exists());
      if (projFile.isFile()) {
        assertTrue(Files.asByteSource(projFile).contentEquals(Files.asByteSource(outFile)));
      }
    }
  }

  Iterable<File> recursePath(Path path) {
    return Files.fileTraverser().breadthFirst(path.toFile());
  }

  @Test
  public void testOutputDirNoProjectWrite() throws IOException {
    ProjectWorkspace workspace =
        TestDataHelper.createProjectWorkspaceForScenario(
            this, "min_sdk_version_from_binary_manifest", temporaryFolder);
    workspace.setUp();
    AssumeAndroidPlatform.get(workspace).assumeSdkIsAvailable();

    Path projPath = temporaryFolder.getRoot().getPath();
    Path outPath = temporaryFolder2.getRoot().getPath();

    long lastModifiedProject =
        Streams.stream(recursePath(projPath)).map(File::lastModified).max(Long::compare).get();
    ProcessResult result = workspace.runBuckCommand("project", "--output-dir", outPath.toString());
    result.assertSuccess("buck project should exit cleanly");

    for (File file : recursePath(projPath)) {
      if (!(file.isDirectory()
          || file.getPath().contains("log")
          || file.getName().equals(".progressestimations.json")
          || file.getName().equals(".currentversion"))) {
        assertTrue(file.lastModified() <= lastModifiedProject);
      }
    }
  }

  @Test
  public void testDifferentOutputDirSameProject() throws IOException {
    ProjectWorkspace workspace =
        TestDataHelper.createProjectWorkspaceForScenario(
            this, "min_sdk_version_from_binary_manifest", temporaryFolder);
    workspace.setUp();
    AssumeAndroidPlatform.get(workspace).assumeSdkIsAvailable();

    Path out1Path = temporaryFolder2.newFolder("project1").getPath();
    // Make sure buck project creates a dir if it doesn't exist
    Path out2Path = temporaryFolder2.getRoot().resolve("project2/subdir").getPath();
    workspace
        .runBuckCommand("project", "--output-dir", out1Path.toString())
        .assertSuccess("buck project should exit cleanly");
    workspace
        .runBuckCommand("project", "--output-dir", out2Path.toString())
        .assertSuccess("buck project should exit cleanly");

    List<File> out1Files = Lists.newArrayList(recursePath(out1Path));
    List<File> out2Files = Lists.newArrayList(recursePath(out2Path));
    assertEquals(out1Files.size(), out2Files.size());
    for (File file1 : out1Files) {
      Path relativePath = out1Path.relativize(Paths.get(file1.getPath()));
      File file2 = out2Path.resolve(relativePath).toFile();
      assertTrue(file2.exists());
      if (file1.isFile()) {
        assertTrue(Files.asByteSource(file1).contentEquals(Files.asByteSource(file2)));
      }
    }
  }

  @Test
  public void testBuckModuleRegenerateSubproject() throws Exception {
    ProjectWorkspace workspace =
        TestDataHelper.createProjectWorkspaceForScenario(
                this, "incrementalProject", temporaryFolder.newFolder())
            .setUp();
    AssumeAndroidPlatform.get(workspace).assumeSdkIsAvailable();
    final String extraModuleFilePath = "modules/extra/modules_extra.iml";
    final File extraModuleFile = workspace.getPath(extraModuleFilePath).toFile();
    workspace
        .runBuckCommand("project", "--intellij-aggregation-mode=none", "//modules/tip:tip")
        .assertSuccess();
    assertFalse(extraModuleFile.exists());
    final String modulesBefore = workspace.getFileContents(".idea/modules.xml");
    final String fileXPath =
        String.format(
            "/project/component/modules/module[contains(@filepath,'%s')]", extraModuleFilePath);
    assertThat(XmlDomParser.parse(modulesBefore), not(hasXPath(fileXPath)));

    // Run regenerate on the new modules
    workspace
        .runBuckCommand(
            "project", "--intellij-aggregation-mode=none", "--update", "//modules/extra:extra")
        .assertSuccess();
    assertTrue(extraModuleFile.exists());
    final String modulesAfter = workspace.getFileContents(".idea/modules.xml");
    assertThat(XmlDomParser.parse(modulesAfter), hasXPath(fileXPath));
    workspace.verify();
  }

  @Test
  public void testBuckModuleRegenerateSubprojectNoOp() throws IOException {
    ProjectWorkspace workspace =
        TestDataHelper.createProjectWorkspaceForScenario(
                this, "incrementalProject", temporaryFolder.newFolder())
            .setUp();
    AssumeAndroidPlatform.get(workspace).assumeSdkIsAvailable();
    workspace
        .runBuckCommand(
            "project",
            "--intellij-aggregation-mode=none",
            "//modules/tip:tip",
            "//modules/extra:extra")
        .assertSuccess();
    workspace.verify();
    // Run regenerate, should be a no-op relative to previous
    workspace
        .runBuckCommand(
            "project", "--intellij-aggregation-mode=none", "--update", "//modules/extra:extra")
        .assertSuccess();
    workspace.verify();
  }

  @Test
  public void testBuckModuleRegenerateWithExportedLibs() throws Exception {
    ProjectWorkspace workspace =
        TestDataHelper.createProjectWorkspaceForScenario(
                this, "incrementalProject", temporaryFolder.newFolder())
            .setUp();
    AssumeAndroidPlatform.get(workspace).assumeSdkIsAvailable();
    final String libraryFilePath = ".idea/libraries/__modules_lib_guava.xml";
    final File libraryFile = workspace.getPath(libraryFilePath).toFile();
    workspace
        .runBuckCommand("project", "--intellij-aggregation-mode=none", "//modules/tip:tip")
        .assertSuccess();
    assertFalse(libraryFile.exists());
    // Run regenerate and we should see the library file get created
    workspace
        .runBuckCommand(
            "project",
            "--intellij-aggregation-mode=none",
            "--update",
            "//modules/tip:tipwithexports")
        .assertSuccess();
    assertTrue(libraryFile.exists());
  }

  @Test
  public void testCrossCellIntelliJProject() throws Exception {

    ProjectWorkspace primary =
        TestDataHelper.createProjectWorkspaceForScenarioWithoutDefaultCell(
            this, "inter-cell/primary", temporaryFolder.newFolder());
    primary.setUp();
    AssumeAndroidPlatform.get(primary).assumeSdkIsAvailable();

    ProjectWorkspace secondary =
        TestDataHelper.createProjectWorkspaceForScenarioWithoutDefaultCell(
            this, "inter-cell/secondary", temporaryFolder.newFolder());
    secondary.setUp();

    TestDataHelper.overrideBuckconfig(
        primary,
        ImmutableMap.of(
            "repositories",
            ImmutableMap.of("secondary", secondary.getPath(".").normalize().toString())));

    // First try with cross-cell enabled
    String target = "//apps/sample:app_with_cross_cell_android_lib";
    ProcessResult result = primary.runBuckCommand("project", "--ide", "intellij", target);
    result.assertSuccess();

    String libImlPath = ".idea/libraries/secondary__java_com_crosscell_crosscell.xml";
    Node doc = XmlDomParser.parse(primary.getFileContents(libImlPath));
    String urlXpath = "/component/library/CLASSES/root/@url";
    // Assert that the library URL is inside the project root
    assertThat(
        doc, hasXPath(urlXpath, startsWith("jar://$PROJECT_DIR$/buck-out/cells/secondary/gen/")));
  }

  @Test
  public void testGeneratingModulesInMultiCells() throws Exception {

    ProjectWorkspace primary =
        TestDataHelper.createProjectWorkspaceForScenarioWithoutDefaultCell(
            this, "modules_in_multi_cells/primary", temporaryFolder.newFolder("primary"));
    primary.setUp();
    AssumeAndroidPlatform.get(primary).assumeSdkIsAvailable();

    ProjectWorkspace secondary =
        TestDataHelper.createProjectWorkspaceForScenarioWithoutDefaultCell(
            this, "modules_in_multi_cells/secondary", temporaryFolder.newFolder("secondary"));
    secondary.setUp();

    TestDataHelper.overrideBuckconfig(
        primary,
        ImmutableMap.of(
            "repositories",
            ImmutableMap.of("secondary", secondary.getPath(".").normalize().toString())));

    String target = "//java/com/sample/app:app";
    ProcessResult result =
        primary.runBuckCommand(
            "project",
            "--config",
            "intellij.multi_cell_module_support=true",
            "--config",
            "intellij.keep_module_files_in_module_dirs=true",
            "--intellij-aggregation-mode=None",
            "--ide",
            "intellij",
            target);
    result.assertSuccess();
    primary.verify();
    secondary.verify();
  }

  @Test
  public void testLibraryNameGeneration() throws IOException {
    runBuckProjectAndVerify("library_name_generation");
  }

  @Test
  public void testAliasRules() throws IOException {
    /*
    Verifies the project generation in the case of aliases and exported dependencies in alias.
    There are three main cases tested here:
        1. Exported dependencies from alias: fbsrc/thirdparty/java/com/dev/baz
        2. Alias inside an exported dependency: fbsrc/thirdparty/java/com/dev/bar/actualbar
        3. Alias with no exported dependency: fbsrc/thirdparty/java/com/dev/bax
     */
    runBuckProjectAndVerify("alias_rules");
  }

  private ProcessResult runBuckProjectAndVerify(String folderWithTestData, String... commandArgs)
      throws IOException {

    ProjectWorkspace workspace =
        TestDataHelper.createProjectWorkspaceForScenario(this, folderWithTestData, temporaryFolder);
    workspace.setUp();
    AssumeAndroidPlatform.get(workspace).assumeSdkIsAvailable();

    ProcessResult result =
        workspace.runBuckCommand(Lists.asList("project", commandArgs).toArray(new String[0]));
    result.assertSuccess("buck project should exit cleanly");

    workspace.verify();

    return result;
  }
}

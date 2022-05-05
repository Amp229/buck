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

package com.facebook.buck.features.apple.projectV2;

import static com.facebook.buck.apple.common.AppleFlavors.SWIFT_COMPILE_FLAVOR;
import static com.facebook.buck.apple.common.AppleFlavors.SWIFT_UNDERLYING_VFS_OVERLAY_FLAVOR;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import com.facebook.buck.apple.AppleConfig;
import com.facebook.buck.apple.AppleNativeIntegrationTestUtils;
import com.facebook.buck.apple.toolchain.ApplePlatform;
import com.facebook.buck.core.filesystems.RelPath;
import com.facebook.buck.core.model.BuildTarget;
import com.facebook.buck.core.model.BuildTargetFactory;
import com.facebook.buck.core.model.InternalFlavor;
import com.facebook.buck.core.model.impl.BuildTargetPaths;
import com.facebook.buck.cxx.CxxLibraryDescription;
import com.facebook.buck.cxx.toolchain.HeaderMode;
import com.facebook.buck.io.filesystem.ProjectFilesystem;
import com.facebook.buck.io.filesystem.TestProjectFilesystems;
import com.facebook.buck.testutil.ProcessResult;
import com.facebook.buck.testutil.TemporaryPaths;
import com.facebook.buck.testutil.integration.BuckBuildLog;
import com.facebook.buck.testutil.integration.ProjectWorkspace;
import com.facebook.buck.testutil.integration.TestDataHelper;
import com.facebook.buck.util.ProcessExecutor;
import com.facebook.buck.util.environment.Platform;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.hamcrest.Matchers;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;

public class ProjectIntegrationTest {
  @Rule public TemporaryPaths temporaryFolder = new TemporaryPaths();

  @Before
  public void setUp() {
    Assume.assumeThat(Platform.detect(), Matchers.not(Platform.WINDOWS));
  }

  @Test
  public void testBuckProjectGeneratedSchemesDoNotIncludeOtherTests() throws IOException {
    ProjectWorkspace workspace =
        createWorkspace(this, "project_generated_schemes_do_not_include_other_tests");

    ProcessResult result = workspace.runBuckCommand("project");
    result.assertSuccess();

    workspace.verify();
  }

  @Test
  public void generatingAllWorkspacesWillNotIncludeAllProjectsInEachOfThem() throws IOException {
    ProjectWorkspace workspace =
        createWorkspace(
            this, "generating_all_workspaces_will_not_include_all_projects_in_each_of_them");
    ProcessResult result = workspace.runBuckCommand("project");
    result.assertSuccess();

    workspace.verify();
  }

  @Test
  public void schemeWithActionConfigNames() throws IOException {
    ProjectWorkspace workspace = createWorkspace(this, "scheme_with_action_config_names");

    ProcessResult result = workspace.runBuckCommand("project");
    result.assertSuccess();

    workspace.verify();
  }

  @Test
  public void schemeWithExtraTests() throws IOException {
    ProjectWorkspace workspace = createWorkspace(this, "scheme_with_extra_tests");

    ProcessResult result = workspace.runBuckCommand("project");
    result.assertSuccess();

    workspace.verify();
  }

  @Test
  public void schemeWithExtraTestsWithoutSrcTarget() throws IOException {
    ProjectWorkspace workspace =
        createWorkspace(this, "scheme_with_extra_tests_without_src_target");

    ProcessResult result = workspace.runBuckCommand("project");
    result.assertSuccess();

    workspace.verify();
  }

  @Test
  public void generatingRootDirectoryProject() throws IOException {
    ProjectWorkspace workspace = createWorkspace(this, "generating_root_directory_project");

    ProcessResult result = workspace.runBuckCommand("project", "//:bundle");
    result.assertSuccess();

    workspace.verify();
  }

  @Test
  public void testGeneratesWorkspaceFromAlias() throws IOException {
    ProjectWorkspace workspace = createWorkspace(this, "project_implicit_workspace_generation");

    ProcessResult result = workspace.runBuckCommand("project", "//bin:alias");
    result.assertSuccess();
    Files.exists(workspace.resolve("test/test.xcworkspace/contents.xcworkspacedata"));
    Files.exists(workspace.resolve("test/test.xcodeproj/project.pbxproj"));
  }

  @Test
  public void testGeneratesWorkspaceFromBundle() throws IOException {
    ProjectWorkspace workspace = createWorkspace(this, "project_implicit_workspace_generation");

    ProcessResult result = workspace.runBuckCommand("project", "//bin:app");
    result.assertSuccess();
    Files.exists(workspace.resolve("bin/app.xcworkspace/contents.xcworkspacedata"));
    Files.exists(workspace.resolve("bin/bin.xcodeproj/project.pbxproj"));
  }

  @Test
  public void testGeneratesWorkspaceFromLibrary() throws IOException {
    ProjectWorkspace workspace = createWorkspace(this, "project_implicit_workspace_generation");

    ProcessResult result = workspace.runBuckCommand("project", "//lib:lib");
    result.assertSuccess();
    Files.exists(workspace.resolve("lib/lib.xcworkspace/contents.xcworkspacedata"));
    Files.exists(workspace.resolve("lib/lib.xcodeproj/project.pbxproj"));
  }

  @Test
  public void testGeneratesWorkspaceFromBinary() throws IOException {
    ProjectWorkspace workspace = createWorkspace(this, "project_implicit_workspace_generation");

    ProcessResult result = workspace.runBuckCommand("project", "//bin:bin");
    result.assertSuccess();
    Files.exists(workspace.resolve("bin/bin.xcworkspace/contents.xcworkspacedata"));
    Files.exists(workspace.resolve("bin/bin.xcodeproj/project.pbxproj"));
  }

  @Test
  public void testGeneratesWorkspaceFromTest() throws IOException {
    ProjectWorkspace workspace = createWorkspace(this, "project_implicit_workspace_generation");

    ProcessResult result = workspace.runBuckCommand("project", "//test:test");
    result.assertSuccess();
    Files.exists(workspace.resolve("test/test.xcworkspace/contents.xcworkspacedata"));
    Files.exists(workspace.resolve("test/test.xcodeproj/project.pbxproj"));
  }

  @Test
  public void testAttemptingToGenerateWorkspaceFromResourceTargetIsABuildError()
      throws IOException {
    ProjectWorkspace workspace = createWorkspace(this, "project_implicit_workspace_generation");

    ProcessResult processResult = workspace.runBuckCommand("project", "//res:res");
    processResult.assertFailure();
    assertThat(
        processResult.getStderr(),
        containsString(
            "//res:res must be a xcode_workspace_config, apple_binary, apple_bundle, apple_library, apple_test, or an alias pointing to one of the above"));
  }

  @Test
  public void testAttemptingToGenerateWorkspaceFromAliasPointingToResourceTargetIsABuildError()
      throws IOException {
    ProjectWorkspace workspace = createWorkspace(this, "project_implicit_workspace_generation");

    ProcessResult processResult = workspace.runBuckCommand("project", "//res:res-alias");
    processResult.assertFailure();
    assertThat(
        processResult.getStderr(),
        containsString(
            "//res:res-alias must point to a xcode_workspace_config, apple_binary, apple_bundle, apple_library, or apple_test"));
  }

  @Test
  public void testAttemptToGenerateWorkspaceFromBuckModuleContainingInvalidTargets()
      throws IOException {
    ProjectWorkspace workspace = createWorkspace(this, "project_implicit_workspace_generation");

    ProcessResult processResult = workspace.runBuckCommand("project", "//mixed:", "--experimental");
    processResult.assertFailure();
    assertThat(
        processResult.getStderr(),
        containsString(
            "//mixed:res must be a xcode_workspace_config, apple_binary, apple_bundle, apple_library"));
  }

  @Test
  public void testGenerateWorkspaceFromBuckModuleContainingInvalidTargets() throws IOException {
    ProjectWorkspace workspace = createWorkspace(this, "project_implicit_workspace_generation");

    ProcessResult processResult =
        workspace.runBuckCommand(
            "project",
            "//mixed:",
            "--experimental",
            "--config",
            "apple.project_generator_ignore_invalid_targets=true");
    processResult.assertSuccess();
    Files.exists(workspace.resolve("mixed/lib.xcodeproj/project.pbxproj"));
  }

  @Test
  public void testGeneratingProjectWithTargetUsingGenruleSourceBuildsGenrule() throws IOException {
    ProjectWorkspace workspace = createWorkspace(this, "target_using_genrule_source");

    ProcessResult result = workspace.runBuckCommand("project", "//lib:lib", "--experimental");
    result.assertSuccess();

    BuckBuildLog buildLog = workspace.getBuildLog();
    buildLog.assertTargetBuiltLocally("//lib:gen");
    buildLog.assertTargetBuiltLocally("other_cell//:gen");
  }

  @Test
  public void testGeneratingProjectWithGenruleResourceBuildsGenrule() throws IOException {
    Assume.assumeThat(Platform.detect(), Matchers.is(Platform.MACOS));
    ProjectWorkspace workspace = createWorkspace(this, "target_using_genrule_resource");

    ProcessResult processResult =
        workspace.runBuckCommand(
            "project",
            "//app:TestApp",
            "--experimental",
            "--config",
            "cxx.default_platform=iphonesimulator-x86_64");

    processResult.assertSuccess();
    BuckBuildLog buildLog = workspace.getBuildLog();
    buildLog.assertTargetBuiltLocally("//app:GenResource");
  }

  @Test
  public void testBuckProjectFocus() throws IOException {
    Assume.assumeThat(Platform.detect(), Matchers.is(Platform.MACOS));
    assumeTrue(
        AppleNativeIntegrationTestUtils.isApplePlatformAvailable(ApplePlatform.IPHONESIMULATOR));
    ProjectWorkspace workspace = createWorkspace(this, "project_focus");

    ProcessResult result =
        workspace.runBuckCommand(
            "project",
            "--focus",
            "//Libraries/Dep1:Dep1_1#iphonesimulator-x86_64 //Libraries/Dep2:Dep2",
            "//Apps:TestApp#iphonesimulator-x86_64");
    result.assertSuccess();

    workspace.verify();
  }

  @Test
  public void testBuckProjectFocusWithTests() throws IOException {
    ProjectWorkspace workspace = createWorkspace(this, "project_focus_with_tests");

    ProcessResult result =
        workspace.runBuckCommand(
            "project",
            "--config",
            "project.ide=xcode",
            "--with-tests",
            "--focus",
            "//Tests:",
            "//Apps:TestApp");
    result.assertSuccess();
  }

  @Test
  public void testGeneratingProjectMetadataWithGenrule() throws IOException {
    ProjectWorkspace workspace = createWorkspace(this, "target_using_genrule_source");

    ProcessResult result = workspace.runBuckCommand("project", "//lib:lib", "--experimental");
    result.assertSuccess();
    workspace.verify();
  }

  @Test
  public void testBuckProjectShowsFullOutput() throws Exception {
    ProjectWorkspace workspace = createWorkspace(this, "target_using_genrule_source");

    ProcessResult result =
        workspace.runBuckCommand("project", "--show-full-output", "//lib:lib", "--experimental");
    result.assertSuccess();

    workspace.verify();

    assertEquals(
        "//lib:lib#default,static "
            + workspace.getDestPath().resolve("lib").resolve("lib.xcworkspace")
            + System.lineSeparator(),
        result.getStdout());
  }

  @Test
  public void testBuckProjectShowsOutput() throws IOException {
    ProjectWorkspace workspace = createWorkspace(this, "target_using_genrule_source");

    ProcessResult result =
        workspace.runBuckCommand("project", "--show-output", "//lib:lib", "--experimental");
    workspace.verify();

    assertEquals(
        "//lib:lib#default,static " + Paths.get("lib", "lib.xcworkspace") + System.lineSeparator(),
        result.getStdout());
  }

  @Test
  public void testBuckProjectWithCell() throws IOException, InterruptedException {
    Assume.assumeThat(Platform.detect(), Matchers.is(Platform.MACOS));
    assumeTrue(AppleNativeIntegrationTestUtils.isApplePlatformAvailable(ApplePlatform.MACOSX));
    ProjectWorkspace workspace = createWorkspace(this, "project_with_cell");

    ProcessResult result =
        workspace.runBuckCommand(
            "project",
            "//Apps:workspace",
            "--experimental",
            "--config",
            "cxx.default_platform=iphonesimulator-x86_64");
    result.assertSuccess();

    runXcodebuild(workspace, "Apps/TestApp.xcworkspace", "TestApp");
  }

  @Test
  public void testBuckProjectWithEmbeddedCellBuckout() throws IOException, InterruptedException {
    Assume.assumeThat(Platform.detect(), Matchers.is(Platform.MACOS));
    assumeTrue(AppleNativeIntegrationTestUtils.isApplePlatformAvailable(ApplePlatform.MACOSX));
    ProjectWorkspace workspace = createWorkspace(this, "project_with_cell");

    ProcessResult result =
        workspace.runBuckCommand(
            "project",
            "//Apps:workspace",
            "--experimental",
            "--config",
            "cxx.default_platform=iphonesimulator-x86_64");
    result.assertSuccess();

    runXcodebuild(workspace, "Apps/TestApp.xcworkspace", "TestApp");
  }

  @Test
  public void testBuckProjectWithCellAndMergedHeaderMap() throws IOException, InterruptedException {
    Assume.assumeThat(Platform.detect(), Matchers.is(Platform.MACOS));
    assumeTrue(AppleNativeIntegrationTestUtils.isApplePlatformAvailable(ApplePlatform.MACOSX));
    ProjectWorkspace workspace = createWorkspace(this, "project_with_cell");

    ProcessResult result =
        workspace.runBuckCommand(
            "project",
            "--config",
            "apple.merge_header_maps_in_xcode=true",
            "//Apps:workspace",
            "--experimental",
            "--config",
            "cxx.default_platform=iphonesimulator-x86_64");
    result.assertSuccess();

    runXcodebuild(workspace, "Apps/TestApp.xcworkspace", "TestApp");
  }

  @Ignore
  @Test(timeout = 3 * 60 * 1_000)
  public void testBuckProjectWithAppleBundleTests() throws IOException, InterruptedException {
    Assume.assumeThat(Platform.detect(), Matchers.is(Platform.MACOS));
    assumeTrue(AppleNativeIntegrationTestUtils.isApplePlatformAvailable(ApplePlatform.MACOSX));
    ProjectWorkspace workspace = createWorkspace(this, "project_with_apple_bundle_test");

    ProcessResult result = workspace.runBuckCommand("project", "//app:bundle");
    result.assertSuccess();

    ProcessExecutor.Result xcodeTestResult =
        workspace.runCommand(
            "xcodebuild",
            "-workspace",
            "app/bundle.xcworkspace",
            "-scheme",
            "bundle",
            "-destination 'platform=OS X,arch=x86_64'",
            "clean",
            "test");
    xcodeTestResult.getStderr().ifPresent(System.err::print);
    assertEquals("xcodebuild should succeed", 0, xcodeTestResult.getExitCode());
  }

  @Test
  public void testBuckProjectWithEmbeddedCellBuckoutAndMergedHeaderMap()
      throws IOException, InterruptedException {
    Assume.assumeThat(Platform.detect(), Matchers.is(Platform.MACOS));
    assumeTrue(AppleNativeIntegrationTestUtils.isApplePlatformAvailable(ApplePlatform.MACOSX));
    ProjectWorkspace workspace = createWorkspace(this, "project_with_cell");

    ProcessResult result =
        workspace.runBuckCommand(
            "project",
            "--config",
            "apple.merge_header_maps_in_xcode=true",
            "//Apps:workspace",
            "--experimental",
            "--config",
            "cxx.default_platform=iphonesimulator-x86_64");
    result.assertSuccess();

    runXcodebuild(workspace, "Apps/TestApp.xcworkspace", "TestApp");
  }

  @Test
  public void testBuckProjectOtherCell() throws IOException {
    Assume.assumeThat(Platform.detect(), Matchers.is(Platform.MACOS));
    assumeTrue(AppleNativeIntegrationTestUtils.isApplePlatformAvailable(ApplePlatform.MACOSX));
    ProjectWorkspace workspace = createWorkspace(this, "project_with_cell");

    ProcessResult result =
        workspace.runBuckCommand(
            "project",
            "--config",
            "apple.merge_header_maps_in_xcode=true",
            "--show-output",
            "bar//Dep2:Dep2");
    result.assertSuccess();

    assertEquals(
        "bar//Dep2:Dep2#default,static "
            + Paths.get("bar", "Dep2", "Dep2.xcworkspace")
            + System.lineSeparator(),
        result.getStdout());
  }

  @Test
  public void testBuckProjectWithReuseActionGraph() throws IOException {
    Assume.assumeThat(Platform.detect(), Matchers.is(Platform.MACOS));
    assumeTrue(AppleNativeIntegrationTestUtils.isApplePlatformAvailable(ApplePlatform.MACOSX));

    ProjectWorkspace workspace =
        createWorkspace(this, "project_with_swift_dependency_on_modular_objective_c_library");
    workspace.addBuckConfigLocalOption("cxx", "default_platform", "iphonesimulator-x86_64");
    workspace.addBuckConfigLocalOption("apple", "project_generator_index_via_compile_args", "true");

    ProcessResult result = workspace.runBuckCommand("project", "//Apps:App", "--experimental");

    ProjectFilesystem filesystem =
        TestProjectFilesystems.createProjectFilesystem(workspace.getDestPath());
    BuildTarget objCTarget = BuildTargetFactory.newInstance("//Libraries:ObjCDep");
    BuildTarget exportedHeadersTarget = getExportedHeaderTarget(objCTarget);

    // Verify the required headers for indexing is generated
    assertTrue(
        filesystem.isDirectory(
            filesystem.resolve(getTargetOutputPath(exportedHeadersTarget, filesystem))));
    result.assertSuccess();
  }

  @Test
  public void testBuckProjectWithSwiftDependencyOnModularObjectiveCLibrary() throws IOException {
    Assume.assumeThat(Platform.detect(), Matchers.is(Platform.MACOS));
    assumeTrue(AppleNativeIntegrationTestUtils.isApplePlatformAvailable(ApplePlatform.MACOSX));

    ProjectWorkspace workspace =
        createWorkspace(this, "project_with_swift_dependency_on_modular_objective_c_library");

    ProcessResult result =
        workspace.runBuckCommand(
            "project",
            "//Apps:App",
            "--experimental",
            "--config",
            "cxx.default_platform=iphonesimulator-x86_64",
            "--config",
            "apple.project_generator_index_via_compile_args=true");

    ProjectFilesystem filesystem =
        TestProjectFilesystems.createProjectFilesystem(workspace.getDestPath());

    BuildTarget swiftTarget = BuildTargetFactory.newInstance("//Libraries:SwiftDep");
    BuildTarget objCTarget = BuildTargetFactory.newInstance("//Libraries:ObjCDep");

    BuildTarget exportedHeadersTarget = getExportedHeaderTarget(objCTarget);

    List<String> swiftArgs = getSwiftIndexingArgs("SwiftDep", workspace, filesystem, swiftTarget);

    // assert we're setting the correct Swift flags to include the ObjC modular header
    // and that we're setting the correct module name
    assertEquals(
        swiftArgs,
        ImmutableList.of(
            "-Xcc",
            "-I",
            "-Xcc",
            getAbsolutePathString(exportedHeadersTarget, filesystem),
            "-module-name",
            "SwiftDep"));

    result.assertSuccess();
  }

  @Test
  public void testBuckProjectWithSwiftObjectiveCMixedLibrary() throws IOException {
    Assume.assumeThat(Platform.detect(), Matchers.is(Platform.MACOS));
    assumeTrue(AppleNativeIntegrationTestUtils.isApplePlatformAvailable(ApplePlatform.MACOSX));

    ProjectWorkspace workspace = createWorkspace(this, "project_with_mixed_module");

    ProcessResult result =
        workspace.runBuckCommand(
            "project",
            "//Apps:App",
            "--experimental",
            "--config",
            "cxx.default_platform=iphonesimulator-x86_64",
            "--config",
            "apple.project_generator_index_via_compile_args=true");

    ProjectFilesystem filesystem =
        TestProjectFilesystems.createProjectFilesystem(workspace.getDestPath());

    BuildTarget swiftTarget = BuildTargetFactory.newInstance("//Libraries:Primary");
    BuildTarget swiftDepTarget = BuildTargetFactory.newInstance("//Libraries:DepA");
    BuildTarget objCTarget = BuildTargetFactory.newInstance("//Libraries:ExternalHeaders");

    BuildTarget swiftVfsOverlayTarget =
        swiftTarget.withFlavors(
            SWIFT_UNDERLYING_VFS_OVERLAY_FLAVOR, InternalFlavor.of("iphonesimulator-x86_64"));

    BuildTarget swiftCompileTarget =
        swiftTarget.withFlavors(
            InternalFlavor.of("swift-compile"), InternalFlavor.of("iphonesimulator-x86_64"));
    BuildTarget swiftDepCompileTarget =
        swiftDepTarget.withFlavors(
            InternalFlavor.of("swift-compile"), InternalFlavor.of("iphonesimulator-x86_64"));
    BuildTarget swiftDepMixedModuleCompileTarget =
        swiftDepTarget.withFlavors(
            SWIFT_COMPILE_FLAVOR, InternalFlavor.of("iphonesimulator-x86_64"));

    BuildTarget swiftExportedHeadersTarget = getExportedHeaderTarget(swiftTarget);
    BuildTarget swiftDepExportedHeadersTarget = getExportedHeaderTarget(swiftDepTarget);
    BuildTarget objCExportedHeadersTarget = getExportedHeaderTarget(objCTarget);

    List<String> swiftArgs = getSwiftIndexingArgs("Primary", workspace, filesystem, swiftTarget);

    List<String> expectedArgs =
        ImmutableList.of(
            "-import-underlying-module",
            "-I",
            getAbsolutePathString(swiftDepMixedModuleCompileTarget, filesystem),
            "-I",
            getAbsolutePathString(swiftDepCompileTarget, filesystem),
            "-I",
            getAbsolutePathString(swiftCompileTarget, filesystem),
            "-Xcc",
            "-ivfsoverlay",
            "-Xcc",
            getAbsolutePathString(swiftVfsOverlayTarget, filesystem)
                + "/unextended-module-overlay.yaml",
            "-Xcc",
            "-I",
            "-Xcc",
            getAbsolutePathString(swiftExportedHeadersTarget, filesystem),
            "-Xcc",
            "-I",
            "-Xcc",
            getAbsolutePathString(swiftDepExportedHeadersTarget, filesystem),
            "-Xcc",
            "-I",
            "-Xcc",
            getAbsolutePathString(swiftDepCompileTarget, filesystem),
            "-Xcc",
            "-I",
            "-Xcc",
            getAbsolutePathString(objCExportedHeadersTarget, filesystem),
            "-Xcc",
            "-I",
            "-Xcc",
            getAbsolutePathString(swiftCompileTarget, filesystem),
            "-module-name",
            "Primary");

    // assert we're setting the correct Swift flags to handle mixed modules
    assertEquals(swiftArgs, expectedArgs);

    result.assertSuccess();
  }

  private List<String> getSwiftIndexingArgs(
      String targetName,
      ProjectWorkspace workspace,
      ProjectFilesystem filesystem,
      BuildTarget swiftTarget)
      throws IOException {
    Path swiftTargetPath = workspace.getPath(getTargetOutputPath(swiftTarget, filesystem));

    Path swiftTargetXCconfigPath =
        swiftTargetPath.getParent().resolve(targetName + "-Debug.xcconfig");
    List<String> configs = filesystem.readLines(swiftTargetXCconfigPath);
    String swiftPath =
        configs.stream().filter(config -> config.startsWith("OTHER_SWIFT_FLAGS")).findFirst().get();

    Pattern responseFileRegex = Pattern.compile("OTHER_SWIFT_FLAGS = (.*)");
    Matcher m = responseFileRegex.matcher(swiftPath);
    assertTrue(m.find());
    List<String> Args = Arrays.asList(m.group(1).split("\\s+"));
    return Args;
  }

  private String getAbsolutePathString(BuildTarget buildTarget, ProjectFilesystem filesystem) {
    return filesystem.resolve(getTargetOutputPath(buildTarget, filesystem)).toString();
  }

  private RelPath getTargetOutputPath(BuildTarget target, ProjectFilesystem fileSystem) {
    return BuildTargetPaths.getGenPath(fileSystem.getBuckPaths(), target, "%s");
  }

  private BuildTarget getExportedHeaderTarget(BuildTarget rawTarget) {
    return rawTarget.withAppendedFlavors(
        CxxLibraryDescription.Type.EXPORTED_HEADERS.getFlavor(),
        InternalFlavor.of("iphonesimulator-x86_64"),
        HeaderMode.SYMLINK_TREE_WITH_MODULEMAP.getFlavor());
  }

  private void runXcodebuild(ProjectWorkspace workspace, String workspacePath, String schemeName)
      throws IOException, InterruptedException {
    ProcessExecutor.Result processResult =
        workspace.runCommand(
            "xcodebuild",

            // "json" output.
            "-json",

            // Make sure the output stays in the temp folder.
            "-derivedDataPath",
            "xcode-out/",

            // Build the project that we just generated
            "-workspace",
            workspacePath,
            "-scheme",
            schemeName,

            // Build for iphonesimulator
            "-arch",
            "x86_64",
            "-sdk",
            "iphonesimulator");
    processResult.getStderr().ifPresent(System.err::print);
    assertEquals("xcodebuild should succeed", 0, processResult.getExitCode());
  }

  private ProjectWorkspace createWorkspace(Object testCase, String scenario) throws IOException {
    ProjectWorkspace workspace =
        TestDataHelper.createProjectWorkspaceForScenario(testCase, scenario, temporaryFolder);
    workspace.setUp();

    ProjectFilesystem projectFilesystem = workspace.getProjectFileSystem();
    Path buildScriptPath = AppleProjectHelper.getBuildScriptPath(projectFilesystem);

    TestDataHelper.overrideBuckconfig(
        workspace,
        ImmutableMap.of(
            AppleConfig.APPLE_SECTION,
            ImmutableMap.of(AppleConfig.BUILD_SCRIPT, buildScriptPath.toString())));

    return workspace;
  }
}

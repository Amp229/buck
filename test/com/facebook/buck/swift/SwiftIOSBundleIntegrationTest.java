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

package com.facebook.buck.swift;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeThat;
import static org.junit.Assume.assumeTrue;

import com.facebook.buck.apple.AppleDescriptions;
import com.facebook.buck.apple.AppleNativeIntegrationTestUtils;
import com.facebook.buck.apple.toolchain.ApplePlatform;
import com.facebook.buck.core.filesystems.AbsPath;
import com.facebook.buck.core.filesystems.RelPath;
import com.facebook.buck.core.model.BuildTarget;
import com.facebook.buck.core.model.BuildTargetFactory;
import com.facebook.buck.core.model.InternalFlavor;
import com.facebook.buck.core.model.impl.BuildTargetPaths;
import com.facebook.buck.io.filesystem.ProjectFilesystem;
import com.facebook.buck.io.filesystem.TestProjectFilesystems;
import com.facebook.buck.testutil.ProcessResult;
import com.facebook.buck.testutil.TemporaryPaths;
import com.facebook.buck.testutil.integration.ProjectWorkspace;
import com.facebook.buck.testutil.integration.TestDataHelper;
import com.facebook.buck.util.environment.Platform;
import java.nio.file.Files;
import java.nio.file.Path;
import org.hamcrest.CoreMatchers;
import org.junit.Rule;
import org.junit.Test;

public class SwiftIOSBundleIntegrationTest {
  @Rule public TemporaryPaths tmp = new TemporaryPaths();

  @Test
  public void simpleApplicationBundle() throws Exception {
    assumeThat(
        AppleNativeIntegrationTestUtils.isSwiftAvailable(ApplePlatform.IPHONESIMULATOR), is(true));
    ProjectWorkspace workspace =
        TestDataHelper.createProjectWorkspaceForScenario(
            this, "simple_swift_application_bundle", tmp);
    workspace.setUp();
    ProjectFilesystem filesystem =
        TestProjectFilesystems.createProjectFilesystem(workspace.getDestPath());

    BuildTarget target = workspace.newBuildTarget("//:DemoApp#iphonesimulator-x86_64,no-debug");
    workspace.runBuckCommand("build", target.getFullyQualifiedName()).assertSuccess();

    workspace.verify(
        RelPath.get("DemoApp_output.expected"),
        BuildTargetPaths.getGenPath(
            filesystem.getBuckPaths(),
            target.withAppendedFlavors(AppleDescriptions.NO_INCLUDE_FRAMEWORKS_FLAVOR),
            "%s"));

    Path appPath =
        workspace.getPath(
            BuildTargetPaths.getGenPath(
                    filesystem.getBuckPaths(),
                    target.withAppendedFlavors(AppleDescriptions.NO_INCLUDE_FRAMEWORKS_FLAVOR),
                    "%s")
                .resolve(target.getShortName() + ".app"));
    assertTrue(Files.exists(appPath.resolve(target.getShortName())));
  }

  @Test
  public void swiftStdLibsContainConcurrency() throws Exception {
    assumeThat(
        AppleNativeIntegrationTestUtils.isSwiftAvailable(ApplePlatform.IPHONESIMULATOR), is(true));
    ProjectWorkspace workspace =
        TestDataHelper.createProjectWorkspaceForScenario(
            this, "swift_concurrency_application", tmp);
    workspace.setUp();
    ProjectFilesystem filesystem =
        TestProjectFilesystems.createProjectFilesystem(workspace.getDestPath());

    BuildTarget target = workspace.newBuildTarget("//:DemoApp#iphonesimulator-x86_64,no-debug");
    workspace.runBuckCommand("build", target.getFullyQualifiedName()).assertSuccess();

    Path frameworksPath =
        workspace.getPath(
            BuildTargetPaths.getGenPath(
                    filesystem.getBuckPaths(),
                    target.withAppendedFlavors(AppleDescriptions.NO_INCLUDE_FRAMEWORKS_FLAVOR),
                    "%s")
                .resolve(target.getShortName() + ".app")
                .resolve("Frameworks"));
    assertTrue(Files.exists(frameworksPath.resolve("libswift_Concurrency.dylib")));
  }

  @Test
  public void swiftWithSwiftDependenciesBuildsSomething() throws Exception {
    assumeThat(
        AppleNativeIntegrationTestUtils.isSwiftAvailable(ApplePlatform.IPHONESIMULATOR), is(true));
    ProjectWorkspace workspace =
        TestDataHelper.createProjectWorkspaceForScenario(this, "swift_on_swift", tmp);
    workspace.setUp();
    ProjectFilesystem filesystem =
        TestProjectFilesystems.createProjectFilesystem(workspace.getDestPath());

    BuildTarget target = workspace.newBuildTarget("//:parent");
    workspace.runBuckCommand("build", target.getFullyQualifiedName()).assertSuccess();

    target = workspace.newBuildTarget("//:libparent");
    workspace.runBuckCommand("build", target.getFullyQualifiedName()).assertSuccess();

    target = workspace.newBuildTarget("//:ios_sos#iphonesimulator-x86_64,no-debug");
    workspace.runBuckCommand("build", target.getFullyQualifiedName()).assertSuccess();

    Path appPath =
        workspace.getPath(
            BuildTargetPaths.getGenPath(
                    filesystem.getBuckPaths(),
                    target.withAppendedFlavors(AppleDescriptions.NO_INCLUDE_FRAMEWORKS_FLAVOR),
                    "%s")
                .resolve(target.getShortName() + ".app"));
    assertTrue(Files.exists(appPath.resolve(target.getShortName())));
  }

  @Test
  public void swiftLibraryWhenLinkStyleIsNotSharedDoesNotProduceDylib() throws Exception {
    assumeThat(Platform.detect(), is(Platform.MACOS));
    assumeTrue(AppleNativeIntegrationTestUtils.isApplePlatformAvailable(ApplePlatform.MACOSX));

    ProjectWorkspace workspace =
        TestDataHelper.createProjectWorkspaceForScenario(this, "swift_on_swift", tmp);
    workspace.setUp();
    ProjectFilesystem filesystem =
        TestProjectFilesystems.createProjectFilesystem(workspace.getDestPath());

    BuildTarget parentDynamicTarget =
        BuildTargetFactory.newInstance("//:ios_parent_dynamic")
            .withAppendedFlavors(InternalFlavor.of("iphonesimulator-x86_64"));

    ProcessResult result =
        workspace.runBuckCommand(
            "build", parentDynamicTarget.getFullyQualifiedName(), "--config", "cxx.cflags=-g");
    result.assertSuccess();

    Path binaryOutput =
        workspace.resolve(
            BuildTargetPaths.getGenPath(filesystem.getBuckPaths(), parentDynamicTarget, "%s"));
    assertThat(Files.exists(binaryOutput), CoreMatchers.is(true));

    assertThat(
        workspace.runCommand("file", binaryOutput.toString()).getStdout().get(),
        containsString("executable"));
    assertThat(
        workspace.runCommand("otool", "-hv", binaryOutput.toString()).getStdout().get(),
        containsString("X86_64"));
    assertThat(
        workspace.runCommand("otool", "-L", binaryOutput.toString()).getStdout().get(),
        not(containsString("libdep1.dylib")));

    AbsPath dep1Output =
        tmp.getRoot()
            .resolve(filesystem.getBuckPaths().getGenDir())
            .resolve("iosdep1#iphonesimulator-x86_64,swift-compile")
            .resolve("libiosdep1.dylib");
    assertThat(Files.notExists(dep1Output.getPath()), CoreMatchers.is(true));
  }

  @Test
  public void swiftLibraryWhenLinkStyleIsSharedShouldProduceDylib() throws Exception {
    assumeThat(Platform.detect(), is(Platform.MACOS));
    assumeTrue(AppleNativeIntegrationTestUtils.isApplePlatformAvailable(ApplePlatform.MACOSX));

    ProjectWorkspace workspace =
        TestDataHelper.createProjectWorkspaceForScenario(this, "swift_on_swift", tmp);
    workspace.setUp();
    ProjectFilesystem filesystem =
        TestProjectFilesystems.createProjectFilesystem(workspace.getDestPath());

    BuildTarget parentDynamicTarget =
        BuildTargetFactory.newInstance("//:ios_parent_dynamic")
            .withAppendedFlavors(InternalFlavor.of("iphonesimulator-x86_64"));

    ProcessResult result =
        workspace.runBuckCommand(
            "build", parentDynamicTarget.getFullyQualifiedName(), "--config", "cxx.cflags=-g");
    result.assertSuccess();

    Path binaryOutput =
        workspace.resolve(
            BuildTargetPaths.getGenPath(filesystem.getBuckPaths(), parentDynamicTarget, "%s"));
    assertThat(Files.exists(binaryOutput), CoreMatchers.is(true));

    assertThat(
        workspace.runCommand("file", binaryOutput.toString()).getStdout().get(),
        containsString("executable"));
    assertThat(
        workspace.runCommand("otool", "-hv", binaryOutput.toString()).getStdout().get(),
        containsString("X86_64"));
    assertThat(
        workspace.runCommand("otool", "-L", binaryOutput.toString()).getStdout().get(),
        containsString("libiosdep1.dylib"));

    AbsPath parentOutput =
        tmp.getRoot()
            .resolve(
                BuildTargetPaths.getGenPath(
                    filesystem.getBuckPaths(),
                    BuildTargetFactory.newInstance(
                        "//:ios_parent_dynamic#iphonesimulator-x86_64,swift-compile"),
                    "%s"))
            .resolve("ios_parent_dynamic.swiftmodule");
    assertThat(Files.exists(parentOutput.getPath()), CoreMatchers.is(true));

    BuildTarget iosdep1Target =
        BuildTargetFactory.newInstance("//:iosdep1")
            .withAppendedFlavors(InternalFlavor.of("iphonesimulator-x86_64"));
    Path iosdep1TargetOutput =
        workspace.resolve(
            BuildTargetPaths.getGenPath(filesystem.getBuckPaths(), iosdep1Target, "%s"));
    assertThat(
        Files.exists(iosdep1TargetOutput.resolve("libiosdep1.dylib")), CoreMatchers.is(true));
  }

  @Test
  public void testSwiftSharedLibraryCustomSoname() throws Exception {
    assumeThat(Platform.detect(), is(Platform.MACOS));
    assumeTrue(AppleNativeIntegrationTestUtils.isApplePlatformAvailable(ApplePlatform.MACOSX));

    ProjectWorkspace workspace =
        TestDataHelper.createProjectWorkspaceForScenario(this, "swift_on_swift", tmp);
    workspace.setUp();
    ProjectFilesystem filesystem =
        TestProjectFilesystems.createProjectFilesystem(workspace.getDestPath());

    ProcessResult result =
        workspace.runBuckCommand(
            "build", ":dep1_soname#iphonesimulator-x86_64,shared", "--config", "cxx.cflags=-g");
    result.assertSuccess();

    AbsPath binaryOutput =
        tmp.getRoot()
            .resolve(
                BuildTargetPaths.getGenPath(
                    filesystem.getBuckPaths(),
                    BuildTargetFactory.newInstance("//:dep1_soname#iphonesimulator-x86_64"),
                    "%s"))
            .resolve("custom-soname");
    assertThat(Files.exists(binaryOutput.getPath()), CoreMatchers.is(true));

    assertThat(
        workspace.runCommand("file", binaryOutput.toString()).getStdout().get(),
        containsString("shared library"));
    assertThat(
        workspace.runCommand("otool", "-hv", binaryOutput.toString()).getStdout().get(),
        containsString("X86_64"));
    assertThat(
        workspace.runCommand("otool", "-L", binaryOutput.toString()).getStdout().get(),
        containsString("@rpath/custom-soname"));
    assertThat(
        workspace.runCommand("otool", "-L", binaryOutput.toString()).getStdout().get(),
        not(containsString("@rpath/dep1-soname")));
  }

  @Test
  public void testSwiftPreferredLinkage() throws Exception {
    assumeThat(Platform.detect(), is(Platform.MACOS));
    assumeTrue(AppleNativeIntegrationTestUtils.isApplePlatformAvailable(ApplePlatform.MACOSX));

    ProjectWorkspace workspace =
        TestDataHelper.createProjectWorkspaceForScenario(this, "swift_on_swift", tmp);
    workspace.setUp();
    ProjectFilesystem filesystem =
        TestProjectFilesystems.createProjectFilesystem(workspace.getDestPath());

    workspace.replaceFileContents(
        "BUCK",
        "preferred_linkage = \"any\",  # iosdep1 preferred_linkage anchor",
        "preferred_linkage = \"static\"");

    ProcessResult result =
        workspace.runBuckCommand(
            "build", ":ios_parent_dynamic#iphonesimulator-x86_64", "--config", "cxx.cflags=-g");
    result.assertSuccess();

    AbsPath binaryOutput =
        tmp.getRoot()
            .resolve(
                BuildTargetPaths.getGenPath(
                    filesystem.getBuckPaths(),
                    BuildTargetFactory.newInstance("//:ios_parent_dynamic#iphonesimulator-x86_64"),
                    "%s"));
    assertThat(Files.exists(binaryOutput.getPath()), CoreMatchers.is(true));

    AbsPath dep1Output =
        tmp.getRoot()
            .resolve(filesystem.getBuckPaths().getGenDir())
            .resolve("iosdep1#iphonesimulator-x86_64")
            .resolve("libiosdep1.dylib");
    assertThat(Files.exists(dep1Output.getPath()), CoreMatchers.is(false));

    assertThat(
        workspace.runCommand("otool", "-L", binaryOutput.toString()).getStdout().get(),
        not(containsString("libiosdep1.dylib")));

    assertThat(
        workspace.runCommand("nm", binaryOutput.toString()).getStdout().orElse(""),
        containsString("baz"));
  }

  @Test
  public void swiftDependsOnObjCRunsAndPrintsMessage() throws Exception {
    assumeThat(
        AppleNativeIntegrationTestUtils.isSwiftAvailable(ApplePlatform.IPHONESIMULATOR), is(true));
    ProjectWorkspace workspace =
        TestDataHelper.createProjectWorkspaceForScenario(this, "swift_on_objc", tmp);
    workspace.setUp();
    ProjectFilesystem filesystem =
        TestProjectFilesystems.createProjectFilesystem(workspace.getDestPath());

    BuildTarget target = workspace.newBuildTarget("//:binary#iphonesimulator-x86_64");
    workspace.runBuckCommand("build", target.getFullyQualifiedName()).assertSuccess();

    target = workspace.newBuildTarget("//:bundle#iphonesimulator-x86_64,no-debug");
    workspace.runBuckCommand("build", target.getFullyQualifiedName()).assertSuccess();

    Path appPath =
        workspace.getPath(
            BuildTargetPaths.getGenPath(
                    filesystem.getBuckPaths(),
                    target.withAppendedFlavors(AppleDescriptions.NO_INCLUDE_FRAMEWORKS_FLAVOR),
                    "%s")
                .resolve(target.getShortName() + ".app"));
    assertTrue(Files.exists(appPath.resolve(target.getShortName())));
  }
}

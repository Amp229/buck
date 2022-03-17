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

import static com.facebook.buck.testutil.RegexMatcher.containsPattern;
import static com.facebook.buck.testutil.RegexMatcher.containsRegex;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.android.apksig.ApkVerifier;
import com.facebook.buck.android.toolchain.AndroidBuildToolsLocation;
import com.facebook.buck.android.toolchain.AndroidSdkLocation;
import com.facebook.buck.android.toolchain.TestAndroidSdkLocationFactory;
import com.facebook.buck.android.toolchain.impl.AndroidBuildToolsResolver;
import com.facebook.buck.core.config.FakeBuckConfig;
import com.facebook.buck.core.filesystems.RelPath;
import com.facebook.buck.core.model.BuildTarget;
import com.facebook.buck.core.model.BuildTargetFactory;
import com.facebook.buck.core.model.impl.BuildTargetPaths;
import com.facebook.buck.io.filesystem.ProjectFilesystem;
import com.facebook.buck.jvm.java.testutil.AbiCompilationModeTest;
import com.facebook.buck.testutil.ProcessResult;
import com.facebook.buck.testutil.TemporaryPaths;
import com.facebook.buck.testutil.integration.BuckBuildLog;
import com.facebook.buck.testutil.integration.DexInspector;
import com.facebook.buck.testutil.integration.ProjectWorkspace;
import com.facebook.buck.testutil.integration.ZipInspector;
import com.facebook.buck.util.ProcessExecutor;
import com.facebook.buck.util.environment.Platform;
import com.facebook.buck.util.zip.ZipConstants;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.hash.HashCode;
import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;
import org.apache.commons.compress.archivers.zip.ZipUtil;
import org.hamcrest.CoreMatchers;
import org.hamcrest.Matchers;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.tukaani.xz.XZInputStream;

public class AndroidApkIntegrationTest extends AbiCompilationModeTest {

  @Rule public TemporaryPaths tmpFolder = new TemporaryPaths();

  private ProjectWorkspace workspace;

  private ProjectFilesystem filesystem;

  private static final String SIMPLE_TARGET = "//apps/multidex:app";
  private static final String RES_D8_TARGET = "//apps/multidex:app_with_resources_and_d8";
  private static final String RES_GROUPS_TARGET = "//apps/multidex:app_with_resources_and_groups";
  private static final String RAW_DEX_TARGET = "//apps/multidex:app-art";

  @Before
  public void setUp() throws IOException {
    workspace =
        AndroidProjectWorkspace.create(
            new AndroidApkIntegrationTest(), "android_project", tmpFolder);
    workspace.setUp();
    AssumeAndroidPlatform.get(workspace).assumeSdkIsAvailable();
    AssumeAndroidPlatform.get(workspace).assumeNdkIsAvailable();
    setWorkspaceCompilationMode(workspace);
    filesystem = workspace.getProjectFileSystem();
  }

  @Test
  public void testChangeAndroidSdkWillInvalidateRuleKey() throws IOException {
    workspace
        .runBuckBuild(SIMPLE_TARGET, "-c", "android.compile_sdk_version=android-28")
        .assertSuccess();
    workspace.getBuildLog().assertTargetBuiltLocally(SIMPLE_TARGET);
    workspace
        .runBuckBuild(SIMPLE_TARGET, "-c", "android.compile_sdk_version=android-27")
        .assertSuccess();
    workspace.getBuildLog().assertTargetBuiltLocally(SIMPLE_TARGET);
  }

  @Test
  public void testNonExopackageHasSecondary() throws IOException {
    workspace.runBuckBuild(SIMPLE_TARGET).assertSuccess();

    Path apkPath =
        workspace.getPath(
            BuildTargetPaths.getGenPath(
                filesystem.getBuckPaths(),
                BuildTargetFactory.newInstance(SIMPLE_TARGET),
                "%s.apk"));

    ZipInspector zipInspector = new ZipInspector(apkPath);

    zipInspector.assertFileExists("assets/secondary-program-dex-jars/metadata.txt");
    zipInspector.assertFileExists("assets/secondary-program-dex-jars/secondary-1.dex.jar");
    zipInspector.assertFileDoesNotExist("classes2.dex");

    zipInspector.assertFileExists("classes.dex");
    if (AssumeAndroidPlatform.get(workspace).isArmAvailable()) {
      zipInspector.assertFileExists("lib/armeabi/libnative_cxx_lib.so");
    }
    zipInspector.assertFileExists("lib/armeabi-v7a/libnative_cxx_lib.so");
    zipInspector.assertFileExists("lib/x86/libnative_cxx_lib.so");

    List<String> metadata =
        zipInspector.getFileContentsLines("assets/secondary-program-dex-jars/metadata.txt");
    assertEquals(metadata.get(0), ".id dex");
    DexTestUtils.validateMetadata(apkPath);
  }

  @Test
  public void testProguardBuild() throws IOException {
    String target = "//apps/multidex:app_with_proguard";
    workspace.runBuckCommand("build", target).assertSuccess();

    Path apkPath = workspace.buildAndReturnOutput(target);
    ZipInspector zipInspector = new ZipInspector(apkPath);

    zipInspector.assertFileExists("assets/secondary-program-dex-jars/metadata.txt");
    zipInspector.assertFileExists("assets/secondary-program-dex-jars/secondary-1.dex.jar");
    zipInspector.assertFileDoesNotExist("classes2.dex");

    zipInspector.assertFileExists("classes.dex");
    if (AssumeAndroidPlatform.get(workspace).isArmAvailable()) {
      zipInspector.assertFileExists("lib/armeabi/libnative_cxx_lib.so");
    }
    zipInspector.assertFileExists("lib/armeabi-v7a/libnative_cxx_lib.so");
    zipInspector.assertFileExists("lib/x86/libnative_cxx_lib.so");
    DexTestUtils.validateMetadata(apkPath);
  }

  @Test
  public void testRawSplitDexHasSecondary() throws IOException {
    ProcessResult result = workspace.runBuckCommand("build", RAW_DEX_TARGET);
    result.assertSuccess();

    Path apkPath =
        workspace.getPath(
            BuildTargetPaths.getGenPath(
                filesystem.getBuckPaths(),
                BuildTargetFactory.newInstance(RAW_DEX_TARGET),
                "%s.apk"));
    ZipInspector zipInspector = new ZipInspector(apkPath);
    zipInspector.assertFileDoesNotExist("assets/secondary-program-dex-jars/metadata.txt");

    zipInspector.assertFileDoesNotExist("assets/secondary-program-dex-jars/secondary-1.dex.jar");
    zipInspector.assertFileExists("classes2.dex");

    zipInspector.assertFileExists("classes.dex");
    if (AssumeAndroidPlatform.get(workspace).isArmAvailable()) {
      zipInspector.assertFileExists("lib/armeabi/libnative_cxx_lib.so");
    }
    zipInspector.assertFileExists("lib/armeabi-v7a/libnative_cxx_lib.so");
    zipInspector.assertFileExists("lib/x86/libnative_cxx_lib.so");
  }

  @Test
  public void testDisguisedExecutableIsRenamedWithNDKPrior17() throws IOException {
    AssumeAndroidPlatform.get(workspace).assumeArmIsAvailable();
    Path output = workspace.buildAndReturnOutput("//apps/sample:app_with_disguised_exe-16");
    ZipInspector zipInspector = new ZipInspector(output);
    zipInspector.assertFileExists("lib/armeabi/libmybinary.so");
    zipInspector.assertFileExists("lib/armeabi-v7a/libmybinary.so");
    zipInspector.assertFileExists("lib/x86/libmybinary.so");
  }

  @Test
  public void testDisguisedExecutableIsRenamed() throws IOException {
    Path output = workspace.buildAndReturnOutput("//apps/sample:app_with_disguised_exe");
    ZipInspector zipInspector = new ZipInspector(output);
    zipInspector.assertFileExists("lib/armeabi-v7a/libmybinary.so");
    zipInspector.assertFileExists("lib/x86/libmybinary.so");
  }

  @Test
  public void testNdkLibraryIsIncludedWithNdkPrior17() throws IOException {
    AssumeAndroidPlatform.get(workspace).assumeArmIsAvailable();
    Path output = workspace.buildAndReturnOutput("//apps/sample:app_with_ndk_library-16");
    ZipInspector zipInspector = new ZipInspector(output);
    zipInspector.assertFileExists("lib/armeabi/libfakenative.so");
    zipInspector.assertFileExists("lib/armeabi-v7a/libfakenative.so");
    zipInspector.assertFileExists("lib/mips/libfakenative.so");
    zipInspector.assertFileExists("lib/x86/libfakenative.so");
  }

  @Test
  public void testNdkLibraryIsIncluded() throws IOException {
    Path output = workspace.buildAndReturnOutput("//apps/sample:app_with_ndk_library");
    ZipInspector zipInspector = new ZipInspector(output);
    zipInspector.assertFileExists("lib/armeabi-v7a/libfakenative.so");
    zipInspector.assertFileExists("lib/x86/libfakenative.so");
  }

  @Test
  public void testEditingNdkLibraryForcesRebuild() throws IOException, InterruptedException {
    String apkWithNdkLibrary = "//apps/sample:app_with_ndk_library";
    Path output = workspace.buildAndReturnOutput(apkWithNdkLibrary);
    ZipInspector zipInspector = new ZipInspector(output);
    zipInspector.assertFileExists("lib/armeabi-v7a/libfakenative.so");

    // Sleep 1 second (plus another half to be super duper safe) to make sure that
    // fakesystem.c gets a later timestamp than the fakesystem.o that was produced
    // during the build in setUp.  If we don't do this, there's a chance that the
    // ndk-build we run during the upcoming build will not rebuild it (on filesystems
    // that have 1-second granularity for last modified).
    // To verify this, create a Makefile with the following rule (don't forget to use a tab):
    // out: in
    //   cat $< > $@
    // Run: echo foo > in ; make ; cat out ; echo bar > in ; make ; cat out
    // On a filesystem with 1-second mtime granularity, the last "cat" should print "foo"
    // (with very high probability).
    Thread.sleep(1500);
    workspace.replaceFileContents(
        "native/fakenative/jni/fakesystem.c", "exit(status)", "exit(1+status)");

    workspace.resetBuildLogFile();
    workspace.buildAndReturnOutput(apkWithNdkLibrary);
    workspace.getBuildLog().assertTargetBuiltLocally(apkWithNdkLibrary);
  }

  @Test
  public void testEditingPrimaryDexClassForcesRebuildForSimplePackage() throws IOException {
    BuildTarget buildTarget = BuildTargetFactory.newInstance(SIMPLE_TARGET);
    RelPath outputPath =
        BuildTargetPaths.getGenPath(filesystem.getBuckPaths(), buildTarget, "%s.apk");
    HashFunction hashFunction = Hashing.sha1();
    String dexFileName = "classes.dex";

    workspace.runBuckCommand("build", SIMPLE_TARGET).assertSuccess();
    ZipInspector zipInspector = new ZipInspector(workspace.getPath(outputPath));
    HashCode originalHash = hashFunction.hashBytes(zipInspector.getFileContents(dexFileName));

    workspace.replaceFileContents(
        "java/com/sample/app/MyApplication.java", "MyReplaceableName", "ChangedValue");

    workspace.resetBuildLogFile();
    ProcessResult result = workspace.runBuckCommand("build", SIMPLE_TARGET);
    result.assertSuccess();
    BuckBuildLog buildLog = workspace.getBuildLog();
    buildLog.assertTargetBuiltLocally(SIMPLE_TARGET);

    HashCode hashAfterChange = hashFunction.hashBytes(zipInspector.getFileContents(dexFileName));
    assertThat(
        "MyApplication.java file has been edited. Final artifact hash must change as well",
        originalHash,
        is(not(equalTo(hashAfterChange))));
  }

  @Test
  public void testEditingSecondaryDexClassForcesRebuildForSimplePackage() throws IOException {
    workspace.runBuckBuild(SIMPLE_TARGET).assertSuccess();

    workspace.replaceFileContents("java/com/sample/lib/Sample.java", "package com", "package\ncom");

    workspace.resetBuildLogFile();
    ProcessResult result = workspace.runBuckCommand("build", SIMPLE_TARGET);
    result.assertSuccess();
    BuckBuildLog buildLog = workspace.getBuildLog();

    buildLog.assertTargetBuiltLocally(SIMPLE_TARGET);
  }

  @Test
  public void testPrimaryDexOnlyIncludesSpecifiedClasses() throws IOException {
    Path apkPath =
        workspace.buildAndReturnOutput("//apps/multidex:app_with_sample_class_in_primary");

    String primaryDex = "classes.dex";
    String standardSecondaryDex = "assets/secondary-program-dex-jars/secondary-1_1.dex.jar";
    String secondaryDexFromPrimaryDexRejects =
        "assets/secondary-program-dex-jars/secondary-3_1.dex.jar";

    ZipInspector zipInspector = new ZipInspector(apkPath);
    zipInspector.assertFileExists(primaryDex);
    zipInspector.assertFileExists(standardSecondaryDex);
    zipInspector.assertFileExists(secondaryDexFromPrimaryDexRejects);

    DexInspector primaryDexInspector = new DexInspector(apkPath, primaryDex);
    primaryDexInspector.assertTypeExists("Lcom/facebook/sample/Sample;");
    primaryDexInspector.assertTypeDoesNotExist("Lcom/facebook/sample/Sample2;");
    primaryDexInspector.assertTypeDoesNotExist("Lcom/facebook/sample/Sample3;");

    DexInspector standardSecondaryDexInspector = new DexInspector(apkPath, standardSecondaryDex);
    standardSecondaryDexInspector.assertTypeExists("Lcom/facebook/sample/Small;");

    DexInspector secondaryDexFromPrimaryDexRejectsInspector =
        new DexInspector(apkPath, secondaryDexFromPrimaryDexRejects);
    secondaryDexFromPrimaryDexRejectsInspector.assertTypeDoesNotExist(
        "Lcom/facebook/sample/Sample;");
    secondaryDexFromPrimaryDexRejectsInspector.assertTypeExists("Lcom/facebook/sample/Sample2;");
    secondaryDexFromPrimaryDexRejectsInspector.assertTypeExists("Lcom/facebook/sample/Sample3;");

    DexTestUtils.validateMetadata(apkPath, ImmutableSet.of());

    // Build again with a different primary dex pattern
    workspace.replaceFileContents(
        "apps/multidex/BUCK", "# ADDED_IN_PRIMARY_CLASS_NAMES_TEST", "\"/Sample2^\"");
    Path newApkPath =
        workspace.buildAndReturnOutput("//apps/multidex:app_with_sample_class_in_primary");

    String newPrimaryDex = "classes.dex";
    String newStandardSecondaryDex = "assets/secondary-program-dex-jars/secondary-1_1.dex.jar";
    String newSecondaryDexFromPrimaryDexRejects =
        "assets/secondary-program-dex-jars/secondary-3_1.dex.jar";

    ZipInspector newZipInspector = new ZipInspector(newApkPath);
    newZipInspector.assertFileExists(newPrimaryDex);
    newZipInspector.assertFileExists(newStandardSecondaryDex);
    newZipInspector.assertFileExists(newSecondaryDexFromPrimaryDexRejects);

    DexInspector newPrimaryDexInspector = new DexInspector(newApkPath, newPrimaryDex);
    newPrimaryDexInspector.assertTypeExists("Lcom/facebook/sample/Sample;");
    newPrimaryDexInspector.assertTypeExists("Lcom/facebook/sample/Sample2;");
    newPrimaryDexInspector.assertTypeDoesNotExist("Lcom/facebook/sample/Sample3;");

    DexInspector newStandardSecondaryDexInspector =
        new DexInspector(newApkPath, newStandardSecondaryDex);
    newStandardSecondaryDexInspector.assertTypeExists("Lcom/facebook/sample/Small;");

    DexInspector newSecondaryDexInspector =
        new DexInspector(newApkPath, newSecondaryDexFromPrimaryDexRejects);
    newSecondaryDexInspector.assertTypeDoesNotExist("Lcom/facebook/sample/Sample;");
    newSecondaryDexInspector.assertTypeDoesNotExist("Lcom/facebook/sample/Sample2;");
    newSecondaryDexInspector.assertTypeExists("Lcom/facebook/sample/Sample3;");

    DexTestUtils.validateMetadata(newApkPath, ImmutableSet.of());
  }

  @Test
  public void testPrimaryDexOnlyIncludesSpecifiedClassesWithRawCompression() throws IOException {
    Path apkPath =
        workspace.buildAndReturnOutput(
            "//apps/multidex:app_with_sample_class_in_primary_and_raw_compression");

    String primaryDex = "classes.dex";
    String standardSecondaryDex = "classes2.dex";
    String secondaryDexFromPrimaryDexRejects = "classes3.dex";

    ZipInspector zipInspector = new ZipInspector(apkPath);
    zipInspector.assertFileExists(primaryDex);
    zipInspector.assertFileExists(standardSecondaryDex);
    zipInspector.assertFileExists(secondaryDexFromPrimaryDexRejects);

    DexInspector primaryDexInspector = new DexInspector(apkPath, primaryDex);
    primaryDexInspector.assertTypeExists("Lcom/facebook/sample/Sample;");
    primaryDexInspector.assertTypeDoesNotExist("Lcom/facebook/sample/Sample2;");
    primaryDexInspector.assertTypeDoesNotExist("Lcom/facebook/sample/Sample3;");

    DexInspector standardSecondaryDexInspector = new DexInspector(apkPath, standardSecondaryDex);
    standardSecondaryDexInspector.assertTypeExists("Lcom/facebook/sample/Small;");

    DexInspector secondaryDexFromPrimaryDexRejectsInspector =
        new DexInspector(apkPath, secondaryDexFromPrimaryDexRejects);
    secondaryDexFromPrimaryDexRejectsInspector.assertTypeDoesNotExist(
        "Lcom/facebook/sample/Sample;");
    secondaryDexFromPrimaryDexRejectsInspector.assertTypeExists("Lcom/facebook/sample/Sample2;");
    secondaryDexFromPrimaryDexRejectsInspector.assertTypeExists("Lcom/facebook/sample/Sample3;");


    // Build again with a different primary dex pattern
    workspace.replaceFileContents(
        "apps/multidex/BUCK",
        "# ADDED_IN_RAW_COMPRESSION_PRIMARY_CLASS_NAMES_TEST",
        "\"/Sample2^\"");
    Path newApkPath =
        workspace.buildAndReturnOutput(
            "//apps/multidex:app_with_sample_class_in_primary_and_raw_compression");

    String newPrimaryDex = "classes.dex";
    String newStandardSecondaryDex = "classes2.dex";
    String newSecondaryDexFromPrimaryDexRejects = "classes3.dex";

    ZipInspector newZipInspector = new ZipInspector(newApkPath);
    newZipInspector.assertFileExists(newPrimaryDex);
    newZipInspector.assertFileExists(newStandardSecondaryDex);
    newZipInspector.assertFileExists(newSecondaryDexFromPrimaryDexRejects);

    DexInspector newPrimaryDexInspector = new DexInspector(newApkPath, newPrimaryDex);
    newPrimaryDexInspector.assertTypeExists("Lcom/facebook/sample/Sample;");
    newPrimaryDexInspector.assertTypeExists("Lcom/facebook/sample/Sample2;");
    newPrimaryDexInspector.assertTypeDoesNotExist("Lcom/facebook/sample/Sample3;");

    DexInspector newStandardSecondaryDexInspector =
        new DexInspector(newApkPath, newStandardSecondaryDex);
    newStandardSecondaryDexInspector.assertTypeExists("Lcom/facebook/sample/Small;");

    DexInspector newSecondaryDexInspector =
        new DexInspector(newApkPath, newSecondaryDexFromPrimaryDexRejects);
    newSecondaryDexInspector.assertTypeDoesNotExist("Lcom/facebook/sample/Sample;");
    newSecondaryDexInspector.assertTypeDoesNotExist("Lcom/facebook/sample/Sample2;");
    newSecondaryDexInspector.assertTypeExists("Lcom/facebook/sample/Sample3;");

  }

  @Test
  public void testPrimaryDexOnlyIncludesSpecifiedClassesWithXZCompression() throws IOException {
    Path apkPath =
        workspace.buildAndReturnOutput(
            "//apps/multidex:app_with_sample_class_in_primary_and_xz_compression");

    String primaryDex = "classes.dex";
    String standardSecondaryDex = "assets/secondary-program-dex-jars/secondary-1.dex.jar.xz";
    String secondaryDexFromPrimaryDexRejects =
        "assets/secondary-program-dex-jars/secondary-2.dex.jar.xz";

    ZipInspector zipInspector = new ZipInspector(apkPath);
    zipInspector.assertFileExists(primaryDex);
    zipInspector.assertFileExists(standardSecondaryDex);
    zipInspector.assertFileExists(secondaryDexFromPrimaryDexRejects);

    DexInspector primaryDexInspector = new DexInspector(apkPath, primaryDex);
    primaryDexInspector.assertTypeExists("Lcom/facebook/sample/Sample;");
    primaryDexInspector.assertTypeDoesNotExist("Lcom/facebook/sample/Sample2;");
    primaryDexInspector.assertTypeDoesNotExist("Lcom/facebook/sample/Sample3;");

    DexInspector standardSecondaryDexInspector = new DexInspector(apkPath, standardSecondaryDex);
    standardSecondaryDexInspector.assertTypeExists("Lcom/facebook/sample/Small;");

    DexInspector secondaryDexFromPrimaryDexRejectsInspector =
        new DexInspector(apkPath, secondaryDexFromPrimaryDexRejects);
    secondaryDexFromPrimaryDexRejectsInspector.assertTypeDoesNotExist(
        "Lcom/facebook/sample/Sample;");
    secondaryDexFromPrimaryDexRejectsInspector.assertTypeExists("Lcom/facebook/sample/Sample2;");
    secondaryDexFromPrimaryDexRejectsInspector.assertTypeExists("Lcom/facebook/sample/Sample3;");

    DexTestUtils.validateMetadata(apkPath);

    // Build again with a different primary dex pattern
    workspace.replaceFileContents(
        "apps/multidex/BUCK",
        "# ADDED_IN_XZ_COMPRESSION_PRIMARY_CLASS_NAMES_TEST",
        "\"/Sample2^\"");
    Path newApkPath =
        workspace.buildAndReturnOutput(
            "//apps/multidex:app_with_sample_class_in_primary_and_xz_compression");

    String newPrimaryDex = "classes.dex";
    String newStandardSecondaryDex = "assets/secondary-program-dex-jars/secondary-1.dex.jar.xz";
    String newSecondaryDexFromPrimaryDexRejects =
        "assets/secondary-program-dex-jars/secondary-2.dex.jar.xz";

    ZipInspector newZipInspector = new ZipInspector(newApkPath);
    newZipInspector.assertFileExists(newPrimaryDex);
    newZipInspector.assertFileExists(newStandardSecondaryDex);
    newZipInspector.assertFileExists(newSecondaryDexFromPrimaryDexRejects);

    DexInspector newPrimaryDexInspector = new DexInspector(newApkPath, newPrimaryDex);
    newPrimaryDexInspector.assertTypeExists("Lcom/facebook/sample/Sample;");
    newPrimaryDexInspector.assertTypeExists("Lcom/facebook/sample/Sample2;");
    newPrimaryDexInspector.assertTypeDoesNotExist("Lcom/facebook/sample/Sample3;");

    DexInspector newStandardSecondaryDexInspector =
        new DexInspector(newApkPath, newStandardSecondaryDex);
    newStandardSecondaryDexInspector.assertTypeExists("Lcom/facebook/sample/Small;");

    DexInspector newSecondaryDexInspector =
        new DexInspector(newApkPath, newSecondaryDexFromPrimaryDexRejects);
    newSecondaryDexInspector.assertTypeDoesNotExist("Lcom/facebook/sample/Sample;");
    newSecondaryDexInspector.assertTypeDoesNotExist("Lcom/facebook/sample/Sample2;");
    newSecondaryDexInspector.assertTypeExists("Lcom/facebook/sample/Sample3;");

    DexTestUtils.validateMetadata(newApkPath);
  }

  @Test
  public void testPrimaryDexOnlyIncludesSpecifiedClassesWithXZSCompression() throws IOException {
    Path apkPath =
        workspace.buildAndReturnOutput(
            "//apps/multidex:app_with_sample_class_in_primary_and_xzs_compression");

    ZipInspector zipInspector = new ZipInspector(apkPath);

    String primaryDex = "classes.dex";
    DexInspector primaryDexInspector = new DexInspector(apkPath, primaryDex);
    primaryDexInspector.assertTypeExists("Lcom/facebook/sample/Sample;");
    primaryDexInspector.assertTypeDoesNotExist("Lcom/facebook/sample/Sample2;");
    primaryDexInspector.assertTypeDoesNotExist("Lcom/facebook/sample/Sample3;");

    List<String> metadata =
        zipInspector.getFileContentsLines("assets/secondary-program-dex-jars/metadata.txt");
    List<DexTestUtils.DexMetadata> metadataFiles = DexTestUtils.moduleMetadata(metadata);
    assertEquals(2, metadataFiles.size());

    zipInspector.assertFileExists("assets/secondary-program-dex-jars/secondary.dex.jar.xzs");
    byte[] xzsBytes =
        zipInspector.getFileContents("assets/secondary-program-dex-jars/secondary.dex.jar.xzs");
    Path unpackedXzsPath =
        workspace.getPath(apkPath.getParent().resolve("unxzs/secondary.dex.jar"));
    unpackedXzsPath.getParent().toFile().mkdirs();
    Files.copy(new XZInputStream(new ByteArrayInputStream(xzsBytes)), unpackedXzsPath);

    ImmutableMap.Builder<Path, Integer> dexSizeMapBuilder = ImmutableMap.builder();
    for (DexTestUtils.DexMetadata dexMetadata : metadataFiles) {
      String xzMeta =
          zipInspector
              .getFileContentsLines(
                  "assets/secondary-program-dex-jars/" + dexMetadata.dexFile + ".meta")
              .get(0);
      int jarSize = readJarSize(xzMeta);
      dexSizeMapBuilder.put(dexMetadata.dexFile, jarSize);
    }
    ImmutableMap<Path, Integer> dexSizeMap = dexSizeMapBuilder.build();

    int totalDexSize =
        metadataFiles.stream()
            .map(dexMetadata -> dexSizeMap.get(dexMetadata.dexFile))
            .reduce(0, Integer::sum);
    assertEquals(totalDexSize, unpackedXzsPath.toFile().length());

    FileInputStream jarConcatStream = new FileInputStream(unpackedXzsPath.toFile());
    for (int i = 0; i < 2; i++) {
      DexTestUtils.DexMetadata dexMetadata = metadataFiles.get(i);
      int jarSize = dexSizeMap.get(dexMetadata.dexFile);
      byte[] dexJarContents = new byte[jarSize];
      assertEquals(jarConcatStream.read(dexJarContents, 0, jarSize), jarSize);

      Path dexJarFile =
          workspace.getPath(
              apkPath.getParent().resolve(String.format("unxzs/secondary-%s.dex.jar", i + 1)));
      Files.write(dexJarFile, dexJarContents);

      ZipInspector dexJarInspector = new ZipInspector(dexJarFile);

      dexJarInspector.assertFileExists("classes.dex");

      DexInspector dexInspector = new DexInspector(dexJarFile);
      dexInspector.assertTypeExists(dexMetadata.getJvmName());

      if (i == 0) {
        dexInspector.assertTypeExists("Lcom/facebook/sample/Small;");
        dexInspector.assertTypeDoesNotExist("Lcom/facebook/sample/Sample2;");
        dexInspector.assertTypeDoesNotExist("Lcom/facebook/sample/Sample3;");
      } else {
        dexInspector.assertTypeDoesNotExist("Lcom/facebook/sample/Small;");
        dexInspector.assertTypeExists("Lcom/facebook/sample/Sample2;");
        dexInspector.assertTypeExists("Lcom/facebook/sample/Sample3;");
      }
    }

    // Build again with a different primary dex pattern
    workspace.replaceFileContents(
        "apps/multidex/BUCK",
        "# ADDED_IN_XZS_COMPRESSION_PRIMARY_CLASS_NAMES_TEST",
        "\"/Sample2^\"");
    Path newApkPath =
        workspace.buildAndReturnOutput(
            "//apps/multidex:app_with_sample_class_in_primary_and_xzs_compression");

    DexTestUtils.validateMetadata(newApkPath);

    ZipInspector newZipInspector = new ZipInspector(apkPath);

    String newPrimaryDex = "classes.dex";
    newZipInspector.assertFileExists(newPrimaryDex);
    DexInspector newPrimaryDexInspector = new DexInspector(newApkPath, newPrimaryDex);
    newPrimaryDexInspector.assertTypeExists("Lcom/facebook/sample/Sample;");
    newPrimaryDexInspector.assertTypeExists("Lcom/facebook/sample/Sample2;");
    newPrimaryDexInspector.assertTypeDoesNotExist("Lcom/facebook/sample/Sample3;");

    List<String> newMetadata =
        zipInspector.getFileContentsLines("assets/secondary-program-dex-jars/metadata.txt");
    List<DexTestUtils.DexMetadata> newMetadataFiles = DexTestUtils.moduleMetadata(newMetadata);
    assertEquals(2, newMetadataFiles.size());

    newZipInspector.assertFileExists("assets/secondary-program-dex-jars/secondary.dex.jar.xzs");
    byte[] newXzsBytes =
        zipInspector.getFileContents("assets/secondary-program-dex-jars/secondary.dex.jar.xzs");
    Path newUnpackedXzsPath =
        workspace.getPath(newApkPath.getParent().resolve("newunxzs/secondary.dex.jar"));
    newUnpackedXzsPath.getParent().toFile().mkdirs();
    Files.copy(new XZInputStream(new ByteArrayInputStream(newXzsBytes)), newUnpackedXzsPath);

    ImmutableMap.Builder<Path, Integer> newDexSizeMapBuilder = ImmutableMap.builder();
    for (DexTestUtils.DexMetadata dexMetadata : newMetadataFiles) {
      String xzMeta =
          newZipInspector
              .getFileContentsLines(
                  "assets/secondary-program-dex-jars/" + dexMetadata.dexFile + ".meta")
              .get(0);
      int jarSize = readJarSize(xzMeta);
      newDexSizeMapBuilder.put(dexMetadata.dexFile, jarSize);
    }
    ImmutableMap<Path, Integer> newDexSizeMap = newDexSizeMapBuilder.build();

    int newTotalDexSize =
        newMetadataFiles.stream()
            .map(dexMetadata -> newDexSizeMap.get(dexMetadata.dexFile))
            .reduce(0, Integer::sum);
    assertEquals(newTotalDexSize, newUnpackedXzsPath.toFile().length());

    FileInputStream newJarConcatStream = new FileInputStream(newUnpackedXzsPath.toFile());
    for (int i = 0; i < 2; i++) {
      DexTestUtils.DexMetadata newDexMetadata = newMetadataFiles.get(i);
      int jarSize = newDexSizeMap.get(newDexMetadata.dexFile);
      byte[] dexJarContents = new byte[jarSize];
      assertEquals(newJarConcatStream.read(dexJarContents, 0, jarSize), jarSize);

      Path dexJarFile =
          workspace.getPath(
              newApkPath
                  .getParent()
                  .resolve(String.format("newunxzs/secondary-%s.dex.jar", i + 1)));
      Files.write(dexJarFile, dexJarContents);

      ZipInspector dexJarInspector = new ZipInspector(dexJarFile);

      dexJarInspector.assertFileExists("classes.dex");

      DexInspector dexInspector = new DexInspector(dexJarFile);
      dexInspector.assertTypeExists(newDexMetadata.getJvmName());

      if (i == 0) {
        dexInspector.assertTypeExists("Lcom/facebook/sample/Small;");
        dexInspector.assertTypeDoesNotExist("Lcom/facebook/sample/Sample2;");
        dexInspector.assertTypeDoesNotExist("Lcom/facebook/sample/Sample3;");
      } else {
        dexInspector.assertTypeDoesNotExist("Lcom/facebook/sample/Small;");
        dexInspector.assertTypeDoesNotExist("Lcom/facebook/sample/Sample2;");
        dexInspector.assertTypeExists("Lcom/facebook/sample/Sample3;");
      }
    }
  }

  @Test
  public void testNotAllJavaLibrariesFetched() throws IOException {
    String target = "//apps/multidex:app_with_deeper_deps";
    workspace.runBuckCommand("build", target).assertSuccess();
    workspace.replaceFileContents(
        "java/com/sample/app/MyApplication.java", "package com", "package\ncom");

    workspace.resetBuildLogFile();
    workspace.runBuckCommand("build", target).assertSuccess();
    BuckBuildLog buildLog = workspace.getBuildLog();

    buildLog.assertTargetBuiltLocally(target);
    buildLog.assertTargetIsAbsent("//java/com/sample/lib:lib");
  }

  @Test
  public void testXzsMultipleSecondaryDexes() throws IOException {
    Path apkPath = workspace.buildAndReturnOutput("//apps/multidex:xzs_multiple_dex");

    ZipInspector zipInspector = new ZipInspector(apkPath);
    zipInspector.assertFileExists("assets/secondary-program-dex-jars/secondary.dex.jar.xzs");
    List<String> metadata =
        zipInspector.getFileContentsLines("assets/secondary-program-dex-jars/metadata.txt");
    List<DexTestUtils.DexMetadata> moduleMetadata = DexTestUtils.moduleMetadata(metadata);
    assertTrue(moduleMetadata.size() > 100);

    // Checks that the metadata is ordered
    DexTestUtils.validateMetadata(apkPath);

    byte[] xzsBytes =
        zipInspector.getFileContents("assets/secondary-program-dex-jars/secondary.dex.jar.xzs");
    Path unpackedXzsPath =
        workspace.getPath(apkPath.getParent().resolve("unxzs/secondary.dex.jar"));
    unpackedXzsPath.getParent().toFile().mkdirs();
    Files.copy(new XZInputStream(new ByteArrayInputStream(xzsBytes)), unpackedXzsPath);

    ImmutableMap.Builder<Path, Integer> dexSizeMapBuilder = ImmutableMap.builder();
    for (DexTestUtils.DexMetadata dexMetadata : moduleMetadata) {
      String xzMeta =
          zipInspector
              .getFileContentsLines(
                  "assets/secondary-program-dex-jars/" + dexMetadata.dexFile + ".meta")
              .get(0);
      int jarSize = readJarSize(xzMeta);
      dexSizeMapBuilder.put(dexMetadata.dexFile, jarSize);
    }
    ImmutableMap<Path, Integer> dexSizeMap = dexSizeMapBuilder.build();

    int totalDexSize =
        moduleMetadata.stream()
            .map(dexMetadata -> dexSizeMap.get(dexMetadata.dexFile))
            .reduce(0, Integer::sum);
    assertEquals(totalDexSize, unpackedXzsPath.toFile().length());

    int i = 1;
    FileInputStream jarConcatStream = new FileInputStream(unpackedXzsPath.toFile());
    for (DexTestUtils.DexMetadata dexMetadata : moduleMetadata) {
      int jarSize = dexSizeMap.get(dexMetadata.dexFile);
      byte[] dexJarContents = new byte[jarSize];
      assertEquals(jarConcatStream.read(dexJarContents, 0, jarSize), jarSize);

      Path dexJarFile =
          workspace.getPath(
              apkPath.getParent().resolve(String.format("unxzs/secondary-%s.dex.jar", i)));
      Files.write(dexJarFile, dexJarContents);

      ZipInspector dexJarInspector = new ZipInspector(dexJarFile);

      dexJarInspector.assertFileExists("classes.dex");

      DexInspector dexInspector = new DexInspector(dexJarFile);
      dexInspector.assertTypeExists(dexMetadata.getJvmName());
      i += 1;
    }
  }

  static final Pattern META_FILE_PATTERN = Pattern.compile("jar:(\\d*) dex:\\d*");

  public static int readJarSize(String metaContents) {
    Matcher matcher = META_FILE_PATTERN.matcher(metaContents);
    matcher.matches();
    return new Integer(matcher.group(1));
  }

  @Test(expected = AssertionError.class)
  public void testRawDexTooManyDexes() {
    workspace.buildAndReturnOutput("//apps/multidex:raw_dex_over_100");
  }

  @Test
  public void testPrimaryDexMethodOverflow() {
    ProcessResult result = workspace.runBuckBuild("//apps/multidex:primary_dex_method_overflow");
    result.assertFailure("Should fail with primary dex method count overflow");

    assertThat(
        "Dex weight warning should be logged.",
        result.getStderr(),
        containsRegex("Primary dex size exceeds 64k method ref limit"));

    assertThat(
        "Method counts should be logged.",
        result.getStderr(),
        containsRegex(
            "The largest libraries in the dex, by number of methods:\n"
                + "methods   dex file path\n"
                + "5002      buck-out/.*generated_lib_method_overflow_[0-9]*#d8_dex.jar\n"
                + "5002      buck-out/.*generated_lib_method_overflow_[0-9]*#d8_dex.jar"));
  }

  @Test
  public void testPrimaryDexFieldOverflow() {
    ProcessResult result = workspace.runBuckBuild("//apps/multidex:primary_dex_field_overflow");
    result.assertFailure("Should fail with primary dex field count overflow");

    assertThat(
        "Dex weight warning should be logged.",
        result.getStderr(),
        containsRegex("Primary dex size exceeds 64k field ref limit"));

    assertThat(
        "Field counts should be logged.",
        result.getStderr(),
        containsRegex(
            "The largest libraries in the dex, by number of fields:\n"
                + "fields    dex file path\n"
                + "5000      buck-out/.*generated_lib_field_overflow_[0-9]*#d8_dex.jar\n"
                + "5000      buck-out/.*generated_lib_field_overflow_[0-9]*#d8_dex.jar"));
  }

  @Test
  public void testSecondaryDexMethodOverflow() {
    ProcessResult result = workspace.runBuckBuild("//apps/multidex:secondary_dex_method_overflow");
    result.assertFailure("Should fail with secondary dex method count overflow");

    assertThat(
        "Dex weight warning should be logged.",
        result.getStderr(),
        containsRegex("Secondary dex size exceeds 64k method ref limit"));

    assertThat(
        "Method counts should be logged.",
        result.getStderr(),
        containsRegex(
            "The largest libraries in the dex, by number of methods:\n"
                + "methods   dex file path\n"
                + "5002      buck-out/.*generated_lib_method_overflow_[0-9]*#d8/dex\\.jar\n"
                + "5002      buck-out/.*generated_lib_method_overflow_[0-9]*#d8/dex\\.jar"));
  }

  @Test
  public void testSecondaryDexFieldOverflow() {
    ProcessResult result = workspace.runBuckBuild("//apps/multidex:secondary_dex_field_overflow");
    result.assertFailure("Should fail with secondary dex field count overflow");

    assertThat(
        "Dex weight warning should be logged.",
        result.getStderr(),
        containsRegex("Secondary dex size exceeds 64k field ref limit"));

    assertThat(
        "Field counts should be logged.",
        result.getStderr(),
        containsRegex(
            "The largest libraries in the dex, by number of fields:\n"
                + "fields    dex file path\n"
                + "5000      buck-out/.*generated_lib_field_overflow_[0-9]*#d8/dex\\.jar\n"
                + "5000      buck-out/.*generated_lib_field_overflow_[0-9]*#d8/dex\\.jar"));
  }

  @Test
  public void testDexGroups() throws IOException {
    Path apkPath = workspace.buildAndReturnOutput("//apps/multidex:dex_groups");

    ZipInspector zipInspector = new ZipInspector(apkPath);
    zipInspector.getDirectoryContents(Paths.get("assets"));
    zipInspector.assertFileExists("assets/secondary-program-dex-jars/secondary-1_1.dex.jar");
    zipInspector.assertFileExists("assets/secondary-program-dex-jars/secondary-100_1.dex.jar");
    DexTestUtils.validateMetadata(apkPath, ImmutableSet.of());
  }

  @Test
  public void testDexGroupsWithSecondaryResources() throws IOException {
    Path apkPath = workspace.buildAndReturnOutput("//apps/multidex:dex_groups_r_dot_secondary_dex");

    String secondaryDex2 = "assets/secondary-program-dex-jars/secondary-2_1.dex.jar";
    String secondaryDex3 = "assets/secondary-program-dex-jars/secondary-3_1.dex.jar";
    String rDotJavaDex = "assets/secondary-program-dex-jars/secondary-4_1.dex.jar";

    ZipInspector zipInspector = new ZipInspector(apkPath);
    zipInspector.assertFileExists(secondaryDex2);
    zipInspector.assertFileExists(secondaryDex3);
    DexTestUtils.validateMetadata(apkPath, ImmutableSet.of());

    DexInspector dex2Inspector = new DexInspector(apkPath, secondaryDex2);
    dex2Inspector.assertTypeExists("Lcom/facebook/sample/Sample;");
    dex2Inspector.assertTypeExists("Lcom/facebook/sample/Sample2;");
    dex2Inspector.assertTypeExists("Lcom/facebook/sample/Sample3;");
    DexInspector dex3Inspector = new DexInspector(apkPath, secondaryDex3);
    dex3Inspector.assertTypeExists("Lcom/facebook/sample2/Sample;");
    DexInspector rDotJavaInspector = new DexInspector(apkPath, rDotJavaDex);
    rDotJavaInspector.assertTypeExists("Lcom/sample/R;");
    rDotJavaInspector.assertTypeExists("Lcom/sample2/R;");
  }

  @Test
  public void testProvidedDependenciesAreExcludedEvenIfSpecifiedInOtherDeps() throws IOException {
    String target = "//apps/sample:app_with_exported_and_provided_deps";
    ProcessResult result = workspace.runBuckBuild(target);
    result.assertSuccess();

    DexInspector dexInspector =
        new DexInspector(
            workspace.getPath(
                BuildTargetPaths.getGenPath(
                    filesystem.getBuckPaths(), BuildTargetFactory.newInstance(target), "%s.apk")));

    dexInspector.assertTypeExists("Lcom/facebook/sample/Dep;");
    dexInspector.assertTypeExists("Lcom/facebook/sample/ExportedDep;");
    dexInspector.assertTypeDoesNotExist("Lcom/facebook/sample/ProvidedDep;");
    dexInspector.assertTypeDoesNotExist("Lcom/facebook/sample/DepProvidedDep;");
    dexInspector.assertTypeDoesNotExist("Lcom/facebook/sample/ExportedProvidedDep;");
  }

  @Test
  public void testPreprocessorForcesReDex() throws IOException {
    String target = "//java/com/preprocess:disassemble";
    Path outputFile = workspace.buildAndReturnOutput(target);
    String output = new String(Files.readAllBytes(outputFile), UTF_8);
    assertThat(output, containsString("content=2"));

    workspace.replaceFileContents("java/com/preprocess/convert.py", "content=2", "content=3");

    outputFile = workspace.buildAndReturnOutput(target);
    output = new String(Files.readAllBytes(outputFile), UTF_8);
    assertThat(output, containsString("content=3"));
  }

  @Test
  public void testDxFindsReferencedResources() throws IOException {
    workspace.runBuckBuild(SIMPLE_TARGET).assertSuccess();
    BuildTarget dexTarget = BuildTargetFactory.newInstance("//java/com/sample/lib:lib#d8");
    ProjectFilesystem filesystem = workspace.getProjectFileSystem();
    Optional<String> resourcesFromMetadata =
        DexProducedFromJavaLibrary.getMetadataResources(filesystem, dexTarget);
    assertTrue(resourcesFromMetadata.isPresent());
    assertEquals("[\"com.sample.top_layout\",\"com.sample2.title\"]", resourcesFromMetadata.get());
  }

  @Test
  public void testD8FindsReferencedResources() throws IOException {
    workspace.runBuckBuild(RES_D8_TARGET).assertSuccess();
    BuildTarget dexTarget = BuildTargetFactory.newInstance("//java/com/sample/lib:lib#d8");
    ProjectFilesystem filesystem = workspace.getProjectFileSystem();
    Optional<String> resourcesFromMetadata =
        DexProducedFromJavaLibrary.getMetadataResources(filesystem, dexTarget);
    assertTrue(resourcesFromMetadata.isPresent());
    assertEquals("[\"com.sample.top_layout\",\"com.sample2.title\"]", resourcesFromMetadata.get());
  }

  @Test
  public void testDexGroupsReferencedResources() throws IOException {
    workspace.runBuckBuild(RES_GROUPS_TARGET).assertSuccess();
    BuildTarget dexTarget =
        BuildTargetFactory.newInstance(
            "//apps/multidex:app_with_resources_and_groups#pre_dex_group_1");
    ProjectFilesystem filesystem = workspace.getProjectFileSystem();
    Path resourcesFile =
        BuildTargetPaths.getGenPath(filesystem.getBuckPaths(), dexTarget, "%s")
            .resolve("referenced_resources.txt");
    Optional<String> referencedResources = filesystem.readFileIfItExists(resourcesFile);
    assertTrue(referencedResources.isPresent());
    // resources from both //java/com/sample/lib:lib and //java/com/sample2:lib
    assertEquals(
        "[\"com.sample.top_layout\",\"com.sample2.title\",\"com.sample2.sample2_string\"]",
        referencedResources.get());
  }

  @Test
  public void testDexingIsInputBased() throws IOException {
    workspace.runBuckBuild(SIMPLE_TARGET).assertSuccess();

    BuckBuildLog buildLog = workspace.getBuildLog();
    buildLog.assertTargetBuiltLocally("//java/com/sample/lib:lib#d8");

    workspace.replaceFileContents(
        "java/com/sample/lib/Sample.java", "import", "import /* no output change */");
    workspace.runBuckBuild(SIMPLE_TARGET).assertSuccess();
    buildLog = workspace.getBuildLog();
    buildLog.assertNotTargetBuiltLocally("//java/com/sample/lib:lib#d8");
    buildLog.assertTargetHadMatchingInputRuleKey("//java/com/sample/lib:lib#d8");

    workspace.replaceFileContents(
        "java/com/sample/lib/Sample.java", "import", "import /* \n some output change */");
    workspace.runBuckBuild(SIMPLE_TARGET).assertSuccess();
    buildLog = workspace.getBuildLog();
    buildLog.assertTargetBuiltLocally("//java/com/sample/lib:lib#d8");
  }

  @Test
  public void testProguardDontObfuscateGeneratesMappingFile() {
    String target = "//apps/sample:app_proguard_dontobfuscate";
    workspace.runBuckCommand("build", target).assertSuccess();

    Path mapping =
        workspace.getPath(
            BuildTargetPaths.getGenPath(
                filesystem.getBuckPaths(),
                BuildTargetFactory.newInstance(target),
                "%s/proguard/mapping.txt"));
    assertTrue(Files.exists(mapping));
  }

  private static Path unzip(Path tmpDir, Path zipPath, String name) throws IOException {
    Path outPath = tmpDir.resolve(zipPath.getFileName());
    try (ZipFile zipFile = new ZipFile(zipPath.toFile())) {
      Files.copy(
          zipFile.getInputStream(zipFile.getEntry(name)),
          outPath,
          StandardCopyOption.REPLACE_EXISTING);
      return outPath;
    }
  }

  @Test
  public void testApksHaveDeterministicTimestamps() throws IOException {
    String target = "//apps/sample:app";
    ProcessResult result = workspace.runBuckCommand("build", target);
    result.assertSuccess();

    // Iterate over each of the entries, expecting to see all zeros in the time fields.
    Path apk =
        workspace.getPath(
            BuildTargetPaths.getGenPath(
                filesystem.getBuckPaths(), BuildTargetFactory.newInstance(target), "%s.apk"));
    Date dosEpoch = new Date(ZipUtil.dosToJavaTime(ZipConstants.DOS_FAKE_TIME));
    try (ZipInputStream is = new ZipInputStream(Files.newInputStream(apk))) {
      for (ZipEntry entry = is.getNextEntry(); entry != null; entry = is.getNextEntry()) {
        assertThat(entry.getName(), new Date(entry.getTime()), equalTo(dosEpoch));
      }
    }
  }

  @Test
  public void testLibraryMetadataChecksum() throws IOException {
    String target = "//apps/sample:app_cxx_lib_asset";
    workspace.runBuckCommand("build", target).assertSuccess();
    Path pathToZip =
        workspace.getPath(
            BuildTargetPaths.getGenPath(
                filesystem.getBuckPaths(), BuildTargetFactory.newInstance(target), "%s.apk"));
    ZipFile file = new ZipFile(pathToZip.toFile());
    ZipEntry metadata = file.getEntry("assets/lib/metadata.txt");
    assertNotNull(metadata);

    BufferedReader contents =
        new BufferedReader(new InputStreamReader(file.getInputStream(metadata)));
    String line = contents.readLine();
    byte[] buffer = new byte[512];
    while (line != null) {
      // Each line is of the form <filename> <filesize> <SHA256 checksum>
      String[] tokens = line.split(" ");
      assertSame(tokens.length, 3);
      String filename = tokens[0];
      int filesize = Integer.parseInt(tokens[1]);
      String checksum = tokens[2];

      ZipEntry lib = file.getEntry("assets/lib/" + filename);
      assertNotNull(lib);
      InputStream is = file.getInputStream(lib);
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      while (filesize > 0) {
        int read = is.read(buffer, 0, Math.min(buffer.length, filesize));
        assertTrue(read >= 0);
        out.write(buffer, 0, read);
        filesize -= read;
      }
      String actualChecksum = Hashing.sha256().hashBytes(out.toByteArray()).toString();
      assertEquals(checksum, actualChecksum);
      is.close();
      out.close();
      line = contents.readLine();
    }
    file.close();
    contents.close();
  }

  @Test
  public void testStripRulesAreShared() throws IOException {
    workspace.runBuckCommand("build", "//apps/sample:app_cxx_lib_asset").assertSuccess();
    workspace.resetBuildLogFile();
    workspace.runBuckCommand("build", "//apps/sample:app_cxx_different_rule_name").assertSuccess();
    BuckBuildLog buildLog = workspace.getBuildLog();

    for (BuildTarget target : buildLog.getAllTargets()) {
      String rawTarget = target.toString();
      if (rawTarget.contains("libgnustl_shared.so") || rawTarget.contains("libc___shared.so")) {
        // Stripping the C++ runtime is currently not shared.
        continue;
      }
      if (rawTarget.contains("strip")) {
        buildLog.assertNotTargetBuiltLocally(rawTarget);
      }
    }
  }

  @Test
  public void testSimpleD8App() {
    workspace.runBuckBuild("//apps/sample:app_with_d8").assertSuccess();
  }

  @Test
  public void testD8AppWithMultidexContainsCanaryClasses() throws IOException {
    workspace.runBuckBuild("//apps/multidex:app_with_d8").assertSuccess();
    final Path path =
        workspace.buildAndReturnOutput("//apps/multidex:disassemble_app_with_d8_for_canary");
    final List<String> smali = filesystem.readLines(path);
    assertFalse(smali.isEmpty());
  }

  @Test
  public void testD8AppWithMultidexJumboString() throws IOException {
    // FORCE_JUMBO should be set and D8 should respect that.
    // FORCE_JUMBO is used when PreDexMerging is enabled (the default).
    workspace.runBuckBuild("//apps/multidex:app_with_d8").assertSuccess();
    final Path path =
        workspace.buildAndReturnOutput("//apps/multidex:disassemble_app_with_d8_for_jumbo_string");
    final List<String> smali = filesystem.readLines(path);
    assertFalse(smali.isEmpty());
    boolean foundString = false;
    for (String line : smali) {
      if (line.contains("const-string")) {
        foundString = true;
        assertTrue(line.contains("const-string/jumbo"));
      }
    }
    assertTrue(foundString);
  }

  @Test
  public void testResourceOverrides() throws IOException {
    Path path = workspace.buildAndReturnOutput("//apps/sample:strings_dump_overrides");
    assertThat(
        workspace.getFileContents(path),
        containsPattern(Pattern.compile("^String #[0-9]*: Real App Name$", Pattern.MULTILINE)));
  }

  @Test
  public void testResourceOverridesAapt2() throws Exception {
    AssumeAndroidPlatform.get(workspace).assumeAapt2WithOutputTextSymbolsIsAvailable();
    workspace.replaceFileContents(
        "apps/sample/BUCK", "'aapt1',  # app_with_res_overrides", "'aapt2',");

    testResourceOverrides();
  }

  @Test
  public void testApkEmptyResDirectoriesBuildsCorrectly() {
    workspace.runBuckBuild("//apps/sample:app_with_aar_and_no_res").assertSuccess();
  }

  @Test
  public void testInstrumentationApkWithEmptyResDepBuildsCorrectly() {
    workspace.runBuckBuild("//apps/sample:instrumentation_apk").assertSuccess();
  }

  @Test
  public void testInvalidKeystoreKeyAlias() throws IOException {
    workspace.runBuckBuild(SIMPLE_TARGET).assertSuccess();

    workspace.replaceFileContents(
        "keystores/debug.keystore.properties", "key.alias=my_alias", "key.alias=invalid_alias");

    workspace.resetBuildLogFile();
    ProcessResult result = workspace.runBuckCommand("build", SIMPLE_TARGET);
    result.assertFailure("Invalid keystore key alias should fail.");

    assertThat(
        "error message for invalid keystore key alias is incorrect.",
        result.getStderr(),
        containsRegex("The keystore \\[.*\\] key\\.alias \\[.*\\].*does not exist"));
  }

  @Test
  public void testManifestMerge() throws IOException {
    Path mergedPath = workspace.buildAndReturnOutput("//manifests:manifest");
    String contents = workspace.getFileContents(mergedPath);

    Pattern readCalendar =
        Pattern.compile(
            "<uses-permission-sdk-23 android:name=\"android\\.permission\\.READ_CALENDAR\" />");
    int matchCount = 0;
    Matcher matcher = readCalendar.matcher(contents);
    while (matcher.find()) {
      matchCount++;
    }
    assertEquals(
        String.format(
            "Expected one uses-permission-sdk-23=READ_CALENDAR tag, but found %d: %s",
            matchCount, contents),
        1,
        matchCount);
  }

  @Test
  public void testAutomaticManifestMerge() throws IOException {
    Path dumpPath = workspace.buildAndReturnOutput("//apps/sample:dump_merged_manifest");
    String contents = workspace.getFileContents(dumpPath);

    assertThat(contents, containsString("READ_CALENDAR"));
  }

  @Test
  public void testErrorReportingDuringManifestMerging() {
    ProcessResult processResult =
        workspace.runBuckBuild("//apps/sample:dump_invalid_merged_manifest");
    assertThat(
        processResult.getStderr(),
        containsString(
            "The prefix \"tools\" for attribute \"tools:targetAPI\" "
                + "associated with an element type \"intent-filter\" is not bound."));
  }

  @Test
  public void testProguardOutput() throws IOException {
    ImmutableMap<String, Path> outputs =
        workspace.buildMultipleAndReturnOutputs(
            "//apps/sample:proguard_output_dontobfuscate",
            "//apps/sample:proguard_output_dontobfuscate_no_aapt");

    String withAapt =
        workspace.getFileContents(outputs.get("//apps/sample:proguard_output_dontobfuscate"));
    String withoutAapt =
        workspace.getFileContents(
            outputs.get("//apps/sample:proguard_output_dontobfuscate_no_aapt"));

    assertThat(withAapt, containsString("-printmapping"));
    assertThat(withAapt, containsString("#generated"));
    assertThat(withoutAapt, containsString("-printmapping"));
    assertThat(withoutAapt, CoreMatchers.not(containsString("#generated")));
  }

  @Test
  public void testSimpleApkSignature() throws IOException {
    Path apkPath = workspace.buildAndReturnOutput(SIMPLE_TARGET);
    File apkFile = filesystem.getPathForRelativePath(apkPath).toFile();
    ApkVerifier.Builder apkVerifierBuilder = new ApkVerifier.Builder(apkFile);
    ApkVerifier.Result result;
    try {
      result = apkVerifierBuilder.build().verify();
    } catch (Exception e) {
      throw new IOException("Failed to determine APK's minimum supported platform version", e);
    }
    assertTrue(result.isVerifiedUsingV1Scheme());
    assertTrue(result.isVerifiedUsingV2Scheme());
  }

  @Test
  public void testClasspathQueryFunctionWorksOnAndroidBinary() throws IOException {
    Path output = workspace.buildAndReturnOutput("//apps/sample:dump_classpath");
    String[] actualClasspath = workspace.getFileContents(output).split("\\s+");
    assertThat(
        actualClasspath,
        Matchers.array(
            Matchers.containsString("//apps/sample:app"),
            Matchers.containsString("//java/com/sample/lib:lib")));
  }

  @Test
  public void testMinApiDrivesDxToProduceHigherVersionedBytecode() throws Exception {
    // min api level 28 causes dex to produce version 39 dex files. However, dexdump 28.0.x
    // produces this error when trying to read them:
    //   E/libdex  (93235): ERROR: unsupported dex version (30 33 39 00)
    // This has been fixed in build tools 29+
    AssumeAndroidPlatform.get(workspace).assumeBuildToolsVersionIsAtLeast("29");
    Path outputApk = workspace.buildAndReturnOutput("//apps/sample:app_with_min_28");
    workspace
        .getBuildLog()
        .assertTargetBuiltLocally("//apps/sample:app_with_min_28#class_file_to_dex_processing");
    AndroidSdkLocation androidSdkLocation = TestAndroidSdkLocationFactory.create(filesystem);
    AndroidBuildToolsResolver buildToolsResolver =
        new AndroidBuildToolsResolver(
            new AndroidBuckConfig(FakeBuckConfig.empty(), Platform.detect()), androidSdkLocation);
    AndroidBuildToolsLocation buildToolsLocation =
        AndroidBuildToolsLocation.of(buildToolsResolver.getBuildToolsPath());
    Path dexdumpLocation = buildToolsLocation.getBuildToolsPath().resolve("dexdump");
    ProcessExecutor.Result result =
        workspace.runCommand(dexdumpLocation.toString(), "-f", outputApk.toString());
    String stderr = result.getStderr().orElse("");
    Matcher matcher =
        Pattern.compile("DEX version\\s*'(\\d+)'").matcher(result.getStdout().orElse(""));
    assertTrue(
        "Unable to find a match for the DEX version message. Stderr:\n " + stderr, matcher.find());
    int dexVersion = Integer.parseInt(matcher.group(1));
    assertThat(dexVersion, Matchers.greaterThanOrEqualTo(39));
  }
}

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

package com.facebook.buck.jvm.java;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import com.facebook.buck.cd.model.java.AbiGenerationMode;
import com.facebook.buck.core.build.execution.context.StepExecutionContext;
import com.facebook.buck.core.filesystems.AbsPath;
import com.facebook.buck.core.filesystems.RelPath;
import com.facebook.buck.io.filesystem.ProjectFilesystem;
import com.facebook.buck.io.filesystem.TestProjectFilesystems;
import com.facebook.buck.step.TestExecutionContext;
import com.facebook.buck.testutil.TemporaryPaths;
import com.facebook.buck.testutil.integration.TestDataHelper;
import com.facebook.buck.testutil.integration.ZipInspector;
import com.facebook.buck.util.zip.CustomJarOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import org.junit.Rule;
import org.junit.Test;

public class CalculateClassAbiStepTest {
  @Rule public TemporaryPaths temp = new TemporaryPaths();

  @Test
  public void shouldCalculateAbiFromAStubJar() throws IOException {
    AbsPath outDir = temp.newFolder();
    ProjectFilesystem filesystem = TestProjectFilesystems.createProjectFilesystem(outDir);

    Path directory = TestDataHelper.getTestDataDirectory(this);
    Path source = directory.resolve("prebuilt/junit.jar");
    RelPath binJar = RelPath.get("source.jar");
    Files.copy(source, outDir.resolve(binJar).getPath());
    RelPath abiJar = RelPath.get("abi.jar");
    RelPath jvmAbiGenDir = RelPath.get("abi_tmp");

    AbsPath rootPath = filesystem.getRootPath();
    StepExecutionContext executionContext = TestExecutionContext.newInstance(rootPath);
    new CalculateClassAbiStep(binJar, jvmAbiGenDir, abiJar, AbiGenerationMode.CLASS)
        .executeIsolatedStep(executionContext);

    Path abiJarPath = outDir.resolve(abiJar).getPath();
    String seenHash = filesystem.computeSha1(abiJarPath).getHash();

    // Hi there! This is hardcoded here because we want to make sure buck always produces the same
    // jar files across timezones and versions. If the test is failing because of an intentional
    // modification to how we produce abi .jar files, then just update the hash, otherwise please
    // investigate why the value is different.
    // NOTE: If this starts failing on CI for no obvious reason it's possible that the offset
    // calculation in ZipConstants.getFakeTime() does not account for DST correctly.
    assertEquals("51b28115808a8684550a7b026154a94075358b68", seenHash);

    // Assert that the abiJar contains non-class resources (like txt files).
    ZipInspector inspector = new ZipInspector(abiJarPath);
    inspector.assertFileExists("LICENSE.txt");

    try (JarFile jarFile = new JarFile(abiJarPath.toFile())) {
      Manifest manifest = jarFile.getManifest();
      assertNotNull(
          manifest
              .getAttributes("junit/runner/BaseTestRunner.class")
              .getValue(CustomJarOutputStream.DIGEST_ATTRIBUTE_NAME));
    }
  }
}

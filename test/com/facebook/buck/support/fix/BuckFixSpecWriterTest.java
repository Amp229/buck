/*
 * Copyright (c) Facebook, Inc. and its affiliates.
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

package com.facebook.buck.support.fix;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.facebook.buck.core.filesystems.AbsPath;
import com.facebook.buck.core.model.BuildId;
import com.facebook.buck.io.watchman.WatchmanError;
import com.facebook.buck.log.InvocationInfo;
import com.facebook.buck.testutil.TemporaryPaths;
import com.facebook.buck.util.types.Either;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import org.junit.Rule;
import org.junit.Test;

public class BuckFixSpecWriterTest {
  final BuckFixSpec specWithPaths = BuckFixSpecTest.specWithPaths;

  @Rule public TemporaryPaths tmp = new TemporaryPaths();

  @Test
  public void writesAndParsesFixSpecFile() throws IOException {

    InvocationInfo info =
        InvocationInfo.of(
            new BuildId("1234"),
            false,
            false,
            "build",
            ImmutableList.of("//:target"),
            ImmutableList.of("//:target"),
            Paths.get("buck-out", "log"),
            false,
            "repository",
            "",
            WatchmanError.TEST);

    BuckFixSpecWriter.writeSpecToLogDir(tmp.getRoot().getPath(), info, specWithPaths);

    AbsPath fixSpecPath =
        tmp.getRoot().resolve(info.getLogDirectoryPath()).resolve("buck_fix_spec.json");

    assertTrue(Files.exists(fixSpecPath.getPath()));

    Either<BuckFixSpec, BuckFixSpecParser.FixSpecFailure> parsedFixSpec =
        BuckFixSpecParser.parseFromFixSpecFile(fixSpecPath.getPath());

    assertEquals(specWithPaths, parsedFixSpec.getLeft());
  }
}

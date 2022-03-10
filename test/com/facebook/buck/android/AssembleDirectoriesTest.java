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

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;

import com.facebook.buck.core.build.buildable.context.FakeBuildableContext;
import com.facebook.buck.core.build.context.FakeBuildContext;
import com.facebook.buck.core.build.execution.context.StepExecutionContext;
import com.facebook.buck.core.filesystems.AbsPath;
import com.facebook.buck.core.model.BuildTarget;
import com.facebook.buck.core.model.BuildTargetFactory;
import com.facebook.buck.core.rules.ActionGraphBuilder;
import com.facebook.buck.core.rules.resolver.impl.TestActionGraphBuilder;
import com.facebook.buck.core.sourcepath.FakeSourcePath;
import com.facebook.buck.core.sourcepath.SourcePath;
import com.facebook.buck.io.filesystem.ProjectFilesystem;
import com.facebook.buck.io.filesystem.impl.FakeProjectFilesystem;
import com.facebook.buck.step.Step;
import com.facebook.buck.step.TestExecutionContext;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Before;
import org.junit.Test;

public class AssembleDirectoriesTest {
  private StepExecutionContext context;
  private ProjectFilesystem filesystem;

  @Before
  public void setUp() {
    filesystem = FakeProjectFilesystem.createJavaOnlyFilesystem();
    AbsPath rootPath = filesystem.getRootPath();
    context =
        TestExecutionContext.newBuilder()
            .setRuleCellRoot(rootPath)
            .setBuildCellRootPath(rootPath.getPath())
            .build();
  }

  @Test
  public void testAssembleFoldersWithRelativePath() throws IOException, InterruptedException {
    AbsPath tmp = filesystem.getRootPath();
    Files.createDirectories(tmp.resolve("folder_a").getPath());
    Files.write(tmp.resolve("folder_a/a.txt").getPath(), "".getBytes(UTF_8));
    Files.write(tmp.resolve("folder_a/b.txt").getPath(), "".getBytes(UTF_8));
    Files.createDirectories(tmp.resolve("folder_b").getPath());
    Files.write(tmp.resolve("folder_b/c.txt").getPath(), "".getBytes(UTF_8));
    Files.write(tmp.resolve("folder_b/d.txt").getPath(), "".getBytes(UTF_8));

    BuildTarget target = BuildTargetFactory.newInstance("//:output_folder");
    ImmutableList<SourcePath> directories =
        ImmutableList.of(
            FakeSourcePath.of(filesystem, "folder_a"), FakeSourcePath.of(filesystem, "folder_b"));
    ActionGraphBuilder graphBuilder = new TestActionGraphBuilder();
    AssembleDirectories assembleDirectories =
        new AssembleDirectories(target, filesystem, graphBuilder, directories);
    graphBuilder.addToIndex(assembleDirectories);

    ImmutableList<Step> steps =
        assembleDirectories.getBuildSteps(
            FakeBuildContext.withSourcePathResolver(graphBuilder.getSourcePathResolver())
                .withBuildCellRootPath(tmp),
            new FakeBuildableContext());
    for (Step step : steps) {
      assertEquals(0, step.execute(context).getExitCode());
    }
    AbsPath outputFile =
        graphBuilder
            .getSourcePathResolver()
            .getAbsolutePath(assembleDirectories.getSourcePathToOutput());
    try (DirectoryStream<Path> dir = Files.newDirectoryStream(outputFile.getPath())) {
      assertEquals(4, Iterables.size(dir));
    }
  }
}

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

package com.facebook.buck.externalactions.android;

import static org.hamcrest.MatcherAssert.assertThat;

import com.facebook.buck.core.build.execution.context.StepExecutionContext;
import com.facebook.buck.core.filesystems.AbsPath;
import com.facebook.buck.io.filesystem.ProjectFilesystem;
import com.facebook.buck.io.filesystem.impl.FakeProjectFilesystem;
import com.facebook.buck.io.filesystem.impl.ProjectFilesystemUtils;
import com.facebook.buck.rules.modern.model.BuildableCommand;
import com.facebook.buck.step.TestExecutionContext;
import com.facebook.buck.step.isolatedsteps.IsolatedStep;
import com.facebook.buck.step.isolatedsteps.android.SplitResourcesStep;
import com.facebook.buck.step.isolatedsteps.android.ZipalignStep;
import com.facebook.buck.util.json.ObjectMappers;
import com.google.common.collect.ImmutableList;
import com.google.common.io.Files;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import org.hamcrest.Matchers;
import org.junit.Test;

public class SplitResourcesExternalActionTest {

  @Test
  public void canGetSteps() throws Exception {
    ProjectFilesystem filesystem = new FakeProjectFilesystem();
    Path temp = filesystem.createTempFile("split_resources_", "");
    AbsPath projectRootPath = filesystem.getRootPath();
    StepExecutionContext context = TestExecutionContext.newInstance();
    SplitResourcesExternalActionArgs args =
        SplitResourcesExternalActionArgs.of(
            "aapt_resources",
            "r_dot_txt",
            "primary_resource_output",
            "unaligned_exo",
            "r_dot_txt_output",
            projectRootPath.toString(),
            ProjectFilesystemUtils.relativize(projectRootPath, projectRootPath).toString(),
            "exo-resources.unaligned.zip",
            "exo_resources_output",
            true,
            ImmutableList.of("command", "prefix"));
    try {
      String json = ObjectMappers.WRITER.writeValueAsString(args);
      Files.asCharSink(temp.toFile(), StandardCharsets.UTF_8).write(json);
    } catch (IOException e) {
      throw new IllegalStateException("Failed to write SplitResourcesExternalActionArgs JSON", e);
    }

    BuildableCommand buildableCommand =
        BuildableCommand.newBuilder()
            .setExternalActionClass(SplitResourcesExternalAction.class.getName())
            .addExtraFiles(temp.toString())
            .build();
    ImmutableList<IsolatedStep> steps =
        new SplitResourcesExternalAction().getSteps(buildableCommand);

    assertThat(steps, Matchers.hasSize(2));

    assertThat(steps.get(0), Matchers.instanceOf(SplitResourcesStep.class));
    SplitResourcesStep actualSplitResourcesStep = (SplitResourcesStep) steps.get(0);
    assertThat(
        actualSplitResourcesStep.getDescription(context),
        Matchers.equalTo("split_exo_resources aapt_resources r_dot_txt"));

    assertThat(steps.get(1), Matchers.instanceOf(ZipalignStep.class));
    ZipalignStep actualZipAlignStep = (ZipalignStep) steps.get(1);
    assertThat(
        actualZipAlignStep.getShellCommand(context),
        Matchers.contains(
            "command", "prefix", "-f", "4", "exo-resources.unaligned.zip", "exo_resources_output"));
  }
}

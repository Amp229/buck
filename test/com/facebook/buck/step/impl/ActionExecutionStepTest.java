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

package com.facebook.buck.step.impl;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.facebook.buck.core.artifact.Artifact;
import com.facebook.buck.core.artifact.ArtifactFilesystem;
import com.facebook.buck.core.build.execution.context.StepExecutionContext;
import com.facebook.buck.core.build.execution.context.actionid.ActionId;
import com.facebook.buck.core.filesystems.AbsPath;
import com.facebook.buck.core.filesystems.RelPath;
import com.facebook.buck.core.model.BuildTarget;
import com.facebook.buck.core.model.BuildTargetFactory;
import com.facebook.buck.core.model.impl.BuildPaths;
import com.facebook.buck.core.rules.actions.ActionCreationException;
import com.facebook.buck.core.rules.actions.ActionExecutionResult;
import com.facebook.buck.core.rules.actions.ActionRegistryForTests;
import com.facebook.buck.core.rules.actions.FakeAction;
import com.facebook.buck.core.sourcepath.ExplicitBuildTargetSourcePath;
import com.facebook.buck.event.BuckEventBus;
import com.facebook.buck.event.BuckEventBusForTests;
import com.facebook.buck.event.BuckEventBusForTests.CapturingEventListener;
import com.facebook.buck.event.ConsoleEvent;
import com.facebook.buck.io.filesystem.ProjectFilesystem;
import com.facebook.buck.io.filesystem.TestProjectFilesystems;
import com.facebook.buck.io.filesystem.impl.DefaultProjectFilesystemFactory;
import com.facebook.buck.io.filesystem.impl.FakeProjectFilesystem;
import com.facebook.buck.io.filesystem.impl.FakeProjectFilesystemFactory;
import com.facebook.buck.step.StepExecutionResult;
import com.facebook.buck.testutil.TemporaryPaths;
import com.facebook.buck.util.Console;
import com.facebook.buck.util.FakeProcessExecutor;
import com.facebook.buck.util.environment.Platform;
import com.facebook.buck.util.timing.FakeClock;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Iterables;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.Rule;
import org.junit.Test;

public class ActionExecutionStepTest {

  private static final boolean WITH_DOWNWARD_API = false;

  @Rule public TemporaryPaths tmp = new TemporaryPaths();

  @Test
  public void canExecuteAnAction() throws IOException, ActionCreationException {
    ProjectFilesystem projectFilesystem = new FakeProjectFilesystem();
    Path baseCell = Paths.get("cell");
    BuildTarget buildTarget = BuildTargetFactory.newInstance("//my:foo");

    Path output = Paths.get("somepath");

    FakeAction.FakeActionExecuteLambda actionFunction =
        (srcs, inputs, outputs, ctx) -> {
          assertEquals(ImmutableSortedSet.of(), inputs);
          assertThat(outputs, hasSize(1));
          assertEquals(
              ExplicitBuildTargetSourcePath.of(
                  buildTarget,
                  BuildPaths.getGenDir(projectFilesystem.getBuckPaths(), buildTarget)
                      .resolve(output)),
              Iterables.getOnlyElement(outputs).asBound().getSourcePath());
          ctx.logError(new RuntimeException("message"), "my error %s", 1);
          ctx.postEvent(ConsoleEvent.info("my test info"));
          return ActionExecutionResult.success(
              Optional.empty(), Optional.of("my std err"), ImmutableList.of());
        };

    ActionRegistryForTests actionFactoryForTests = new ActionRegistryForTests(buildTarget);
    Artifact declaredArtifact = actionFactoryForTests.declareArtifact(output);
    FakeAction action =
        new FakeAction(
            actionFactoryForTests,
            ImmutableSortedSet.of(),
            ImmutableSortedSet.of(),
            ImmutableSortedSet.of(declaredArtifact),
            actionFunction);

    ActionExecutionStep step =
        new ActionExecutionStep(
            action, new ArtifactFilesystem(projectFilesystem), WITH_DOWNWARD_API);
    BuckEventBus testEventBus = BuckEventBusForTests.newInstance();
    CapturingEventListener consoleEventListener = new CapturingEventListener();
    testEventBus.register(consoleEventListener);
    assertEquals(
        StepExecutionResult.builder().setExitCode(0).setStderr(Optional.of("my std err")).build(),
        step.execute(
            getCommonExecutionContentBuilder(projectFilesystem, baseCell, testEventBus).build()));

    assertThat(
        consoleEventListener.getConsoleEventLogMessages(),
        contains(
            containsString(
                "my error 1" + System.lineSeparator() + "java.lang.RuntimeException: message"),
            containsString("my test info")));
  }

  @Test
  public void createsPackagePathBeforeExecution() throws IOException {
    ProjectFilesystem projectFilesystem =
        TestProjectFilesystems.createProjectFilesystem(tmp.getRoot());

    Path baseCell = Paths.get("cell");
    Path output = Paths.get("somepath");
    BuckEventBus testEventBus = BuckEventBusForTests.newInstance();
    BuildTarget buildTarget = BuildTargetFactory.newInstance("//my:foo");

    ActionExecutionResult.ActionExecutionFailure result =
        ActionExecutionResult.failure(
            Optional.empty(), Optional.of("my std err"), ImmutableList.of(), Optional.empty());

    ActionRegistryForTests actionFactoryForTests = new ActionRegistryForTests(buildTarget);
    Artifact declaredArtifact = actionFactoryForTests.declareArtifact(output);
    FakeAction action =
        new FakeAction(
            actionFactoryForTests,
            ImmutableSortedSet.of(),
            ImmutableSortedSet.of(),
            ImmutableSortedSet.of(declaredArtifact),
            (srcs, inputs, outputs, ctx) -> result);

    ActionExecutionStep step =
        new ActionExecutionStep(
            action, new ArtifactFilesystem(projectFilesystem), WITH_DOWNWARD_API);

    RelPath packagePath = BuildPaths.getGenDir(projectFilesystem.getBuckPaths(), buildTarget);

    assertFalse(projectFilesystem.exists(packagePath));
    assertEquals(
        StepExecutionResult.builder().setExitCode(-1).setStderr(Optional.of("my std err")).build(),
        step.execute(
            getCommonExecutionContentBuilder(projectFilesystem, baseCell, testEventBus)
                .setProjectFilesystemFactory(new DefaultProjectFilesystemFactory())
                .build()));
    assertTrue(projectFilesystem.isDirectory(packagePath));
  }

  @Test
  public void deletesExistingOutputsOnDiskBeforeExecuting() throws IOException {
    ProjectFilesystem projectFilesystem =
        TestProjectFilesystems.createProjectFilesystem(tmp.getRoot());

    Path baseCell = Paths.get("cell");
    Path output = Paths.get("somepath");
    BuckEventBus testEventBus = BuckEventBusForTests.newInstance();
    BuildTarget buildTarget = BuildTargetFactory.newInstance("//my:foo");

    ActionExecutionResult.ActionExecutionFailure result =
        ActionExecutionResult.failure(
            Optional.empty(), Optional.of("my std err"), ImmutableList.of(), Optional.empty());

    ActionRegistryForTests actionFactoryForTests = new ActionRegistryForTests(buildTarget);
    Artifact declaredArtifact = actionFactoryForTests.declareArtifact(output);
    FakeAction action =
        new FakeAction(
            actionFactoryForTests,
            ImmutableSortedSet.of(),
            ImmutableSortedSet.of(),
            ImmutableSortedSet.of(declaredArtifact),
            (srcs, inputs, outputs, ctx) -> result);

    ActionExecutionStep step =
        new ActionExecutionStep(
            action, new ArtifactFilesystem(projectFilesystem), WITH_DOWNWARD_API);

    Path expectedPath =
        BuildPaths.getGenDir(projectFilesystem.getBuckPaths(), buildTarget).resolve(output);

    projectFilesystem.mkdirs(expectedPath.getParent());
    projectFilesystem.writeContentsToPath("contents", expectedPath);

    assertTrue(projectFilesystem.exists(expectedPath));
    assertEquals(
        StepExecutionResult.builder().setExitCode(-1).setStderr(Optional.of("my std err")).build(),
        step.execute(
            getCommonExecutionContentBuilder(projectFilesystem, baseCell, testEventBus).build()));
    assertFalse("file must exist: " + expectedPath, projectFilesystem.exists(expectedPath));
  }

  private StepExecutionContext.Builder getCommonExecutionContentBuilder(
      ProjectFilesystem projectFilesystem, Path baseCell, BuckEventBus testEventBus) {
    AbsPath rootPath = projectFilesystem.getRootPath();
    return StepExecutionContext.builder()
        .setConsole(Console.createNullConsole())
        .setBuckEventBus(testEventBus)
        .setPlatform(Platform.UNKNOWN)
        .setEnvironment(ImmutableMap.of())
        .setBuildCellRootPath(baseCell)
        .setProcessExecutor(new FakeProcessExecutor())
        .setProjectFilesystemFactory(new FakeProjectFilesystemFactory())
        .setRuleCellRoot(rootPath)
        .setActionId(ActionId.of("test_action_id"))
        .setClock(FakeClock.doNotCare())
        .setWorkerToolPools(new ConcurrentHashMap<>());
  }
}

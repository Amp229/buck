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

package com.facebook.buck.rules.modern;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.stringContainsInOrder;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import com.facebook.buck.core.build.buildable.context.BuildableContext;
import com.facebook.buck.core.build.buildable.context.FakeBuildableContext;
import com.facebook.buck.core.build.context.BuildContext;
import com.facebook.buck.core.build.context.FakeBuildContext;
import com.facebook.buck.core.exceptions.BuckUncheckedExecutionException;
import com.facebook.buck.core.model.BuildTarget;
import com.facebook.buck.core.model.BuildTargetFactory;
import com.facebook.buck.core.model.impl.BuildPaths;
import com.facebook.buck.core.rulekey.AddToRuleKey;
import com.facebook.buck.core.rules.ActionGraphBuilder;
import com.facebook.buck.core.rules.SourcePathRuleFinder;
import com.facebook.buck.core.rules.resolver.impl.TestActionGraphBuilder;
import com.facebook.buck.io.filesystem.BuildCellRelativePath;
import com.facebook.buck.io.filesystem.ProjectFilesystem;
import com.facebook.buck.io.filesystem.impl.FakeProjectFilesystem;
import com.facebook.buck.step.Step;
import com.facebook.buck.step.fs.MkdirStep;
import com.facebook.buck.step.fs.RmStep;
import com.facebook.buck.testutil.MoreAsserts;
import com.google.common.collect.ImmutableList;
import org.junit.Test;

public class ModernBuildRuleTest {

  @Test
  public void shouldErrorWhenPublicOutputPathIsInsideTempPath() {
    ProjectFilesystem filesystem = new FakeProjectFilesystem();
    BuildTarget target = BuildTargetFactory.newInstance("//foo:bar");
    ModernBuildRule<?> rule =
        new InvalidPublicOutputPathBuildRule(target, filesystem, new TestActionGraphBuilder());
    BuildableContext buildableContext = new FakeBuildableContext();

    BuckUncheckedExecutionException expectedException =
        assertThrows(
            BuckUncheckedExecutionException.class,
            () -> rule.deriveOutputs(buildableContext::recordArtifact));

    Throwable cause = expectedException.getCause();
    assertThat(cause, instanceOf(IllegalStateException.class));
    assertThat(
        cause.getMessage(),
        stringContainsInOrder(
            "PublicOutputPath ", "should not be inside rule temporary directory: "));
  }

  @Test
  public void testBuildRuleStepsCreatesRootPathExactlyOnce() {
    ActionGraphBuilder actionGraphBuilder = new TestActionGraphBuilder();
    ProjectFilesystem filesystem = new FakeProjectFilesystem();
    BuildTarget target = BuildTargetFactory.newInstance("//foo:bar");
    NoOpModernBuildRule rule = new NoOpModernBuildRule(target, filesystem, actionGraphBuilder);

    BuildContext buildContext =
        FakeBuildContext.withSourcePathResolver(actionGraphBuilder.getSourcePathResolver());
    BuildableContext buildableContext = new FakeBuildableContext();
    ImmutableList<Step> steps = rule.getBuildSteps(buildContext, buildableContext);
    MoreAsserts.assertStepsNames(
        "The root directory should be remote and created exactly once",
        ImmutableList.of("delegated_rm", "delegated_mkdir", "delegated_rm", "delegated_mkdir"),
        steps);
    assertEquals(
        RmStep.of(
            BuildCellRelativePath.fromCellRelativePath(
                buildContext.getBuildCellRootPath(),
                filesystem,
                filesystem
                    .getBuckPaths()
                    .getGenDir()
                    .resolve(
                        BuildPaths.getBaseDir(filesystem, target)
                            .toPath(filesystem.getFileSystem()))),
            true),
        steps.get(0));

    assertEquals(
        MkdirStep.of(
            BuildCellRelativePath.fromCellRelativePath(
                buildContext.getBuildCellRootPath(),
                filesystem,
                filesystem
                    .getBuckPaths()
                    .getGenDir()
                    .resolve(
                        BuildPaths.getBaseDir(filesystem, target)
                            .toPath(filesystem.getFileSystem())))),
        steps.get(1));

    assertEquals(
        RmStep.of(
            BuildCellRelativePath.fromCellRelativePath(
                buildContext.getBuildCellRootPath(),
                filesystem,
                filesystem
                    .getBuckPaths()
                    .getScratchDir()
                    .resolve(
                        BuildPaths.getBaseDir(filesystem, target)
                            .toPath(filesystem.getFileSystem()))),
            true),
        steps.get(2));

    assertEquals(
        MkdirStep.of(
            BuildCellRelativePath.fromCellRelativePath(
                buildContext.getBuildCellRootPath(),
                filesystem,
                filesystem
                    .getBuckPaths()
                    .getScratchDir()
                    .resolve(
                        BuildPaths.getBaseDir(filesystem, target)
                            .toPath(filesystem.getFileSystem())))),
        steps.get(3));
  }

  static class InvalidPublicOutputPathBuildRule
      extends ModernBuildRule<InvalidPublicOutputPathBuildRule> implements Buildable {

    @AddToRuleKey private final OutputPath path;

    public InvalidPublicOutputPathBuildRule(
        BuildTarget buildTarget, ProjectFilesystem filesystem, SourcePathRuleFinder finder) {
      super(buildTarget, filesystem, finder, InvalidPublicOutputPathBuildRule.class);
      this.path = new PublicOutputPath(getOutputPathResolver().getTempPath("foo"));
    }

    @Override
    public ImmutableList<Step> getBuildSteps(
        BuildContext buildContext,
        ProjectFilesystem filesystem,
        OutputPathResolver outputPathResolver,
        BuildCellRelativePathFactory buildCellPathFactory) {
      return ImmutableList.of();
    }
  }
}

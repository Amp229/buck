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
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertNotNull;

import com.facebook.buck.core.build.buildable.context.FakeBuildableContext;
import com.facebook.buck.core.build.context.BuildContext;
import com.facebook.buck.core.build.context.FakeBuildContext;
import com.facebook.buck.core.filesystems.AbsPath;
import com.facebook.buck.core.filesystems.RelPath;
import com.facebook.buck.core.model.BuildTargetFactory;
import com.facebook.buck.core.model.impl.BuildTargetPaths;
import com.facebook.buck.core.rules.ActionGraphBuilder;
import com.facebook.buck.core.rules.resolver.impl.TestActionGraphBuilder;
import com.facebook.buck.io.filesystem.ProjectFilesystem;
import com.facebook.buck.io.filesystem.impl.FakeProjectFilesystem;
import com.facebook.buck.jvm.java.FakeJavaLibrary;
import com.facebook.buck.step.Step;
import com.google.common.collect.ImmutableSortedMap;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.hash.HashCode;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Optional;
import org.hamcrest.Matchers;
import org.junit.Test;

public class DexProducedFromJavaLibraryTest {
  @Test
  public void testGetBuildStepsWithD8MethodsDesugar() throws Exception {

    ProjectFilesystem filesystem = FakeProjectFilesystem.createJavaOnlyFilesystem();

    ActionGraphBuilder graphBuilder = new TestActionGraphBuilder();

    FakeJavaLibrary javaLibRule =
        new FakeJavaLibrary(
            BuildTargetFactory.newInstance("//foo:lib"), filesystem, ImmutableSortedSet.of());
    graphBuilder.addToIndex(javaLibRule);
    RelPath jarLibOutput =
        BuildTargetPaths.getGenPath(
            filesystem.getBuckPaths(), javaLibRule.getBuildTarget(), "%s.jar");
    javaLibRule.setOutputFile(jarLibOutput.toString());

    FakeJavaLibrary javaBarRule =
        new FakeJavaLibrary(
            BuildTargetFactory.newInstance("//foo:bar"),
            filesystem,
            ImmutableSortedSet.of(javaLibRule)) {
          @Override
          public ImmutableSortedMap<String, HashCode> getClassNamesToHashes() {
            return ImmutableSortedMap.of("com/example/Foo", HashCode.fromString("cafebabe"));
          }
        };
    graphBuilder.addToIndex(javaBarRule);
    RelPath jarBarOutput =
        BuildTargetPaths.getGenPath(
            filesystem.getBuckPaths(), javaBarRule.getBuildTarget(), "%s.jar");
    javaBarRule.setOutputFile(jarBarOutput.toString());

    BuildContext context =
        FakeBuildContext.withSourcePathResolver(graphBuilder.getSourcePathResolver())
            .withBuildCellRootPath(filesystem.getRootPath());
    FakeBuildableContext buildableContext = new FakeBuildableContext();

    RelPath dexOutput =
        BuildTargetPaths.getGenPath(
            filesystem.getBuckPaths(),
            javaBarRule.getBuildTarget().withFlavors(AndroidBinaryGraphEnhancer.D8_FLAVOR),
            "%s.dex.jar");
    createFiles(filesystem, dexOutput.toString(), jarLibOutput.toString(), jarBarOutput.toString());

    DexProducedFromJavaLibrary preDex =
        new DexProducedFromJavaLibrary(
            BuildTargetFactory.newInstance("//foo:bar#d8"),
            filesystem,
            graphBuilder,
            TestAndroidPlatformTargetFactory.create(),
            javaBarRule,
            1,
            ImmutableSortedSet.of(javaLibRule.getSourcePathToOutput()),
            false,
            Optional.of(27));
    List<Step> steps = preDex.getBuildSteps(context, buildableContext);
    D8Step d8Step = null;
    for (Step step : steps) {
      if (step instanceof D8Step) {
        d8Step = (D8Step) step;
        break;
      }
    }

    assertNotNull(d8Step);
    assertThat(
        d8Step.classpathFiles,
        Matchers.hasItem(
            context
                .getSourcePathResolver()
                .getAbsolutePath(javaLibRule.getSourcePathToOutput())
                .getPath()));

    assertThat(
        d8Step.getIsolatedStepDescription(null),
        Matchers.allOf(
            Matchers.containsString("--min-api 27"),
            Matchers.containsString("--no-desugaring"),
            Matchers.containsString("--intermediate"),
            Matchers.containsString("--force-jumbo"),
            Matchers.containsString("--debug")));
  }

  private void createFiles(ProjectFilesystem filesystem, String... paths) throws IOException {
    AbsPath root = filesystem.getRootPath();
    for (String path : paths) {
      AbsPath resolved = root.resolve(path);
      Files.createDirectories(resolved.getParent().getPath());
      Files.write(resolved.getPath(), "".getBytes(UTF_8));
    }
  }
}

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

package com.facebook.buck.features.python;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.junit.MatcherAssume.assumeThat;

import com.facebook.buck.core.exceptions.HumanReadableException;
import com.facebook.buck.core.model.BuildTarget;
import com.facebook.buck.core.model.BuildTargetFactory;
import com.facebook.buck.core.rules.resolver.impl.TestActionGraphBuilder;
import com.facebook.buck.core.sourcepath.FakeSourcePath;
import com.facebook.buck.util.environment.Platform;
import com.google.common.collect.ImmutableMap;
import com.google.common.jimfs.Jimfs;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.hamcrest.Matchers;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class PythonResolvedComponentsGroupTest {

  @Rule public ExpectedException thrown = ExpectedException.none();

  @Test
  public void duplicateModuleError() throws IOException {
    assumeThat(Platform.detect(), Matchers.not(Matchers.is(Platform.WINDOWS)));
    thrown.expect(HumanReadableException.class);
    thrown.expectMessage(
        Matchers.containsString(
            "found duplicate entries for module foo when creating python package"));
    FileSystem fileSystem = Jimfs.newFileSystem();
    Path foo1 = fileSystem.getPath("/target1/foo");
    Files.createDirectories(foo1.getParent());
    Files.write(foo1, "something".getBytes(StandardCharsets.UTF_8));
    Path foo2 = fileSystem.getPath("/target2/foo");
    Files.createDirectories(foo2.getParent());
    Files.write(foo2, "something else".getBytes(StandardCharsets.UTF_8));
    PythonResolvedComponentsGroup components =
        ImmutablePythonResolvedComponentsGroup.builder()
            .setCanAccessComponentContents(true)
            .putComponents(
                BuildTargetFactory.newInstance("//:target1"),
                new PythonMappedComponents.Resolved(
                    new TestActionGraphBuilder().getSourcePathResolver(),
                    ImmutableMap.of(Paths.get("foo"), FakeSourcePath.of(foo1))))
            .putComponents(
                BuildTargetFactory.newInstance("//:target2"),
                new PythonMappedComponents.Resolved(
                    new TestActionGraphBuilder().getSourcePathResolver(),
                    ImmutableMap.of(Paths.get("foo"), FakeSourcePath.of(foo2))))
            .build();
    components.forEachModule(Optional.empty(), (dst, src) -> {});
  }

  @Test
  public void testDuplicateIdenticalSourcesInComponentsIsOk() throws IOException {
    PythonResolvedComponentsGroup components =
        ImmutablePythonResolvedComponentsGroup.builder()
            .setCanAccessComponentContents(true)
            .putComponents(
                BuildTargetFactory.newInstance("//:target1"),
                new PythonMappedComponents.Resolved(
                    new TestActionGraphBuilder().getSourcePathResolver(),
                    ImmutableMap.of(
                        Paths.get("foo"),
                        FakeSourcePath.of(Paths.get("target/foo").toAbsolutePath()))))
            .putComponents(
                BuildTargetFactory.newInstance("//:target2"),
                new PythonMappedComponents.Resolved(
                    new TestActionGraphBuilder().getSourcePathResolver(),
                    ImmutableMap.of(
                        Paths.get("foo"),
                        FakeSourcePath.of(Paths.get("target/foo").toAbsolutePath()))))
            .build();
    // Use an ImmutableMap to verify we don't propagate duplicate entries for the duplicate module.
    ImmutableMap.Builder<Path, Path> builder = ImmutableMap.builder();
    components.forEachModule(Optional.empty(), builder::put);
    builder.build();
  }

  @Test
  public void defaultInitPy() throws IOException {
    BuildTarget target1 = BuildTargetFactory.newInstance("//:target1");
    BuildTarget target2 = BuildTargetFactory.newInstance("//:target2");
    PythonResolvedComponentsGroup components =
        ImmutablePythonResolvedComponentsGroup.builder()
            .setCanAccessComponentContents(true)
            .putComponents(
                target1,
                new PythonMappedComponents.Resolved(
                    new TestActionGraphBuilder().getSourcePathResolver(),
                    ImmutableMap.of(
                        Paths.get("foo/src.py"),
                            FakeSourcePath.of(Paths.get("target1/src.py").toAbsolutePath()),
                        Paths.get("src.py"),
                            FakeSourcePath.of(Paths.get("target1/src.py").toAbsolutePath()))))
            .putComponents(
                target2,
                new PythonMappedComponents.Resolved(
                    new TestActionGraphBuilder().getSourcePathResolver(),
                    ImmutableMap.of(
                        Paths.get("bar/src.py"),
                        FakeSourcePath.of(Paths.get("target2/src.py").toAbsolutePath()),
                        Paths.get("bar/__init__.py"),
                        FakeSourcePath.of(Paths.get("target2/__init__.py").toAbsolutePath()))))
            .build();
    Map<Path, Path> modules = new HashMap<>();
    components.forEachModule(Optional.of(Paths.get("default/__init__.py")), modules::put);
    assertThat(modules.keySet(), Matchers.hasItem(Paths.get("foo/__init__.py")));
  }
}

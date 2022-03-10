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

package com.facebook.buck.maven;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.facebook.buck.core.build.buildable.context.BuildableContext;
import com.facebook.buck.core.build.context.BuildContext;
import com.facebook.buck.core.filesystems.AbsPath;
import com.facebook.buck.core.model.BuildTargetFactory;
import com.facebook.buck.core.model.impl.BuildTargetPaths;
import com.facebook.buck.core.rulekey.AddToRuleKey;
import com.facebook.buck.core.rules.ActionGraphBuilder;
import com.facebook.buck.core.rules.BuildRule;
import com.facebook.buck.core.rules.impl.AbstractBuildRule;
import com.facebook.buck.core.rules.resolver.impl.TestActionGraphBuilder;
import com.facebook.buck.core.sourcepath.ExplicitBuildTargetSourcePath;
import com.facebook.buck.core.sourcepath.SourcePath;
import com.facebook.buck.io.filesystem.ProjectFilesystem;
import com.facebook.buck.io.filesystem.impl.FakeProjectFilesystem;
import com.facebook.buck.jvm.core.HasMavenCoordinates;
import com.facebook.buck.jvm.java.MavenPublishable;
import com.facebook.buck.step.Step;
import com.facebook.buck.testutil.TemporaryPaths;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSortedSet;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.SortedSet;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.apache.maven.model.io.xpp3.MavenXpp3Writer;
import org.junit.Rule;
import org.junit.Test;

public class PomIntegrationTest {

  private static final MavenXpp3Writer MAVEN_XPP_3_WRITER = new MavenXpp3Writer();
  private static final MavenXpp3Reader MAVEN_XPP_3_READER = new MavenXpp3Reader();
  private static final String URL = "http://example.com";

  @Rule public TemporaryPaths tmp = new TemporaryPaths();
  private final ActionGraphBuilder graphBuilder = new TestActionGraphBuilder();
  private final ProjectFilesystem filesystem = FakeProjectFilesystem.createRealTempFilesystem();

  @Test
  public void testMultipleInvocation() throws Exception {
    // Setup: deps: com.example:with-deps:jar:1.0 -> com.othercorp:no-deps:jar:1.0
    BuildRule dep = createMavenPublishable("//example:dep", "com.othercorp:no-deps:1.0");

    MavenPublishable item =
        createMavenPublishable("//example:has-deps", "com.example:with-deps:1.0", dep);

    AbsPath pomPath = tmp.getRoot().resolve("pom.xml");
    assertFalse(Files.exists(pomPath.getPath()));

    // Basic case
    Pom.generatePomFile(item, pomPath);

    Model pomModel = parse(pomPath);
    assertEquals("com.example", pomModel.getGroupId());
    assertEquals("with-deps", pomModel.getArtifactId());
    assertEquals("1.0", pomModel.getVersion());
    List<Dependency> dependencies = pomModel.getDependencies();
    assertEquals(1, dependencies.size());
    Dependency dependency = dependencies.get(0);
    assertEquals("com.othercorp", dependency.getGroupId());
    assertEquals("no-deps", dependency.getArtifactId());
    assertEquals("1.0", dependency.getVersion());

    // Corrupt dependency data and ensure buck restores that
    removeDependencies(pomModel, pomPath);

    Pom.generatePomFile(item, pomPath);
    pomModel = parse(pomPath);

    // Add extra pom data and ensure buck preserves that
    pomModel.setUrl(URL);
    serializePom(pomModel, pomPath);

    Pom.generatePomFile(item, pomPath);

    pomModel = parse(pomPath);
    assertEquals(URL, pomModel.getUrl());
  }

  private MavenPublishable createMavenPublishable(
      String target, String mavenCoords, BuildRule... deps) {
    return graphBuilder.addToIndex(new PublishedViaMaven(target, filesystem, mavenCoords, deps));
  }

  private static void serializePom(Model pomModel, AbsPath destination) throws IOException {
    try (BufferedWriter writer =
        Files.newBufferedWriter(destination.getPath(), StandardCharsets.UTF_8)) {
      MAVEN_XPP_3_WRITER.write(writer, pomModel);
    }
  }

  private static void removeDependencies(Model model, AbsPath pomFile) throws IOException {
    model.setDependencies(Collections.emptyList());
    serializePom(model, pomFile);
  }

  private static Model parse(AbsPath pomFile) throws Exception {
    return parse(pomFile.getPath());
  }

  private static Model parse(Path pomFile) throws Exception {
    assertTrue(Files.isRegularFile(pomFile));

    try (Reader reader = Files.newBufferedReader(pomFile, StandardCharsets.UTF_8)) {
      return MAVEN_XPP_3_READER.read(reader);
    }
  }

  private static class PublishedViaMaven extends AbstractBuildRule implements MavenPublishable {
    @AddToRuleKey private final String coords;
    private final ImmutableSortedSet<BuildRule> deps;

    public PublishedViaMaven(
        String target, ProjectFilesystem filesystem, String coords, BuildRule... deps) {
      super(BuildTargetFactory.newInstance(target), filesystem);
      this.coords = coords;
      this.deps = ImmutableSortedSet.copyOf(deps);
    }

    @Override
    public Iterable<HasMavenCoordinates> getMavenDeps() {
      return FluentIterable.from(getBuildDeps()).filter(HasMavenCoordinates.class);
    }

    @Override
    public Iterable<BuildRule> getPackagedDependencies() {
      return deps;
    }

    @Override
    public Optional<String> getMavenCoords() {
      return Optional.of(coords);
    }

    @Override
    public SortedSet<BuildRule> getBuildDeps() {
      return deps;
    }

    @Override
    public ImmutableList<Step> getBuildSteps(
        BuildContext context, BuildableContext buildableContext) {
      return ImmutableList.of();
    }

    @Override
    public SourcePath getSourcePathToOutput() {
      return ExplicitBuildTargetSourcePath.of(
          getBuildTarget(),
          BuildTargetPaths.getGenPath(
              getProjectFilesystem().getBuckPaths(), getBuildTarget(), "%s.jar"));
    }
  }
}

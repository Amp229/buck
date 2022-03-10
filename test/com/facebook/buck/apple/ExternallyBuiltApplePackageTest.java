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

package com.facebook.buck.apple;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertNotEquals;

import com.facebook.buck.core.build.buildable.context.FakeBuildableContext;
import com.facebook.buck.core.build.context.FakeBuildContext;
import com.facebook.buck.core.model.BuildTarget;
import com.facebook.buck.core.model.BuildTargetFactory;
import com.facebook.buck.core.rules.ActionGraphBuilder;
import com.facebook.buck.core.rules.resolver.impl.TestActionGraphBuilder;
import com.facebook.buck.core.sourcepath.FakeSourcePath;
import com.facebook.buck.io.filesystem.ProjectFilesystem;
import com.facebook.buck.io.filesystem.impl.FakeProjectFilesystem;
import com.facebook.buck.rules.keys.DefaultRuleKeyFactory;
import com.facebook.buck.rules.keys.TestDefaultRuleKeyFactory;
import com.facebook.buck.sandbox.NoSandboxExecutionStrategy;
import com.facebook.buck.shell.AbstractGenruleStep;
import com.facebook.buck.shell.ShellStep;
import com.facebook.buck.step.TestExecutionContext;
import com.facebook.buck.testutil.FakeFileHashCache;
import com.facebook.buck.util.environment.Platform;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.hash.HashCode;
import java.nio.file.Paths;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

public class ExternallyBuiltApplePackageTest {

  private String bundleLocation;
  private BuildTarget buildTarget;
  private ProjectFilesystem projectFilesystem;
  private ActionGraphBuilder graphBuilder;
  private ExternallyBuiltApplePackage.ApplePackageConfigAndPlatformInfo config;
  private final Platform platform = Platform.detect();

  @Before
  public void setUp() {
    Assume.assumeThat(Platform.detect(), not(Platform.WINDOWS));
    bundleLocation = "Fake/Bundle/Location";
    buildTarget = BuildTargetFactory.newInstance("//foo", "package");
    projectFilesystem = new FakeProjectFilesystem();
    graphBuilder = new TestActionGraphBuilder();
    config =
        ImmutableApplePackageConfigAndPlatformInfo.ofImpl(
            AppleConfig.ApplePackageConfig.of("echo $SDKROOT $OUT", "api"),
            FakeAppleRuleDescriptions.DEFAULT_IPHONEOS_ARMV7_PLATFORM);
  }

  @Test
  public void sdkrootEnvironmentVariableIsSet() {
    ExternallyBuiltApplePackage rule =
        new ExternallyBuiltApplePackage(
            buildTarget,
            projectFilesystem,
            new NoSandboxExecutionStrategy(),
            graphBuilder,
            config,
            FakeSourcePath.of(bundleLocation),
            true,
            Optional.empty(),
            Optional.empty(),
            false);
    graphBuilder.addToIndex(rule);
    ShellStep step =
        Iterables.getOnlyElement(
            Iterables.filter(
                rule.getBuildSteps(
                    FakeBuildContext.withSourcePathResolver(graphBuilder.getSourcePathResolver()),
                    new FakeBuildableContext()),
                AbstractGenruleStep.class));
    assertThat(
        step.getEnvironmentVariables(platform),
        hasEntry(
            "SDKROOT",
            FakeAppleRuleDescriptions.DEFAULT_IPHONEOS_ARMV7_PLATFORM
                .getAppleSdkPaths()
                .getSdkPath()
                .toString()));
  }

  @Test
  public void outputContainsCorrectExtension() {
    ExternallyBuiltApplePackage rule =
        new ExternallyBuiltApplePackage(
            buildTarget,
            projectFilesystem,
            new NoSandboxExecutionStrategy(),
            graphBuilder,
            config,
            FakeSourcePath.of("Fake/Bundle/Location"),
            true,
            Optional.empty(),
            Optional.empty(),
            false);
    graphBuilder.addToIndex(rule);
    assertThat(
        graphBuilder
            .getSourcePathResolver()
            .getCellUnsafeRelPath(Objects.requireNonNull(rule.getSourcePathToOutput()))
            .toString(),
        endsWith(".api"));
  }

  @Test
  public void commandContainsCorrectCommand() {
    ExternallyBuiltApplePackage rule =
        new ExternallyBuiltApplePackage(
            buildTarget,
            projectFilesystem,
            new NoSandboxExecutionStrategy(),
            graphBuilder,
            config,
            FakeSourcePath.of("Fake/Bundle/Location"),
            true,
            Optional.empty(),
            Optional.empty(),
            false);
    graphBuilder.addToIndex(rule);
    AbstractGenruleStep step =
        Iterables.getOnlyElement(
            Iterables.filter(
                rule.getBuildSteps(
                    FakeBuildContext.withSourcePathResolver(graphBuilder.getSourcePathResolver()),
                    new FakeBuildableContext()),
                AbstractGenruleStep.class));
    assertThat(
        step.getScriptFileContents(TestExecutionContext.newInstance()),
        is(equalTo("echo $SDKROOT $OUT")));
  }

  @Test
  public void platformVersionAffectsRuleKey() {
    Function<String, ExternallyBuiltApplePackage> packageWithVersion =
        input ->
            new ExternallyBuiltApplePackage(
                buildTarget,
                projectFilesystem,
                new NoSandboxExecutionStrategy(),
                graphBuilder,
                config.withPlatform(config.getPlatform().withBuildVersion(Optional.of(input))),
                FakeSourcePath.of("Fake/Bundle/Location"),
                true,
                Optional.empty(),
                Optional.empty(),
                false);
    assertNotEquals(
        newRuleKeyFactory().build(packageWithVersion.apply("real")),
        newRuleKeyFactory().build(packageWithVersion.apply("fake")));
  }

  @Test
  public void sdkVersionAffectsRuleKey() {
    Function<String, ExternallyBuiltApplePackage> packageWithSdkVersion =
        input ->
            new ExternallyBuiltApplePackage(
                buildTarget,
                projectFilesystem,
                new NoSandboxExecutionStrategy(),
                graphBuilder,
                config.withPlatform(
                    config
                        .getPlatform()
                        .withAppleSdk(config.getPlatform().getAppleSdk().withVersion(input))),
                FakeSourcePath.of("Fake/Bundle/Location"),
                true,
                Optional.empty(),
                Optional.empty(),
                false);
    assertNotEquals(
        newRuleKeyFactory().build(packageWithSdkVersion.apply("real")),
        newRuleKeyFactory().build(packageWithSdkVersion.apply("fake")));
  }

  private DefaultRuleKeyFactory newRuleKeyFactory() {
    return new TestDefaultRuleKeyFactory(
        new FakeFileHashCache(
            ImmutableMap.of(Paths.get(bundleLocation).toAbsolutePath(), HashCode.fromInt(5))),
        graphBuilder);
  }
}

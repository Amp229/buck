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

package com.facebook.buck.features.go;

import com.facebook.buck.core.build.buildable.context.BuildableContext;
import com.facebook.buck.core.build.context.BuildContext;
import com.facebook.buck.core.build.execution.context.StepExecutionContext;
import com.facebook.buck.core.filesystems.RelPath;
import com.facebook.buck.core.model.BuildTarget;
import com.facebook.buck.core.model.OutputLabel;
import com.facebook.buck.core.model.impl.BuildTargetPaths;
import com.facebook.buck.core.rules.BuildRule;
import com.facebook.buck.core.rules.BuildRuleParams;
import com.facebook.buck.core.rules.BuildRuleResolver;
import com.facebook.buck.core.rules.attr.HasRuntimeDeps;
import com.facebook.buck.core.rules.common.BuildableSupport;
import com.facebook.buck.core.rules.impl.AbstractBuildRuleWithDeclaredAndExtraDeps;
import com.facebook.buck.core.rules.tool.BinaryBuildRule;
import com.facebook.buck.core.sourcepath.ForwardingBuildTargetSourcePath;
import com.facebook.buck.core.sourcepath.SourcePath;
import com.facebook.buck.core.sourcepath.resolver.SourcePathResolverAdapter;
import com.facebook.buck.core.test.rule.ExternalTestRunnerRule;
import com.facebook.buck.core.test.rule.ExternalTestRunnerTestSpec;
import com.facebook.buck.core.test.rule.ExternalTestSpec;
import com.facebook.buck.core.test.rule.TestRule;
import com.facebook.buck.core.toolchain.tool.Tool;
import com.facebook.buck.features.go.GoTestCoverStep.Mode;
import com.facebook.buck.io.filesystem.BuildCellRelativePath;
import com.facebook.buck.io.filesystem.ProjectFilesystem;
import com.facebook.buck.rules.args.Arg;
import com.facebook.buck.step.Step;
import com.facebook.buck.step.fs.MakeCleanDirectoryStep;
import com.facebook.buck.test.TestCaseSummary;
import com.facebook.buck.test.TestResultSummary;
import com.facebook.buck.test.TestResults;
import com.facebook.buck.test.TestRunningOptions;
import com.facebook.buck.test.result.type.ResultType;
import com.facebook.buck.util.collect.MoreSets;
import com.google.common.base.Joiner;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Streams;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import javax.annotation.Nullable;

@SuppressWarnings("PMD.TestClassWithoutTestCases")
public class GoTest extends AbstractBuildRuleWithDeclaredAndExtraDeps
    implements TestRule, HasRuntimeDeps, ExternalTestRunnerRule, BinaryBuildRule {

  private static final Pattern TEST_START_PATTERN = Pattern.compile("^=== RUN\\s+(?<name>.*)$");
  private static final Pattern TEST_FINISHED_PATTERN =
      Pattern.compile(
          "^\\s*--- (?<status>PASS|FAIL|SKIP): (?<name>.+) \\((?<duration>\\d+\\.\\d+)(?: seconds|s)\\)$");
  // Extra time to wait for the process to exit on top of the test timeout
  private static final int PROCESS_TIMEOUT_EXTRA_MS = 5000;
  private static final String NON_PRINTABLE_REPLACEMENT = "囧";

  private final GoBinary testMain;

  private final ImmutableSet<String> labels;
  private final Optional<Long> testRuleTimeoutMs;
  private final ImmutableSet<String> contacts;
  private final ImmutableMap<String, Arg> env;
  private final boolean runTestsSeparately;
  private final ImmutableSortedSet<SourcePath> resources;
  private final Mode coverageMode;
  private final boolean withDownwardApi;

  public GoTest(
      BuildTarget buildTarget,
      ProjectFilesystem projectFilesystem,
      BuildRuleParams buildRuleParams,
      GoBinary testMain,
      ImmutableSet<String> labels,
      ImmutableSet<String> contacts,
      Optional<Long> testRuleTimeoutMs,
      ImmutableMap<String, Arg> env,
      boolean runTestsSeparately,
      ImmutableSortedSet<SourcePath> resources,
      Mode coverageMode,
      boolean withDownwardApi) {
    super(buildTarget, projectFilesystem, buildRuleParams);
    this.testMain = testMain;
    this.labels = labels;
    this.contacts = contacts;
    this.testRuleTimeoutMs = testRuleTimeoutMs;
    this.env = env;
    this.runTestsSeparately = runTestsSeparately;
    this.resources = resources;
    this.coverageMode = coverageMode;
    this.withDownwardApi = withDownwardApi;
  }

  @Override
  public ImmutableList<Step> runTests(
      StepExecutionContext executionContext,
      TestRunningOptions options,
      BuildContext buildContext,
      TestReportingCallback testReportingCallback) {
    Optional<Long> processTimeoutMs =
        testRuleTimeoutMs.map(timeout -> timeout + PROCESS_TIMEOUT_EXTRA_MS);

    SourcePathResolverAdapter resolver = buildContext.getSourcePathResolver();
    ImmutableList.Builder<String> args = ImmutableList.builder();
    args.addAll(
        testMain.getExecutableCommand(OutputLabel.defaultLabel()).getCommandPrefix(resolver));
    args.add("-test.v");
    if (coverageMode != Mode.NONE) {
      Path coverProfile =
          getProjectFilesystem().resolve(getPathToTestOutputDirectory()).resolve("coverage");
      args.add("-test.coverprofile", coverProfile.toString());
    }
    if (testRuleTimeoutMs.isPresent()) {
      args.add("-test.timeout", testRuleTimeoutMs.get() + "ms");
    }

    return new ImmutableList.Builder<Step>()
        .addAll(
            MakeCleanDirectoryStep.of(
                BuildCellRelativePath.fromCellRelativePath(
                    buildContext.getBuildCellRootPath(),
                    getProjectFilesystem(),
                    getPathToTestOutputDirectory())))
        .add(
            new GoTestStep(
                getProjectFilesystem(),
                // Note: the working directory here is important for tests
                // targets that have resources defined on them.
                getPathToTestWorkingDirectory().getPath(),
                args.build(),
                Stream.of(
                        testMain
                            .getExecutableCommand(OutputLabel.defaultLabel())
                            .getEnvironment(resolver),
                        Arg.stringify(env, resolver))
                    .flatMap(m -> m.entrySet().stream())
                    .collect(ImmutableMap.toImmutableMap(Entry::getKey, Entry::getValue)),
                getPathToTestExitCode(),
                processTimeoutMs,
                getPathToTestResults(),
                withDownwardApi))
        .build();
  }

  private ImmutableList<TestResultSummary> parseTestResults() throws IOException {
    ImmutableList.Builder<TestResultSummary> summariesBuilder = ImmutableList.builder();
    CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder();
    decoder.onMalformedInput(CodingErrorAction.REPLACE);
    decoder.replaceWith(NON_PRINTABLE_REPLACEMENT);
    try (BufferedReader reader =
        new BufferedReader(
            new InputStreamReader(
                Files.newInputStream(getProjectFilesystem().resolve(getPathToTestResults())),
                decoder))) {
      Set<String> currentTests = new HashSet<>();
      List<String> stdout = new ArrayList<>();
      List<String> stackTrace = new ArrayList<>();
      String line = reader.readLine();
      while (line != null) {
        Matcher matcher;
        if ((matcher = TEST_START_PATTERN.matcher(line)).matches()) {
          currentTests.add(matcher.group("name"));
          line = reader.readLine();
        } else if ((matcher = TEST_FINISHED_PATTERN.matcher(line)).matches()) {
          if (!currentTests.contains(matcher.group("name"))) {
            throw new RuntimeException(
                String.format(
                    "Error parsing test output: test case end '%s' does not match any start in [%s]",
                    matcher.group("name"), Joiner.on(", ").join(currentTests)));
          }

          ResultType result = ResultType.FAILURE;
          if ("PASS".equals(matcher.group("status"))) {
            result = ResultType.SUCCESS;
          } else if ("SKIP".equals(matcher.group("status"))) {
            result = ResultType.ASSUMPTION_VIOLATION;
          }

          double timeTaken = 0.0;
          try {
            timeTaken = Float.parseFloat(matcher.group("duration"));
          } catch (NumberFormatException ex) {
            Throwables.throwIfUnchecked(ex);
          }
          line = reader.readLine();
          // Looking ahead to capture error messages
          while (line != null
              && !TEST_START_PATTERN.matcher(line).matches()
              && !TEST_FINISHED_PATTERN.matcher(line).matches()
              && !line.equals("PASS")
              && !line.equals("FAIL")) {
            stackTrace.add(line);
            line = reader.readLine();
          }
          summariesBuilder.add(
              new TestResultSummary(
                  "go_test",
                  matcher.group("name"),
                  result,
                  (long) (timeTaken * 1000),
                  "",
                  Joiner.on(System.lineSeparator()).join(stackTrace),
                  Joiner.on(System.lineSeparator()).join(stdout),
                  ""));

          currentTests.remove(matcher.group("name"));
          stdout.clear();
          stackTrace.clear();
        } else {
          stdout.add(line);
          line = reader.readLine();
        }
      }

      for (String testName : currentTests) {
        // This can happen in case of e.g. a panic.
        summariesBuilder.add(
            new TestResultSummary(
                "go_test",
                testName,
                ResultType.FAILURE,
                0,
                "incomplete",
                "",
                Joiner.on(System.lineSeparator()).join(stdout),
                ""));
      }
    }
    return summariesBuilder.build();
  }

  @Override
  public Callable<TestResults> interpretTestResults(
      StepExecutionContext executionContext,
      SourcePathResolverAdapter pathResolver,
      boolean isUsingTestSelectors) {
    return () ->
        TestResults.of(
            getBuildTarget(),
            ImmutableList.of(
                new TestCaseSummary(getBuildTarget().getFullyQualifiedName(), parseTestResults())),
            contacts,
            labels.stream().map(Object::toString).collect(ImmutableSet.toImmutableSet()));
  }

  @Override
  public ImmutableSet<String> getLabels() {
    return labels;
  }

  @Override
  public ImmutableSet<String> getContacts() {
    return contacts;
  }

  @Override
  public Optional<String> getOncall() {
    return MoreSets.only(getContacts());
  }

  @Override
  public Path getPathToTestOutputDirectory() {
    return BuildTargetPaths.getGenPath(
            getProjectFilesystem().getBuckPaths(), getBuildTarget(), "__test_%s_output__")
        .getPath();
  }

  @Override
  public ImmutableList<? extends Step> getBuildSteps(
      BuildContext context, BuildableContext buildableContext) {
    return ImmutableList.of();
  }

  @Override
  @Nullable
  public SourcePath getSourcePathToOutput() {
    SourcePath sourcePath = testMain.getSourcePathToOutput();
    if (sourcePath == null) {
      return null;
    }
    return ForwardingBuildTargetSourcePath.of(getBuildTarget(), sourcePath);
  }

  protected Path getPathToTestResults() {
    return getPathToTestOutputDirectory().resolve("results");
  }

  protected RelPath getPathToTestWorkingDirectory() {
    return testMain.getPathToBinaryDirectory();
  }

  protected Path getPathToTestExitCode() {
    return getPathToTestOutputDirectory().resolve("exitCode");
  }

  @Override
  public boolean runTestSeparately() {
    return runTestsSeparately;
  }

  @Override
  public boolean supportsStreamingTests() {
    return false;
  }

  @Override
  public Stream<BuildTarget> getRuntimeDeps(BuildRuleResolver buildRuleResolver) {
    return Streams.concat(
        Stream.of((testMain.getBuildTarget())),
        resources.stream()
            .map(buildRuleResolver::filterBuildRuleInputs)
            .flatMap(ImmutableSet::stream)
            .map(BuildRule::getBuildTarget),
        env.values().stream()
            .flatMap(arg -> BuildableSupport.deriveDeps(arg, buildRuleResolver))
            .map(BuildRule::getBuildTarget));
  }

  @Override
  public ExternalTestSpec getExternalTestRunnerSpec(
      StepExecutionContext executionContext,
      TestRunningOptions testRunningOptions,
      BuildContext buildContext) {
    return ExternalTestRunnerTestSpec.builder()
        .setCwd(getProjectFilesystem().getRootPath().getPath())
        .setTarget(getBuildTarget())
        .setType("go")
        .putAllEnv(
            testMain
                .getExecutableCommand(OutputLabel.defaultLabel())
                .getEnvironment(buildContext.getSourcePathResolver()))
        .putAllEnv(Arg.stringify(env, buildContext.getSourcePathResolver()))
        .addAllCommand(
            testMain
                .getExecutableCommand(OutputLabel.defaultLabel())
                .getCommandPrefix(buildContext.getSourcePathResolver()))
        .addAllLabels(getLabels())
        .addAllContacts(getContacts())
        .setOncall(getOncall())
        .build();
  }

  @Override
  public Tool getExecutableCommand(OutputLabel outputLabel) {
    return testMain.getExecutableCommand(OutputLabel.defaultLabel());
  }
}

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

package com.facebook.buck.cli.cquery;

import static com.facebook.buck.testutil.integration.ProcessOutputAssertions.assertJSONOutputMatchesFileContents;
import static com.facebook.buck.testutil.integration.ProcessOutputAssertions.assertOutputMatches;
import static com.facebook.buck.testutil.integration.ProcessOutputAssertions.assertOutputMatchesExactly;
import static com.facebook.buck.testutil.integration.ProcessOutputAssertions.assertOutputMatchesFileContents;
import static com.facebook.buck.testutil.integration.ProcessOutputAssertions.assertOutputMatchesFileContentsExactly;
import static com.facebook.buck.testutil.integration.ProcessOutputAssertions.assertOutputMatchesPaths;
import static org.junit.Assert.assertEquals;

import com.facebook.buck.cli.ThriftOutputUtils;
import com.facebook.buck.query.thrift.DirectedAcyclicGraph;
import com.facebook.buck.query.thrift.DirectedAcyclicGraphNode;
import com.facebook.buck.testutil.ProcessResult;
import com.facebook.buck.testutil.TemporaryPaths;
import com.facebook.buck.testutil.integration.ProjectWorkspace;
import com.facebook.buck.testutil.integration.TestDataHelper;
import com.google.common.collect.ImmutableSet;
import java.io.IOException;
import java.util.Map;
import org.junit.Rule;
import org.junit.Test;

public class ConfiguredQueryCommandIntegrationTest {

  @Rule public TemporaryPaths tmp = new TemporaryPaths();

  /**
   * =============================================================================================
   * ====================================== Output Formats =======================================
   * =============================================================================================
   */
  @Test
  public void basicTargetPrinting() throws IOException {
    ProjectWorkspace workspace =
        TestDataHelper.createProjectWorkspaceForScenario(this, "sample_apple", tmp);
    workspace.setUp();

    ProcessResult result = workspace.runBuckCommand("cquery", "//lib:foo");
    // TODO(srice): We shouldn't expect it to print a readable name, but until we know what the hash
    // is going to be it doesn't matter what we put here.
    assertOutputMatches("//lib:foo (//config/platform:ios)", result);
  }

  @Test
  public void basicJsonPrinting() throws IOException {
    ProjectWorkspace workspace =
        TestDataHelper.createProjectWorkspaceForScenario(this, "sample_apple", tmp);
    workspace.setUp();

    ProcessResult result =
        workspace.runBuckCommand(
            "cquery", "//lib/...", "--target-universe", "//bin:mac-bin", "--output-format", "json");
    assertJSONOutputMatchesFileContents("stdout-basic-json-printing.json", result, workspace);
  }

  @Test
  public void basicAttributePrinting() throws IOException {
    ProjectWorkspace workspace =
        TestDataHelper.createProjectWorkspaceForScenario(this, "sample_apple", tmp);
    workspace.setUp();

    ProcessResult result =
        workspace.runBuckCommand(
            "cquery",
            "//lib/...",
            "--target-universe",
            "//bin:mac-bin",
            "--output-attribute",
            "buck.type",
            // Also test `-a` alias
            "-a",
            "srcs");
    assertJSONOutputMatchesFileContents("stdout-basic-attribute-printing.json", result, workspace);
  }

  @Test
  public void outputAllAttributes() throws IOException {
    ProjectWorkspace workspace =
        TestDataHelper.createProjectWorkspaceForScenario(this, "output_all_attributes", tmp);
    workspace.setUp();

    for (String arg : new String[] {"-A", "--output-all-attributes"}) {
      ProcessResult result =
          workspace.runBuckCommand("cquery", "//...", "--target-universe", "//:gr", arg);
      assertJSONOutputMatchesFileContents("stdout.json", result, workspace);
    }
  }

  @Test
  public void basicDotPrinting() throws IOException {
    ProjectWorkspace workspace =
        TestDataHelper.createProjectWorkspaceForScenario(this, "sample_apple", tmp);
    workspace.setUp();

    ProcessResult result =
        workspace.runBuckCommand(
            "cquery",
            "//lib/...",
            "--target-universe",
            "//bin:ios-bin,//bin:mac-bin",
            "--output-format",
            "dot");
    assertOutputMatchesFileContents("stdout-basic-dot-printing", result, workspace);
  }

  @Test
  public void basicDotAttributePrinting() throws IOException {
    ProjectWorkspace workspace =
        TestDataHelper.createProjectWorkspaceForScenario(this, "sample_apple", tmp);
    workspace.setUp();

    ProcessResult result =
        workspace.runBuckCommand(
            "cquery",
            "//lib/...",
            "--target-universe",
            "//bin:ios-bin,//bin:mac-bin",
            "--output-format",
            "dot",
            "--output-attribute",
            "srcs");
    assertOutputMatchesFileContents("stdout-basic-dot-attribute-printing", result, workspace);
  }

  @Test
  public void basicDotCompactPrinting() throws IOException {
    ProjectWorkspace workspace =
        TestDataHelper.createProjectWorkspaceForScenario(this, "sample_apple", tmp);
    workspace.setUp();

    ProcessResult result =
        workspace.runBuckCommand(
            "cquery",
            "//lib:maconly",
            "--target-universe",
            "//bin:ios-bin,//bin:mac-bin",
            "--output-format",
            "dot_compact");
    assertOutputMatchesFileContentsExactly("stdout-basic-dot-compact-printing", result, workspace);
  }

  @Test
  public void basicDotCompactAttributePrinting() throws IOException {
    ProjectWorkspace workspace =
        TestDataHelper.createProjectWorkspaceForScenario(this, "sample_apple", tmp);
    workspace.setUp();

    ProcessResult result =
        workspace.runBuckCommand(
            "cquery",
            "//lib:maconly",
            "--target-universe",
            "//bin:ios-bin,//bin:mac-bin",
            "--output-format",
            "dot_compact",
            "--output-attribute",
            "srcs");
    assertOutputMatchesFileContentsExactly(
        "stdout-basic-dot-compact-attribute-printing", result, workspace);
  }

  @Test
  public void basicDotBfsPrinting() throws IOException {
    ProjectWorkspace workspace =
        TestDataHelper.createProjectWorkspaceForScenario(this, "sample_apple", tmp);
    workspace.setUp();

    ProcessResult result =
        workspace.runBuckCommand(
            "cquery",
            "//lib/...",
            "--target-universe",
            "//bin:mac-bin",
            "--output-format",
            "dot_bfs");
    assertOutputMatchesFileContentsExactly("stdout-basic-dot-bfs-printing", result, workspace);
  }

  @Test
  public void basicDotBfsAttributePrinting() throws IOException {
    ProjectWorkspace workspace =
        TestDataHelper.createProjectWorkspaceForScenario(this, "sample_apple", tmp);
    workspace.setUp();

    ProcessResult result =
        workspace.runBuckCommand(
            "cquery",
            "//lib/...",
            "--target-universe",
            "//bin:mac-bin",
            "--output-format",
            "dot_bfs",
            "--output-attribute",
            "srcs");
    assertOutputMatchesFileContentsExactly(
        "stdout-basic-dot-bfs-attribute-printing", result, workspace);
  }

  @Test
  public void basicDotBfsCompactPrinting() throws IOException {
    ProjectWorkspace workspace =
        TestDataHelper.createProjectWorkspaceForScenario(this, "sample_apple", tmp);
    workspace.setUp();

    ProcessResult result =
        workspace.runBuckCommand(
            "cquery",
            "//lib/...",
            "--target-universe",
            "//bin:mac-bin",
            "--output-format",
            "dot_bfs_compact");
    assertOutputMatchesFileContentsExactly(
        "stdout-basic-dot-bfs-compact-printing", result, workspace);
  }

  @Test
  public void basicDotBfsCompactAttributePrinting() throws IOException {
    ProjectWorkspace workspace =
        TestDataHelper.createProjectWorkspaceForScenario(this, "sample_apple", tmp);
    workspace.setUp();

    ProcessResult result =
        workspace.runBuckCommand(
            "cquery",
            "//lib/...",
            "--target-universe",
            "//bin:mac-bin",
            "--output-format",
            "dot_bfs_compact",
            "--output-attribute",
            "srcs");
    assertOutputMatchesFileContentsExactly(
        "stdout-basic-dot-bfs-compact-attribute-printing", result, workspace);
  }

  @Test
  public void basicThriftPrinting() throws IOException {
    ProjectWorkspace workspace =
        TestDataHelper.createProjectWorkspaceForScenario(this, "sample_apple", tmp);
    workspace.setUp();

    ProcessResult result =
        workspace.runBuckCommand(
            "cquery",
            "//lib/...",
            "--target-universe",
            "//bin:ios-bin,//bin:mac-bin",
            "--output-format",
            "thrift");

    result.assertSuccess();
    DirectedAcyclicGraph thriftDag = ThriftOutputUtils.parseThriftDag(result.getStdout());
    assertEquals(
        ImmutableSet.copyOf(ThriftOutputUtils.nodesToStringList(thriftDag)),
        ImmutableSet.of(
            "//lib:bar (//config/platform:ios)",
            "//lib:bar (//config/platform:macos)",
            "//lib:foo (//config/platform:ios)",
            "//lib:foo (//config/platform:macos)",
            "//lib:maconly (//config/platform:macos)"));
    assertEquals(
        ImmutableSet.copyOf(ThriftOutputUtils.edgesToStringList(thriftDag)),
        ImmutableSet.of(
            "//lib:foo (//config/platform:ios)->//lib:bar (//config/platform:ios)",
            "//lib:foo (//config/platform:macos)->//lib:bar (//config/platform:macos)",
            "//lib:foo (//config/platform:macos)->//lib:maconly (//config/platform:macos)"));
  }

  @Test
  public void basicThriftAttributePrinting() throws IOException {
    ProjectWorkspace workspace =
        TestDataHelper.createProjectWorkspaceForScenario(this, "sample_apple", tmp);
    workspace.setUp();

    ProcessResult result =
        workspace.runBuckCommand(
            "cquery",
            "//lib/...",
            "--target-universe",
            "//bin:ios-bin,//bin:mac-bin",
            "--output-format",
            "thrift",
            "--output-attribute",
            "srcs");

    result.assertSuccess();
    DirectedAcyclicGraph thriftDag = ThriftOutputUtils.parseThriftDag(result.getStdout());
    // Since this is the same query as `basicThriftPrinting` (plus the additional CLI param) we're
    // relying on the previous test to validate the structure of the output.
    DirectedAcyclicGraphNode node =
        ThriftOutputUtils.findNodeByName(thriftDag, "//lib:foo (//config/platform:macos)").get();
    Map<String, String> attributes = node.getNodeAttributes();
    assertEquals(1, attributes.size());
    assertEquals("[foo-macos.m]", attributes.get("srcs"));
  }

  @Test
  public void basicMultiQueryPrinting() throws IOException {
    ProjectWorkspace workspace =
        TestDataHelper.createProjectWorkspaceForScenario(this, "sample_apple", tmp);
    workspace.setUp();

    ProcessResult result =
        workspace.runBuckCommand(
            "cquery",
            "%s",
            "--target-universe",
            "//bin:ios-bin,//bin:mac-bin",
            "//lib:foo",
            "//lib:maconly");
    assertOutputMatchesFileContents("stdout-basic-multi-query-printing", result, workspace);
  }

  @Test
  public void basicMultiQueryJsonPrinting() throws IOException {
    ProjectWorkspace workspace =
        TestDataHelper.createProjectWorkspaceForScenario(this, "sample_apple", tmp);
    workspace.setUp();

    ProcessResult result =
        workspace.runBuckCommand(
            "cquery",
            "%s",
            "--target-universe",
            "//bin:ios-bin,//bin:mac-bin",
            "--output-format",
            "json",
            "//lib:foo",
            "//lib:maconly");
    assertJSONOutputMatchesFileContents(
        "stdout-basic-multi-query-json-printing.json", result, workspace);
  }

  @Test
  public void basicMultiQueryAttributePrinting() throws IOException {
    ProjectWorkspace workspace =
        TestDataHelper.createProjectWorkspaceForScenario(this, "sample_apple", tmp);
    workspace.setUp();

    ProcessResult result =
        workspace.runBuckCommand(
            "cquery",
            "%s",
            "--target-universe",
            "//bin:mac-bin",
            "--output-attribute",
            "buck.type",
            "--output-attribute",
            "srcs",
            "//bin/...",
            "//lib/...");
    assertJSONOutputMatchesFileContents(
        "stdout-basic-multi-query-attribute-printing.json", result, workspace);
  }

  /**
   * =============================================================================================
   * =============================== General cquery functionality ================================
   * =============================================================================================
   */
  @Test
  public void configurationIncludedAsComputedAttribute() throws IOException {
    ProjectWorkspace workspace =
        TestDataHelper.createProjectWorkspaceForScenario(this, "sample_apple", tmp);
    workspace.setUp();

    ProcessResult result =
        workspace.runBuckCommand(
            "cquery",
            "//lib:bar",
            "--target-universe",
            "//bin:tvos-bin",
            "--output-attribute",
            "buck.configuration");
    assertJSONOutputMatchesFileContents(
        "stdout-configuration-included-as-computed-attribute.json", result, workspace);
  }

  @Test
  public void containsFullyQualifiedNameAttribute() throws IOException {
    ProjectWorkspace workspace =
        TestDataHelper.createProjectWorkspaceForScenario(this, "sample_apple", tmp);
    workspace.setUp();

    ProcessResult result =
        workspace.runBuckCommand(
            "cquery",
            "//lib:bar",
            "--target-universe",
            "//bin:tvos-bin",
            "--output-attribute",
            "fully_qualified_name");
    assertJSONOutputMatchesFileContents(
        "stdout-contains-fully-qualified-name-attribute.json", result, workspace);
  }

  @Test
  public void implicitTargetUniverseForRdeps() throws IOException {
    ProjectWorkspace workspace =
        TestDataHelper.createProjectWorkspaceForScenario(this, "sample_apple", tmp);
    workspace.setUp();

    // Even though `//lib:bar` has a default_target_platform of tvos, the universe is created with
    // ios-bin and therefore we match the version of bar that is configured for ios.
    ProcessResult result = workspace.runBuckCommand("cquery", "rdeps(//bin:ios-bin, //lib:bar, 0)");
    assertOutputMatches("//lib:bar (//config/platform:ios)", result);
  }

  @Test
  public void implicitTargetUniverseWithSetSubstitution() throws IOException {
    ProjectWorkspace workspace =
        TestDataHelper.createProjectWorkspaceForScenario(this, "sample_apple", tmp);
    workspace.setUp();

    // `%Ss` is short hand for `set(<args>)`, which means we should create a universe with the union
    // of all of the configs
    ProcessResult result = workspace.runBuckCommand("cquery", "%Ss", "//lib:bar", "//lib:foo");
    assertOutputMatches(
        "//lib:bar (//config/platform:ios)\n//lib:bar (//config/platform:tvos)\n//lib:foo (//config/platform:ios)",
        result);
  }

  @Test
  public void targetUniverseChangesOutput() throws IOException {
    ProjectWorkspace workspace =
        TestDataHelper.createProjectWorkspaceForScenario(this, "sample_apple", tmp);
    workspace.setUp();

    ProcessResult tvOSResult =
        workspace.runBuckCommand("cquery", "//lib:foo", "--target-universe", "//bin:tvos-bin");
    assertOutputMatches("//lib:foo (//config/platform:tvos)", tvOSResult);

    ProcessResult macOSResult =
        workspace.runBuckCommand("cquery", "//lib:foo", "--target-universe", "//bin:mac-bin");
    assertOutputMatches("//lib:foo (//config/platform:macos)", macOSResult);
  }

  @Test
  public void targetUniverseSpecifiedAsRepeatedArgument() throws IOException {
    ProjectWorkspace workspace =
        TestDataHelper.createProjectWorkspaceForScenario(this, "sample_apple", tmp);
    workspace.setUp();

    ProcessResult result =
        workspace.runBuckCommand(
            "cquery",
            "//lib:foo",
            "--target-universe",
            "//bin:tvos-bin",
            "--target-universe",
            "//bin:mac-bin");
    assertOutputMatches(
        "//lib:foo (//config/platform:macos)\n//lib:foo (//config/platform:tvos)", result);
  }

  @Test
  public void multipleLinesPrintedForOneTargetInMulitpleConfigurations() throws IOException {
    ProjectWorkspace workspace =
        TestDataHelper.createProjectWorkspaceForScenario(this, "sample_apple", tmp);
    workspace.setUp();

    ProcessResult result =
        workspace.runBuckCommand(
            "cquery", "//lib:foo", "--target-universe", "//bin:ios-bin,//bin:mac-bin");
    assertOutputMatchesFileContents(
        "stdout-multiple-lines-printed-for-one-target-in-multiple-configurations",
        result,
        workspace);
  }

  @Test
  public void
      twoTargetsWithDifferentConfigurationsInTargetUniverseBothGetPrintedWithRecursiveTargetSpec()
          throws IOException {
    ProjectWorkspace workspace =
        TestDataHelper.createProjectWorkspaceForScenario(this, "sample_apple", tmp);
    workspace.setUp();

    ProcessResult result =
        workspace.runBuckCommand(
            "cquery", "set(//lib/...)", "--target-universe", "//bin:ios-bin,//bin:tvos-bin");
    assertOutputMatchesFileContents(
        "stdout-two-targets-in-target-universe-causes-overlap-to-be-printed-in-both-configurations",
        result,
        workspace);
  }

  @Test
  public void
      twoTargetsWithDifferentConfigurationsInTargetUniverseBothGetPrintedWithSpecificTargetSpec()
          throws IOException {
    ProjectWorkspace workspace =
        TestDataHelper.createProjectWorkspaceForScenario(this, "sample_apple", tmp);
    workspace.setUp();

    ProcessResult result =
        workspace.runBuckCommand(
            "cquery", "set(//lib:foo)", "--target-universe", "//bin:ios-bin,//bin:tvos-bin");
    assertOutputMatches(
        "//lib:foo (//config/platform:ios)\n//lib:foo (//config/platform:tvos)", result);
  }

  @Test
  public void targetPlatformsArgCausesUniverseToBeCreatedWithThatPlatform() throws IOException {
    ProjectWorkspace workspace =
        TestDataHelper.createProjectWorkspaceForScenario(this, "sample_apple", tmp);
    workspace.setUp();

    ProcessResult result =
        workspace.runBuckCommand(
            "cquery",
            "//lib:foo",
            "--target-universe",
            "//bin:tvos-bin",
            "--target-platforms",
            "//config/platform:ios");
    assertOutputMatches("//lib:foo (//config/platform:ios)", result);
  }

  @Test
  public void rootRecursiveTargetSpecPrintsEveryTarget() throws IOException {
    ProjectWorkspace workspace =
        TestDataHelper.createProjectWorkspaceForScenario(this, "sample_apple", tmp);
    workspace.setUp();

    ProcessResult result = workspace.runBuckCommand("cquery", "//...");
    assertOutputMatchesFileContents(
        "stdout-root-recursive-target-spec-prints-every-target", result, workspace);
  }

  @Test
  public void buckDirectDependenciesAttributeIncludesConfiguration() throws IOException {
    ProjectWorkspace workspace =
        TestDataHelper.createProjectWorkspaceForScenario(this, "sample_apple", tmp);
    workspace.setUp();

    ProcessResult result =
        workspace.runBuckCommand(
            "cquery",
            "//lib:foo",
            "--target-universe",
            "//bin:tvos-bin",
            "--output-format",
            "json",
            "--output-attribute",
            "buck.direct_dependencies");
    assertJSONOutputMatchesFileContents(
        "stdout-buck-direct-dependencies-attribute-includes-configuration.json", result, workspace);
  }

  /**
   * =============================================================================================
   * ================================== Function specific tests ==================================
   * =============================================================================================
   */
  @Test
  public void allpathsFunctionReturnsSubgraphBetweenTwoNodes() throws IOException {
    ProjectWorkspace workspace =
        TestDataHelper.createProjectWorkspaceForScenario(this, "large_project", tmp);
    workspace.setUp();

    ProcessResult result =
        workspace.runBuckCommand(
            "cquery", "allpaths(//apps/..., //codegen/...)", "--target-universe", "//apps/...");

    assertOutputMatchesFileContents(
        "stdout-allpaths-function-returns-subgraph-between-two-nodes", result, workspace);
  }

  @Test
  public void attrfilterFunctionOnlyReturnsTargetsWithMatchingValue() throws IOException {
    ProjectWorkspace workspace =
        TestDataHelper.createProjectWorkspaceForScenario(this, "large_project", tmp);
    workspace.setUp();
    ProcessResult result =
        workspace.runBuckCommand(
            "cquery",
            "attrfilter(compiler_flags, '-Oz', //libraries/apple/...)",
            "--target-universe",
            "//apps/apple/...");

    assertOutputMatchesFileContents(
        "stdout-attrfilter-function-only-returns-targets-with-matching-value", result, workspace);
  }

  @Test
  public void attrregexfilterFunctionAppliesRegexMatchingToAttribute() throws IOException {
    ProjectWorkspace workspace =
        TestDataHelper.createProjectWorkspaceForScenario(this, "large_project", tmp);
    workspace.setUp();
    ProcessResult result =
        workspace.runBuckCommand(
            "cquery", "attrregexfilter(default_target_platform, '.*-opt', //apps/apple/...)");

    assertOutputMatchesFileContents(
        "stdout-attrregexfilter-function-applies-regex-matching-to-attribute", result, workspace);
  }

  @Test
  public void buildfileFunctionReturnsPathToBUCKFileOfTarget() throws IOException {
    ProjectWorkspace workspace =
        TestDataHelper.createProjectWorkspaceForScenario(this, "large_project", tmp);
    workspace.setUp();
    ProcessResult result = workspace.runBuckCommand("cquery", "buildfile(appletv-app-prod)");

    assertOutputMatchesPaths("apps/apple/BUCK", result);
  }

  @Test
  public void configFunctionConfiguresTargetForSpecificPlatform() throws IOException {
    ProjectWorkspace workspace =
        TestDataHelper.createProjectWorkspaceForScenario(this, "sample_apple", tmp);
    workspace.setUp();

    ProcessResult result =
        workspace.runBuckCommand(
            "cquery",
            "config(//lib:foo, //config/platform:tvos)",
            "--target-universe",
            "//bin:ios-bin,//bin:tvos-bin");
    assertOutputMatches("//lib:foo (//config/platform:tvos)", result);
  }

  @Test
  public void configFunctionConfiguresTargetForDefaultTargetPlatformIfNoSecondArgumentGiven()
      throws IOException {
    ProjectWorkspace workspace =
        TestDataHelper.createProjectWorkspaceForScenario(this, "sample_apple", tmp);
    workspace.setUp();

    ProcessResult result =
        workspace.runBuckCommand(
            "cquery", "config(//lib:bar)", "--target-universe", "//bin:ios-bin,//bin:tvos-bin");
    assertOutputMatches("//lib:bar (//config/platform:tvos)", result);
  }

  @Test
  public void configFunctionReturnsNothingWhenNodeNotInUniverse() throws IOException {
    ProjectWorkspace workspace =
        TestDataHelper.createProjectWorkspaceForScenario(this, "sample_apple", tmp);
    workspace.setUp();

    ProcessResult result =
        workspace.runBuckCommand(
            "cquery",
            "config(//lib:foo, //config/platform:tvos)",
            "--target-universe",
            "//bin:ios-bin",
            "--output-attribute",
            "buck.type");
    assertOutputMatchesExactly("{ }\n", result);
  }

  @Test
  public void depsFunctionReturnsDependenciesForConfiguredTargets() throws IOException {
    ProjectWorkspace workspace =
        TestDataHelper.createProjectWorkspaceForScenario(this, "sample_apple", tmp);
    workspace.setUp();

    ProcessResult result =
        workspace.runBuckCommand(
            "cquery", "deps(//lib:foo)", "--target-universe", "//bin:mac-bin,//bin:tvos-bin");
    assertOutputMatchesFileContents(
        "stdout-deps-function-returns-dependencies-for-configured-targets", result, workspace);
  }

  @Test
  public void filterFunctionReturnsTargetsWhoseNamesMatchRegularExpressions() throws IOException {
    ProjectWorkspace workspace =
        TestDataHelper.createProjectWorkspaceForScenario(this, "large_project", tmp);
    workspace.setUp();
    ProcessResult result =
        workspace.runBuckCommand(
            "cquery",
            "filter('-infra', //libraries/android/...)",
            "--target-universe",
            "//apps/android/...");

    assertOutputMatchesFileContents(
        "stdout-filter-function-returns-targets-whose-names-match-regular-expression",
        result,
        workspace);
  }

  @Test
  public void inputsFunctionReturnsRelativePathForSuppliedTarget() throws IOException {
    ProjectWorkspace workspace =
        TestDataHelper.createProjectWorkspaceForScenario(this, "sample_apple", tmp);
    workspace.setUp();

    ProcessResult result = workspace.runBuckCommand("cquery", "inputs(//lib:foo)");
    assertOutputMatchesPaths("lib/foo-ios.m", result);
  }

  @Test
  public void kindFunctionReturnsTargetsWhoseTypeMatchesRegex() throws IOException {
    ProjectWorkspace workspace =
        TestDataHelper.createProjectWorkspaceForScenario(this, "large_project", tmp);
    workspace.setUp();
    ProcessResult result =
        workspace.runBuckCommand("cquery", "kind(genrule, deps(//apps/android:foo-binary))");

    assertOutputMatches("//codegen:backend-types-android (//config/platform:android)", result);
  }

  @Test
  public void labelsFunctionReturnsValueOfAttribute() throws IOException {
    ProjectWorkspace workspace =
        TestDataHelper.createProjectWorkspaceForScenario(this, "large_project", tmp);
    workspace.setUp();
    ProcessResult result =
        workspace.runBuckCommand("cquery", "labels(exported_headers, //libraries/apple/...)");

    assertOutputMatchesPaths("libraries/apple/LanguageUtilities.h", result);
  }

  @Test
  public void ownerFunctionReturnsOwnerOfFileInAllConfigurationsInUniverse() throws IOException {
    ProjectWorkspace workspace =
        TestDataHelper.createProjectWorkspaceForScenario(this, "sample_apple", tmp);
    workspace.setUp();

    ProcessResult result =
        workspace.runBuckCommand(
            "cquery", "owner(lib/bar-all.m)", "--target-universe", "//bin:ios-bin,//bin:tvos-bin");
    assertOutputMatches(
        "//lib:bar (//config/platform:ios)\n//lib:bar (//config/platform:tvos)", result);
  }

  @Test
  public void ownerForFileWithOwnerThatsOutsideTargetUniverseReturnsNothing() throws IOException {
    ProjectWorkspace workspace =
        TestDataHelper.createProjectWorkspaceForScenario(this, "sample_apple", tmp);
    workspace.setUp();

    // Even though `lib/maconly.m` is unconditionally included as a source of `//lib:maconly`, that
    // target is outside the target universe and therefore the query should return no results.
    ProcessResult result =
        workspace.runBuckCommand(
            "cquery", "owner(lib/maconly.m)", "--target-universe", "//bin:tvos-bin");
    assertOutputMatches("", result);
  }

  @Test
  public void testsofFunctionReturnsNothingWhenNodeNotInUniverse() throws IOException {
    ProjectWorkspace workspace =
        TestDataHelper.createProjectWorkspaceForScenario(this, "large_project", tmp);
    workspace.setUp();
    ProcessResult result =
        workspace.runBuckCommand(
            "cquery",
            "testsof(//libraries/apple:DatabaseKit)",
            "--target-universe",
            "//libraries/...");

    assertOutputMatchesExactly("", result);
  }

  @Test
  public void testsofFunctionReturnsValueOfTestsAttribute() throws IOException {
    ProjectWorkspace workspace =
        TestDataHelper.createProjectWorkspaceForScenario(this, "large_project", tmp);
    workspace.setUp();
    ProcessResult result =
        workspace.runBuckCommand(
            "cquery",
            "testsof(deps(set(//apps/android/... //apps/apple/...)))",
            "--target-universe",
            "//...");

    assertOutputMatchesFileContents(
        "stdout-testsof-function-returns-value-of-tests-attribute", result, workspace);
  }
}

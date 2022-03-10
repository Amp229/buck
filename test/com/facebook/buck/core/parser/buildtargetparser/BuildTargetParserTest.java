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

package com.facebook.buck.core.parser.buildtargetparser;

import static com.facebook.buck.core.cell.TestCellBuilder.createCellRoots;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItems;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.facebook.buck.core.cell.CellPathResolver;
import com.facebook.buck.core.cell.TestCellPathResolver;
import com.facebook.buck.core.exceptions.BuildTargetParseException;
import com.facebook.buck.core.filesystems.ForwardRelPath;
import com.facebook.buck.core.model.BaseName;
import com.facebook.buck.core.model.CellRelativePath;
import com.facebook.buck.core.model.InternalFlavor;
import com.facebook.buck.core.model.UnconfiguredBuildTarget;
import com.facebook.buck.parser.exceptions.NoSuchBuildTargetException;
import com.facebook.buck.util.types.Either;
import com.google.common.collect.ImmutableMap;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class BuildTargetParserTest {

  private final BuildTargetParser parser = BuildTargetParser.INSTANCE;

  @Rule public ExpectedException exception = ExpectedException.none();

  @Test
  public void testParseRootRule() {
    // Parse "//:fb4a" with the BuildTargetParser and test all of its observers.
    UnconfiguredBuildTarget buildTarget =
        parser.parseTarget("//:fb4a", null, createCellRoots(null).getCellNameResolver());
    assertEquals("fb4a", buildTarget.getShortNameAndFlavorPostfix());
    assertEquals("//", buildTarget.getBaseName().toString());
    assertEquals(ForwardRelPath.of(""), buildTarget.getCellRelativeBasePath().getPath());
    assertEquals("//:fb4a", buildTarget.getFullyQualifiedName());
  }

  @Test
  public void testParseRuleWithFlavors() {
    UnconfiguredBuildTarget buildTarget =
        parser.parseTarget("//:lib#foo,bar", null, createCellRoots(null).getCellNameResolver());
    // Note the sort order.
    assertEquals("lib#bar,foo", buildTarget.getShortNameAndFlavorPostfix());
    assertEquals("//", buildTarget.getBaseName().toString());
    assertEquals(
        Paths.get(""), buildTarget.getCellRelativeBasePath().getPath().toPathDefaultFileSystem());
    // Note the sort order.
    assertEquals("//:lib#bar,foo", buildTarget.getFullyQualifiedName());
    assertThat(
        buildTarget.getFlavors().getSet(),
        hasItems(InternalFlavor.of("foo"), InternalFlavor.of("bar")));
  }

  @Test
  public void testParseValidTargetWithDots() {
    UnconfiguredBuildTarget buildTarget =
        parser.parseTarget(
            "//..a/b../a...b:assets", null, createCellRoots(null).getCellNameResolver());
    assertEquals("assets", buildTarget.getShortNameAndFlavorPostfix());
    assertEquals("//..a/b../a...b", buildTarget.getBaseName().toString());
    assertEquals(
        Paths.get("..a", "b..", "a...b"),
        buildTarget.getCellRelativeBasePath().getPath().toPathDefaultFileSystem());
    assertEquals("//..a/b../a...b:assets", buildTarget.getFullyQualifiedName());
  }

  @Test
  public void testParsePathWithDot() {
    exception.expect(BuildTargetParseException.class);
    exception.expectMessage("incorrect base name: //..");
    parser.parseTarget("//.:assets", null, createCellRoots(null).getCellNameResolver());
  }

  @Test
  public void testParsePathWithDotDot() {
    exception.expect(BuildTargetParseException.class);
    exception.expectMessage("incorrect base name: //../facebookorca");
    parser.parseTarget(
        "//../facebookorca:assets", null, createCellRoots(null).getCellNameResolver());
  }

  @Test
  public void testParseAbsolutePath() {
    exception.expect(BuildTargetParseException.class);
    exception.expectMessage("incorrect base name: ///facebookorca");
    parser.parseTarget("///facebookorca:assets", null, createCellRoots(null).getCellNameResolver());
  }

  @Test
  public void testParseDoubleSlashPath() {
    exception.expect(BuildTargetParseException.class);
    exception.expectMessage("incorrect base name: //facebook//orca");
    parser.parseTarget(
        "//facebook//orca:assets", null, createCellRoots(null).getCellNameResolver());
  }

  @Test
  public void testParseTrailingColon() {
    try {
      parser.parseTarget(
          "//facebook/orca:assets:", null, createCellRoots(null).getCellNameResolver());
      fail("parse() should throw an exception");
    } catch (BuildTargetParseException e) {
      assertEquals("//facebook/orca:assets: cannot end with a colon", e.getMessage());
    }
  }

  @Test
  public void testParseNoColon() {
    try {
      parser.parseTarget(
          "//facebook/orca/assets", null, createCellRoots(null).getCellNameResolver());
      fail("parse() should throw an exception");
    } catch (BuildTargetParseException e) {
      assertEquals(
          "//facebook/orca/assets must contain exactly one colon (found 0)", e.getMessage());
    }
  }

  @Test
  public void testParseMultipleColons() {
    try {
      parser.parseTarget(
          "//facebook:orca:assets", null, createCellRoots(null).getCellNameResolver());
      fail("parse() should throw an exception");
    } catch (BuildTargetParseException e) {
      assertEquals(
          "//facebook:orca:assets must contain exactly one colon (found 2)", e.getMessage());
    }
  }

  @Test
  public void testSlashBeforeColon() {
    try {
      parser.parseTarget("//facebook/:orca", null, createCellRoots(null).getCellNameResolver());
      fail("parse() should throw an exception");
    } catch (BuildTargetParseException e) {
      assertEquals(
          "When parsing //facebook/:orca: incorrect base name: //facebook/.", e.getMessage());
    }
  }

  @Test
  public void testParseFullyQualified() {
    UnconfiguredBuildTarget buildTarget =
        parser.parseTarget(
            "//facebook/orca:assets", null, createCellRoots(null).getCellNameResolver());
    assertEquals("//facebook/orca", buildTarget.getBaseName().toString());
    assertEquals("assets", buildTarget.getShortNameAndFlavorPostfix());
  }

  @Test
  public void testParseBuildFile() {
    UnconfiguredBuildTarget buildTarget =
        parser.parseTarget(
            ":assets", BaseName.of("//facebook/orca"), createCellRoots(null).getCellNameResolver());
    assertEquals("//facebook/orca", buildTarget.getBaseName().toString());
    assertEquals("assets", buildTarget.getShortNameAndFlavorPostfix());
  }

  @Test
  public void testParseWithVisibilityContext() {
    Either<UnconfiguredBuildTarget, CellRelativePath> target =
        parser.parseTargetOrPackageWildcard(
            "//java/com/example:", null, createCellRoots(null).getCellNameResolver());
    assertEquals(
        "A build target that ends with a colon should be treated as a wildcard build target "
            + "when parsed in the context of a visibility argument.",
        "//java/com/example",
        target.getRight().toString());
  }

  @Test
  public void testParseWithRepoName() {
    Path localRepoRoot = Paths.get("/opt/local/repo").toAbsolutePath();
    CellPathResolver cellRoots =
        TestCellPathResolver.create(
            Paths.get("/opt/local/rootcell").toAbsolutePath(),
            ImmutableMap.of("localreponame", localRepoRoot));
    String targetStr = "localreponame//foo/bar:baz";

    UnconfiguredBuildTarget buildTarget =
        parser.parseTarget(targetStr, null, cellRoots.getCellNameResolver());
    assertEquals("localreponame//foo/bar:baz", buildTarget.getFullyQualifiedName());
    assertTrue(buildTarget.getCell().getLegacyName().isPresent());
    assertEquals("localreponame", buildTarget.getCell().getName());
  }

  @Test
  public void atPrefixOfCellsIsSupportedAndIgnored() {
    Path localRepoRoot = Paths.get("/opt/local/repo").toAbsolutePath();
    CellPathResolver cellRoots =
        TestCellPathResolver.create(
            Paths.get("/opt/local/rootcell").toAbsolutePath(),
            ImmutableMap.of("localreponame", localRepoRoot));
    String targetStr = "@localreponame//foo/bar:baz";

    UnconfiguredBuildTarget buildTarget =
        parser.parseTarget(targetStr, null, cellRoots.getCellNameResolver());
    assertEquals("localreponame//foo/bar:baz", buildTarget.getFullyQualifiedName());
    assertTrue(buildTarget.getCell().getLegacyName().isPresent());
    assertEquals("localreponame", buildTarget.getCell().getName());
  }

  @Test
  public void testParseFailsWithRepoNameAndRelativeTarget() throws NoSuchBuildTargetException {
    exception.expect(BuildTargetParseException.class);
    String invalidTargetStr = "myRepo:baz";
    parser.parseTarget(invalidTargetStr, null, createCellRoots(null).getCellNameResolver());
  }

  @Test
  public void testParseWithBackslash() {
    exception.expect(BuildTargetParseException.class);
    exception.expectMessage("incorrect base name: //com\\microsoft\\windows");
    String backslashStr = "//com\\microsoft\\windows:something";
    parser.parseTarget(backslashStr, null, createCellRoots(null).getCellNameResolver());
  }

  @Test
  public void testIncludesTargetNameInMissingCellErrorMessage() {
    Path localRepoRoot = Paths.get("/opt/local/repo").toAbsolutePath();
    CellPathResolver cellRoots =
        TestCellPathResolver.create(
            Paths.get("/opt/local/rootcell").toAbsolutePath(),
            ImmutableMap.of("localreponame", localRepoRoot));

    exception.expect(BuildTargetParseException.class);
    // It contains the target
    exception.expectMessage("lclreponame//facebook/orca:assets");
    // The invalid cell
    exception.expectMessage("Unknown cell: lclreponame");
    // And the suggestion
    exception.expectMessage("localreponame");
    parser.parseTarget("lclreponame//facebook/orca:assets", null, cellRoots.getCellNameResolver());
  }
}

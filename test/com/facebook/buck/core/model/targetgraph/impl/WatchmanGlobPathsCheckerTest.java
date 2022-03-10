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

package com.facebook.buck.core.model.targetgraph.impl;

import static org.junit.Assume.assumeTrue;

import com.facebook.buck.cli.TestWithBuckd;
import com.facebook.buck.core.exceptions.HumanReadableException;
import com.facebook.buck.core.filesystems.AbsPath;
import com.facebook.buck.core.filesystems.ForwardRelPath;
import com.facebook.buck.core.model.BuildTargetFactory;
import com.facebook.buck.io.filesystem.ProjectFilesystem;
import com.facebook.buck.io.filesystem.impl.FakeProjectFilesystem;
import com.facebook.buck.io.watchman.Watchman;
import com.facebook.buck.io.watchman.WatchmanTestUtils;
import com.facebook.buck.testutil.TemporaryPaths;
import com.google.common.collect.ImmutableSet;
import java.nio.file.Files;
import java.nio.file.Paths;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class WatchmanGlobPathsCheckerTest {

  private ProjectFilesystem projectFilesystem;
  private Watchman watchman;
  private AbsPath root;

  @Rule public ExpectedException expectedException = ExpectedException.none();
  @Rule public TemporaryPaths tmp = new TemporaryPaths();
  @Rule public TestWithBuckd testWithBuckd = new TestWithBuckd(tmp); // set up Watchman

  @Before
  public void setUp() throws Exception {
    projectFilesystem = new FakeProjectFilesystem(tmp.getRoot());
    root = tmp.getRoot();
    watchman = WatchmanTestUtils.buildWatchman(tmp.getRoot());
    assumeTrue(watchman.getTransportPath().isPresent());
  }

  @Test
  public void testCheckPathsThrowsWithNonExistingPath() {
    PathsChecker checker = new WatchmanPathsChecker(watchman, false);

    expectedException.expect(HumanReadableException.class);
    expectedException.expectMessage(
        "//:a references non-existing or incorrect type of file or directory 'b'");

    checker.checkPaths(
        projectFilesystem,
        BuildTargetFactory.newInstance("//:a"),
        ImmutableSet.of(ForwardRelPath.of("b")));
  }

  @Test
  public void testCheckPathsPassesWithExistingPath() throws Exception {
    PathsChecker checker = new WatchmanPathsChecker(watchman, false);
    tmp.newFile("b");

    WatchmanTestUtils.sync(watchman);

    checker.checkPaths(
        projectFilesystem,
        BuildTargetFactory.newInstance("//:a"),
        ImmutableSet.of(ForwardRelPath.of("b")));
  }

  @Test
  public void testCheckPathsPassesWithExistingFiles() throws Exception {

    PathsChecker checker = new WatchmanPathsChecker(watchman, false);
    tmp.newFile("b");

    WatchmanTestUtils.sync(watchman);

    checker.checkFilePaths(
        projectFilesystem,
        BuildTargetFactory.newInstance("//:a"),
        ImmutableSet.of(ForwardRelPath.of("b")));
  }

  @Test
  public void testCheckPathsPassesWithExistingDirectory() throws Exception {

    PathsChecker checker = new WatchmanPathsChecker(watchman, false);
    tmp.newFolder("b");

    WatchmanTestUtils.sync(watchman);

    checker.checkDirPaths(
        projectFilesystem,
        BuildTargetFactory.newInstance("//:a"),
        ImmutableSet.of(ForwardRelPath.of("b")));
  }

  @Test
  public void testCheckPathsFailedWithExistingDirectory() throws Exception {
    expectedException.expect(HumanReadableException.class);
    expectedException.expectMessage(
        "//:a references non-existing or incorrect type of file or directory 'b'");

    PathsChecker checker = new WatchmanPathsChecker(watchman, false);
    tmp.newFolder("b");

    WatchmanTestUtils.sync(watchman);

    checker.checkFilePaths(
        projectFilesystem,
        BuildTargetFactory.newInstance("//:a"),
        ImmutableSet.of(ForwardRelPath.of("b")));
  }

  @Test
  public void testCheckPathsFailedWithExistingFiles() throws Exception {
    expectedException.expect(HumanReadableException.class);
    expectedException.expectMessage(
        "//:a references non-existing or incorrect type of file or directory 'b'");

    PathsChecker checker = new WatchmanPathsChecker(watchman, false);
    tmp.newFile("b");

    WatchmanTestUtils.sync(watchman);

    checker.checkDirPaths(
        projectFilesystem,
        BuildTargetFactory.newInstance("//:a"),
        ImmutableSet.of(ForwardRelPath.of("b")));
  }

  @Test
  public void testCheckPathsFailedWithCaseSensitive() throws Exception {
    expectedException.expect(HumanReadableException.class);
    expectedException.expectMessage(
        "//:a references non-existing or incorrect type of file or directory 'b'");

    PathsChecker checker = new WatchmanPathsChecker(watchman, false);
    tmp.newFile("B");

    WatchmanTestUtils.sync(watchman);

    checker.checkFilePaths(
        projectFilesystem,
        BuildTargetFactory.newInstance("//:a"),
        ImmutableSet.of(ForwardRelPath.of("b")));
  }

  @Test
  public void testCheckPathsPassWithSymlink() throws Exception {
    PathsChecker checker = new WatchmanPathsChecker(watchman, false);
    tmp.newFile("b");

    Files.createSymbolicLink(root.resolve("symlink-to-regular-file").getPath(), Paths.get("b"));

    WatchmanTestUtils.sync(watchman);

    checker.checkFilePaths(
        projectFilesystem,
        BuildTargetFactory.newInstance("//:a"),
        ImmutableSet.of(ForwardRelPath.of("symlink-to-regular-file")));
  }

  @Test
  public void testCheckPathsFailedWithMultipleFiles() throws Exception {
    expectedException.expect(HumanReadableException.class);
    expectedException.expectMessage(
        "//:a references non-existing or incorrect type of file or directory 'd'");

    PathsChecker checker = new WatchmanPathsChecker(watchman, false);
    tmp.newFile("b");
    tmp.newFile("c");

    WatchmanTestUtils.sync(watchman);

    checker.checkFilePaths(
        projectFilesystem,
        BuildTargetFactory.newInstance("//:a"),
        ImmutableSet.of(ForwardRelPath.of("b"), ForwardRelPath.of("c"), ForwardRelPath.of("d")));
  }

  @Test
  public void testFallbackCheckPathsFailedWithExistingFiles() throws Exception {
    expectedException.expect(HumanReadableException.class);
    expectedException.expectMessage("In //:a expected directory: b");

    PathsChecker checker = new WatchmanPathsChecker(watchman, true);
    tmp.newFile("b");
    projectFilesystem.createNewFile(Paths.get("b"));

    WatchmanTestUtils.sync(watchman);

    checker.checkDirPaths(
        projectFilesystem,
        BuildTargetFactory.newInstance("//:a"),
        ImmutableSet.of(ForwardRelPath.of("b")));
  }

  @Test
  public void testFallbackCheckPathsFailedWithMultipleFiles() throws Exception {
    expectedException.expect(HumanReadableException.class);
    expectedException.expectMessage("//:a references non-existing file or directory 'd'");

    PathsChecker checker = new WatchmanPathsChecker(watchman, true);
    tmp.newFile("b");
    tmp.newFile("c");
    projectFilesystem.createNewFile(Paths.get("b"));
    projectFilesystem.createNewFile(Paths.get("c"));

    WatchmanTestUtils.sync(watchman);

    checker.checkFilePaths(
        projectFilesystem,
        BuildTargetFactory.newInstance("//:a"),
        ImmutableSet.of(ForwardRelPath.of("b"), ForwardRelPath.of("c"), ForwardRelPath.of("d")));
  }
}

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

package com.facebook.buck.io.file;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.facebook.buck.core.filesystems.RelPath;
import com.facebook.buck.io.watchman.Capability;
import com.google.common.collect.ImmutableList;
import java.io.File;
import java.util.EnumSet;
import org.junit.Test;

public class RecursiveFileMatcherTest {

  @Test
  public void matchesPathsUnderProvidedBasePath() {
    RelPath basePath = RelPath.get("foo");
    RecursiveFileMatcher matcher = RecursiveFileMatcher.of(basePath);
    assertTrue(matcher.matches(basePath.resolveRel("bar")));
  }

  @Test
  public void doesNotMatchPathsOutsideOfProvidedBasePath() {
    RecursiveFileMatcher matcher = RecursiveFileMatcher.of(RelPath.get("foo"));
    assertFalse(matcher.matches(RelPath.get("not_relative_too_root")));
  }

  @Test
  public void usesWatchmanQueryToMatchProvidedBasePath() {
    RecursiveFileMatcher matcher = RecursiveFileMatcher.of(RelPath.get("path"));
    assertThat(
        matcher.toWatchmanMatchQuery(EnumSet.noneOf(Capability.class)),
        equalTo(ImmutableList.of("match", "path" + File.separator + "**", "wholename")));
  }

  @Test
  public void watchmanQueryWithDirnameCapability() {
    RecursiveFileMatcher matcher = RecursiveFileMatcher.of(RelPath.get("path"));
    assertThat(
        matcher.toWatchmanMatchQuery(EnumSet.of(Capability.DIRNAME)),
        equalTo(ImmutableList.of("dirname", "path")));
  }

  @Test
  public void returnsAPathWhenAskedForPathOrGlob() {
    RecursiveFileMatcher matcher = RecursiveFileMatcher.of(RelPath.get("path"));
    PathMatcher.PathOrGlob pathOrGlob = matcher.getPathOrGlob();
    assertTrue(pathOrGlob.isPath());
    assertThat(pathOrGlob.getValue(), equalTo("path"));
  }

  @Test
  public void getGlob() {
    RecursiveFileMatcher matcher = RecursiveFileMatcher.of(RelPath.get("path"));
    String glob = matcher.getGlob();
    assertThat(glob, equalTo("path" + File.separator + "**"));
  }
}

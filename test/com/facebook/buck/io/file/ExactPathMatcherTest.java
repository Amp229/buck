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
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.facebook.buck.core.filesystems.RelPath;
import com.facebook.buck.io.watchman.Capability;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.nio.file.InvalidPathException;
import java.util.EnumSet;
import org.junit.Test;

public class ExactPathMatcherTest {

  @Test
  public void matchesExplicitlyProvidedPaths() {
    ExactPathMatcher matcher = ExactPathMatcher.of(".idea");
    assertTrue(matcher.matches(RelPath.get(".idea")));
  }

  @Test
  public void doesNotMatchPathsThatAreNotExactlyTheSame() {
    ExactPathMatcher matcher = ExactPathMatcher.of(".idea");
    assertFalse(matcher.matches(RelPath.get(".ideas")));
  }

  @Test
  public void usesWatchmanQueryToMatchPathsExactlyMatchingProvidedOne() {
    ExactPathMatcher matcher = ExactPathMatcher.of(".idea");
    assertEquals(
        matcher.toWatchmanMatchQuery(EnumSet.noneOf(Capability.class)),
        ImmutableList.of("match", ".idea", "wholename", ImmutableMap.of("includedotfiles", true)));
  }

  @Test
  public void returnsAGlobWhenAskedForPathOrGlob() {
    ExactPathMatcher matcher = ExactPathMatcher.of(".idea");
    PathMatcher.PathOrGlob pathOrGlob = matcher.getPathOrGlob();
    assertTrue(pathOrGlob.isGlob());
    assertThat(pathOrGlob.getValue(), equalTo(".idea"));
  }

  @Test
  public void verifyPathWithAnAsteriskChar() {
    String path = ".idea/blah/blah/bl*ah/foo/bar";
    InvalidPathException expectedException =
        assertThrows(InvalidPathException.class, () -> ExactPathMatcher.of(path));

    assertThat(expectedException.getCause(), equalTo(null));
    assertThat(expectedException.getMessage(), equalTo("Illegal char <*> at index 18: " + path));
  }
}

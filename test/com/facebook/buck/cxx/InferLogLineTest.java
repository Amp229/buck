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

package com.facebook.buck.cxx;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.hamcrest.junit.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import com.facebook.buck.core.filesystems.AbsPath;
import com.facebook.buck.core.model.BuildTarget;
import com.facebook.buck.core.model.BuildTargetFactory;
import com.facebook.buck.util.environment.Platform;
import com.google.common.collect.ImmutableSet;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

public class InferLogLineTest {

  @Before
  public void setUp() throws Exception {
    Assume.assumeThat(Platform.detect(), not(Platform.WINDOWS));
  }

  @Test
  public void testFromBuildTargetThrowsWhenPathIsNotAbsolute() {
    BuildTarget testBuildTarget =
        BuildTargetFactory.newInstance(
            "//target", "short", CxxInferEnhancer.InferFlavors.INFER_CAPTURE_ALL.getFlavor());
    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () -> InferLogLine.of(testBuildTarget, AbsPath.get("buck-out/a/b/c/")));
    assertThat(exception, notNullValue());
    assertThat(exception.getMessage(), startsWith("path must be absolute"));
  }

  @Test
  public void testToStringWithCell() {
    BuildTarget testBuildTarget =
        BuildTargetFactory.newInstance("cellname//target:short")
            .withFlavors(
                ImmutableSet.of(CxxInferEnhancer.InferFlavors.INFER_CAPTURE_ALL.getFlavor()));

    String expectedOutput =
        "cellname//target:short#infer-capture-all\t[infer-capture-all]\t/User/user/src/buck-out/a/b/c";
    assertEquals(
        expectedOutput,
        InferLogLine.of(testBuildTarget, AbsPath.get("/User/user/src/buck-out/a/b/c/"))
            .getFormattedString());
  }

  @Test
  public void testToStringWithoutCell() {
    BuildTarget testBuildTarget =
        BuildTargetFactory.newInstance(
            "//target", "short", CxxInferEnhancer.InferFlavors.INFER_CAPTURE_ALL.getFlavor());

    String expectedOutput =
        "//target:short#infer-capture-all\t[infer-capture-all]\t/User/user/src/buck-out/a/b/c";
    assertEquals(
        expectedOutput,
        InferLogLine.of(testBuildTarget, AbsPath.get("/User/user/src/buck-out/a/b/c/"))
            .getFormattedString());
  }
}

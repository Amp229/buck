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

package com.facebook.buck.core.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.facebook.buck.core.cell.name.CanonicalCellName;
import com.facebook.buck.core.filesystems.ForwardRelPath;
import java.util.Optional;
import org.junit.Test;

public class CellRelativePathTest {
  @Test
  public void testToString() {
    assertEquals(
        "foo//bar/baz",
        CellRelativePath.of(
                CanonicalCellName.unsafeOf(Optional.of("foo")), ForwardRelPath.of("bar/baz"))
            .toString());
    assertEquals(
        "//bar/baz",
        CellRelativePath.of(CanonicalCellName.rootCell(), ForwardRelPath.of("bar/baz")).toString());
  }

  @Test
  public void startsWith() {
    CellRelativePath fooBarBaz =
        CellRelativePath.of(
            CanonicalCellName.unsafeOf(Optional.of("foo")), ForwardRelPath.of("bar/baz"));
    CellRelativePath fooBar =
        CellRelativePath.of(
            CanonicalCellName.unsafeOf(Optional.of("foo")), ForwardRelPath.of("bar"));
    CellRelativePath foo =
        CellRelativePath.of(CanonicalCellName.unsafeOf(Optional.of("foo")), ForwardRelPath.of(""));
    CellRelativePath bar =
        CellRelativePath.of(CanonicalCellName.unsafeOf(Optional.of("bar")), ForwardRelPath.of(""));

    CellRelativePath[] paths = {
      foo, fooBar, fooBarBaz, bar,
    };

    for (CellRelativePath p1 : paths) {
      for (CellRelativePath p2 : paths) {
        assertEquals(p1.startsWith(p2), p1.toString().startsWith(p2.toString()));
      }
    }
  }

  @Test
  public void getParentButEmptyForSingleSegment() {
    assertNull(
        CellRelativePath.of(CanonicalCellName.rootCell(), ForwardRelPath.of(""))
            .getParentButEmptyForSingleSegment());
    assertEquals(
        CellRelativePath.of(CanonicalCellName.rootCell(), ForwardRelPath.of("")),
        CellRelativePath.of(CanonicalCellName.rootCell(), ForwardRelPath.of("foo"))
            .getParentButEmptyForSingleSegment());
    assertEquals(
        CellRelativePath.of(CanonicalCellName.rootCell(), ForwardRelPath.of("foo")),
        CellRelativePath.of(CanonicalCellName.rootCell(), ForwardRelPath.of("foo/bar"))
            .getParentButEmptyForSingleSegment());
  }
}

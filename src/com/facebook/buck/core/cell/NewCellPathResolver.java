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

package com.facebook.buck.core.cell;

import com.facebook.buck.core.cell.name.CanonicalCellName;
import com.facebook.buck.core.filesystems.AbsPath;
import com.google.common.collect.ImmutableSortedMap;
import java.nio.file.Path;

/**
 * The {@link NewCellPathResolver} maps between {@link CanonicalCellName}s and the absolute {@link
 * Path} that they are rooted at.
 *
 * <p>The behavior of {@link NewCellPathResolver} is the same for all cells in a build (unlike
 * {@link CellPathResolver}).
 */
public interface NewCellPathResolver {

  /**
   * Note: unlike {@link CellPathResolver#getCellPath(CanonicalCellName)} this function always
   * returns a value. Existence/visibility of the cell is enforced when the {@link
   * CanonicalCellName} is resolved.
   *
   * @param cellName Canonical name of the cell.
   * @return Absolute path to the physical root of the cell.
   */
  AbsPath getCellPath(CanonicalCellName cellName);

  /**
   * @param path Absolute path to the physical root of the cell.
   * @return Canonical name of the cell.
   */
  CanonicalCellName getCanonicalCellName(Path path);

  /** Get all cells. */
  ImmutableSortedMap<CanonicalCellName, AbsPath> getCellToPathMap();
}

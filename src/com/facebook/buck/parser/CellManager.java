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

package com.facebook.buck.parser;

import com.facebook.buck.core.cell.Cell;
import com.facebook.buck.core.cell.Cells;
import com.facebook.buck.core.cell.name.CanonicalCellName;
import com.facebook.buck.core.filesystems.AbsPath;
import com.facebook.buck.core.model.targetgraph.TargetNode;
import com.facebook.buck.util.types.Unit;
import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;

class CellManager {

  private final Cells cells;
  private final ConcurrentHashMap<CanonicalCellName, Unit> cellsMap = new ConcurrentHashMap<>();
  private final SymlinkCache symlinkCache;

  public CellManager(Cells cells, SymlinkCache symlinkCache) {
    this.cells = cells;
    this.symlinkCache = symlinkCache;
    symlinkCache.registerCell(cells.getRootCell());
  }

  void register(Cell cell) {
    if (!cellsMap.containsKey(cell.getCanonicalName())) {
      cellsMap.put(cell.getCanonicalName(), Unit.UNIT);
      symlinkCache.registerCell(cell);
    }
  }

  Cell getCell(CanonicalCellName cellName) {
    Cell cell = cells.getCell(cellName);
    register(cell);
    return cell;
  }

  void registerInputsUnderSymlinks(AbsPath buildFile, TargetNode<?> node) throws IOException {
    Cell currentCell = getCell(node.getBuildTarget().getCell());
    symlinkCache.registerInputsUnderSymlinks(currentCell, buildFile, node);
  }

  void close() {
    symlinkCache.close();
  }
}

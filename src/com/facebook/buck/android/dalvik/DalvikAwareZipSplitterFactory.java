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

package com.facebook.buck.android.dalvik;

import com.facebook.buck.android.apkmodule.APKModule;
import com.facebook.buck.io.filesystem.ProjectFilesystem;
import com.google.common.collect.ImmutableMultimap;
import java.nio.file.Path;
import java.util.Set;
import java.util.function.Predicate;

public class DalvikAwareZipSplitterFactory implements ZipSplitterFactory {

  private final long linearAllocLimit;
  private final long methodRefCountBufferSpace;
  private final long fieldRefCountBufferSpace;

  public DalvikAwareZipSplitterFactory(
      long linearAllocLimit, long methodRefCountBufferSpace, long fieldRefCountBufferSpace) {
    this.linearAllocLimit = linearAllocLimit;
    this.methodRefCountBufferSpace = methodRefCountBufferSpace;
    this.fieldRefCountBufferSpace = fieldRefCountBufferSpace;
  }

  @Override
  public ZipSplitter newInstance(
      ProjectFilesystem filesystem,
      Set<Path> inFiles,
      Path outPrimary,
      Path outSecondaryDir,
      String secondaryPattern,
      Path outDexStoresDir,
      Predicate<String> requiredInPrimaryZip,
      ImmutableMultimap<APKModule, String> additionalDexStoreSets,
      APKModule rootAPKModule,
      ZipSplitter.DexSplitStrategy dexSplitStrategy,
      Path reportDir) {
    return DalvikAwareZipSplitter.splitZip(
        filesystem,
        inFiles,
        outPrimary,
        outSecondaryDir,
        secondaryPattern,
        outDexStoresDir,
        linearAllocLimit,
        methodRefCountBufferSpace,
        fieldRefCountBufferSpace,
        requiredInPrimaryZip,
        additionalDexStoreSets,
        rootAPKModule,
        dexSplitStrategy,
        reportDir);
  }
}

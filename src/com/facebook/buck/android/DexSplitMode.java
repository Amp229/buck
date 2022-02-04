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

package com.facebook.buck.android;

import com.facebook.buck.android.dalvik.ZipSplitter;
import com.facebook.buck.android.dalvik.ZipSplitter.DexSplitStrategy;
import com.facebook.buck.core.rulekey.AddToRuleKey;
import com.facebook.buck.core.rulekey.AddsToRuleKey;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedSet;
import java.util.Collection;

/** Bundles together some information about whether and how we should split up dex files. */
class DexSplitMode implements AddsToRuleKey {
  public static final DexSplitMode NO_SPLIT =
      new DexSplitMode(
          /* shouldSplitDex */ false,
          ZipSplitter.DexSplitStrategy.MAXIMIZE_PRIMARY_DEX_SIZE,
          DexStore.JAR,
          /* linearAllocHardLimit */ 0,
          /* methodRefCountBufferSpace */ 0,
          /* fieldRefCountBufferSpace */ 0,
          /* splitDexLibLimit */ 0,
          /* primaryDexPatterns */ ImmutableSet.of(),
          /* allowRDotJavaInSecondaryDex */ false);

  /**
   * By default, assume we have 5MB of linear alloc, 1MB of which is taken up by the framework, so
   * that leaves 4MB.
   */
  static final long DEFAULT_LINEAR_ALLOC_HARD_LIMIT = 4 * 1024 * 1024;

  /**
   * Limit the maximum number of pre-dexed libraries that are input to each dex group rule. The
   * default of 0 sets no limit, producing a single dex group per APK module.
   */
  static final int DEFAULT_DEX_GROUP_LIB_LIMIT = 0;

  @AddToRuleKey private final boolean shouldSplitDex;

  @AddToRuleKey private final DexStore dexStore;

  @AddToRuleKey private final ZipSplitter.DexSplitStrategy dexSplitStrategy;

  @AddToRuleKey private final long linearAllocHardLimit;

  /**
   * Non-predexed builds count method and field refs to split secondary dexes when exactly 64k refs
   * are reached.
   *
   * <p>This is a hack to leave extra field ref space when splitting dexes, to account for
   * inaccuracies in how buck counts refs vs d8
   *
   * <p>TODO: use d8 to count refs/split dexes T70194276
   */
  @AddToRuleKey private final long methodRefCountBufferSpace;

  /** See methodRefCountBufferSpace */
  @AddToRuleKey private final long fieldRefCountBufferSpace;

  @AddToRuleKey private final int dexGroupLibLimit;

  @AddToRuleKey private final ImmutableSortedSet<String> primaryDexPatterns;

  /**
   * Boolean identifying whether we should allow the dex splitting to move R classes into secondary
   * dex files.
   */
  @AddToRuleKey private boolean allowRDotJavaInSecondaryDex;

  /**
   * @param primaryDexPatterns Set of substrings that, when matched, will cause individual input
   *     class or resource files to be placed into the primary jar (and thus the primary dex
   *     output). These classes are required for correctness.
   * @param allowRDotJavaInSecondaryDex whether to allow R.java classes in the secondary dex files
   */
  public DexSplitMode(
      boolean shouldSplitDex,
      DexSplitStrategy dexSplitStrategy,
      DexStore dexStore,
      long linearAllocHardLimit,
      long methodRefCountBufferSpace,
      long fieldRefCountBufferSpace,
      int dexGroupLibLimit,
      Collection<String> primaryDexPatterns,
      boolean allowRDotJavaInSecondaryDex) {
    this.shouldSplitDex = shouldSplitDex;
    this.dexSplitStrategy = dexSplitStrategy;
    this.dexStore = dexStore;
    this.linearAllocHardLimit = linearAllocHardLimit;
    this.methodRefCountBufferSpace = methodRefCountBufferSpace;
    this.fieldRefCountBufferSpace = fieldRefCountBufferSpace;
    this.dexGroupLibLimit = dexGroupLibLimit;
    this.primaryDexPatterns = ImmutableSortedSet.copyOf(primaryDexPatterns);
    this.allowRDotJavaInSecondaryDex = allowRDotJavaInSecondaryDex;
  }

  public DexSplitMode(
      boolean shouldSplitDex,
      DexSplitStrategy dexSplitStrategy,
      DexStore dexStore,
      long linearAllocHardLimit,
      Collection<String> primaryDexPatterns,
      boolean allowRDotJavaInSecondaryDex) {
    this(
        shouldSplitDex,
        dexSplitStrategy,
        dexStore,
        linearAllocHardLimit,
        0,
        0,
        DEFAULT_DEX_GROUP_LIB_LIMIT,
        primaryDexPatterns,
        allowRDotJavaInSecondaryDex);
  }

  public DexStore getDexStore() {
    return dexStore;
  }

  public boolean isShouldSplitDex() {
    return shouldSplitDex;
  }

  ZipSplitter.DexSplitStrategy getDexSplitStrategy() {
    Preconditions.checkState(isShouldSplitDex());
    return dexSplitStrategy;
  }

  public long getLinearAllocHardLimit() {
    return linearAllocHardLimit;
  }

  public long getMethodRefCountBufferSpace() {
    return methodRefCountBufferSpace;
  }

  public long getFieldRefCountBufferSpace() {
    return fieldRefCountBufferSpace;
  }

  public int getDexGroupLibLimit() {
    return dexGroupLibLimit;
  }

  public ImmutableSet<String> getPrimaryDexPatterns() {
    return primaryDexPatterns;
  }

  public boolean isAllowRDotJavaInSecondaryDex() {
    return allowRDotJavaInSecondaryDex;
  }
}

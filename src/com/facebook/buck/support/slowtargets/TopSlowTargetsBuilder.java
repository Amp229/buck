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

package com.facebook.buck.support.slowtargets;

import com.facebook.buck.core.model.BuildTarget;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.NavigableSet;
import java.util.TreeSet;
import javax.annotation.concurrent.GuardedBy;

/**
 * TopSlowTargetsBuilder is a builder that reacts to the completion of {@link BuildTarget}s and
 * records the top slow targets that it has seen. TopSlowTargetsBuilder efficiently tracks the top N
 * build targets that it sees, where N is a parameter to the constructor.
 */
public final class TopSlowTargetsBuilder {

  /** Slow target NavigableSet. */
  @GuardedBy("this")
  private final NavigableSet<SlowTarget> slowTargets;

  /** Map of targets currently tracked as the top slow targets of the build. */
  @GuardedBy("this")
  private final Map<BuildTarget, SlowTarget> targetSlowTargetMap;

  /** The number of top slow rules to track (a.k.a N), a parameter to the constructor. */
  private final int targetCount;

  /** Constructs a TopSlowTargetsBuilder with a N of 10. */
  public TopSlowTargetsBuilder() {
    this(20);
  }

  /**
   * Constructs a TopSlowTargetsBuilder that keeps track of the top ruleCount slow rules.
   *
   * @param ruleCount The number of top slow rules to keep track of.
   */
  public TopSlowTargetsBuilder(final int ruleCount) {
    this.targetCount = ruleCount;
    this.slowTargets =
        new TreeSet<>(
            new Comparator<SlowTarget>() {
              @Override
              public int compare(final SlowTarget target1, final SlowTarget target2) {
                if (target1.getDurationMilliseconds() == target2.getDurationMilliseconds()) {
                  return target1.getTarget().compareTo(target2.getTarget());
                }
                return Long.compare(
                    target1.getDurationMilliseconds(), target2.getDurationMilliseconds());
              }
            });
    this.targetSlowTargetMap = new HashMap<>();
  }

  /**
   * Records the given target with associated execution time. If this target is one of the top N
   * slow rules that we've seen, it's recorded in the top slow rules. For targets with same
   * duration, we use the BuildTarget as key for comparator in the set.
   *
   * @param target The target that has just finished executing
   * @param durationMilliseconds The execution duration of the given target
   */
  public synchronized void onTargetCompleted(
      final BuildTarget target,
      final long durationMilliseconds,
      final long targetStartTimeMilliseconds) {
    SlowTarget slowTarget =
        new SlowTarget(target, durationMilliseconds, targetStartTimeMilliseconds);
    targetSlowTargetMap.put(target, slowTarget);
    slowTargets.add(slowTarget);
    trimToRequestedLength();
  }

  /**
   * Records an output size associated with the given target. In cases where the output size is
   * known after the immediate execution of a target, this method is called with the output size for
   * the given target.
   *
   * @param target The target that has just finished executing
   * @param outputSize The size of the target's output, or zero if not applicable
   */
  public synchronized void onTargetOutputSize(final BuildTarget target, final long outputSize) {
    if (outputSize == 0) {
      return;
    }

    if (targetSlowTargetMap.containsKey(target)) {
      targetSlowTargetMap.get(target).setOutputSize(outputSize);
    }
  }

  /**
   * Drops any excess nodes from the slow target list, if the number of nodes in the list exceeds
   * {@link #targetCount}.
   */
  private void trimToRequestedLength() {
    if (slowTargets.size() > targetCount) {
      Preconditions.checkState(slowTargets.size() == targetCount + 1);
      SlowTarget condemnedTarget = slowTargets.pollFirst();
      targetSlowTargetMap.remove(condemnedTarget.getTarget());
    }
  }

  /**
   * Returns a list of the top N slow rules that this builder has observed, sorted from fastest to
   * slowest.
   */
  public synchronized ImmutableList<SlowTarget> getSlowRules() {
    return ImmutableList.copyOf(slowTargets);
  }
}

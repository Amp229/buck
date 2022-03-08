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

package com.facebook.buck.util.concurrent;

import com.google.common.base.Preconditions;
import java.util.concurrent.locks.Lock;

/** Locked state of the lock. */
public abstract class AutoCloseableLocked implements AutoCloseable {
  private final Lock lock;
  // Volatile here is just to make infer happy, this
  // class is not thread safe.
  private volatile boolean locked;

  AutoCloseableLocked(Lock lock) {
    lock.lock();
    this.lock = lock;
    this.locked = true;
  }

  /** This is a dummy function to be used to mute unused locked parameter warning. */
  public void markUsed() {}

  @Override
  public void close() {
    Preconditions.checkState(locked);
    lock.unlock();
    locked = false;
  }
}

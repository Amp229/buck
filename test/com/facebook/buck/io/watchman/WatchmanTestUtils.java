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

package com.facebook.buck.io.watchman;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.facebook.buck.core.filesystems.AbsPath;
import com.facebook.buck.event.console.TestEventConsole;
import com.facebook.buck.util.timing.FakeClock;
import com.facebook.buck.util.types.Either;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.junit.AssumptionViolatedException;

public class WatchmanTestUtils {
  /** Sync all watchman roots. */
  public static void sync(Watchman watchman)
      throws IOException, InterruptedException, WatchmanQueryFailedException {
    try (WatchmanClient client = watchman.createClient()) {
      for (Map.Entry<AbsPath, ProjectWatch> e : watchman.getProjectWatches().entrySet()) {
        assertEquals(ImmutableSet.of(e.getKey()), watchman.getProjectWatches().keySet());
        // synchronize using clock request
        int syncTimeoutSeconds = 10;
        Either<WatchmanQueryResp.Generic, WatchmanClient.Timeout> clockResult =
            client.queryWithTimeout(
                Long.MAX_VALUE,
                Long.MAX_VALUE,
                WatchmanQuery.clock(
                    e.getValue().getWatchRoot(), Optional.of(syncTimeoutSeconds * 1000)));
        assertTrue(
            "sync+clock query timed out in " + syncTimeoutSeconds + "s", clockResult.isLeft());
      }
    }
  }

  public static void setupWatchman(AbsPath root) throws IOException {
    Files.write(
        root.resolve(".watchmanconfig").getPath(),
        "{\"ignore_dirs\":[\"buck-out\",\".buckd\"]}".getBytes(StandardCharsets.UTF_8));
  }

  public static Watchman buildWatchman(AbsPath root) throws InterruptedException {
    WatchmanFactory watchmanFactory = new WatchmanFactory();
    return watchmanFactory.build(
        ImmutableSet.of(root),
        ImmutableMap.of(),
        new TestEventConsole(),
        FakeClock.doNotCare(),
        Optional.empty(),
        1_000,
        TimeUnit.SECONDS.toNanos(10),
        TimeUnit.SECONDS.toNanos(1));
  }

  public static Watchman buildWatchmanAssumeNotNull(AbsPath root) throws InterruptedException {
    Watchman watchman = buildWatchman(root);
    if (watchman instanceof WatchmanFactory.NullWatchman) {
      // TODO(nga): why there's no watchman on CI? This should be AssertionError
      throw new AssumptionViolatedException(
          "failed to create watchman: " + ((WatchmanFactory.NullWatchman) watchman).reason);
    }
    return watchman;
  }
}

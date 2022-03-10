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

package com.facebook.buck.cli;

import static org.junit.Assume.assumeFalse;

import com.facebook.buck.event.console.TestEventConsole;
import com.facebook.buck.io.watchman.Watchman;
import com.facebook.buck.io.watchman.WatchmanError;
import com.facebook.buck.io.watchman.WatchmanFactory;
import com.facebook.buck.io.watchman.WatchmanTestUtils;
import com.facebook.buck.support.state.BuckGlobalStateLifecycleManager;
import com.facebook.buck.testutil.TemporaryPaths;
import com.facebook.buck.util.environment.EnvVariablesProvider;
import com.facebook.buck.util.timing.FakeClock;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.junit.rules.ExternalResource;

/** Test rule to include when you'd like your test class to simulate using buckd. */
public class TestWithBuckd extends ExternalResource {

  private final TemporaryPaths temporaryPaths;
  private BuckGlobalStateLifecycleManager globalState;

  public TestWithBuckd(TemporaryPaths temporaryPaths) {
    this.temporaryPaths = temporaryPaths;
  }

  @Override
  protected void before() throws IOException, InterruptedException {
    // In case root_restrict_files is enabled in /etc/watchmanconfig, assume
    // this is one of the entries so it doesn't give up.
    temporaryPaths.newFolder(".git");
    temporaryPaths.newFile(".arcconfig");
    // Create an empty watchman config file.
    WatchmanTestUtils.setupWatchman(temporaryPaths.getRoot());
    WatchmanFactory watchmanFactory = new WatchmanFactory();
    Watchman watchman =
        watchmanFactory.build(
            ImmutableSet.of(temporaryPaths.getRoot()),
            getWatchmanEnv(),
            new TestEventConsole(),
            FakeClock.doNotCare(),
            Optional.empty(),
            1_000,
            TimeUnit.SECONDS.toNanos(10),
            TimeUnit.SECONDS.toNanos(1));

    globalState = new BuckGlobalStateLifecycleManager();

    // We assume watchman has been installed and configured properly on the system, and that setting
    // up the watch is successful.
    assumeFalse(watchman == new WatchmanFactory.NullWatchman("test", WatchmanError.TEST));
  }

  private static ImmutableMap<String, String> getWatchmanEnv() {
    ImmutableMap.Builder<String, String> envBuilder = ImmutableMap.builder();
    String systemPath = EnvVariablesProvider.getSystemEnv().get("PATH");
    if (systemPath != null) {
      envBuilder.put("PATH", systemPath);
    }
    return envBuilder.build();
  }

  public BuckGlobalStateLifecycleManager getGlobalState() {
    return globalState;
  }
}

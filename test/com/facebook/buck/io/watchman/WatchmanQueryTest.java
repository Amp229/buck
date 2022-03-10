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

import static org.junit.Assert.*;

import com.facebook.buck.core.filesystems.ForwardRelPath;
import com.google.common.collect.ImmutableList;
import java.util.Optional;
import org.junit.Test;

public class WatchmanQueryTest {
  @Test
  public void queryDescDefault() {
    assertEquals("get-pid", WatchmanQuery.getPid().queryDesc());
  }

  @Test
  public void queryDescQuery() {
    WatchmanQuery.Query q =
        WatchmanQuery.query(
            new WatchRoot("/p", true),
            ForwardRelPath.EMPTY,
            Optional.empty(),
            Optional.empty(),
            ImmutableList.of());
    assertEquals("sync+query", q.queryDesc());
    assertEquals("sync+query", q.withSyncTimeout(10).queryDesc());
    assertEquals("query", q.withSyncTimeout(0).queryDesc());
  }
}

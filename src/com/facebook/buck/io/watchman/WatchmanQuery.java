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

import com.facebook.buck.core.filesystems.ForwardRelPath;
import com.facebook.buck.core.util.immutables.BuckStyleValue;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.util.Optional;
import org.immutables.value.Value;

/**
 * Enumerate all watchman queries.
 *
 * <p>Responses are {@link WatchmanQueryResp}.
 */
public abstract class WatchmanQuery<R extends WatchmanQueryResp> {

  private WatchmanQuery() {}

  public abstract ImmutableList<Object> toProtocolArgs();

  @SuppressWarnings("unchecked")
  public R decodeResponse(ImmutableMap<String, Object> resp) throws WatchmanQueryFailedException {
    return (R) WatchmanQueryResp.generic(resp);
  }

  /** Short query description to be used in logs. */
  public String queryDesc() {
    String command = (String) toProtocolArgs().get(0);
    if (this instanceof QueryWithSync) {
      return ((QueryWithSync) this).queryDescPrefix() + command;
    } else {
      return command;
    }
  }

  private interface QueryWithSync {
    boolean doesSync();

    default String queryDescPrefix() {
      return doesSync() ? "sync+" : "";
    }
  }

  /** {@code query} query. */
  @BuckStyleValue
  @Value.Immutable(copy = true, builder = false, prehash = false, intern = false)
  @SuppressWarnings("immutables:untype")
  public abstract static class Query extends WatchmanQuery<WatchmanQueryResp.Generic>
      implements QueryWithSync {
    public abstract WatchRoot getPath();

    public abstract ForwardRelPath getRelativeRoot();

    public abstract Optional<Object> getExpression();

    public abstract Optional<Object> getGlob();

    public abstract ImmutableList<String> getFields();

    @Value.Default
    @Value.Parameter(false)
    public Optional<String> getSince() {
      return Optional.empty();
    }

    public Query withSince(String since) {
      return ((ImmutableQuery) this).withSince(Optional.of(since));
    }

    @Value.Default
    @Value.Parameter(false)
    public Optional<Boolean> getCaseSensitive() {
      return Optional.empty();
    }

    public Query withCaseSensitive(boolean caseSensitive) {
      return ((ImmutableQuery) this).withCaseSensitive(Optional.of(caseSensitive));
    }

    /** Milliseconds. */
    @Value.Default
    @Value.Parameter(false)
    public Optional<Integer> getSyncTimeout() {
      return Optional.empty();
    }

    @Override
    public boolean doesSync() {
      // Sync is disabled when `sync_timeout` parameter is specified as zero
      return !getSyncTimeout().equals(Optional.of(0));
    }

    public Query withSyncTimeout(int syncTimeoutMillis) {
      return ((ImmutableQuery) this).withSyncTimeout(Optional.of(syncTimeoutMillis));
    }

    @Value.Default
    @Value.Parameter(false)
    public Optional<Boolean> getEmptyOnFreshInstance() {
      return Optional.empty();
    }

    public Query withEmptyOnFreshInstance(boolean emptyOnFreshInstance) {
      return ((ImmutableQuery) this).withEmptyOnFreshInstance(Optional.of(emptyOnFreshInstance));
    }

    @Override
    public ImmutableList<Object> toProtocolArgs() {
      ImmutableMap.Builder<String, Object> args = ImmutableMap.builder();
      if (!getRelativeRoot().isEmpty()) {
        args.put("relative_root", getRelativeRoot().toString());
      }
      getSince().ifPresent(s -> args.put("since", s));
      getCaseSensitive().ifPresent(c -> args.put("case_sensitive", c));
      getEmptyOnFreshInstance().ifPresent(e -> args.put("empty_on_fresh_instance", e));
      getSyncTimeout().ifPresent(t -> args.put("sync_timeout", t));
      getExpression().ifPresent(e -> args.put("expression", e));
      getGlob().ifPresent(g -> args.put("glob", g));
      args.put("fields", getFields());
      return ImmutableList.of("query", getPath().toString(), args.build());
    }
  }

  /** {@code version} query. */
  @BuckStyleValue
  public abstract static class Version extends WatchmanQuery<WatchmanQueryResp.Generic> {
    public abstract ImmutableList<String> getRequired();

    public abstract ImmutableList<String> getOptional();

    @Override
    public ImmutableList<Object> toProtocolArgs() {
      return ImmutableList.of(
          "version", ImmutableMap.of("required", getRequired(), "optional", getOptional()));
    }
  }

  /** {@code watch-project} query. */
  @BuckStyleValue
  public abstract static class WatchProject
      extends WatchmanQuery<WatchmanQueryResp.WatchProjectResp> {
    public abstract String getPath();

    @Override
    public ImmutableList<Object> toProtocolArgs() {
      return ImmutableList.of("watch-project", getPath());
    }

    @Override
    public WatchmanQueryResp.WatchProjectResp decodeResponse(ImmutableMap<String, Object> resp)
        throws WatchmanQueryFailedException {
      String watch = (String) resp.get("watch");
      if (watch == null) {
        throw new WatchmanQueryFailedException(
            "watch-project response has no `watch` field: " + resp, WatchmanError.NO_WATCH_FIELD);
      }
      String relativePath = (String) resp.getOrDefault("relative_path", "");
      return WatchmanQueryResp.watchProject(new WatchRoot(watch), relativePath);
    }
  }

  /** {@code watch} query. */
  @BuckStyleValue
  public abstract static class Watch extends WatchmanQuery<WatchmanQueryResp.Generic> {
    public abstract String getPath();

    @Override
    public ImmutableList<Object> toProtocolArgs() {
      return ImmutableList.of("watch", getPath());
    }
  }

  /** {@code clock} query. */
  @BuckStyleValue
  public abstract static class Clock extends WatchmanQuery<WatchmanQueryResp.Generic>
      implements QueryWithSync {
    public abstract WatchRoot getPath();

    /** Milliseconds. */
    public abstract Optional<Integer> getSyncTimeout();

    @Override
    public boolean doesSync() {
      return getSyncTimeout().isPresent();
    }

    @Override
    public ImmutableList<Object> toProtocolArgs() {
      return ImmutableList.of(
          "clock",
          getPath().toString(),
          getSyncTimeout().map(t -> ImmutableMap.of("sync_timeout", t)).orElse(ImmutableMap.of()));
    }
  }

  /** {@code get-pid} query. */
  @BuckStyleValue
  public abstract static class GetPid extends WatchmanQuery<WatchmanQueryResp.Generic> {
    @Override
    public ImmutableList<Object> toProtocolArgs() {
      return ImmutableList.of("get-pid");
    }
  }

  /** {@code query} query. */
  public static Query query(
      WatchRoot path,
      ForwardRelPath relativeRoot,
      Optional<Object> expression,
      Optional<Object> glob,
      ImmutableList<String> fields) {
    return ImmutableQuery.ofImpl(path, relativeRoot, expression, glob, fields);
  }

  /** {@code clock} query. */
  public static Clock clock(WatchRoot path, Optional<Integer> syncTimeoutMillis) {
    return ImmutableClock.ofImpl(path, syncTimeoutMillis);
  }

  /** {@code watch-project} query. */
  public static WatchProject watchProject(String path) {
    return ImmutableWatchProject.ofImpl(path);
  }

  /** {@code watch} query. */
  public static Watch watch(String path) {
    return ImmutableWatch.ofImpl(path);
  }

  /** {@code version} query. */
  public static Version version(ImmutableList<String> required, ImmutableList<String> optional) {
    return ImmutableVersion.ofImpl(required, optional);
  }

  /** {@code get-pid} query. */
  public static GetPid getPid() {
    return ImmutableGetPid.of();
  }
}

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

package com.facebook.buck.skylark.io.impl;

import com.facebook.buck.core.filesystems.ForwardRelPath;
import com.facebook.buck.io.watchman.WatchRoot;
import com.facebook.buck.io.watchman.WatchmanClient;
import com.facebook.buck.io.watchman.WatchmanQuery;
import com.facebook.buck.io.watchman.WatchmanQueryFailedException;
import com.facebook.buck.io.watchman.WatchmanQueryResp;
import com.facebook.buck.util.bser.BserNull;
import com.facebook.buck.util.types.Either;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import java.io.IOException;
import java.io.Serializable;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.annotation.Nullable;

/**
 * An implementation of globbing functionality that allows resolving file paths based on include
 * patterns (file patterns that should be returned) minus exclude patterns (file patterns that
 * should be excluded from the resulting set) using Watchman tool for improved performance.
 *
 * <p>The implementation is mostly compatible with glob_watchman.py and as such differs from the
 * {@link NativeGlobber} in certain ways:
 *
 * <ul>
 *   <li>does not fail for patterns that cannot possibly match
 *   <li>does not collapse multiple slashes
 * </ul>
 */
public class WatchmanGlobber {

  /**
   * Watchman options to use when globbing.
   *
   * @see WatchmanGlobber#run(Collection, Collection, EnumSet, long, long)
   */
  public enum Option {
    /**
     * Do not return directories which match include patterns.
     *
     * <p>Symlinks referring to directories are still returned unless {@link
     * Option#EXCLUDE_SYMLINKS} is also specified.
     *
     * <p>This option corresponds to a <a
     * href="https://facebook.github.io/watchman/docs/expr/type.html">{@code type} expression</a>
     * which excludes directories.
     */
    EXCLUDE_DIRECTORIES,
    /**
     * Do not return regular files which match include patterns.
     *
     * <p>This option corresponds to a <a
     * href="https://facebook.github.io/watchman/docs/expr/type.html">{@code type} expression</a>
     * which excludes directories.
     */
    EXCLUDE_REGULAR_FILES,
    /**
     * Do not return symbolic links which match include patterns.
     *
     * <p>Without this option, symbolic links are returned, regardless of their target.
     *
     * <p>This option corresponds to a <a
     * href="https://facebook.github.io/watchman/docs/expr/type.html">{@code type} expression</a>
     * which excludes symbolic links.
     */
    EXCLUDE_SYMLINKS,
    /**
     * Match path components exactly, even on case-insensitive file systems.
     *
     * <p>By default, whether or not patterns ignore case depends on <a
     * href="https://facebook.github.io/watchman/docs/cmd/query.html#case-sensitivity">Watchman's
     * default behavior</a>.
     *
     * <p>This option affects both include patterns and exclude patterns.
     *
     * <p>This option corresponds to the query's <a
     * href="https://facebook.github.io/watchman/docs/cmd/query.html#case-sensitivity">{@code
     * case_sensitive} option</a> to {@code true}.
     */
    FORCE_CASE_SENSITIVE,
  }

  /** A map-like class represents the file attributes queried from watchman globber */
  public static class WatchmanFileAttributes {
    private final ImmutableMap<String, ?> attributeMap;

    public WatchmanFileAttributes(Map<String, ?> attributeMap) {
      this.attributeMap = ImmutableMap.copyOf(attributeMap);
    }

    public ImmutableMap<String, ?> getAttributeMap() {
      return attributeMap;
    }

    @Override
    public boolean equals(@Nullable Object obj) {
      if (!(obj instanceof WatchmanFileAttributes)) {
        return false;
      }
      WatchmanFileAttributes that = (WatchmanFileAttributes) obj;
      return this.attributeMap.equals(that.getAttributeMap());
    }

    @Override
    public int hashCode() {
      return attributeMap.hashCode();
    }
  }

  private static final ImmutableList<String> NAME_ONLY_FIELD = ImmutableList.of("name");
  private final WatchmanClient watchmanClient;
  private final long queryWarnTimeoutNanos;
  private final long queryPollTimeoutNanos;
  /** Path used as a root when resolving patterns. */
  private final ForwardRelPath basePath;

  private final WatchRoot watchmanWatchRoot;

  private WatchmanGlobber(
      WatchmanClient watchmanClient,
      long queryPollTimeoutNanos,
      long queryWarnTimeoutNanos,
      ForwardRelPath basePath,
      WatchRoot watchmanWatchRoot) {
    this.watchmanClient = watchmanClient;
    this.queryWarnTimeoutNanos = queryWarnTimeoutNanos;
    this.queryPollTimeoutNanos = queryPollTimeoutNanos;
    this.basePath = basePath;
    this.watchmanWatchRoot = watchmanWatchRoot;
  }

  /**
   * @param include File patterns that should be included in the resulting set.
   * @param exclude File patterns that should be excluded from the resulting set.
   * @param excludeDirectories Whether directories should be excluded from the resulting set.
   * @return The set of paths resolved using include patterns minus paths excluded by exclude
   *     patterns.
   * @see WatchmanGlobber#run(Collection, Collection, EnumSet, long, long)
   */
  public Optional<ImmutableSet<String>> run(
      Collection<String> include, Collection<String> exclude, boolean excludeDirectories)
      throws IOException, InterruptedException, WatchmanQueryFailedException {
    return run(
        include,
        exclude,
        excludeDirectories ? EnumSet.of(Option.EXCLUDE_DIRECTORIES) : EnumSet.noneOf(Option.class),
        queryPollTimeoutNanos,
        queryWarnTimeoutNanos);
  }

  public Optional<ImmutableSet<String>> run(
      Collection<String> include, Collection<String> exclude, Option option)
      throws IOException, InterruptedException, WatchmanQueryFailedException {
    return run(include, exclude, EnumSet.of(option), queryPollTimeoutNanos, queryWarnTimeoutNanos);
  }

  public Optional<ImmutableSet<String>> run(
      Collection<String> include, Collection<String> exclude, EnumSet<Option> options)
      throws IOException, InterruptedException, WatchmanQueryFailedException {
    return run(include, exclude, options, queryPollTimeoutNanos, queryWarnTimeoutNanos);
  }

  /**
   * @param include File patterns that should be included in the resulting set.
   * @param exclude File patterns that should be excluded from the resulting set.
   * @param options Customizations for matching behavior.
   * @param timeoutNanos timeout in nanoseconds
   * @param warnTimeNanos time to polling results if query is slow
   * @return The set of paths resolved using include patterns minus paths excluded by exclude
   *     patterns.
   * @throws WatchmanQueryFailedException Watchman returned an error response.
   */
  public Optional<ImmutableSet<String>> run(
      Collection<String> include,
      Collection<String> exclude,
      EnumSet<Option> options,
      long timeoutNanos,
      long warnTimeNanos)
      throws IOException, InterruptedException, WatchmanQueryFailedException {
    Either<WatchmanQueryResp.Generic, WatchmanClient.Timeout> result =
        performWatchmanQuery(
            timeoutNanos, warnTimeNanos, include, exclude, options, NAME_ONLY_FIELD);
    if (!result.isLeft()) {
      return Optional.empty();
    }

    WatchmanQueryResp.Generic generic = result.getLeft();
    @SuppressWarnings("unchecked")
    List<String> files = (List<String>) generic.getResp().get("files");
    return Optional.of(ImmutableSet.copyOf(files));
  }

  private Either<WatchmanQueryResp.Generic, WatchmanClient.Timeout> performWatchmanQuery(
      long timeoutNanos,
      long pollingTimeNanos,
      Collection<String> include,
      Collection<String> exclude,
      EnumSet<Option> options,
      ImmutableList<String> fields)
      throws IOException, InterruptedException, WatchmanQueryFailedException {

    WatchmanQuery.Query query =
        WatchmanQuery.query(
            watchmanWatchRoot,
            basePath,
            Optional.of(toMatchExpressions(exclude, options)),
            Optional.of(ImmutableList.copyOf(include)),
            fields);

    if (options.contains(Option.FORCE_CASE_SENSITIVE)) {
      query = query.withCaseSensitive(true);
    }

    // Disable sync cookies. We did `clock` while creating watchman
    // (we recreate watchman and re-watch and re-clock on each buck command),
    // and `clock` query performed the sync.
    query = query.withSyncTimeout(0);

    return watchmanClient.queryWithTimeout(timeoutNanos, pollingTimeNanos, query);
  }

  /**
   * If only query file names, watchman returns [ "filename1", "filename2", "filename3" ... ]
   *
   * <p>If queries more than file name, i.e. size, watchman returns [ { "name": filename1, "size":
   * 12345 }, { "name": filename2, "size": 123456 }, ... ]
   *
   * <p>This function will convert both query into a map like [ "filename1": { "name": "filename1",
   * "size": 12345 } ]
   *
   * <p>This function does not check the fields is valid due to its performance. The capacity can be
   * checked by command "watchman list-capabilities"
   *
   * @param includePatterns File patterns that should be included in the resulting set.
   * @param excludePatterns File patterns that should be excluded from the resulting set.
   * @param options Customizations for matching behavior.
   * @param timeoutNanos timeout in nanoseconds
   * @param warnTimeNanos time to polling results if query is slow
   * @param fields Fields to query
   * @return a optional map of matching file names to their file properties.
   */
  public Optional<ImmutableMap<String, WatchmanFileAttributes>> runWithExtraFields(
      Collection<String> includePatterns,
      Collection<String> excludePatterns,
      EnumSet<Option> options,
      long timeoutNanos,
      long warnTimeNanos,
      ImmutableList<String> fields)
      throws IOException, InterruptedException, WatchmanQueryFailedException {
    Preconditions.checkArgument(!fields.isEmpty());

    if (fields.equals(NAME_ONLY_FIELD)) {
      Optional<ImmutableSet<String>> nameSet =
          run(includePatterns, excludePatterns, options, timeoutNanos, warnTimeNanos);
      if (nameSet.isPresent()) {
        ImmutableMap<String, WatchmanFileAttributes> resultMap =
            nameSet.get().stream()
                .collect(
                    ImmutableMap.toImmutableMap(
                        Function.identity(),
                        name -> new WatchmanFileAttributes(ImmutableMap.of("name", name))));
        return Optional.of(resultMap);
      } else {
        return Optional.empty();
      }
    }

    Either<WatchmanQueryResp.Generic, WatchmanClient.Timeout> result =
        performWatchmanQuery(
            timeoutNanos, warnTimeNanos, includePatterns, excludePatterns, options, fields);
    if (!result.isLeft()) {
      return Optional.empty();
    }

    WatchmanQueryResp.Generic generic = result.getLeft();
    @SuppressWarnings("unchecked")
    List<Map<String, ?>> resultEntries = (List<Map<String, ?>>) generic.getResp().get("files");
    ImmutableMap<String, WatchmanFileAttributes> resultMap =
        resultEntries.stream()
            .filter(
                entry ->
                    entry.containsKey("name")
                        && entry.values().stream().allMatch(v -> v != BserNull.NULL))
            .collect(
                ImmutableMap.toImmutableMap(
                    entry -> (String) entry.get("name"), WatchmanFileAttributes::new));
    return resultMap.isEmpty() ? Optional.empty() : Optional.of(resultMap);
  }

  /** Returns an expression for every matched include file should match in order to be returned. */
  private static ImmutableList<Object> toMatchExpressions(
      Collection<String> exclude, EnumSet<Option> options) {
    ImmutableList.Builder<Object> matchExpressions = ImmutableList.builder();
    matchExpressions.add("allof", toTypeExpression(options));
    if (!exclude.isEmpty()) {
      matchExpressions.add(toExcludeExpression(exclude));
    }
    return matchExpressions.build();
  }

  /** Returns an expression for matching types of files to return. */
  private static ImmutableList<Object> toTypeExpression(EnumSet<Option> options) {
    ImmutableList.Builder<Object> typeExpressionBuilder = ImmutableList.builder().add("anyof");

    if (!options.contains(Option.EXCLUDE_REGULAR_FILES)) {
      typeExpressionBuilder.add(ImmutableList.of("type", "f"));
    }
    if (!options.contains(Option.EXCLUDE_DIRECTORIES)) {
      typeExpressionBuilder.add(ImmutableList.of("type", "d"));
    }
    if (!options.contains(Option.EXCLUDE_SYMLINKS)) {
      typeExpressionBuilder.add(ImmutableList.of("type", "l"));
    }
    return typeExpressionBuilder.build();
  }

  /** Returns an expression that excludes all paths in {@code exclude}. */
  private static ImmutableList<Serializable> toExcludeExpression(Collection<String> exclude) {
    return ImmutableList.of(
        "not",
        ImmutableList.builder()
            .add("anyof")
            .addAll(
                exclude.stream()
                    .map(e -> ImmutableList.of("match", e, "wholename"))
                    .collect(Collectors.toList()))
            .build());
  }

  /**
   * Factory method for creating {@link WatchmanGlobber} instances.
   *
   * @param basePath The base path relative to which paths matching glob patterns will be resolved.
   */
  public static WatchmanGlobber create(
      WatchmanClient watchmanClient,
      long queryPollTimeoutNanos,
      long queryWarnTimeoutNanos,
      ForwardRelPath basePath,
      WatchRoot watchmanWatchRoot) {
    return new WatchmanGlobber(
        watchmanClient, queryPollTimeoutNanos, queryWarnTimeoutNanos, basePath, watchmanWatchRoot);
  }
}

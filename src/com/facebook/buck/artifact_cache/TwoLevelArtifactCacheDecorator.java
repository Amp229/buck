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

package com.facebook.buck.artifact_cache;

import com.facebook.buck.artifact_cache.config.ArtifactCacheMode;
import com.facebook.buck.artifact_cache.config.CacheReadMode;
import com.facebook.buck.core.exceptions.HumanReadableException;
import com.facebook.buck.core.model.BuildTarget;
import com.facebook.buck.core.rulekey.RuleKey;
import com.facebook.buck.core.util.log.Logger;
import com.facebook.buck.counters.CounterRegistry;
import com.facebook.buck.counters.IntegerCounter;
import com.facebook.buck.counters.SamplingCounter;
import com.facebook.buck.counters.TagSetCounter;
import com.facebook.buck.event.BuckEventBus;
import com.facebook.buck.io.file.BorrowablePath;
import com.facebook.buck.io.file.LazyPath;
import com.facebook.buck.io.filesystem.ProjectFilesystem;
import com.facebook.buck.util.stream.RichStream;
import com.facebook.buck.util.types.Unit;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import javax.annotation.Nullable;

/**
 * The {@link DirArtifactCache} and {@link HttpArtifactCache} caches use a straightforward rulekey
 * -> (metadata, artifact) mapping. This works well and is easy to reason about. That also means
 * that we will fetch `artifact` whenever they `key` or `metadata` change possibly resulting in
 * fetching the same artifact multiple times. This class is an attempt at reducing the number of
 * repeated fetches for the same artifact. The data is stored and retrieved using the following
 * scheme: rulekey -> (metadata, content hash) content hash -> artifact This means we only download
 * the artifact when its contents change. This means that rules with different keys but identical
 * outputs require less network bandwidth at the expense of doubling latency for downloading rules
 * whose outputs we had not yet seen.
 */
public class TwoLevelArtifactCacheDecorator implements ArtifactCache, CacheDecorator {

  @VisibleForTesting static final String METADATA_KEY = "TWO_LEVEL_CACHE_CONTENT_HASH";
  public static final String COUNTER_CATEGORY = "buck_two_level_cache_stats";

  private static final Logger LOG = Logger.get(TwoLevelArtifactCacheDecorator.class);

  private final ArtifactCache delegate;
  private final SecondLevelArtifactCache secondLevelDelegate;
  private final ProjectFilesystem projectFilesystem;
  private final Path emptyFilePath;
  private final boolean performTwoLevelStores;
  private final long minimumTwoLevelStoredArtifactSize;
  private final Optional<Long> maximumTwoLevelStoredArtifactSize;

  private final TagSetCounter secondLevelCacheHitTypes;
  private final SamplingCounter secondLevelCacheHitBytes;
  private final IntegerCounter secondLevelCacheMisses;

  public TwoLevelArtifactCacheDecorator(
      ArtifactCache delegate,
      SecondLevelArtifactCache secondLevelDelegate,
      ProjectFilesystem projectFilesystem,
      BuckEventBus buckEventBus,
      boolean performTwoLevelStores,
      long minimumTwoLevelStoredArtifactSize,
      Optional<Long> maximumTwoLevelStoredArtifactSize) {
    this.delegate = delegate;
    this.secondLevelDelegate = secondLevelDelegate;
    this.projectFilesystem = projectFilesystem;
    this.performTwoLevelStores = performTwoLevelStores;
    this.minimumTwoLevelStoredArtifactSize = minimumTwoLevelStoredArtifactSize;
    this.maximumTwoLevelStoredArtifactSize = maximumTwoLevelStoredArtifactSize;

    Path tmpDir = projectFilesystem.getBuckPaths().getTmpDir();
    try {
      projectFilesystem.mkdirs(tmpDir);
      this.emptyFilePath =
          projectFilesystem.resolve(
              projectFilesystem.createTempFile(tmpDir, ".buckcache", ".empty"));
    } catch (IOException e) {
      throw new HumanReadableException(
          "Could not create file in " + projectFilesystem.resolve(tmpDir));
    }

    secondLevelCacheHitTypes =
        new TagSetCounter(COUNTER_CATEGORY, "second_level_cache_hit_types", ImmutableMap.of());
    secondLevelCacheHitBytes =
        new SamplingCounter(COUNTER_CATEGORY, "second_level_cache_hit_bytes", ImmutableMap.of());
    secondLevelCacheMisses =
        new IntegerCounter(COUNTER_CATEGORY, "second_level_cache_misses", ImmutableMap.of());
    buckEventBus.post(
        new CounterRegistry.AsyncCounterRegistrationEvent(
            ImmutableList.of(
                secondLevelCacheHitTypes, secondLevelCacheHitBytes, secondLevelCacheMisses)));
  }

  @Override
  public ListenableFuture<CacheResult> fetchAsync(
      @Nullable BuildTarget target, RuleKey ruleKey, LazyPath output) {
    return Futures.transformAsync(
        delegate.fetchAsync(target, ruleKey, output),
        (CacheResult fetchResult) -> {
          if (!fetchResult.getType().isSuccess()) {
            LOG.verbose("Missed first-level lookup.");
            return Futures.immediateFuture(fetchResult);
          } else if (!fetchResult.getMetadata().containsKey(METADATA_KEY)) {
            LOG.verbose("Found a single-level entry.");
            return Futures.immediateFuture(fetchResult);
          }
          LOG.verbose("Found a first-level artifact with metadata: %s", fetchResult.getMetadata());

          String contentHashKey = fetchResult.getMetadata().get(METADATA_KEY);
          LOG.debug(
              "First-level lookup finished using ruleKey: %s, "
                  + "got second-level contentHashKey: %s",
              ruleKey.toString(), contentHashKey);
          ListenableFuture<CacheResult> outputFileFetchResultFuture =
              Futures.catchingAsync(
                  secondLevelDelegate.fetchAsync(target, contentHashKey, output),
                  Exception.class,
                  e -> {
                    LOG.warn(e, "Error in second-level cache fetch, reporting cache miss.");
                    return Futures.immediateFuture(
                        CacheResult.miss(
                            "TwoLevelArtifactCacheDecorator", ArtifactCacheMode.unknown));
                  },
                  MoreExecutors.directExecutor());

          return Futures.transformAsync(
              outputFileFetchResultFuture,
              (CacheResult outputFileFetchResult) -> {
                outputFileFetchResult =
                    outputFileFetchResult.withTwoLevelContentHashKey(Optional.of(contentHashKey));

                if (!outputFileFetchResult.getType().isSuccess()) {
                  LOG.debug("Missed second-level lookup using contentHashKey: %s", contentHashKey);
                  secondLevelCacheMisses.inc();

                  // Note: for misses, the fetchResult metadata is not important, so we return
                  // outputFileFetchResult to signal the miss (as fetchResult was a hit).
                  return Futures.immediateFuture(outputFileFetchResult);
                }

                if (outputFileFetchResult.cacheSource().isPresent()) {
                  secondLevelCacheHitTypes.add(outputFileFetchResult.cacheSource().get());
                }
                if (outputFileFetchResult.artifactSizeBytes().isPresent()) {
                  secondLevelCacheHitBytes.addSample(
                      outputFileFetchResult.artifactSizeBytes().get());
                }

                LOG.verbose(
                    "Found a second-level artifact with metadata: %s",
                    outputFileFetchResult.getMetadata());
                // Note: in the case of a hit, we return fetchResult, rather than
                // outputFileFetchResult,
                // so that the client gets the correct metadata.
                CacheResult finalResult =
                    fetchResult
                        .withTwoLevelContentHashKey(Optional.of(contentHashKey))
                        .withArtifactSizeBytes(outputFileFetchResult.artifactSizeBytes());

                // The two level content hash was not part of the original metadata that was
                // stored
                // to the cache, don't include it in the result.
                finalResult =
                    finalResult.withMetadata(
                        Optional.of(
                            ImmutableMap.copyOf(
                                RichStream.from(finalResult.getMetadata().entrySet())
                                    .filter(e -> !Objects.equals(e.getKey(), METADATA_KEY))
                                    .toOnceIterable())));
                return Futures.immediateFuture(finalResult);
              },
              MoreExecutors.directExecutor());
        },
        MoreExecutors.directExecutor());
  }

  @Override
  public void skipPendingAndFutureAsyncFetches() {
    delegate.skipPendingAndFutureAsyncFetches();
  }

  @Override
  public ListenableFuture<Unit> store(ArtifactInfo info, BorrowablePath output) {

    return Futures.transformAsync(
        attemptTwoLevelStore(info, output),
        input -> {
          if (input) {
            return Futures.immediateFuture(null);
          }
          return delegate.store(info, output);
        },
        MoreExecutors.directExecutor());
  }

  @Override
  public ListenableFuture<CacheDeleteResult> deleteAsync(List<RuleKey> ruleKeys) {
    // Artifact can be stored as two-level entry (rule key -> hash -> content)
    // and delete operation only deletes first level (rule key -> hash) in that case.
    return delegate.deleteAsync(ruleKeys);
  }

  @Override
  public ArtifactCache getDelegate() {
    return delegate;
  }

  private ListenableFuture<Boolean> attemptTwoLevelStore(ArtifactInfo info, BorrowablePath output) {
    try {
      long fileSize = projectFilesystem.getFileSize(output.getPath());

      if (!performTwoLevelStores
          || fileSize < minimumTwoLevelStoredArtifactSize
          || (maximumTwoLevelStoredArtifactSize.isPresent()
              && fileSize > maximumTwoLevelStoredArtifactSize.get())) {
        return Futures.immediateFuture(false);
      }
    } catch (IOException e) {
      throw new RuntimeException("Cannot get file size of " + output.getPath());
    }

    // We need to upload artifacts in this order to prevent race condition. If we would do
    // it concurrently it is possible that we upload metadata before the file. Then other
    // builder read metadata, but cannot find a file (which is still being uploaded), and
    // decide that we have to re-upload it. With enough machines building the same target
    // we end up with constant re-uploading and rebuilding flow. The following issue is
    // only in case when output hash changes between builds.
    return Futures.transformAsync(
        secondLevelDelegate.storeAsync(info, output),
        contentKey -> {
          if (contentKey == null) {
            throw new RuntimeException("No content key received from second level artifact cache.");
          }

          ImmutableMap<String, String> metadataWithCacheKey =
              ImmutableMap.<String, String>builder()
                  .putAll(info.getMetadata())
                  .put(METADATA_KEY, contentKey)
                  .build();

          ArtifactInfo metadata =
              ArtifactInfo.builder()
                  .setRuleKeys(info.getRuleKeys())
                  .setMetadata(metadataWithCacheKey)
                  .setBuildTarget(info.getBuildTarget())
                  .setBuildTimeMs(info.getBuildTimeMs())
                  .build();

          return Futures.transform(
              delegate.store(metadata, BorrowablePath.notBorrowablePath(emptyFilePath)),
              __ -> true,
              MoreExecutors.directExecutor());
        },
        MoreExecutors.directExecutor());
  }

  @Override
  public CacheReadMode getCacheReadMode() {
    return delegate.getCacheReadMode();
  }

  @Override
  public void close() {
    delegate.close();
    secondLevelDelegate.close();

    try {
      projectFilesystem.deleteFileAtPath(emptyFilePath);
    } catch (IOException e) {
      LOG.debug("Exception when deleting temp file %s.", emptyFilePath, e);
    }
  }
}

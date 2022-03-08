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

package com.facebook.buck.event.listener;

import static com.facebook.buck.log.MachineReadableLogConfig.PREFIX_BUILD_FINISHED;
import static com.facebook.buck.log.MachineReadableLogConfig.PREFIX_BUILD_RULE_FINISHED;
import static com.facebook.buck.log.MachineReadableLogConfig.PREFIX_CACHE_STATS;
import static com.facebook.buck.log.MachineReadableLogConfig.PREFIX_EXIT_CODE;
import static com.facebook.buck.log.MachineReadableLogConfig.PREFIX_INVOCATION_INFO;
import static com.facebook.buck.log.MachineReadableLogConfig.PREFIX_PERFTIMES;

import com.facebook.buck.artifact_cache.ArtifactCacheEvent;
import com.facebook.buck.artifact_cache.CacheCountersSummary;
import com.facebook.buck.artifact_cache.CacheResult;
import com.facebook.buck.artifact_cache.CacheResultType;
import com.facebook.buck.artifact_cache.HttpArtifactCacheEvent;
import com.facebook.buck.artifact_cache.config.ArtifactCacheMode;
import com.facebook.buck.core.build.event.BuildEvent;
import com.facebook.buck.core.build.event.BuildRuleEvent;
import com.facebook.buck.core.build.event.BuildRuleExecutionEvent;
import com.facebook.buck.core.model.BuildId;
import com.facebook.buck.core.util.immutables.BuckStyleValue;
import com.facebook.buck.core.util.log.Logger;
import com.facebook.buck.event.ActionGraphEvent;
import com.facebook.buck.event.ArtifactCompressionEvent;
import com.facebook.buck.event.BuckEventListener;
import com.facebook.buck.event.CommandEvent;
import com.facebook.buck.event.ParsingEvent;
import com.facebook.buck.event.WatchmanStatusEvent;
import com.facebook.buck.event.chrome_trace.ChromeTraceBuckConfig;
import com.facebook.buck.io.filesystem.ProjectFilesystem;
import com.facebook.buck.log.InvocationInfo;
import com.facebook.buck.log.PerfTimesStats;
import com.facebook.buck.log.views.JsonViews;
import com.facebook.buck.logd.client.LogStreamFactory;
import com.facebook.buck.logd.proto.LogType;
import com.facebook.buck.parser.ParseEvent;
import com.facebook.buck.remoteexecution.event.RemoteExecutionActionEvent;
import com.facebook.buck.support.bgtasks.BackgroundTask;
import com.facebook.buck.support.bgtasks.BackgroundTask.Timeout;
import com.facebook.buck.support.bgtasks.TaskAction;
import com.facebook.buck.support.bgtasks.TaskManagerCommandScope;
import com.facebook.buck.support.jvm.GCCollectionEvent;
import com.facebook.buck.util.BuckConstant;
import com.facebook.buck.util.ExitCode;
import com.facebook.buck.util.json.ObjectMappers;
import com.facebook.buck.util.trace.uploader.launcher.UploaderLauncher;
import com.facebook.buck.util.trace.uploader.types.CompressionType;
import com.facebook.buck.util.trace.uploader.types.TraceKind;
import com.facebook.buck.util.versioncontrol.VersionControlStatsEvent;
import com.fasterxml.jackson.core.JsonGenerator.Feature;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.common.eventbus.Subscribe;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import javax.annotation.Nullable;

public class MachineReadableLoggerListener implements BuckEventListener {

  private static final Logger LOG = Logger.get(MachineReadableLoggerListener.class);

  private static final byte[] NEWLINE = "\n".getBytes(StandardCharsets.UTF_8);
  private static final int SHUTDOWN_TIMEOUT_SECONDS = 30;

  private final InvocationInfo info;
  private final ExecutorService executor;
  private final ProjectFilesystem filesystem;
  private final ObjectWriter objectWriter;
  private final BufferedOutputStream outputStream;
  private boolean outputStreamClosed;

  private final ChromeTraceBuckConfig config;
  private final Path logDirectoryPath;
  private final BuildId buildId;
  private final TaskManagerCommandScope managerScope;
  private final boolean syncOnClose;

  private final ConcurrentMap<ArtifactCacheMode, AtomicInteger> cacheModeHits =
      Maps.newConcurrentMap();
  private final ConcurrentMap<ArtifactCacheMode, AtomicInteger> cacheModeHitAttempts =
      Maps.newConcurrentMap();
  private final ConcurrentMap<ArtifactCacheMode, AtomicInteger> cacheModeErrors =
      Maps.newConcurrentMap();
  private final ConcurrentMap<ArtifactCacheMode, AtomicLong> cacheModeBytes =
      Maps.newConcurrentMap();
  private final AtomicInteger cacheMisses = new AtomicInteger(0);
  private final AtomicInteger cacheIgnores = new AtomicInteger(0);
  private final AtomicInteger localKeyUnchangedHits = new AtomicInteger(0);

  @Nullable private PerfTimesStats latestPerfTimesStats;

  // Values to be written in the end of the log.
  private Optional<ExitCode> exitCode = Optional.empty();

  // Cache upload statistics
  private final AtomicInteger cacheUploadSuccessCount = new AtomicInteger();
  private final AtomicInteger cacheUploadFailureCount = new AtomicInteger();

  public MachineReadableLoggerListener(
      InvocationInfo info,
      ProjectFilesystem filesystem,
      ExecutorService executor,
      ImmutableSet<ArtifactCacheMode> cacheModes,
      ChromeTraceBuckConfig config,
      Path logDirectoryPath,
      BuildId buildId,
      TaskManagerCommandScope managerScope,
      LogStreamFactory logStreamFactory,
      boolean syncOnClose)
      throws IOException {
    this.info = info;
    this.filesystem = filesystem;
    this.executor = executor;
    this.config = config;
    this.logDirectoryPath = logDirectoryPath;
    this.buildId = buildId;
    this.managerScope = managerScope;
    this.syncOnClose = syncOnClose;

    for (ArtifactCacheMode mode : cacheModes) {
      cacheModeHits.put(mode, new AtomicInteger(0));
      cacheModeHitAttempts.put(mode, new AtomicInteger(0));
      cacheModeErrors.put(mode, new AtomicInteger(0));
      cacheModeBytes.put(mode, new AtomicLong(0L));
    }

    this.objectWriter =
        ObjectMappers.legacyCreate()
            .copy()
            .configure(MapperFeature.DEFAULT_VIEW_INCLUSION, false)
            .writerWithView(JsonViews.MachineReadableLog.class);

    this.outputStream =
        new BufferedOutputStream(
            logStreamFactory.createLogStream(
                getLogFilePath().toString(), LogType.BUCK_MACHINE_LOG));

    writeToLog(PREFIX_INVOCATION_INFO, info);
  }

  @Subscribe
  public void versionControlStats(VersionControlStatsEvent versionControlStatsEvent) {
    writeToLog("SourceControlInformation", versionControlStatsEvent);
  }

  @Subscribe
  public void parseStarted(ParseEvent.Started event) {
    writeToLog("ParseStarted", event);
  }

  @Subscribe
  public void parseFinished(ParseEvent.Finished event) {
    writeToLog("ParseFinished", event);
  }

  /** @ BuildFinished event handler */
  @Subscribe
  public void buildFinished(BuildEvent.Finished event) {
    writeToLog(PREFIX_BUILD_FINISHED, event);
  }

  @Subscribe
  public void buildRuleEventStarted(BuildRuleEvent.Started event) {
    writeToLog("BuildRuleEvent.Started", event);
  }

  @Subscribe
  public void buildRuleEventResumed(BuildRuleEvent.Resumed event) {
    writeToLog("BuildRuleEvent.Resumed", event);
  }

  @Subscribe
  public void buildRuleEventSuspended(BuildRuleEvent.Suspended event) {
    writeToLog("BuildRuleEvent.Suspended", event);
  }

  @Subscribe
  public void buildRuleEventStartedRuleCalc(BuildRuleEvent.StartedRuleKeyCalc event) {
    writeToLog("BuildRuleEvent.StartedRuleKeyCalc", event);
  }

  @Subscribe
  public void buildRuleEventFinishedRuleCalc(BuildRuleEvent.FinishedRuleKeyCalc event) {
    writeToLog("BuildRuleEvent.FinishedRuleKeyCalc", event);
  }

  @Subscribe
  public void buildRuleEventWillBuildLocally(BuildRuleEvent.WillBuildLocally event) {
    writeToLog("BuildRuleEvent.WillBuildLocally", event);
  }

  @Subscribe
  public void buildRuleExecutionStartedEvent(BuildRuleExecutionEvent.Started event) {
    writeToLog("ExecutionStarted", event);
  }

  @Subscribe
  public void buildRuleExecutionFinishedEvent(BuildRuleExecutionEvent.Finished event) {
    writeToLog("ExecutionFinished", event);
  }

  @Subscribe
  public void buildRuleRemoteExecutionStartedEvent(RemoteExecutionActionEvent.Started event) {
    writeToLog("RemoteExecutionStarted", event);
  }

  @Subscribe
  public void buildRuleRemoteExecutionFinishedEvent(RemoteExecutionActionEvent.Finished event) {
    writeToLog("RemoteExecutionFinished", event);
  }

  @Subscribe
  public void buildRuleRemoteExecutionScheduledEvent(RemoteExecutionActionEvent.Scheduled event) {
    writeToLog("RemoteExecutionScheduled", event);
  }

  @Subscribe
  public void buildRuleRemoteExecutionTerminalEvent(RemoteExecutionActionEvent.Terminal event) {
    writeToLog("RemoteExecutionTerminal", event);
  }

  @Subscribe
  public void actionGraphStartedEvent(ActionGraphEvent.Started event) {
    writeToLog("BuildActionGraphStarted", event);
  }

  @Subscribe
  public void actionGraphFinishedEvent(ActionGraphEvent.Finished event) {
    writeToLog("BuildActionGraphFinished", event);
  }

  @Subscribe
  public void artifactCompressionStarted(ArtifactCompressionEvent.Started event) {
    writeToLog(event.getEventName(), event);
  }

  @Subscribe
  public void artifactCompressionFinished(ArtifactCompressionEvent.Finished event) {
    writeToLog(event.getEventName(), event);
  }

  @Subscribe
  public void buildRuleEventFinished(BuildRuleEvent.Finished event) {
    writeToLog(PREFIX_BUILD_RULE_FINISHED, event);

    CacheResult cacheResult = event.getCacheResult();
    if (cacheResult.getType().isSuccess()) {
      if (cacheResult.getType() == CacheResultType.LOCAL_KEY_UNCHANGED_HIT) {
        localKeyUnchangedHits.incrementAndGet();
      } else if (cacheResult.getType() == CacheResultType.HIT) {
        if (cacheResult.cacheMode().isPresent()) {
          ArtifactCacheMode mode = cacheResult.cacheMode().get();
          AtomicInteger hits = cacheModeHits.get(mode);
          if (hits != null) {
            hits.incrementAndGet();
          }
          AtomicLong bytes = cacheModeBytes.get(mode);
          if (bytes != null) {
            bytes.addAndGet(cacheResult.artifactSizeBytes().orElse(0L));
          }
          AtomicInteger hitAttempts = cacheModeHitAttempts.get(mode);
          if (hitAttempts != null) {
            hitAttempts.addAndGet(cacheResult.getAttempts());
          }
        }
      } else {
        throw new IllegalArgumentException("Unexpected CacheResult: " + cacheResult);
      }
    } else if (cacheResult.getType() == CacheResultType.ERROR) {
      cacheResult.cacheMode().ifPresent(mode -> cacheModeErrors.get(mode).incrementAndGet());
    } else if (cacheResult.getType() == CacheResultType.IGNORED) {
      cacheIgnores.incrementAndGet();
    } else {
      cacheMisses.incrementAndGet();
    }
  }

  @Subscribe
  public void garbageCollection(GCCollectionEvent event) {
    writeToLog(event.getEventName(), event);
  }

  @Subscribe
  public synchronized void commandFinished(CommandEvent.Finished event) {
    exitCode = Optional.of(event.getExitCode());
  }

  @Subscribe
  public synchronized void commandInterrupted(CommandEvent.Interrupted event) {
    exitCode = Optional.of(event.getExitCode());
  }

  @Subscribe
  public void watchmanFileCreation(WatchmanStatusEvent.FileCreation event) {
    writeToLog("FileCreate", event);
  }

  @Subscribe
  public void watchmanFileDeletion(WatchmanStatusEvent.FileDeletion event) {
    writeToLog("FileDelete", event);
  }

  @Subscribe
  public void watchmanOverflow(WatchmanStatusEvent.Overflow event) {
    writeToLog("WatchmanOverflow", event);
  }

  @Subscribe
  public void symlinkInvalidation(ParsingEvent.SymlinkInvalidation event) {
    writeToLog("SymlinkInvalidation", event);
  }

  @Subscribe
  public synchronized void timePerfStatsEvent(PerfTimesEventListener.PerfTimesEvent event) {
    latestPerfTimesStats = event.getPerfTimesStats();
  }

  @Subscribe
  public void onArtifactCacheEvent(HttpArtifactCacheEvent.Finished event) {
    if (event.getOperation() == ArtifactCacheEvent.Operation.STORE) {
      if (event.getStoreData().wasStoreSuccessful().orElse(false)) {
        cacheUploadSuccessCount.incrementAndGet();
      } else {
        cacheUploadFailureCount.incrementAndGet();
      }
    }
  }

  private Path getLogFilePath() {
    return filesystem
        .resolve(info.getLogDirectoryPath())
        .resolve(BuckConstant.BUCK_MACHINE_LOG_FILE_NAME);
  }

  private void writeToLog(String prefix, Object obj) {
    executor.submit(() -> writeToLogImpl(prefix, obj));
  }

  private synchronized void writeToLogImpl(String prefix, Object obj) {
    if (outputStreamClosed) {
      // If we already closed the output stream, don't try writing to it.
      // Closing the output stream can happen long before the executor is
      // shut down, since we use a BackgroundTask to actually call shutDown().
      return;
    }

    try {
      outputStream.write((prefix + " ").getBytes(StandardCharsets.UTF_8));
      objectWriter.without(Feature.AUTO_CLOSE_TARGET).writeValue(outputStream, obj);
      outputStream.write(NEWLINE);
    } catch (JsonProcessingException e) {
      LOG.warn("Failed to process json for event type: %s ", prefix);
    } catch (IOException e) {
      LOG.debug("Failed to write to %s", BuckConstant.BUCK_MACHINE_LOG_FILE_NAME, e);
    }
  }

  @Override
  public void close() {
    synchronized (this) {
      try {
        executor.submit(this::closeImpl);
      } catch (RejectedExecutionException e) {
        // ignore: someone else has closed the executor
        return;
      }
      executor.shutdown();
    }
    // Must release lock here otherwise `closeImpl` won't start

    // Note this flag is off by default, and set only in tests
    if (syncOnClose) {
      try {
        executor.awaitTermination(SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS);
      } catch (InterruptedException e) {
        throw new RuntimeException(e);
      }
    }

    synchronized (this) {
      MachineReadableLoggerListenerCloseArgs args =
          ImmutableMachineReadableLoggerListenerCloseArgs.ofImpl(
              executor,
              info.getLogDirectoryPath().resolve(BuckConstant.BUCK_MACHINE_LOG_FILE_NAME),
              config.getLogUploadMode().shouldUploadLogs(exitCode)
                  ? config.getTraceUploadUri()
                  : Optional.empty(),
              logDirectoryPath,
              buildId);

      BackgroundTask<MachineReadableLoggerListenerCloseArgs> task =
          BackgroundTask.of(
              "MachineReadableLoggerListener_close",
              new MachineReadableLoggerListenerCloseAction(),
              args,
              Timeout.of(config.getMaxUploadTimeoutInSeconds(), TimeUnit.SECONDS));
      managerScope.schedule(task);
    }
  }

  private synchronized void closeImpl() {
    try {
      if (latestPerfTimesStats != null) {
        writeToLogImpl(PREFIX_PERFTIMES, latestPerfTimesStats);
      }
      writeToLogImpl(
          PREFIX_CACHE_STATS,
          CacheCountersSummary.of(
              cacheModeHits,
              cacheModeHitAttempts,
              cacheModeErrors,
              cacheModeBytes,
              cacheModeHits.values().stream().mapToInt(AtomicInteger::get).sum(),
              cacheModeErrors.values().stream().mapToInt(AtomicInteger::get).sum(),
              cacheMisses.get(),
              cacheIgnores.get(),
              cacheModeBytes.values().stream().mapToLong(AtomicLong::get).sum(),
              localKeyUnchangedHits.get(),
              cacheUploadSuccessCount,
              cacheUploadFailureCount));

      outputStream.write(
          String.format(
                  PREFIX_EXIT_CODE + " {\"exitCode\":%d}",
                  exitCode.map(ExitCode::getCode).orElse(-1))
              .getBytes(StandardCharsets.UTF_8));

      outputStreamClosed = true;
      outputStream.close();
    } catch (IOException e) {
      LOG.warn("Failed to close output stream.");
    }
  }

  /**
   * {@link TaskAction} implementation for {@link MachineReadableLoggerListener}. Waits for output
   * executor to finish and close.
   */
  static class MachineReadableLoggerListenerCloseAction
      implements TaskAction<MachineReadableLoggerListenerCloseArgs> {

    @Override
    public void run(MachineReadableLoggerListenerCloseArgs args) throws InterruptedException {
      ExecutorService executor = args.getExecutor();
      executor.shutdown();

      Optional<Process> uploadProcess =
          args.getTraceUploadURI().map(uri -> launchUploader(args, uri));

      // Allow SHUTDOWN_TIMEOUT_SECONDS seconds for already scheduled writeToLog calls
      // to complete.
      try {
        if (!executor.awaitTermination(SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
          String error =
              "Machine readable log failed to complete all jobs within timeout during shutdown";
          LOG.error(error);
        }
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }

      UploaderLauncher.maybeWaitForProcessToFinish(uploadProcess);
    }

    @Nullable
    private Process launchUploader(
        MachineReadableLoggerListenerCloseArgs args, URI traceUploadURI) {
      try {
        return UploaderLauncher.uploadInBackground(
            args.getBuildId(),
            args.getMachineReadableLogFilePath(),
            TraceKind.MACHINE_READABLE_LOG,
            traceUploadURI,
            args.getLogDirectoryPath().resolve("upload-machine-readable-log.log"),
            CompressionType.GZIP);
      } catch (IOException e) {
        LOG.warn(e, "Can't launch uploader process");
      }
      return null;
    }
  }

  /** Arguments to {@link MachineReadableLoggerListenerCloseAction}. */
  @BuckStyleValue
  abstract static class MachineReadableLoggerListenerCloseArgs {

    public abstract ExecutorService getExecutor();

    public abstract Path getMachineReadableLogFilePath();

    public abstract Optional<URI> getTraceUploadURI();

    public abstract Path getLogDirectoryPath();

    public abstract BuildId getBuildId();
  }
}

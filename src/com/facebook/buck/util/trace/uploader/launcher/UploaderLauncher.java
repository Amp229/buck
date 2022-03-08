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

package com.facebook.buck.util.trace.uploader.launcher;

import com.facebook.buck.core.model.BuildId;
import com.facebook.buck.core.util.log.Logger;
import com.facebook.buck.util.env.BuckClasspath;
import com.facebook.buck.util.java.JavaRuntimeUtils;
import com.facebook.buck.util.trace.uploader.types.CompressionType;
import com.facebook.buck.util.trace.uploader.types.TraceKind;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

/** Utility to upload chrome trace in background. */
public class UploaderLauncher {

  private static final Logger LOG = Logger.get(UploaderLauncher.class);

  private static final String UPLOADER_MAIN_CLASS =
      "com.facebook.buck.util.trace.uploader.UploaderMain";

  /** Upload chrome trace in background process which runs even after current process dies. */
  public static Process uploadInBackground(
      BuildId buildId,
      Path traceFilePath,
      TraceKind traceKind,
      URI traceUploadUri,
      Path logFile,
      CompressionType compressionType)
      throws IOException {

    LOG.debug("Uploading build trace in the background. Upload will log to %s", logFile);

    String buckClasspath =
        Objects.requireNonNull(
            BuckClasspath.getBuckClasspathFromEnvVarOrNull(),
            BuckClasspath.ENV_VAR_NAME + " env variable is not set");

    String[] args = {
      JavaRuntimeUtils.getBucksJavaBinCommand(),
      // Directs the VM to refrain from setting the file descriptor limit to the default maximum.
      // https://stackoverflow.com/a/16535804/5208808
      "-XX:-MaxFDLimit",
      "-cp",
      buckClasspath,
      UPLOADER_MAIN_CLASS,
      "--buildId",
      buildId.toString(),
      "--traceFilePath",
      traceFilePath.toString(),
      "--traceKind",
      traceKind.name(),
      "--baseUrl",
      traceUploadUri.toString(),
      "--log",
      logFile.toString(),
      "--compressionType",
      compressionType.name(),
    };

    return Runtime.getRuntime().exec(args);
  }

  /** Waits for process to finish if process is present */
  public static void maybeWaitForProcessToFinish(Optional<Process> uploadProcess)
      throws InterruptedException {
    if (uploadProcess.isPresent()) {
      waitForProcessToFinish(uploadProcess.get());
    }
  }

  /** Waits for process to finish */
  public static void waitForProcessToFinish(Process process) throws InterruptedException {
    try {
      // wait for process to finish
      process.waitFor();
    } finally {
      if (process.isAlive()) {
        LOG.warn("Killing uploader process...");
        process.destroyForcibly();
      }
    }
  }
}

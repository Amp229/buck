/*
 * Copyright (c) Facebook, Inc. and its affiliates.
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

package com.facebook.buck.android.manifest;

import com.android.common.utils.StdLogger;
import com.facebook.buck.util.ThrowingPrintWriter;
import com.google.common.collect.ImmutableSet;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.logging.Logger; // NOPMD

/**
 * Main entry point for executing {@link GenerateManifest} calls.
 *
 * <p>Expected usage: {@code this_binary <skeleton_manifest_path> <module_name>
 * <library_manifest_paths_file> <output_path> <merge_report_path} .
 */
public class GenerateManifestExecutableMain {

  private static final Logger LOG =
      Logger.getLogger(GenerateManifestExecutableMain.class.getName());

  public static void main(String[] args) throws IOException {
    if (args.length != 5) {
      LOG.severe(
          "Must specify a skeleton manifest path, a module name, a file of library manifest paths, an output path and a merge report path");
      System.exit(1);
    }

    Path skeletonManifestPath = Paths.get(args[0]);
    String moduleName = args[1];

    Path libraryManifestsFilePath = Paths.get(args[2]);
    ImmutableSet<Path> libraryManifestsFilePaths =
        Files.readAllLines(libraryManifestsFilePath).stream()
            .map(Paths::get)
            .collect(ImmutableSet.toImmutableSet());

    Path outputPath = Paths.get(args[3]);
    Path mergeReportPath = Paths.get(args[4]);

    String xmlText =
        GenerateManifest.generateXml(
            skeletonManifestPath,
            moduleName,
            libraryManifestsFilePaths,
            outputPath,
            mergeReportPath,
            new StdLogger(StdLogger.Level.ERROR));

    try (ThrowingPrintWriter writer =
        new ThrowingPrintWriter(new FileOutputStream(outputPath.toFile()))) {
      writer.printf(xmlText);
    }

    System.exit(0);
  }
}

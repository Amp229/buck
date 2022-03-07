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

package com.facebook.buck.android;

import com.android.sdklib.build.ApkCreationException;
import com.android.sdklib.build.DuplicateFileException;
import com.android.sdklib.build.SealedApkException;
import com.facebook.buck.android.apk.ApkBuilderUtils;
import com.facebook.buck.android.apk.KeystoreProperties;
import com.facebook.buck.core.build.execution.context.StepExecutionContext;
import com.facebook.buck.core.exceptions.HumanReadableException;
import com.facebook.buck.io.filesystem.ProjectFilesystem;
import com.facebook.buck.step.Step;
import com.facebook.buck.step.StepExecutionResult;
import com.facebook.buck.step.StepExecutionResults;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Multimap;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Path;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.util.Collection;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Merges resources into a final APK. This code is based off of the now deprecated apkbuilder tool:
 * https://android.googlesource.com/platform/sdk/+/fd30096196e3747986bdf8a95cc7713dd6e0b239%5E/sdkmanager/libs/sdklib/src/main/java/com/android/sdklib/build/ApkBuilderMain.java
 */
public class ApkBuilderStep implements Step {

  private final ProjectFilesystem filesystem;
  private final Path resourceApk;
  private final Path dexFile;
  private final Path pathToOutputApkFile;
  private final ImmutableSet<Path> assetDirectories;
  private final ImmutableSet<Path> nativeLibraryDirectories;
  private final ImmutableSet<Path> zipFiles;
  private final ImmutableSet<Path> jarFilesThatMayContainResources;
  private final Supplier<KeystoreProperties> keystorePropertiesSupplier;
  private final Path pathToKeystore;

  /**
   * @param resourceApk Path to the Apk which only contains resources, no dex files.
   * @param pathToOutputApkFile Path to output our APK to.
   * @param dexFile Path to the classes.dex file.
   * @param assetDirectories List of paths to assets to be included in the apk.
   * @param nativeLibraryDirectories List of paths to native directories.
   * @param zipFiles List of paths to zipfiles to be included into the apk.
   */
  public ApkBuilderStep(
      ProjectFilesystem filesystem,
      Path resourceApk,
      Path pathToOutputApkFile,
      Path dexFile,
      ImmutableSet<Path> assetDirectories,
      ImmutableSet<Path> nativeLibraryDirectories,
      ImmutableSet<Path> zipFiles,
      ImmutableSet<Path> jarFilesThatMayContainResources,
      Path pathToKeystore,
      Supplier<KeystoreProperties> keystorePropertiesSupplier) {
    this.filesystem = filesystem;
    this.resourceApk = resourceApk;
    this.pathToOutputApkFile = pathToOutputApkFile;
    this.dexFile = dexFile;
    this.assetDirectories = assetDirectories;
    this.nativeLibraryDirectories = nativeLibraryDirectories;
    this.jarFilesThatMayContainResources = jarFilesThatMayContainResources;
    this.zipFiles = zipFiles;
    this.keystorePropertiesSupplier = keystorePropertiesSupplier;
    this.pathToKeystore = pathToKeystore;
  }

  @Override
  public StepExecutionResult execute(StepExecutionContext context) throws IOException {
    PrintStream output = null;
    if (context.getVerbosity().shouldUseVerbosityFlagIfAvailable()) {
      output = context.getStdOut();
    }

    try {
      ApkBuilderUtils.buildApk(
          filesystem.getPathForRelativePath(resourceApk),
          filesystem.getPathForRelativePath(pathToOutputApkFile),
          filesystem.getPathForRelativePath(dexFile),
          assetDirectories.stream()
              .map(filesystem::getPathForRelativePath)
              .collect(ImmutableSet.toImmutableSet()),
          nativeLibraryDirectories.stream()
              .map(filesystem::getPathForRelativePath)
              .collect(ImmutableSet.toImmutableSet()),
          zipFiles.stream()
              .map(filesystem::getPathForRelativePath)
              .collect(ImmutableSet.toImmutableSet()),
          jarFilesThatMayContainResources.stream()
              .map(filesystem::getPathForRelativePath)
              .collect(ImmutableSet.toImmutableSet()),
          filesystem.getPathForRelativePath(pathToKeystore),
          keystorePropertiesSupplier.get(),
          output);
    } catch (ApkCreationException
        | KeyStoreException
        | NoSuchAlgorithmException
        | SealedApkException
        | UnrecoverableKeyException e) {
      context.logError(e, "Error when creating APK at: %s.", pathToOutputApkFile);
      return StepExecutionResults.ERROR;
    } catch (DuplicateFileException e) {
      throw new HumanReadableException(
          String.format(
              "Found duplicate file for APK: %1$s\nOrigin 1: %2$s\nOrigin 2: %3$s",
              e.getArchivePath(), e.getFile1(), e.getFile2()));
    }
    return StepExecutionResults.SUCCESS;
  }

  @Override
  public String getShortName() {
    return "apk_builder";
  }

  @Override
  public String getDescription(StepExecutionContext context) {
    ImmutableList.Builder<String> args = ImmutableList.builder();
    args.add(
        "java",
        "-classpath",
        // TODO(mbolin): Make the directory that corresponds to $ANDROID_HOME a field that is
        // accessible via an AndroidPlatformTarget and insert that here in place of "$ANDROID_HOME".
        "$ANDROID_HOME/tools/lib/sdklib.jar",
        "com.android.sdklib.build.ApkBuilderMain");
    args.add(String.valueOf(pathToOutputApkFile));
    args.add("-v" /* verbose */);

    // Unfortunately, ApkBuilderMain does not have CLI args to set the keystore,
    // so these member variables are left out of the command:
    // pathToKeystore, pathToKeystorePropertiesFile

    Multimap<String, Collection<Path>> groups =
        ImmutableMultimap.<String, Collection<Path>>builder()
            .put("-z", ImmutableList.of(resourceApk))
            .put("-f", ImmutableList.of(dexFile))
            .put("-rf", assetDirectories)
            .put("-nf", nativeLibraryDirectories)
            .put("-z", zipFiles)
            .put("-rj", jarFilesThatMayContainResources)
            .build();

    for (Map.Entry<String, Collection<Path>> group : groups.entries()) {
      String prefix = group.getKey();
      for (Path path : group.getValue()) {
        args.add(prefix, String.valueOf(path));
      }
    }

    return Joiner.on(' ').join(args.build());
  }
}

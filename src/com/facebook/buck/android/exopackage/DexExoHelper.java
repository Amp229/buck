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

package com.facebook.buck.android.exopackage;

import com.facebook.buck.io.filesystem.ProjectFilesystem;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

/** Installs secondary dexes for exo. */
public class DexExoHelper implements ExoHelper {
  @VisibleForTesting public static final Path SECONDARY_DEX_DIR = Paths.get("secondary-dex");

  private final ProjectFilesystem projectFilesystem;
  private final IsolatedExopackageInfo.IsolatedDexInfo dexInfo;

  DexExoHelper(
      ProjectFilesystem projectFilesystem, IsolatedExopackageInfo.IsolatedDexInfo dexInfo) {
    this.projectFilesystem = projectFilesystem;
    this.dexInfo = dexInfo;
  }

  @Override
  public String getType() {
    return "secondary_dex";
  }

  @Override
  public ImmutableMap<Path, Path> getFilesToInstall() throws IOException {
    return ExopackageUtil.applyFilenameFormat(
        getRequiredDexFiles(), SECONDARY_DEX_DIR, "secondary-%s.dex.jar");
  }

  @Override
  public ImmutableMap<Path, String> getMetadataToInstall() throws IOException {
    return ImmutableMap.of(
        SECONDARY_DEX_DIR.resolve("metadata.txt"), getSecondaryDexMetadataContents());
  }

  private String getSecondaryDexMetadataContents() throws IOException {
    // This is a bit gross.  It was a late addition.  Ideally, we could eliminate this, but
    // it wouldn't be terrible if we don't.  We store the dexed jars on the device
    // with the full SHA-1 hashes in their names.  This is the format that the loader uses
    // internally, so ideally we would just load them in place.  However, the code currently
    // expects to be able to copy the jars from a directory that matches the name in the
    // metadata file, like "secondary-1.dex.jar".  We don't want to give up putting the
    // hashes in the file names (because we use that to skip re-uploads), so just hack
    // the metadata file to have hash-like names.
    return com.google.common.io.Files.toString(
            dexInfo.getMetadata().toFile(), StandardCharsets.UTF_8)
        .replaceAll(
            "secondary-([\\d_]+)\\.dex\\.jar (\\p{XDigit}{40}) ", "secondary-$2.dex.jar $2 ");
  }

  private ImmutableMap<String, Path> getRequiredDexFiles() throws IOException {
    ImmutableMultimap<String, Path> multimap =
        ExopackageInstaller.parseExopackageInfoMetadata(
            dexInfo.getMetadata().getPath(), dexInfo.getDirectory().getPath(), projectFilesystem);
    // Convert multimap to a map, because every key should have only one value.
    ImmutableMap.Builder<String, Path> builder = ImmutableMap.builder();
    for (Map.Entry<String, Path> entry : multimap.entries()) {
      builder.put(entry);
    }
    return builder.build();
  }
}

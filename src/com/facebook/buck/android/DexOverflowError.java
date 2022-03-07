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

import com.android.dexdeps.DexData;
import com.facebook.buck.core.filesystems.AbsPath;
import com.facebook.buck.core.util.log.Logger;
import com.facebook.buck.io.filesystem.ProjectFilesystem;
import com.facebook.buck.step.StepFailedException;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Ordering;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * In the case of dex overflow while merging pre-dexed libraries, count and display method refs or
 * field refs for each library.
 */
public class DexOverflowError {
  private static final Logger log = Logger.get(DexOverflowError.class);

  private static final String PRIMARY_DEX_OVERFLOW_MESSAGE =
      "Primary dex size exceeds 64k %s ref limit\n"
          + "Use primary dex patterns and/or allow_r_dot_java_in_secondary_dex to exclude classes from the primary dex.";

  private static final String SECONDARY_DEX_OVERFLOW_MESSAGE =
      "Secondary dex size exceeds 64k %s ref limit.";

  private static final String SECONDARY_DEX_OVERFLOW_PREDEX_REMEDIATION =
      "secondary_dex_weight_limit determines the maximum size in bytes of secondary dexes.\n"
          + "Reduce secondary_dex_weight_limit until all secondary dexes are small enough.";

  /** Type of ref overflow for a failed dex step */
  public enum OverflowType {
    METHOD,
    FIELD
  }

  private final ProjectFilesystem filesystem;
  private final OverflowType type;
  private final D8Step d8Step;

  public DexOverflowError(ProjectFilesystem filesystem, OverflowType type, D8Step d8Step) {
    this.filesystem = filesystem;
    this.type = type;
    this.d8Step = d8Step;
  }

  /**
   * Determine the dex overflow type for a failed dexing step, if the step failed due to dex limits
   */
  public static Optional<OverflowType> checkOverflow(StepFailedException exception) {
    if (exception.getStep() instanceof D8Step) {
      int exitCode = exception.getExitCode().orElse(D8Step.FAILURE_EXIT_CODE);
      if (exitCode == D8Step.DEX_FIELD_REFERENCE_OVERFLOW_EXIT_CODE) {
        return Optional.of(OverflowType.FIELD);
      } else if (exitCode == D8Step.DEX_METHOD_REFERENCE_OVERFLOW_EXIT_CODE) {
        return Optional.of(OverflowType.METHOD);
      }
    }
    return Optional.empty();
  }

  /** Return a detailed error message to show the user */
  public String getErrorMessage() {
    ImmutableMap<String, Integer> dexInputDetails = null;
    try {
      ImmutableMap.Builder<String, Integer> dexInputsBuilder = ImmutableMap.builder();
      for (Path dexFile : d8Step.getFilesToDex()) {
        String dexFileName = dexFile.toString();
        if (dexFileName.endsWith(".jar") || dexFileName.endsWith(".dex")) {
          dexInputsBuilder.put(dexFileName, countRefs(type, filesystem.resolve(dexFileName)));
        }
      }
      dexInputsBuilder.orderEntriesByValue(Ordering.natural().reversed());
      dexInputDetails = dexInputsBuilder.build();
    } catch (Exception e) {
      log.debug(e, "Exception counting dex references");
    }

    StringBuilder builder = new StringBuilder();
    boolean primaryDexOverflow =
        d8Step.getOutputDexFile().endsWith("classes.dex")
            || d8Step.getOutputDexFile().toString().contains("primaryDex");
    String overflowName = type.name().toLowerCase();
    if (primaryDexOverflow) {
      builder.append(String.format(PRIMARY_DEX_OVERFLOW_MESSAGE, overflowName));
    } else {
      builder.append(String.format(SECONDARY_DEX_OVERFLOW_MESSAGE, overflowName));
      if (dexInputDetails != null && !dexInputDetails.isEmpty()) {
        builder.append(SECONDARY_DEX_OVERFLOW_PREDEX_REMEDIATION);
      }
    }
    builder.append(String.format("\nOutput dex file: %s\n", d8Step.getOutputDexFile()));
    if (dexInputDetails != null && !dexInputDetails.isEmpty()) {
      builder.append(
          String.format(
              "The largest libraries in the dex, by number of %ss:\n%-10sdex file path\n",
              overflowName, overflowName + "s"));

      List<String> dexRows =
          dexInputDetails.entrySet().stream()
              .map(entry -> String.format("%-10s%s", entry.getValue(), entry.getKey()))
              .collect(Collectors.toList());

      builder.append(dexRows.stream().limit(20).collect(Collectors.joining("\n")));

      if (dexRows.size() > 20) {
        builder.append("\n... See buck log for full list");
        log.debug(String.join("\n", dexRows));
      }
    }
    return builder.toString();
  }

  @SuppressWarnings("unchecked")
  private static int countRefs(OverflowType type, AbsPath dexFile)
      throws IOException, NoSuchMethodException, IllegalAccessException, InvocationTargetException {
    Method m = info.persistent.dex.Main.class.getDeclaredMethod("openInputFiles", String.class);
    m.setAccessible(true);

    List<RandomAccessFile> dexFiles =
        (List<RandomAccessFile>) m.invoke(new info.persistent.dex.Main(), dexFile.toString());

    int count = 0;
    if (dexFiles != null) {
      for (RandomAccessFile file : dexFiles) {
        DexData dexData = new DexData(file);
        dexData.load();
        switch (type) {
          case FIELD:
            count += dexData.getFieldRefs().length;
            break;
          case METHOD:
            count += dexData.getMethodRefs().length;
            break;
        }
      }
    }
    return count;
  }
}

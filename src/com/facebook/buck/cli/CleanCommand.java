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

package com.facebook.buck.cli;

import com.facebook.buck.artifact_cache.config.ArtifactCacheBuckConfig;
import com.facebook.buck.artifact_cache.config.DirCacheEntry;
import com.facebook.buck.core.cell.Cell;
import com.facebook.buck.core.util.log.Logger;
import com.facebook.buck.event.listener.JavaUtilsLoggingBuildListener;
import com.facebook.buck.io.filesystem.BuckPaths;
import com.facebook.buck.io.filesystem.ProjectFilesystem;
import com.facebook.buck.util.ExitCode;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;
import org.kohsuke.args4j.Option;

public class CleanCommand extends AbstractCommand {

  private static final Logger LOG = Logger.get(CleanCommand.class);

  private static final String KEEP_CACHE_ARG = "--keep-cache";
  private static final String DRY_RUN_ARG = "--dry-run";

  @Option(name = KEEP_CACHE_ARG, usage = "Keeps the local cache.")
  private boolean keepCache = false;

  @Option(
      name = DRY_RUN_ARG,
      usage = "Performs a dry-run and prints the paths that would be removed.")
  private boolean dryRun = false;

  private void cleanCell(CommandRunnerParams params, Cell cell) {
    // Ideally, we would like the implementation of this method to be as simple as:
    //
    // getProjectFilesystem().deleteRecursivelyIfExists(BuckConstant.BUCK_OUTPUT_DIRECTORY);
    //
    // However, we want to avoid blowing away directories that IntelliJ indexes, because that tends
    // to make it angry. Currently, those directories are:
    //
    // Project.ANDROID_GEN_DIR
    // BuckConstant.ANNOTATION_DIR
    //
    // However, Buck itself also uses BuckConstant.ANNOTATION_DIR. We need to fix things so that
    // IntelliJ does its default thing to generate code from annotations, and manages/indexes those
    // directories itself so we can blow away BuckConstant.ANNOTATION_DIR as part of `buck clean`.
    // This will also reduce how long `buck project` takes.
    //

    ProjectFilesystem projectFilesystem = cell.getFilesystem();

    // On Windows, you have to close all files that will be deleted.
    // Because buck clean will delete build.log, you must close it first.
    JavaUtilsLoggingBuildListener.closeLogFile();

    Set<Path> pathsToDelete = new HashSet<>();
    BuckPaths buckPaths = projectFilesystem.getBuckPaths();
    pathsToDelete.add(buckPaths.getScratchDir().getPath());
    pathsToDelete.add(buckPaths.getGenDir().getPath());
    pathsToDelete.add(buckPaths.getTrashDir());
    pathsToDelete.add(buckPaths.getJournalDir());
    pathsToDelete.add(buckPaths.getTraceDir());
    pathsToDelete.add(buckPaths.getAnnotationDir().getPath());
    pathsToDelete.add(buckPaths.getEmbeddedCellsBuckOutBaseDir());
    processTmpDir(buckPaths.getTmpDir(), pathsToDelete);

    CleanCommandBuckConfig buckConfig = cell.getBuckConfig().getView(CleanCommandBuckConfig.class);

    // Remove dir cache.
    if (!keepCache) {
      ImmutableList<String> excludedCaches = buckConfig.getCleanExcludedCaches();
      pathsToDelete.add(buckPaths.getCacheDir());
      for (DirCacheEntry dirCacheEntry :
          ArtifactCacheBuckConfig.of(cell.getBuckConfig()).getCacheEntries().getDirCacheEntries()) {
        if (dirCacheEntry.getName().isPresent()
            && !excludedCaches.contains(dirCacheEntry.getName().get())) {
          pathsToDelete.add(dirCacheEntry.getCacheDir());
        }
      }
    }

    // Clean out any additional directories specified via config setting.
    for (String subPath : buckConfig.getCleanAdditionalPaths()) {
      pathsToDelete.add(projectFilesystem.getPath(subPath));
    }

    if (dryRun) {
      params
          .getConsole()
          .getStdOut()
          .printf(
              "The following directories and files would be removed for cell %s %n",
              cell.getCanonicalName().getName());
      for (Path path : pathsToDelete) {
        if (projectFilesystem.exists(path)) {
          params.getConsole().getStdOut().println(path.toAbsolutePath());
        }
      }
    } else {
      // Remove all the paths.
      for (Path path : pathsToDelete) {
        try {
          projectFilesystem.deleteRecursivelyIfExists(path);
          LOG.debug("Removed path: %s", path);
        } catch (AccessDeniedException e) {
          params
              .getConsole()
              .printErrorText(
                  "Failed to remove path %s due to AccessDeniedException.%n"
                      + "Make sure that you (not root) own all the contents inside buck-out",
                  path);
          LOG.warn(e, "Failed to remove path %s due to permissions issue", path);
        } catch (IOException e) {
          LOG.warn(e, "Failed to remove path %s", path);
        }
      }
    }
  }

  /**
   * Add every file under tmp dir except directories that belongs to the current buck process
   * (current tmp, resources, buck_run, ...).
   */
  private void processTmpDir(Path tmpDir, Set<Path> pathsToDelete) {
    Path javaTmpDirectory = Paths.get(System.getProperty("java.io.tmpdir"));
    String resourceLockDirectory = System.getProperty("buck.resource_lock_path");
    boolean hasResourceDirectory = resourceLockDirectory != null;
    Path currentResourceDirectory =
        hasResourceDirectory ? Paths.get(resourceLockDirectory).getParent() : null;

    Path tmpAbsPath = tmpDir.toAbsolutePath();
    String currentTmpDir = tmpAbsPath.relativize(javaTmpDirectory).toString();
    String resourcesDir =
        hasResourceDirectory
            ? tmpAbsPath.relativize(currentResourceDirectory.getParent()).toString()
            : null;
    String currentResourcesDir =
        hasResourceDirectory
            ? tmpAbsPath.relativize(currentResourceDirectory).getFileName().toString()
            : null;

    String[] tmpFiles = tmpDir.toFile().list();
    if (tmpFiles != null) {
      for (String tmpFile : tmpFiles) {
        if (tmpFile.equals(currentTmpDir) || tmpFile.startsWith("buck_run.")) {
          continue;
        }
        Path path = tmpDir.resolve(tmpFile);
        if (hasResourceDirectory && tmpFile.equals(resourcesDir)) {
          String[] resourcesSubDirs = path.toFile().list();
          if (resourcesSubDirs != null) {
            for (String resourceDir : resourcesSubDirs) {
              if (resourceDir.equals(currentResourcesDir)) {
                continue;
              }
              pathsToDelete.add(path.resolve(resourceDir));
            }
          }
        } else {
          pathsToDelete.add(path);
        }
      }
    }
  }

  @Override
  public ExitCode runWithoutHelp(CommandRunnerParams params) {
    for (Cell cell : params.getCells().getAllCells()) {
      cleanCell(params, cell);
    }
    return ExitCode.SUCCESS;
  }

  @Override
  public boolean isReadOnly() {
    return false;
  }

  @Override
  public String getShortDescription() {
    return "deletes any generated files and caches";
  }
}

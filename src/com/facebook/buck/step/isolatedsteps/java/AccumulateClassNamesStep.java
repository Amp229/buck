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

package com.facebook.buck.step.isolatedsteps.java;

import com.facebook.buck.core.build.execution.context.IsolatedExecutionContext;
import com.facebook.buck.core.build.execution.context.StepExecutionContext;
import com.facebook.buck.core.filesystems.AbsPath;
import com.facebook.buck.core.filesystems.RelPath;
import com.facebook.buck.io.file.PathMatcher;
import com.facebook.buck.io.filesystem.impl.ProjectFilesystemUtils;
import com.facebook.buck.jvm.java.classes.ClasspathTraversal;
import com.facebook.buck.jvm.java.classes.DefaultClasspathTraverser;
import com.facebook.buck.jvm.java.classes.FileLike;
import com.facebook.buck.jvm.java.classes.FileLikes;
import com.facebook.buck.step.Step;
import com.facebook.buck.step.StepExecutionResult;
import com.facebook.buck.step.StepExecutionResults;
import com.facebook.buck.step.isolatedsteps.IsolatedStep;
import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Ordering;
import com.google.common.hash.HashCode;
import com.google.common.hash.Hashing;
import com.google.common.io.ByteSource;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * {@link Step} that takes a directory or zip of {@code .class} files and traverses it to get the
 * total set of {@code .class} files included by the directory or zip.
 */
public class AccumulateClassNamesStep extends IsolatedStep {

  /**
   * In the generated {@code classes.txt} file, each line will contain the path to a {@code .class}
   * file (without its suffix) and the SHA-1 hash of its contents, separated by this separator.
   */
  public static final String CLASS_NAME_HASH_CODE_SEPARATOR = " ";

  // RelPath based patterns
  private final ImmutableSet<PathMatcher> ignoredPaths;
  private final Optional<RelPath> pathToJarOrClassesDirectory;
  private final RelPath whereClassNamesShouldBeWritten;

  /**
   * @param pathToJarOrClassesDirectory Where to look for .class files. If absent, then an empty
   *     file will be written to {@code whereClassNamesShouldBeWritten}.
   * @param whereClassNamesShouldBeWritten Path to a file where an alphabetically sorted list of
   *     class files and corresponding SHA-1 hashes of their contents will be written.
   */
  public AccumulateClassNamesStep(
      ImmutableSet<PathMatcher> ignoredPaths,
      Optional<RelPath> pathToJarOrClassesDirectory,
      RelPath whereClassNamesShouldBeWritten) {
    this.ignoredPaths = ignoredPaths;
    this.pathToJarOrClassesDirectory = pathToJarOrClassesDirectory;
    this.whereClassNamesShouldBeWritten = whereClassNamesShouldBeWritten;
  }

  @Override
  public StepExecutionResult executeIsolatedStep(IsolatedExecutionContext context)
      throws IOException {
    ImmutableSortedMap<String, HashCode> classNames;
    if (pathToJarOrClassesDirectory.isPresent()) {
      Optional<ImmutableSortedMap<String, HashCode>> classNamesOptional =
          calculateClassHashes(
              context,
              context.getRuleCellRoot(),
              ignoredPaths,
              ImmutableSet.of(),
              pathToJarOrClassesDirectory.get());
      if (classNamesOptional.isPresent()) {
        classNames = classNamesOptional.get();
      } else {
        return StepExecutionResults.ERROR;
      }
    } else {
      classNames = ImmutableSortedMap.of();
    }

    ProjectFilesystemUtils.writeLinesToPath(
        context.getRuleCellRoot(),
        Iterables.transform(
            classNames.entrySet(),
            entry -> entry.getKey() + CLASS_NAME_HASH_CODE_SEPARATOR + entry.getValue()),
        whereClassNamesShouldBeWritten.getPath());

    return StepExecutionResults.SUCCESS;
  }

  @Override
  public String getShortName() {
    return "get_class_names";
  }

  @Override
  public String getIsolatedStepDescription(IsolatedExecutionContext context) {
    String sourceString = pathToJarOrClassesDirectory.map(Object::toString).orElse("null");
    return String.format("get_class_names %s > %s", sourceString, whereClassNamesShouldBeWritten);
  }

  /** @return an Optional that will be absent if there was an error. */
  public static Optional<ImmutableSortedMap<String, HashCode>> calculateClassHashes(
      IsolatedExecutionContext context,
      AbsPath rootPath,
      ImmutableSet<PathMatcher> ignoredPaths,
      ImmutableSet<String> ignoredClassNames,
      RelPath path) {
    Map<String, HashCode> classNames = new HashMap<>();

    ClasspathTraversal traversal =
        new ClasspathTraversal(Collections.singleton(path.getPath()), rootPath, ignoredPaths) {
          @Override
          public void visit(FileLike fileLike) throws IOException {
            // When traversing a JAR file, it may have resources or directory entries that do not
            // end in .class, which should be ignored.
            if (!FileLikes.isClassFile(fileLike)) {
              return;
            }

            String key = FileLikes.getFileNameWithoutClassSuffix(fileLike);
            if (ignoredClassNames.contains(key)) {
              return;
            }

            ByteSource input =
                new ByteSource() {
                  @Override
                  public InputStream openStream() throws IOException {
                    return fileLike.getInput();
                  }
                };
            HashCode value = input.hash(Hashing.sha1());
            HashCode existing = classNames.putIfAbsent(key, value);
            if (existing != null && !existing.equals(value)) {
              throw new IllegalArgumentException(
                  String.format(
                      "Multiple entries with same key but differing values: %1$s=%2$s and %1$s=%3$s",
                      key, value, existing));
            }
          }
        };

    try {
      new DefaultClasspathTraverser().traverse(traversal);
    } catch (IOException e) {
      context.logError(e, "Error accumulating class names for %s.", path);
      return Optional.empty();
    }

    return Optional.of(ImmutableSortedMap.copyOf(classNames, Ordering.natural()));
  }

  /**
   * @param lines that were written in the same format output by {@link
   *     #execute(StepExecutionContext)}.
   */
  public static ImmutableSortedMap<String, HashCode> parseClassHashes(List<String> lines) {
    Map<String, HashCode> classNames = new HashMap<>();

    for (String line : lines) {
      int lastSeparator = line.lastIndexOf(CLASS_NAME_HASH_CODE_SEPARATOR);
      String key = line.substring(0, lastSeparator);
      HashCode value = HashCode.fromString(line.substring(lastSeparator + 1));
      HashCode existing = classNames.putIfAbsent(key, value);
      if (existing != null && !existing.equals(value)) {
        throw new IllegalArgumentException(
            String.format(
                "Multiple entries with same key but differing values: %1$s=%2$s and %1$s=%3$s",
                key, value, existing));
      }
    }

    return ImmutableSortedMap.copyOf(classNames, Ordering.natural());
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("pathToJarOrClassesDirectory", pathToJarOrClassesDirectory)
        .add("whereClassNamesShouldBeWritten", whereClassNamesShouldBeWritten)
        .toString();
  }
}

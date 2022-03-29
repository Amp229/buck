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

package com.facebook.buck.rules.coercer;

import com.facebook.buck.apple.xcode.xcodeproj.PBXReference;
import com.facebook.buck.apple.xcode.xcodeproj.SourceTreePath;
import com.facebook.buck.core.rulekey.AddToRuleKey;
import com.facebook.buck.core.rulekey.AddsToRuleKey;
import com.facebook.buck.core.rules.BuildRule;
import com.facebook.buck.core.rules.SourcePathRuleFinder;
import com.facebook.buck.core.sourcepath.SourcePath;
import com.facebook.buck.core.util.immutables.BuckStyleValue;
import com.google.common.base.Preconditions;
import com.google.common.io.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Iterator;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import org.immutables.value.Value;

/**
 * Frameworks can be specified as either a path to a file, or a path prefixed by a build setting.
 */
@BuckStyleValue
public abstract class FrameworkPath implements Comparable<FrameworkPath>, AddsToRuleKey {

  /**
   * A list of implicitly included framework search paths relative to SDKROOT. Those paths do not
   * require passing -F parameters when compiling and linking. All other SDKROOT framework paths
   * require -F params.
   */
  private static final String[] IMPLICIT_SDKROOT_FRAMEWORK_SEARCH_PATHS =
      new String[] {
        "Library/Frameworks", "System/Library/Frameworks",
      };

  /** The type of framework entry this object represents. */
  protected enum Type {
    /** An Xcode style {@link SourceTreePath}. */
    SOURCE_TREE_PATH,
    /** A Buck style {@link SourcePath}. */
    SOURCE_PATH,
  }

  @AddToRuleKey
  public abstract Type getType();

  @AddToRuleKey
  public abstract Optional<SourceTreePath> getSourceTreePath();

  @AddToRuleKey
  public abstract Optional<SourcePath> getSourcePath();

  public Iterator<BuildRule> getDeps(SourcePathRuleFinder ruleFinder) {
    return ruleFinder.filterBuildRuleInputs(getSourcePath()).iterator();
  }

  public Path getFileName(Function<SourcePath, Path> resolver) {
    switch (getType()) {
      case SOURCE_TREE_PATH:
        return getSourceTreePath().get().getPath().getFileName();
      case SOURCE_PATH:
        return Objects.requireNonNull(resolver.apply(getSourcePath().get())).getFileName();
      default:
        throw new RuntimeException("Unhandled type: " + getType());
    }
  }

  public String getName(Function<SourcePath, Path> resolver) {
    String fileName = getFileName(resolver).toString();
    return Files.getNameWithoutExtension(fileName);
  }

  public boolean isSDKROOTFrameworkPath() {
    if (getSourceTreePath().isPresent()) {
      return getSourceTreePath().get().getSourceTree() == PBXReference.SourceTree.SDKROOT;
    }
    return false;
  }

  /**
   * Determines whether a framework path is implicitly included as part of the compiler and linker.
   * If a framework is implicitly included, there's no neeed
   */
  public boolean isImplicitlyIncludedInSearchPaths() {
    return getSourceTreePath()
        .map(
            sourceTreePath -> {
              if (sourceTreePath.getSourceTree() == PBXReference.SourceTree.SDKROOT) {
                Path path = sourceTreePath.getPath();
                for (String implicitPath : IMPLICIT_SDKROOT_FRAMEWORK_SEARCH_PATHS) {
                  if (path.startsWith(implicitPath)) {
                    return true;
                  }
                }
              }

              return false;
            })
        .orElse(false);
  }

  /** Returns true if the framework is in the PLATFORM_DIR */
  public boolean isPlatformDirFrameworkPath() {
    if (getSourceTreePath().isPresent()) {
      return getSourceTreePath().get().getSourceTree() == PBXReference.SourceTree.PLATFORM_DIR;
    }
    return false;
  }

  public static Path getUnexpandedSearchPath(
      Function<SourcePath, Path> resolver,
      Function<? super Path, Path> relativizer,
      FrameworkPath input) {
    return convertToPath(resolver, relativizer, input).getParent();
  }

  public static FrameworkPath of(
      FrameworkPath.Type type,
      Optional<? extends SourceTreePath> sourceTreePath,
      Optional<? extends SourcePath> sourcePath) {
    return ImmutableFrameworkPath.ofImpl(type, sourceTreePath, sourcePath);
  }

  private static Path convertToPath(
      Function<SourcePath, Path> resolver,
      Function<? super Path, Path> relativizer,
      FrameworkPath input) {
    switch (input.getType()) {
      case SOURCE_TREE_PATH:
        return Paths.get(input.getSourceTreePath().get().toString());
      case SOURCE_PATH:
        return relativizer.apply(resolver.apply(input.getSourcePath().get()));
      default:
        throw new RuntimeException("Unhandled type: " + input.getType());
    }
  }

  @Value.Check
  protected void check() {
    switch (getType()) {
      case SOURCE_TREE_PATH:
        Preconditions.checkArgument(getSourceTreePath().isPresent());
        Preconditions.checkArgument(!getSourcePath().isPresent());
        break;
      case SOURCE_PATH:
        Preconditions.checkArgument(!getSourceTreePath().isPresent());
        Preconditions.checkArgument(getSourcePath().isPresent());
        break;
      default:
        throw new RuntimeException("Unhandled type: " + getType());
    }
  }

  @Override
  public int compareTo(FrameworkPath o) {
    if (this == o) {
      return 0;
    }

    int typeComparisonResult = getType().ordinal() - o.getType().ordinal();
    if (typeComparisonResult != 0) {
      return typeComparisonResult;
    }
    switch (getType()) {
      case SOURCE_TREE_PATH:
        return getSourceTreePath().get().compareTo(o.getSourceTreePath().get());
      case SOURCE_PATH:
        return getSourcePath().get().compareTo(o.getSourcePath().get());
      default:
        throw new RuntimeException("Unhandled type: " + getType());
    }
  }

  public static FrameworkPath ofSourceTreePath(SourceTreePath sourceTreePath) {
    return FrameworkPath.of(Type.SOURCE_TREE_PATH, Optional.of(sourceTreePath), Optional.empty());
  }

  public static FrameworkPath ofSourcePath(SourcePath sourcePath) {
    return FrameworkPath.of(Type.SOURCE_PATH, Optional.empty(), Optional.of(sourcePath));
  }
}

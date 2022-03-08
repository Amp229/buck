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

package com.facebook.buck.jvm.java;

import com.facebook.buck.core.filesystems.RelPath;
import com.facebook.buck.core.model.BuildTarget;
import com.facebook.buck.core.model.HasOutputName;
import com.facebook.buck.core.model.OutputLabel;
import com.facebook.buck.core.model.impl.BuildTargetPaths;
import com.facebook.buck.core.rulekey.AddToRuleKey;
import com.facebook.buck.core.rulekey.AddsToRuleKey;
import com.facebook.buck.core.rules.BuildRule;
import com.facebook.buck.core.rules.SourcePathRuleFinder;
import com.facebook.buck.core.sourcepath.BuildTargetSourcePath;
import com.facebook.buck.core.sourcepath.SourcePath;
import com.facebook.buck.core.util.immutables.BuckStyleValue;
import com.facebook.buck.io.file.MorePaths;
import com.facebook.buck.io.filesystem.ProjectFilesystem;
import com.facebook.buck.io.pathformat.PathFormatter;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableSortedMap;
import java.nio.file.Path;
import java.util.Optional;
import org.immutables.value.Value;

@BuckStyleValue
public abstract class ResourcesParameters implements AddsToRuleKey {
  @Value.Default
  @AddToRuleKey
  public ImmutableSortedMap<String, SourcePath> getResources() {
    return ImmutableSortedMap.of();
  }

  @AddToRuleKey
  public abstract Optional<String> getResourcesRoot();

  public static ResourcesParameters of() {
    return of(ImmutableSortedMap.of(), Optional.empty());
  }

  public static ResourcesParameters of(
      ImmutableSortedMap<String, SourcePath> resources, Optional<String> resourcesRoot) {
    return ImmutableResourcesParameters.ofImpl(resources, resourcesRoot);
  }

  public static ResourcesParameters create(
      ProjectFilesystem projectFilesystem,
      SourcePathRuleFinder ruleFinder,
      ImmutableCollection<SourcePath> resources,
      Optional<Path> resourcesRoot) {
    return ResourcesParameters.of(
        getNamedResources(ruleFinder, projectFilesystem, resources),
        resourcesRoot.map(Path::toString));
  }

  public static ImmutableSortedMap<String, SourcePath> getNamedResources(
      SourcePathRuleFinder ruleFinder,
      ProjectFilesystem filesystem,
      ImmutableCollection<SourcePath> resources) {
    ImmutableSortedMap.Builder<String, SourcePath> builder = ImmutableSortedMap.naturalOrder();
    for (SourcePath rawResource : resources) {
      // If the path to the file defining this rule were:
      // "first-party/orca/lib-http/tests/com/facebook/orca/BUCK"
      //
      // And the value of resource were:
      // "first-party/orca/lib-http/tests/com/facebook/orca/protocol/base/batch_exception1.txt"
      //
      // Assuming that `src_roots = tests` were in the [java] section of the .buckconfig file,
      // then javaPackageAsPath would be:
      // "com/facebook/orca/protocol/base/"
      //
      // And the path that we would want to copy to the classes directory would be:
      // "com/facebook/orca/protocol/base/batch_exception1.txt"
      //
      // Therefore, some path-wrangling is required to produce the correct string.

      Optional<BuildRule> underlyingRule = ruleFinder.getRule(rawResource);
      RelPath relativePathToResource =
          ruleFinder.getSourcePathResolver().getCellUnsafeRelPath(rawResource);

      String resource;

      if (underlyingRule.isPresent()) {
        BuildTarget underlyingTarget = underlyingRule.get().getBuildTarget();
        if (underlyingRule.get() instanceof HasOutputName) {
          resource =
              PathFormatter.pathWithUnixSeparators(
                  underlyingTarget
                      .getCellRelativeBasePath()
                      .getPath()
                      .toPath(filesystem.getFileSystem())
                      .resolve(
                          ((HasOutputName) underlyingRule.get())
                              .getOutputName(getOutputLabel(rawResource))));
        } else {
          RelPath genOutputParent =
              BuildTargetPaths.getGenPath(filesystem.getBuckPaths(), underlyingTarget, "%s")
                  .getParent();
          RelPath scratchOutputParent =
              BuildTargetPaths.getScratchPath(filesystem, underlyingTarget, "%s").getParent();
          Optional<RelPath> outputPath =
              MorePaths.stripPrefix(relativePathToResource, genOutputParent)
                  .or(() -> MorePaths.stripPrefix(relativePathToResource, scratchOutputParent));
          Preconditions.checkState(
              outputPath.isPresent(),
              "%s is used as a resource but does not output to a default output directory",
              underlyingTarget.getFullyQualifiedName());
          resource =
              PathFormatter.pathWithUnixSeparators(
                  underlyingTarget
                      .getCellRelativeBasePath()
                      .getPath()
                      .toPath(filesystem.getFileSystem())
                      .resolve(outputPath.get().getPath()));
        }
      } else {
        resource = PathFormatter.pathWithUnixSeparators(relativePathToResource);
      }
      builder.put(resource, rawResource);
    }
    return builder.build();
  }

  private static OutputLabel getOutputLabel(SourcePath sourcePath) {
    return sourcePath instanceof BuildTargetSourcePath
        ? ((BuildTargetSourcePath) sourcePath).getTargetWithOutputs().getOutputLabel()
        : OutputLabel.defaultLabel();
  }
}

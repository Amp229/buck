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

package com.facebook.buck.features.filegroup;

import com.facebook.buck.core.build.context.BuildContext;
import com.facebook.buck.core.filesystems.AbsPath;
import com.facebook.buck.core.filesystems.RelPath;
import com.facebook.buck.core.model.BuildTarget;
import com.facebook.buck.core.model.HasOutputName;
import com.facebook.buck.core.model.OutputLabel;
import com.facebook.buck.core.rulekey.AddToRuleKey;
import com.facebook.buck.core.rulekey.CustomFieldBehavior;
import com.facebook.buck.core.rules.SourcePathRuleFinder;
import com.facebook.buck.core.sourcepath.SourcePath;
import com.facebook.buck.core.sourcepath.resolver.SourcePathResolverAdapter;
import com.facebook.buck.features.filebundler.CopyingFileBundler;
import com.facebook.buck.features.filebundler.FileBundler;
import com.facebook.buck.io.filesystem.ProjectFilesystem;
import com.facebook.buck.rules.coercer.SourceSet;
import com.facebook.buck.rules.modern.BuildCellRelativePathFactory;
import com.facebook.buck.rules.modern.Buildable;
import com.facebook.buck.rules.modern.ModernBuildRule;
import com.facebook.buck.rules.modern.OutputPath;
import com.facebook.buck.rules.modern.OutputPathResolver;
import com.facebook.buck.rules.modern.RemoteExecutionEnabled;
import com.facebook.buck.step.Step;
import com.facebook.buck.util.PatternsMatcher;
import com.facebook.buck.util.stream.RichStream;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Ordering;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

/** A build rule that copies inputs provided in {@code srcs} to an output directory. */
public class Filegroup extends ModernBuildRule<Filegroup> implements HasOutputName, Buildable {

  @AddToRuleKey private final String name;
  @AddToRuleKey private final Optional<SourceSet> srcs;
  @AddToRuleKey private final OutputPath outputPath;

  @CustomFieldBehavior(RemoteExecutionEnabled.class)
  private final boolean enabled = false;

  public Filegroup(
      BuildTarget buildTarget,
      ProjectFilesystem projectFilesystem,
      SourcePathRuleFinder ruleFinder,
      String name,
      Optional<SourceSet> srcs) {
    super(buildTarget, projectFilesystem, ruleFinder, Filegroup.class);
    this.name = name;
    this.srcs = srcs;

    outputPath = new OutputPath(name);
  }

  @Override
  public ImmutableList<Step> getBuildSteps(
      BuildContext buildContext,
      ProjectFilesystem filesystem,
      OutputPathResolver outputPathResolver,
      BuildCellRelativePathFactory buildCellPathFactory) {
    RelPath outputPath = outputPathResolver.resolvePath(this.outputPath);

    FileBundler bundler = new CopyingFileBundler(filesystem, getBuildTarget());

    ImmutableList.Builder<Step> steps = ImmutableList.builder();

    if (srcs.isPresent()) {
      var actualSrcs = srcs.get();
      actualSrcs.match(
          new SourceSet.Matcher<>() {
            @Override
            public Object named(ImmutableMap<String, SourcePath> named) {

              ImmutableMap.Builder<Path, AbsPath> builder = ImmutableMap.builder();
              SourcePathResolverAdapter sourcePathResolver = buildContext.getSourcePathResolver();

              for (Map.Entry<String, SourcePath> pathEntry : named.entrySet()) {
                builder.put(
                    Map.entry(
                        Path.of(pathEntry.getKey()),
                        sourcePathResolver.getAbsolutePath(pathEntry.getValue())));
              }

              bundler.copy(
                  filesystem,
                  buildCellPathFactory,
                  steps,
                  outputPath.getPath(),
                  builder.build(),
                  PatternsMatcher.NONE);
              return null;
            }

            @Override
            public Object unnamed(ImmutableSet<SourcePath> unnamed) {
              bundler.copy(
                  filesystem,
                  buildCellPathFactory,
                  steps,
                  outputPath.getPath(),
                  RichStream.from(unnamed).toImmutableSortedSet(Ordering.natural()),
                  buildContext.getSourcePathResolver());
              return null;
            }
          });
    }

    return steps.build();
  }

  @Override
  public SourcePath getSourcePathToOutput() {
    return getSourcePath(outputPath);
  }

  @Override
  public String getOutputName(OutputLabel outputLabel) {
    return name;
  }
}

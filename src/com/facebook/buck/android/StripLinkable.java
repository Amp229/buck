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

import com.facebook.buck.android.toolchain.ndk.NdkCxxPlatform;
import com.facebook.buck.core.build.context.BuildContext;
import com.facebook.buck.core.filesystems.RelPath;
import com.facebook.buck.core.model.BuildTarget;
import com.facebook.buck.core.rulekey.AddToRuleKey;
import com.facebook.buck.core.rules.SourcePathRuleFinder;
import com.facebook.buck.core.sourcepath.SourcePath;
import com.facebook.buck.core.sourcepath.resolver.SourcePathResolverAdapter;
import com.facebook.buck.core.toolchain.tool.Tool;
import com.facebook.buck.io.filesystem.ProjectFilesystem;
import com.facebook.buck.io.filesystem.impl.ProjectFilesystemUtils;
import com.facebook.buck.rules.args.Arg;
import com.facebook.buck.rules.modern.BuildCellRelativePathFactory;
import com.facebook.buck.rules.modern.Buildable;
import com.facebook.buck.rules.modern.ModernBuildRule;
import com.facebook.buck.rules.modern.OutputPath;
import com.facebook.buck.rules.modern.OutputPathResolver;
import com.facebook.buck.step.Step;
import com.google.common.collect.ImmutableList;

/**
 * A BuildRule for stripping (removing inessential information from executable binary programs and
 * object files) binaries.
 */
public class StripLinkable extends ModernBuildRule<StripLinkable.Impl> {

  public StripLinkable(
      NdkCxxPlatform ndkPlatform,
      BuildTarget buildTarget,
      ProjectFilesystem projectFilesystem,
      SourcePathRuleFinder ruleFinder,
      Tool stripTool,
      SourcePath sourcePathToStrip,
      String strippedObjectName,
      boolean withDownwardApi) {
    super(
        buildTarget,
        projectFilesystem,
        ruleFinder,
        new Impl(
            stripTool,
            ndkPlatform.getStripApkLibsFlags(),
            sourcePathToStrip,
            strippedObjectName,
            withDownwardApi));
  }

  /** internal buildable implementation */
  static class Impl implements Buildable {

    @AddToRuleKey private final Tool stripTool;
    @AddToRuleKey private final ImmutableList<Arg> stripArgs;
    @AddToRuleKey private final SourcePath sourcePathToStrip;
    @AddToRuleKey private final OutputPath output;
    @AddToRuleKey private final boolean withDownwardApi;

    Impl(
        Tool stripTool,
        ImmutableList<Arg> stripArgs,
        SourcePath sourcePathToStrip,
        String strippedObjectName,
        boolean withDownwardApi) {
      this.stripTool = stripTool;
      this.stripArgs = stripArgs;
      this.sourcePathToStrip = sourcePathToStrip;
      this.output = new OutputPath(strippedObjectName);
      this.withDownwardApi = withDownwardApi;
    }

    @Override
    public ImmutableList<Step> getBuildSteps(
        BuildContext buildContext,
        ProjectFilesystem filesystem,
        OutputPathResolver outputPathResolver,
        BuildCellRelativePathFactory buildCellPathFactory) {

      SourcePathResolverAdapter sourcePathResolverAdapter = buildContext.getSourcePathResolver();
      RelPath destination = outputPathResolver.resolvePath(output);

      return ImmutableList.of(
          new StripStep(
              filesystem.getRootPath(),
              stripTool.getEnvironment(sourcePathResolverAdapter),
              stripTool.getCommandPrefix(sourcePathResolverAdapter),
              Arg.stringify(this.stripArgs, sourcePathResolverAdapter),
              sourcePathResolverAdapter.getAbsolutePath(sourcePathToStrip),
              filesystem.resolve(destination),
              ProjectFilesystemUtils.relativize(
                  filesystem.getRootPath(), buildContext.getBuildCellRootPath()),
              withDownwardApi));
    }
  }

  @Override
  public SourcePath getSourcePathToOutput() {
    return getSourcePath(getBuildable().output);
  }
}

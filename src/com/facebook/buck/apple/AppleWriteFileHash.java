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

package com.facebook.buck.apple;

import com.facebook.buck.core.build.context.BuildContext;
import com.facebook.buck.core.build.execution.context.StepExecutionContext;
import com.facebook.buck.core.filesystems.AbsPath;
import com.facebook.buck.core.model.BuildTarget;
import com.facebook.buck.core.rulekey.AddToRuleKey;
import com.facebook.buck.core.rulekey.CustomFieldBehavior;
import com.facebook.buck.core.rules.SourcePathRuleFinder;
import com.facebook.buck.core.sourcepath.SourcePath;
import com.facebook.buck.io.filesystem.ProjectFilesystem;
import com.facebook.buck.rules.modern.BuildCellRelativePathFactory;
import com.facebook.buck.rules.modern.Buildable;
import com.facebook.buck.rules.modern.ModernBuildRule;
import com.facebook.buck.rules.modern.OutputPath;
import com.facebook.buck.rules.modern.OutputPathResolver;
import com.facebook.buck.rules.modern.RemoteExecutionEnabled;
import com.facebook.buck.step.AbstractExecutionStep;
import com.facebook.buck.step.Step;
import com.facebook.buck.step.StepExecutionResult;
import com.facebook.buck.step.StepExecutionResults;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import javax.annotation.Nonnull;

/**
 * Computes the hash of the given file or integral hash of directory content and writes it out as
 * the output. Includes special configurable logic to extract UUIDs from Mach-O files, which can be
 * used instead, as to avoid re-hashing.
 */
public class AppleWriteFileHash extends ModernBuildRule<AppleWriteFileHash> implements Buildable {
  @AddToRuleKey private final SourcePath inputPath;
  @AddToRuleKey private final OutputPath outputPath;
  @AddToRuleKey private final boolean useMachoUuid;
  @AddToRuleKey private final boolean isCacheable;

  // This class is used for incremental builds, so it does not make sense to be trying to send
  // those over remote execution.
  @CustomFieldBehavior(RemoteExecutionEnabled.class)
  private final boolean remoteExecutionEnabled = false;

  public AppleWriteFileHash(
      BuildTarget buildTarget,
      ProjectFilesystem projectFilesystem,
      SourcePathRuleFinder ruleFinder,
      SourcePath pathToFile,
      boolean useMachoUuid,
      boolean isCacheable) {
    super(buildTarget, projectFilesystem, ruleFinder, AppleWriteFileHash.class);
    this.inputPath = pathToFile;
    this.outputPath = new OutputPath("file.apple_hash");
    this.useMachoUuid = useMachoUuid;
    this.isCacheable = isCacheable;
  }

  @Override
  public boolean isCacheable() {
    return isCacheable;
  }

  @Override
  public ImmutableList<Step> getBuildSteps(
      BuildContext buildContext,
      ProjectFilesystem filesystem,
      OutputPathResolver outputPathResolver,
      BuildCellRelativePathFactory buildCellPathFactory) {

    StringBuilder hashBuilder = new StringBuilder();
    AbsPath absInputPath = buildContext.getSourcePathResolver().getAbsolutePath(inputPath);

    return ImmutableList.of(
        new AppleComputeFileOrDirectoryHashStep(
            hashBuilder, absInputPath, filesystem, useMachoUuid, true),
        new AbstractExecutionStep("writing_apple_file_hash") {
          @Override
          public StepExecutionResult execute(StepExecutionContext context) throws IOException {
            filesystem.writeContentsToPath(
                hashBuilder.toString(), outputPathResolver.resolvePath(outputPath));
            return StepExecutionResults.SUCCESS;
          }
        });
  }

  @Nonnull
  @Override
  public SourcePath getSourcePathToOutput() {
    return getSourcePath(outputPath);
  }
}

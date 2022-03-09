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

package com.facebook.buck.externalactions.android;

import com.facebook.buck.core.filesystems.RelPath;
import com.facebook.buck.external.model.ExternalAction;
import com.facebook.buck.externalactions.utils.ExternalActionsUtils;
import com.facebook.buck.rules.modern.model.BuildableCommand;
import com.facebook.buck.step.isolatedsteps.IsolatedStep;
import com.facebook.buck.step.isolatedsteps.android.GenerateManifestStep;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import java.util.List;

/**
 * {@link ExternalAction} that returns the build steps for {@link
 * com.facebook.buck.android.AndroidManifest}.
 */
public class AndroidManifestExternalAction implements ExternalAction {

  private static final int NUM_EXPECTED_FILES = 1;

  @Override
  public ImmutableList<IsolatedStep> getSteps(BuildableCommand buildableCommand) {
    List<String> json = buildableCommand.getExtraFilesList();
    Preconditions.checkState(
        json.size() == NUM_EXPECTED_FILES,
        "Expected %s JSON files, got %s",
        NUM_EXPECTED_FILES,
        json.size());
    AndroidManifestExternalActionArgs args =
        ExternalActionsUtils.readJsonArgs(
            Iterables.getOnlyElement(json), AndroidManifestExternalActionArgs.class);

    return ImmutableList.of(
        new GenerateManifestStep(
            RelPath.get(args.getSkeletonManifestPath()),
            args.getModuleName(),
            args.getLibraryManifestPaths().stream()
                .map(RelPath::get)
                .collect(ImmutableList.toImmutableList()),
            RelPath.get(args.getOutputManifestPath()),
            RelPath.get(args.getMergeReportPath()),
            ImmutableMap.copyOf(args.getPlaceholders())));
  }
}

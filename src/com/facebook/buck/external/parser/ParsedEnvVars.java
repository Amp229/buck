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

package com.facebook.buck.external.parser;

import com.facebook.buck.core.build.execution.context.actionid.ActionId;
import com.facebook.buck.core.filesystems.AbsPath;
import com.facebook.buck.core.model.BuildId;
import com.facebook.buck.core.util.immutables.BuckStyleValue;
import com.facebook.buck.downwardapi.utils.DownwardApiConstants;
import com.facebook.buck.external.constants.ExternalBinaryBuckConstants;
import com.facebook.buck.util.Verbosity;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import java.nio.file.Path;
import java.nio.file.Paths;

/** Data class for variables passed through the environment. */
@BuckStyleValue
public abstract class ParsedEnvVars {

  public abstract Verbosity getVerbosity();

  public abstract boolean isAnsiTerminal();

  public abstract BuildId getBuildUuid();

  public abstract ActionId getActionId();

  public abstract Path getEventPipe();

  public abstract AbsPath getRuleCellRoot();

  public static ParsedEnvVars parse(ImmutableMap<String, String> envs) {
    return ImmutableParsedEnvVars.ofImpl(
        Verbosity.valueOf(checkNotNull(envs, DownwardApiConstants.ENV_VERBOSITY)),
        Boolean.parseBoolean(checkNotNull(envs, DownwardApiConstants.ENV_ANSI_ENABLED)),
        new BuildId(checkNotNull(envs, DownwardApiConstants.ENV_BUILD_UUID)),
        ActionId.of(checkNotNull(envs, DownwardApiConstants.ENV_ACTION_ID)),
        Paths.get(checkNotNull(envs, DownwardApiConstants.ENV_EVENT_PIPE)),
        AbsPath.get(checkNotNull(envs, ExternalBinaryBuckConstants.ENV_RULE_CELL_ROOT)));
  }

  private static String checkNotNull(ImmutableMap<String, String> envs, String key) {
    return Preconditions.checkNotNull(envs.get(key), "Missing env var: %s", key);
  }
}

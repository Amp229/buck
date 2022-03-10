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

import com.facebook.buck.android.toolchain.AndroidPlatformTarget;
import com.facebook.buck.android.toolchain.AndroidSdkLocation;
import com.facebook.buck.core.config.BuckConfig;
import com.facebook.buck.core.config.FakeBuckConfig;
import com.facebook.buck.core.model.BuildTarget;
import com.facebook.buck.core.model.targetgraph.AbstractNodeBuilder;
import com.facebook.buck.core.toolchain.ToolchainProvider;
import com.facebook.buck.core.toolchain.impl.ToolchainProviderBuilder;
import com.facebook.buck.downwardapi.config.DownwardApiConfig;
import com.facebook.buck.remoteexecution.config.RemoteExecutionConfig;
import com.facebook.buck.rules.macros.StringWithMacros;
import com.facebook.buck.rules.macros.StringWithMacrosUtils;
import com.facebook.buck.sandbox.NoSandboxExecutionStrategy;
import com.facebook.buck.sandbox.SandboxConfig;
import com.facebook.buck.support.cli.config.CliConfig;
import java.nio.file.Paths;

public class ApkGenruleBuilder
    extends AbstractNodeBuilder<
        ApkGenruleDescriptionArg.Builder,
        ApkGenruleDescriptionArg,
        ApkGenruleDescription,
        ApkGenrule> {

  private ApkGenruleBuilder(BuildTarget target, ToolchainProvider toolchainProvider) {
    super(getDescription(toolchainProvider), target);
  }

  private static ApkGenruleDescription getDescription(ToolchainProvider toolchainProvider) {
    BuckConfig buckConfig = FakeBuckConfig.empty();
    DownwardApiConfig downwardApiConfig = buckConfig.getView(DownwardApiConfig.class);
    CliConfig cliConfig = buckConfig.getView(CliConfig.class);
    SandboxConfig sandboxConfig = buckConfig.getView(SandboxConfig.class);
    RemoteExecutionConfig reConfig = buckConfig.getView(RemoteExecutionConfig.class);

    return new ApkGenruleDescription(
        toolchainProvider,
        sandboxConfig,
        reConfig,
        downwardApiConfig,
        cliConfig,
        new NoSandboxExecutionStrategy());
  }

  public static ApkGenruleBuilder create(BuildTarget target) {
    return new ApkGenruleBuilder(target, getToolchainProvider());
  }

  public ApkGenruleBuilder setOut(String out) {
    getArgForPopulating().setOut(out);
    return this;
  }

  public ApkGenruleBuilder setCmd(String cmd) {
    getArgForPopulating().setCmd(StringWithMacrosUtils.format(cmd));
    return this;
  }

  public ApkGenruleBuilder setCmd(StringWithMacros cmd) {
    getArgForPopulating().setCmd(cmd);
    return this;
  }

  public ApkGenruleBuilder setApk(BuildTarget apk) {
    getArgForPopulating().setApk(apk);
    return this;
  }

  static ToolchainProvider getToolchainProvider() {
    return new ToolchainProviderBuilder()
        .withToolchain(
            AndroidSdkLocation.DEFAULT_NAME,
            AndroidSdkLocation.of(Paths.get("/opt/users/android_sdk")))
        .withToolchain(
            AndroidPlatformTarget.DEFAULT_NAME, TestAndroidPlatformTargetFactory.create())
        .build();
  }
}

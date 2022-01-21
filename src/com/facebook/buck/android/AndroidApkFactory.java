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

import com.facebook.buck.android.FilterResourcesSteps.ResourceFilter;
import com.facebook.buck.android.exopackage.ExopackageMode;
import com.facebook.buck.android.toolchain.AndroidPlatformTarget;
import com.facebook.buck.android.toolchain.AndroidSdkLocation;
import com.facebook.buck.core.cell.CellPathResolver;
import com.facebook.buck.core.exceptions.HumanReadableException;
import com.facebook.buck.core.model.BuildTarget;
import com.facebook.buck.core.model.Flavor;
import com.facebook.buck.core.model.InternalFlavor;
import com.facebook.buck.core.rules.ActionGraphBuilder;
import com.facebook.buck.core.rules.BuildRule;
import com.facebook.buck.core.rules.BuildRuleParams;
import com.facebook.buck.core.toolchain.ToolchainProvider;
import com.facebook.buck.downwardapi.config.DownwardApiConfig;
import com.facebook.buck.io.filesystem.ProjectFilesystem;
import com.facebook.buck.jvm.core.JavaLibrary;
import com.facebook.buck.jvm.java.JavaOptions;
import com.facebook.buck.jvm.java.Keystore;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedSet;
import java.util.EnumSet;
import java.util.Optional;
import java.util.function.Supplier;

public class AndroidApkFactory {

  static final Flavor EXO_SYMLINK_TREE = InternalFlavor.of("exo_symlink_tree");

  private final AndroidBuckConfig androidBuckConfig;
  private final DownwardApiConfig downwardApiConfig;
  private final AndroidInstallConfig androidInstallConfig;

  public AndroidApkFactory(
      AndroidBuckConfig androidBuckConfig,
      DownwardApiConfig downwardApiConfig,
      AndroidInstallConfig androidInstallConfig) {
    this.androidBuckConfig = androidBuckConfig;
    this.downwardApiConfig = downwardApiConfig;
    this.androidInstallConfig = androidInstallConfig;
  }

  public AndroidApk create(
      ToolchainProvider toolchainProvider,
      ProjectFilesystem projectFilesystem,
      ActionGraphBuilder graphBuilder,
      CellPathResolver cellPathResolver,
      BuildTarget buildTarget,
      BuildRuleParams params,
      AndroidBinaryGraphEnhancer graphEnhancer,
      DexSplitMode dexSplitMode,
      EnumSet<ExopackageMode> exopackageModes,
      ResourceFilter resourceFilter,
      Supplier<ImmutableSet<JavaLibrary>> rulesToExcludeFromDex,
      AndroidBinaryDescriptionArg args,
      JavaOptions javaOptions) {

    BuildRule keystore = graphBuilder.getRule(args.getKeystore());
    if (!(keystore instanceof Keystore)) {
      throw new HumanReadableException(
          "In %s, keystore='%s' must be a keystore() but was %s().",
          buildTarget, keystore.getFullyQualifiedName(), keystore.getType());
    }

    ProGuardObfuscateStep.SdkProguardType androidSdkProguardConfig =
        args.getAndroidSdkProguardConfig().orElse(ProGuardObfuscateStep.SdkProguardType.NONE);

    AndroidGraphEnhancementResult result = graphEnhancer.createAdditionalBuildables();

    AndroidApkFilesInfo filesInfo =
        new AndroidApkFilesInfo(result, exopackageModes, args.isPackageAssetLibraries());

    if (filesInfo.getExopackageInfo().isPresent()) {
      AndroidApkExopackageSymlinkTree androidApkExopackageSymlinkTree =
          new AndroidApkExopackageSymlinkTree(
              buildTarget.withAppendedFlavors(EXO_SYMLINK_TREE),
              projectFilesystem,
              graphBuilder,
              filesInfo.getExopackageInfo().get(),
              result.getAndroidManifestPath());
      graphBuilder.addToIndex(androidApkExopackageSymlinkTree);
    }

    AndroidPlatformTarget androidPlatformTarget =
        toolchainProvider.getByName(
            AndroidPlatformTarget.DEFAULT_NAME,
            buildTarget.getTargetConfiguration(),
            AndroidPlatformTarget.class);

    return new AndroidApk(
        buildTarget,
        projectFilesystem,
        toolchainProvider.getByName(
            AndroidSdkLocation.DEFAULT_NAME,
            buildTarget.getTargetConfiguration(),
            AndroidSdkLocation.class),
        params,
        graphBuilder,
        Optional.of(args.getProguardJvmArgs()),
        (Keystore) keystore,
        dexSplitMode,
        args.getNoDx(),
        androidSdkProguardConfig,
        args.getOptimizationPasses(),
        args.getProguardConfig(),
        args.isSkipProguard(),
        RedexArgsHelper.getRedexOptions(
            androidBuckConfig,
            buildTarget,
            graphBuilder,
            cellPathResolver,
            args.getRedex(),
            args.getRedexExtraArgs(),
            args.getRedexConfig()),
        args.getResourceCompression(),
        args.getCpuFilters(),
        resourceFilter,
        exopackageModes,
        rulesToExcludeFromDex,
        result,
        args.getXzCompressionLevel(),
        args.isPackageAssetLibraries(),
        args.isCompressAssetLibraries(),
        args.getAssetCompressionAlgorithm(),
        args.getManifestEntries(),
        javaOptions.getJavaRuntime(),
        androidPlatformTarget
            .getZipalignToolProvider()
            .resolve(graphBuilder, buildTarget.getTargetConfiguration()),
        args.getIsCacheable(),
        filesInfo.getDexFilesInfo(),
        filesInfo.getNativeFilesInfo(),
        filesInfo.getResourceFilesInfo(),
        ImmutableSortedSet.copyOf(result.getAPKModuleGraph().getAPKModules()),
        filesInfo.getExopackageInfo(),
        downwardApiConfig.isEnabledForAndroid(),
        androidInstallConfig.getConcurrentInstallEnabled(Optional.empty()));
  }
}

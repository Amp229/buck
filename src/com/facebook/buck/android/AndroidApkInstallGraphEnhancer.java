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

import com.facebook.buck.android.exopackage.AdbConfig;
import com.facebook.buck.android.exopackage.ExopackageInfo;
import com.facebook.buck.android.exopackage.ExopackagePathAndHash;
import com.facebook.buck.core.model.BuildTarget;
import com.facebook.buck.core.model.Flavor;
import com.facebook.buck.core.model.InternalFlavor;
import com.facebook.buck.core.rules.ActionGraphBuilder;
import com.facebook.buck.core.rules.BuildRule;
import com.facebook.buck.core.rules.SourcePathRuleFinder;
import com.facebook.buck.core.rules.impl.NoopBuildRule;
import com.facebook.buck.core.sourcepath.SourcePath;
import com.facebook.buck.io.filesystem.ProjectFilesystem;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.Multimap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

class AndroidApkInstallGraphEnhancer {
  static final Flavor INSTALL_FLAVOR = InternalFlavor.of("install");
  private static final Flavor DIRECTORY_LISTING_FLAVOR = InternalFlavor.of("exo_directory_listing");
  private static final Flavor EXO_DEX_FILES_INSTALL_FLAVOR =
      InternalFlavor.of("exo_dex_files_installer");
  private static final Flavor EXO_MODULE_FILES_INSTALL_FLAVOR =
      InternalFlavor.of("exo_module_files_installer");
  private static final Flavor EXO_NATIVE_LIBS_INSTALL_FLAVOR =
      InternalFlavor.of("exo_native_libs_installer");
  private static final Flavor EXO_FILE_RESOURCE_INSTALL_FLAVOR =
      InternalFlavor.of("exo_resources_installer");

  private ProjectFilesystem projectFilesystem;
  private BuildTarget buildTarget;
  private HasInstallableApk installableApk;
  // TODO(bduff): Merge these
  private AdbConfig adbConfig;

  AndroidApkInstallGraphEnhancer(
      AdbConfig adbConfig,
      ProjectFilesystem projectFilesystem,
      BuildTarget buildTarget,
      HasInstallableApk installableApk) {
    this.projectFilesystem = projectFilesystem;
    this.buildTarget = buildTarget.withFlavors(INSTALL_FLAVOR);
    this.installableApk = installableApk;
    this.adbConfig = adbConfig;
  }

  public void enhance(ActionGraphBuilder graphBuilder) {
    if (installableApk.isConcurrentInstallEnabled()) {
      if (exopackageEnabled()) {
        enhanceForConcurrentExopackageInstall(graphBuilder);
      } else {
        enhanceForConcurrentInstall(graphBuilder);
      }
    } else {
      enhanceForLegacyInstall(graphBuilder);
    }
  }

  private boolean exopackageEnabled() {
    return installableApk.getApkInfo().getExopackageInfo().isPresent();
  }

  private void enhanceForConcurrentExopackageInstall(ActionGraphBuilder graphBuilder) {
    HasInstallableApk.ApkInfo apkInfo = installableApk.getApkInfo();
    Preconditions.checkState(apkInfo.getExopackageInfo().isPresent());

    ExopackageDeviceDirectoryLister directoryLister =
        new ExopackageDeviceDirectoryLister(
            buildTarget.withFlavors(DIRECTORY_LISTING_FLAVOR), projectFilesystem);
    ExopackageInfo exopackageInfo = apkInfo.getExopackageInfo().get();
    ImmutableList.Builder<BuildRule> finisherDeps = ImmutableList.builder();
    if (exopackageInfo.getDexInfo().isPresent()) {
      ExopackageInfo filteredExopackageInfo =
          ExopackageInfo.builder().setDexInfo(exopackageInfo.getDexInfo()).build();
      ExopackageFilesInstaller fileInstaller =
          new ExopackageFilesInstaller(
              buildTarget.withFlavors(EXO_DEX_FILES_INSTALL_FLAVOR),
              projectFilesystem,
              graphBuilder,
              directoryLister.getSourcePathToOutput(),
              apkInfo.getManifestPath(),
              filteredExopackageInfo,
              adbConfig);
      graphBuilder.addToIndex(fileInstaller);
      finisherDeps.add(fileInstaller);
    }

    if (exopackageInfo.getNativeLibsInfo().isPresent()) {
      ExopackageInfo filteredExopackageInfo =
          ExopackageInfo.builder().setNativeLibsInfo(exopackageInfo.getNativeLibsInfo()).build();
      ExopackageFilesInstaller fileInstaller =
          new ExopackageFilesInstaller(
              buildTarget.withFlavors(EXO_NATIVE_LIBS_INSTALL_FLAVOR),
              projectFilesystem,
              graphBuilder,
              directoryLister.getSourcePathToOutput(),
              apkInfo.getManifestPath(),
              filteredExopackageInfo,
              adbConfig);
      graphBuilder.addToIndex(fileInstaller);
      finisherDeps.add(fileInstaller);
    }

    if (exopackageInfo.getModuleInfo().isPresent()) {
      ExopackageInfo filteredExopackageInfo =
          ExopackageInfo.builder().setModuleInfo(exopackageInfo.getModuleInfo()).build();
      ExopackageFilesInstaller fileInstaller =
          new ExopackageFilesInstaller(
              buildTarget.withFlavors(EXO_MODULE_FILES_INSTALL_FLAVOR),
              projectFilesystem,
              graphBuilder,
              directoryLister.getSourcePathToOutput(),
              apkInfo.getManifestPath(),
              filteredExopackageInfo,
              adbConfig);
      graphBuilder.addToIndex(fileInstaller);
      finisherDeps.add(fileInstaller);
    }
    if (exopackageInfo.getResourcesInfo().isPresent()) {
      List<BuildRule> resourceInstallRules =
          createResourceInstallRules(
              exopackageInfo.getResourcesInfo().get(),
              graphBuilder,
              apkInfo.getManifestPath(),
              directoryLister.getSourcePathToOutput());
      resourceInstallRules.forEach(graphBuilder::addToIndex);
      finisherDeps.addAll(resourceInstallRules);
    }

    BuildRule apkInstaller =
        new ExopackageInstallFinisher(
            buildTarget,
            projectFilesystem,
            graphBuilder,
            apkInfo,
            directoryLister,
            finisherDeps.build(),
            adbConfig);

    graphBuilder.addToIndex(directoryLister);
    graphBuilder.addToIndex(apkInstaller);
  }

  private List<BuildRule> createResourceInstallRules(
      ExopackageInfo.ResourcesInfo resourcesInfo,
      SourcePathRuleFinder ruleFinder,
      SourcePath manifestPath,
      SourcePath deviceExoContents) {
    // We construct a single ExopackageResourcesInstaller for each creator of exopackage resources.
    // This is done because the installers will synchronize on the underlying AndroidDevicesHelper
    // and so we don't want a single rule to generate a bunch of resource files and then take up a
    // bunch of build threads all waiting on each other.
    Multimap<BuildRule, ExopackagePathAndHash> creatorMappedPaths =
        resourcesInfo.getResourcesPaths().stream()
            .collect(
                ImmutableListMultimap.toImmutableListMultimap(
                    (ExopackagePathAndHash pathAndHash) ->
                        ruleFinder.getRule(pathAndHash.getPath()).orElse(null),
                    v -> v));
    List<BuildRule> installers = new ArrayList<>();
    int index = 0;
    for (Collection<ExopackagePathAndHash> paths : creatorMappedPaths.asMap().values()) {
      installers.add(
          new ExopackageResourcesInstaller(
              buildTarget.withAppendedFlavors(
                  EXO_FILE_RESOURCE_INSTALL_FLAVOR,
                  InternalFlavor.of(String.format("resources-%d", index))),
              projectFilesystem,
              ruleFinder,
              paths,
              manifestPath,
              deviceExoContents,
              adbConfig));
      index++;
    }
    return installers;
  }

  private void enhanceForConcurrentInstall(ActionGraphBuilder graphBuilder) {
    graphBuilder.addToIndex(
        new AndroidApkNonExoInstaller(buildTarget, projectFilesystem, installableApk));
  }

  private void enhanceForLegacyInstall(ActionGraphBuilder graphBuilder) {
    graphBuilder.addToIndex(new NoopBuildRule(buildTarget, projectFilesystem));
  }
}

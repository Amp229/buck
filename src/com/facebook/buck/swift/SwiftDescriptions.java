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

package com.facebook.buck.swift;

import static com.facebook.buck.cxx.toolchain.nativelink.NativeLinkableGroup.Linkage.STATIC;
import static com.facebook.buck.swift.SwiftLibraryDescription.SWIFT_COMPANION_FLAVOR;

import com.facebook.buck.core.model.BuildTarget;
import com.facebook.buck.core.sourcepath.SourcePath;
import com.facebook.buck.core.sourcepath.SourceWithFlags;
import com.facebook.buck.core.sourcepath.resolver.SourcePathResolverAdapter;
import com.facebook.buck.cxx.CxxLibraryDescription;
import com.facebook.buck.io.file.MorePaths;
import com.google.common.collect.ImmutableBiMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedSet;
import java.nio.file.Path;
import java.util.Optional;

public class SwiftDescriptions {

  static final String SWIFT_HEADER_SUFFIX = "-Swift";
  static final String SWIFT_MAIN_FILENAME = "main.swift";
  public static final String SWIFT_EXTENSION = "swift";

  /** Utility class: do not instantiate. */
  private SwiftDescriptions() {}

  public static boolean isSwiftSource(
      SourceWithFlags source, SourcePathResolverAdapter sourcePathResolverAdapter) {
    return MorePaths.getFileExtension(
            sourcePathResolverAdapter.getAbsolutePath(source.getSourcePath()))
        .equalsIgnoreCase(SWIFT_EXTENSION);
  }

  static ImmutableSortedSet<SourcePath> filterSwiftSources(
      SourcePathResolverAdapter sourcePathResolverAdapter, ImmutableSet<SourceWithFlags> srcs) {
    ImmutableSortedSet.Builder<SourcePath> swiftSrcsBuilder = ImmutableSortedSet.naturalOrder();
    for (SourceWithFlags source : srcs) {
      if (isSwiftSource(source, sourcePathResolverAdapter)) {
        swiftSrcsBuilder.add(source.getSourcePath());
      }
    }
    return swiftSrcsBuilder.build();
  }

  public static void populateSwiftLibraryDescriptionArg(
      SwiftBuckConfig swiftBuckConfig,
      SourcePathResolverAdapter sourcePathResolverAdapter,
      SwiftLibraryDescriptionArg.Builder output,
      CxxLibraryDescription.CommonArg args,
      BuildTarget buildTarget) {

    output.setName(args.getName());
    output.setSrcs(filterSwiftSources(sourcePathResolverAdapter, args.getSrcs()));
    if (args instanceof SwiftCommonArg) {
      SwiftCommonArg swiftArgs = (SwiftCommonArg) args;
      Optional<String> swiftVersion = swiftArgs.getSwiftVersion();
      if (!swiftVersion.isPresent()) {
        swiftVersion = swiftBuckConfig.getVersion();
      }
      output.setCompilerFlags(swiftArgs.getSwiftCompilerFlags());
      output.setVersion(swiftVersion);
      output.setSerializeDebuggingOptions(swiftArgs.getSerializeDebuggingOptions());
      output.setUsesExplicitModules(swiftArgs.getUsesExplicitModules());
      output.setEnableCxxInterop(swiftArgs.getEnableCxxInterop());
      output.setModuleName(getModuleName(buildTarget, swiftArgs));
    } else {
      output.setCompilerFlags(args.getCompilerFlags());
    }

    output.setFrameworks(args.getFrameworks());
    output.setLibraries(args.getLibraries());
    output.setDeps(args.getDeps());
    output.setSupportedPlatformsRegex(args.getSupportedPlatformsRegex());
    output.setBridgingHeader(args.getBridgingHeader());

    boolean isCompanionTarget = buildTarget.getFlavors().contains(SWIFT_COMPANION_FLAVOR);
    output.setPreferredLinkage(
        isCompanionTarget ? Optional.of(STATIC) : args.getPreferredLinkage());
  }

  public static String getModuleName(BuildTarget buildTarget, SwiftCommonArg args) {
    return args.getModuleName()
        .orElse(args.getHeaderPathPrefix().orElse(buildTarget.getShortName()));
  }

  static String toSwiftHeaderName(String moduleName) {
    return moduleName + SWIFT_HEADER_SUFFIX;
  }

  public static ImmutableBiMap<Path, String> getDebugPrefixMap(
      Path sdkRoot, Path platformRoot, Optional<Path> developerRoot) {
    ImmutableBiMap.Builder<Path, String> debugPathsBuilder = ImmutableBiMap.builder();
    debugPathsBuilder.put(sdkRoot, "/APPLE_SDKROOT");
    debugPathsBuilder.put(platformRoot, "/APPLE_PLATFORM_DIR");
    developerRoot.ifPresent(path -> debugPathsBuilder.put(path, "/APPLE_DEVELOPER_DIR"));
    return debugPathsBuilder.build();
  }
}

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

import com.facebook.buck.apple.common.AppleCompilerTargetTriple;
import com.facebook.buck.apple.toolchain.AppleCxxPlatform;
import com.facebook.buck.apple.toolchain.ApplePlatform;
import com.facebook.buck.apple.toolchain.AppleSdk;
import com.facebook.buck.apple.toolchain.AppleSdkPaths;
import com.facebook.buck.core.description.arg.BuildRuleArg;
import com.facebook.buck.core.exceptions.HumanReadableException;
import com.facebook.buck.core.filesystems.AbsPath;
import com.facebook.buck.core.model.BuildTarget;
import com.facebook.buck.core.model.Flavor;
import com.facebook.buck.core.model.UserFlavor;
import com.facebook.buck.core.rules.ActionGraphBuilder;
import com.facebook.buck.core.rules.BuildRule;
import com.facebook.buck.core.rules.BuildRuleCreationContextWithTargetGraph;
import com.facebook.buck.core.rules.BuildRuleParams;
import com.facebook.buck.core.rules.BuildRuleResolver;
import com.facebook.buck.core.rules.DescriptionWithTargetGraph;
import com.facebook.buck.core.sourcepath.SourcePath;
import com.facebook.buck.core.sourcepath.resolver.SourcePathResolverAdapter;
import com.facebook.buck.core.toolchain.tool.Tool;
import com.facebook.buck.core.toolchain.tool.impl.CommandTool;
import com.facebook.buck.core.toolchain.tool.impl.RemoteExecutionEnabledTool;
import com.facebook.buck.core.toolchain.tool.impl.Tools;
import com.facebook.buck.core.toolchain.toolprovider.impl.ToolProviders;
import com.facebook.buck.core.util.immutables.RuleArg;
import com.facebook.buck.cxx.CxxFlags;
import com.facebook.buck.cxx.toolchain.CxxPlatform;
import com.facebook.buck.cxx.toolchain.DebugPathSanitizer;
import com.facebook.buck.cxx.toolchain.PrefixMapDebugPathSanitizer;
import com.facebook.buck.cxx.toolchain.ProvidesCxxPlatform;
import com.facebook.buck.rules.args.Arg;
import com.facebook.buck.rules.args.SourcePathArg;
import com.facebook.buck.rules.args.StringArg;
import com.facebook.buck.swift.SwiftToolchainBuildRule;
import com.facebook.buck.swift.toolchain.SwiftPlatform;
import com.google.common.base.Verify;
import com.google.common.collect.ImmutableBiMap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.nio.file.Path;
import java.util.Optional;

/** Defines an apple_toolchain rule which provides {@link AppleCxxPlatform}. */
public class AppleToolchainDescription
    implements DescriptionWithTargetGraph<AppleToolchainDescriptionArg> {

  @Override
  public BuildRule createBuildRule(
      BuildRuleCreationContextWithTargetGraph context,
      BuildTarget buildTarget,
      BuildRuleParams params,
      AppleToolchainDescriptionArg args) {
    Verify.verify(!buildTarget.isFlavored());
    UserFlavor targetFlavor =
        UserFlavor.of(
            Flavor.replaceInvalidCharacters(args.getSdkName() + "-" + args.getArchitecture()),
            String.format("SDK: %s, architecture: %s", args.getSdkName(), args.getArchitecture()));
    if (!ApplePlatform.isPlatformFlavor(targetFlavor)) {
      throw new HumanReadableException(
          "Can't find Apple platform for SDK: %s and architecture: %s",
          args.getSdkName(), args.getArchitecture());
    }
    ApplePlatform applePlatform = ApplePlatform.fromFlavor(targetFlavor);

    ActionGraphBuilder actionGraphBuilder = context.getActionGraphBuilder();
    BuildRule cxxToolchainRule = actionGraphBuilder.getRule(args.getCxxToolchain());
    if (!(cxxToolchainRule instanceof ProvidesCxxPlatform)) {
      throw new HumanReadableException(
          "Expected %s to be an instance of cxx_toolchain.", cxxToolchainRule.getBuildTarget());
    }
    Optional<BuildRule> swiftToolchainRule =
        args.getSwiftToolchain().map(actionGraphBuilder::getRule);
    if (swiftToolchainRule.isPresent()
        && !(swiftToolchainRule.get() instanceof SwiftToolchainBuildRule)) {
      throw new HumanReadableException(
          "Expected %s to be an instance of swift_toolchain.",
          swiftToolchainRule.get().getBuildTarget());
    }

    SourcePathResolverAdapter pathResolver = actionGraphBuilder.getSourcePathResolver();

    // We are seeing a stack overflow in dsymutil during (fat) LTO
    // builds. Upstream dsymutil was patched to avoid recursion in the
    // offending path in https://reviews.llvm.org/D48899, and
    // https://reviews.llvm.org/D45172 mentioned that there is much
    // more stack space available when single threaded.
    Tool dsymutil = resolveTool(args.getDsymutil(), actionGraphBuilder);
    if (args.getWorkAroundDsymutilLtoStackOverflowBug().orElse(false)) {
      dsymutil = new CommandTool.Builder(dsymutil).addArg("-num-threads=1").build();
    }

    AbsPath sdkPath = pathResolver.getAbsolutePath(args.getSdkPath());
    AbsPath platformPath = pathResolver.getAbsolutePath(args.getPlatformPath());
    Optional<Path> developerPath =
        args.getDeveloperPath()
            .map(sourcePath -> pathResolver.getAbsolutePath(sourcePath).getPath());
    AppleSdkPaths sdkPaths =
        AppleSdkPaths.builder()
            .setSdkPath(sdkPath.getPath())
            .setSdkSourcePath(args.getSdkPath())
            .setPlatformPath(platformPath.getPath())
            .setPlatformSourcePath(args.getPlatformPath())
            .setToolchainPaths(ImmutableList.of())
            .setDeveloperPath(developerPath)
            .build();

    AppleSdk sdk =
        AppleSdk.builder()
            .setName(args.getSdkName())
            .setVersion(args.getVersion())
            .setToolchains(ImmutableList.of())
            .setApplePlatform(applePlatform)
            .setArchitectures(applePlatform.getArchitectures())
            .setTargetTripleEnvironment(args.getSdkEnvironment())
            .build();

    AppleCompilerTargetTriple swiftTarget =
        AppleLibraryDescriptionSwiftEnhancer.createSwiftTargetTriple(
            args.getArchitecture(), sdk, args.getMinVersion());
    Optional<SwiftPlatform> swiftPlatform =
        swiftToolchainRule
            .map(SwiftToolchainBuildRule.class::cast)
            .map(
                rule ->
                    rule.getSwiftPlatform(
                        actionGraphBuilder, context.getProjectFilesystem(), swiftTarget));

    Optional<Tool> dwarfdumpTool =
        args.getDwarfdump()
            .map(dwarfdumpSrcPath -> resolveTool(dwarfdumpSrcPath, actionGraphBuilder));

    AppleCxxPlatform appleCxxPlatform =
        AppleCxxPlatform.builder()
            .setMinVersion(args.getMinVersion())
            .setBuildVersion(args.getBuildVersion())
            .setActool(resolveMacOnlyTool(args.getActool(), actionGraphBuilder))
            .setLibtool(resolveTool(args.getLibtool(), actionGraphBuilder))
            .setIbtool(resolveMacOnlyTool(args.getIbtool(), actionGraphBuilder))
            .setMomc(resolveMacOnlyTool(args.getMomc(), actionGraphBuilder))
            .setCopySceneKitAssets(
                args.getCopySceneKitAssets()
                    .map(path -> resolveMacOnlyTool(path, actionGraphBuilder)))
            .setXctest(resolveMacOnlyTool(args.getXctest(), actionGraphBuilder))
            .setDsymutil(dsymutil)
            .setDwarfdump(dwarfdumpTool)
            .setLipo(resolveTool(args.getLipo(), actionGraphBuilder))
            .setCodesignProvider(ToolProviders.getToolProvider(args.getCodesign(), false))
            .setCodesignAllocate(resolveMacOnlyTool(args.getCodesignAllocate(), actionGraphBuilder))
            .setCxxPlatform(
                getCxxPlatform(
                    (ProvidesCxxPlatform) cxxToolchainRule,
                    pathResolver,
                    targetFlavor,
                    args.getSdkPath(),
                    args.getPlatformPath(),
                    args.getDeveloperPath()))
            .setSwiftPlatform(swiftPlatform)
            .setXcodeVersion(args.getXcodeVersion())
            .setXcodeBuildVersion(args.getXcodeBuildVersion())
            .setAppleSdkPaths(sdkPaths)
            .setAppleSdk(sdk)
            .setWatchKitStubBinary(args.getWatchKitStubBinary())
            .build();

    return new AppleToolchainBuildRule(
        buildTarget, context.getProjectFilesystem(), appleCxxPlatform);
  }

  private Tool resolveTool(SourcePath path, BuildRuleResolver resolver) {
    return RemoteExecutionEnabledTool.getEnabledOnLinuxHost(Tools.resolveTool(path, resolver));
  }

  private Tool resolveMacOnlyTool(SourcePath path, BuildRuleResolver resolver) {
    // These tools will trigger MacDo execution so do not make sense to run remotely
    return new RemoteExecutionEnabledTool(Tools.resolveTool(path, resolver), false);
  }

  @Override
  public Class<AppleToolchainDescriptionArg> getConstructorArgType() {
    return AppleToolchainDescriptionArg.class;
  }

  private CxxPlatform getCxxPlatform(
      ProvidesCxxPlatform cxxToolchainRule,
      SourcePathResolverAdapter pathResolver,
      Flavor flavor,
      SourcePath sdkRoot,
      SourcePath platformRoot,
      Optional<SourcePath> developerRoot) {
    CxxPlatform currentCxxPlatform = cxxToolchainRule.getPlatformWithFlavor(flavor);
    CxxPlatform.Builder cxxPlatformBuilder = CxxPlatform.builder().from(currentCxxPlatform);

    DebugPathSanitizer compilerDebugPathSanitizer =
        new PrefixMapDebugPathSanitizer(".", ImmutableBiMap.of());
    cxxPlatformBuilder.setCompilerDebugPathSanitizer(compilerDebugPathSanitizer);

    ImmutableMap.Builder<String, String> macrosBuilder = ImmutableMap.builder();
    ImmutableMap.Builder<String, Arg> macrosArgsBuilder = ImmutableMap.builder();
    macrosBuilder.put("SDKROOT", pathResolver.getCellUnsafeRelPath(sdkRoot).toString());
    macrosArgsBuilder.put("SDKROOT", SourcePathArg.of(sdkRoot));
    macrosBuilder.put("PLATFORM_DIR", pathResolver.getCellUnsafeRelPath(platformRoot).toString());
    macrosArgsBuilder.put("PLATFORM_DIR", SourcePathArg.of(platformRoot));
    macrosBuilder.put(
        "CURRENT_ARCH",
        ApplePlatform.findArchitecture(flavor).orElseThrow(IllegalStateException::new));
    macrosArgsBuilder.put(
        "CURRENT_ARCH",
        StringArg.of(
            ApplePlatform.findArchitecture(flavor).orElseThrow(IllegalStateException::new)));
    developerRoot.ifPresent(
        path ->
            macrosBuilder.put("DEVELOPER_DIR", pathResolver.getCellUnsafeRelPath(path).toString()));
    developerRoot.ifPresent(path -> macrosArgsBuilder.put("DEVELOPER_DIR", SourcePathArg.of(path)));
    cxxPlatformBuilder.setFlagMacros(macrosBuilder.build());

    // Expand macros in cxx platform flags.
    CxxFlags.translateCxxPlatformFlags(
        cxxPlatformBuilder, currentCxxPlatform, macrosArgsBuilder.build());

    return cxxPlatformBuilder.build();
  }

  /**
   * apple_toolchain defines tools, cxx and swift toolchains and other properties to define
   * AppleCxxPlatform.
   */
  @RuleArg
  interface AbstractAppleToolchainDescriptionArg extends BuildRuleArg {
    /** Name of SDK which should be used. */
    String getSdkName();

    /** Target architecture. */
    String getArchitecture();

    /** Path to Apple platform */
    SourcePath getPlatformPath();

    /** Path to Apple SDK. */
    SourcePath getSdkPath();

    /** Environment for target triple, eg simulator, macabi */
    Optional<String> getSdkEnvironment();

    /** Version of SDK. */
    String getVersion();

    /** Build version. Can be found in ProductBuildVersion in platform version.plist */
    Optional<String> getBuildVersion();

    /** Target SDK version. */
    String getMinVersion();

    /** actool binary. */
    SourcePath getActool();

    /** dsymutil binary. */
    SourcePath getDsymutil();

    /** dwarfdump binary. */
    Optional<SourcePath> getDwarfdump();

    /** ibtool binary. */
    SourcePath getIbtool();

    /** libtool binary. */
    SourcePath getLibtool();

    /** lipo binary. */
    SourcePath getLipo();

    /** momc binary. */
    SourcePath getMomc();

    /** xctest binary. */
    SourcePath getXctest();

    /** copySceneKitAssets binary. */
    Optional<SourcePath> getCopySceneKitAssets();

    /** codesign binary. */
    SourcePath getCodesign();

    /** codesign_allocate binary. */
    SourcePath getCodesignAllocate();

    /** Target for the cxx toolchain which should be used for this SDK. */
    BuildTarget getCxxToolchain();

    /** Target for the swift toolchain which should be used for this SDK. */
    Optional<BuildTarget> getSwiftToolchain();

    /** Developer directory of the toolchain */
    Optional<SourcePath> getDeveloperPath();

    /** XCode version which can be found in DTXcode in XCode plist */
    String getXcodeVersion();

    /** XCode build version from from 'xcodebuild -version' */
    String getXcodeBuildVersion();

    /** If work around for dsymutil should be used. */
    Optional<Boolean> getWorkAroundDsymutilLtoStackOverflowBug();

    /** WatchKit stub binary (WK) */
    Optional<SourcePath> getWatchKitStubBinary();
  }
}

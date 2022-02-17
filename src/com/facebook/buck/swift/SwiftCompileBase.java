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

import com.facebook.buck.apple.common.AppleCompilerTargetTriple;
import com.facebook.buck.core.build.execution.context.StepExecutionContext;
import com.facebook.buck.core.filesystems.AbsPath;
import com.facebook.buck.core.filesystems.RelPath;
import com.facebook.buck.core.model.BuildTarget;
import com.facebook.buck.core.model.Flavor;
import com.facebook.buck.core.model.impl.BuildTargetPaths;
import com.facebook.buck.core.rulekey.AddToRuleKey;
import com.facebook.buck.core.rulekey.DefaultFieldInputs;
import com.facebook.buck.core.rulekey.ExcludeFromRuleKey;
import com.facebook.buck.core.rulekey.ThrowingSerialization;
import com.facebook.buck.core.rules.ActionGraphBuilder;
import com.facebook.buck.core.rules.BuildRule;
import com.facebook.buck.core.rules.BuildRuleResolver;
import com.facebook.buck.core.rules.attr.SupportsInputBasedRuleKey;
import com.facebook.buck.core.rules.common.BuildableSupport;
import com.facebook.buck.core.rules.impl.AbstractBuildRule;
import com.facebook.buck.core.sourcepath.ExplicitBuildTargetSourcePath;
import com.facebook.buck.core.sourcepath.SourcePath;
import com.facebook.buck.core.sourcepath.resolver.SourcePathResolverAdapter;
import com.facebook.buck.core.toolchain.tool.Tool;
import com.facebook.buck.cxx.CxxDescriptionEnhancer;
import com.facebook.buck.cxx.PreprocessorFlags;
import com.facebook.buck.cxx.toolchain.HeaderVisibility;
import com.facebook.buck.cxx.toolchain.LinkerMapMode;
import com.facebook.buck.cxx.toolchain.PathShortener;
import com.facebook.buck.cxx.toolchain.Preprocessor;
import com.facebook.buck.io.file.MorePaths;
import com.facebook.buck.io.file.MostFiles;
import com.facebook.buck.io.filesystem.ProjectFilesystem;
import com.facebook.buck.rules.args.AddsToRuleKeyFunction;
import com.facebook.buck.rules.args.Arg;
import com.facebook.buck.rules.args.FileListableLinkerInputArg;
import com.facebook.buck.rules.args.SourcePathArg;
import com.facebook.buck.rules.args.StringArg;
import com.facebook.buck.rules.coercer.FrameworkPath;
import com.facebook.buck.step.Step;
import com.facebook.buck.step.StepExecutionResult;
import com.facebook.buck.step.StepExecutionResults;
import com.facebook.buck.swift.toolchain.ExplicitModuleOutput;
import com.facebook.buck.util.MoreIterables;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableBiMap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Streams;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.SortedSet;

/** A build rule which compiles one or more Swift sources into a Swift module. */
public abstract class SwiftCompileBase extends AbstractBuildRule
    implements SupportsInputBasedRuleKey {

  private static final String INCLUDE_FLAG = "-I";
  private static final String DEBUG_PREFIX_MAP_FLAG = "-debug-prefix-map";

  @AddToRuleKey protected final Tool swiftCompiler;

  @AddToRuleKey private final String moduleName;

  @AddToRuleKey(stringify = true)
  protected final Path outputPath;

  @AddToRuleKey(stringify = true)
  protected final Path outputFileMapPath;

  @AddToRuleKey protected final boolean incrementalBuild;

  @AddToRuleKey protected final boolean incrementalImports;

  @AddToRuleKey(stringify = true)
  private final Path modulePath;

  @AddToRuleKey(stringify = true)
  private final ImmutableList<Path> objectPaths;

  @AddToRuleKey(stringify = true)
  private final Path swiftdocPath;

  @AddToRuleKey(stringify = true)
  private final Path headerPath;

  @AddToRuleKey private final boolean shouldEmitSwiftdocs;

  @AddToRuleKey protected final ImmutableSortedSet<SourcePath> srcs;
  @AddToRuleKey private final AppleCompilerTargetTriple swiftTarget;
  @AddToRuleKey private final Optional<String> version;
  @AddToRuleKey private final ImmutableList<? extends Arg> compilerFlags;

  @AddToRuleKey private final ImmutableList<Arg> systemFrameworkSearchPaths;
  @AddToRuleKey private final ImmutableSet<FrameworkPath> frameworks;

  @AddToRuleKey
  private final AddsToRuleKeyFunction<FrameworkPath, Optional<Path>> frameworkPathToSearchPath;

  @AddToRuleKey(stringify = true)
  private final Flavor flavor;

  @AddToRuleKey private final boolean enableCxxInterop;

  @AddToRuleKey protected final Optional<SourcePath> bridgingHeader;

  @AddToRuleKey private final Preprocessor cPreprocessor;

  @AddToRuleKey private final PreprocessorFlags cxxDeps;

  @AddToRuleKey private final boolean importUnderlyingModule;

  @AddToRuleKey private final boolean useArgfile;

  @AddToRuleKey protected final boolean withDownwardApi;

  @AddToRuleKey private final boolean inputBasedEnabled;

  @AddToRuleKey protected final boolean useDebugPrefixMap;

  @AddToRuleKey protected final boolean shouldEmitClangModuleBreadcrumbs;

  @AddToRuleKey protected final boolean prefixSerializedDebugInfo;

  @AddToRuleKey private final boolean addXCTestImportPaths;

  @AddToRuleKey private final boolean usesExplicitModules;

  @AddToRuleKey private final boolean serializeDebuggingOptions;

  @AddToRuleKey private final Optional<SourcePath> platformPath;

  @AddToRuleKey private final ImmutableSortedSet<SourcePath> swiftmoduleDeps;

  // The following fields do not have to be part of the rulekey, all the others must.
  @ExcludeFromRuleKey(
      reason = "This is a folder path to the swiftmodule output already included in the rulekey.",
      serialization = ThrowingSerialization.class,
      inputs = DefaultFieldInputs.class)
  private final ImmutableSet<SourcePath> swiftIncludePaths;

  @AddToRuleKey private final ImmutableSet<ExplicitModuleOutput> moduleDeps;

  private BuildableSupport.DepsSupplier depsSupplier;
  protected final Optional<AbsPath> argfilePath; // internal scratch temp path

  // We can't make the debug prefix map part of the rulekey as it is machine specific.
  private final ImmutableBiMap<Path, String> debugPrefixMap;

  SwiftCompileBase(
      SwiftBuckConfig swiftBuckConfig,
      BuildTarget buildTarget,
      AppleCompilerTargetTriple swiftTarget,
      ProjectFilesystem projectFilesystem,
      ActionGraphBuilder graphBuilder,
      Tool swiftCompiler,
      ImmutableList<Arg> systemFrameworkSearchPaths,
      ImmutableSet<FrameworkPath> frameworks,
      AddsToRuleKeyFunction<FrameworkPath, Optional<Path>> frameworkPathToSearchPath,
      Flavor flavor,
      String moduleName,
      Path outputPath,
      Iterable<SourcePath> srcs,
      Optional<String> version,
      ImmutableList<Arg> compilerFlags,
      boolean enableCxxInterop,
      Optional<SourcePath> bridgingHeader,
      Optional<SourcePath> platformPath,
      Preprocessor preprocessor,
      PreprocessorFlags cxxDeps,
      ImmutableBiMap<Path, String> debugPrefixMap,
      boolean importUnderlyingModule,
      boolean withDownwardApi,
      boolean hasPrefixSerializedDebugInfo,
      boolean addXCTestImportPaths,
      boolean serializeDebuggingOptions,
      boolean usesExplicitModules,
      ImmutableSet<ExplicitModuleOutput> moduleDependencies) {
    super(buildTarget, projectFilesystem);
    this.systemFrameworkSearchPaths = systemFrameworkSearchPaths;
    this.frameworks = frameworks;
    this.frameworkPathToSearchPath = frameworkPathToSearchPath;
    this.flavor = flavor;
    this.swiftCompiler = swiftCompiler;
    this.outputPath = outputPath;
    this.platformPath = platformPath;
    this.importUnderlyingModule = importUnderlyingModule;
    this.addXCTestImportPaths = addXCTestImportPaths;
    this.usesExplicitModules = usesExplicitModules;
    this.headerPath = outputPath.resolve(SwiftDescriptions.toSwiftHeaderName(moduleName) + ".h");
    this.moduleName = moduleName;

    this.incrementalBuild = swiftBuckConfig.getIncrementalBuild();
    this.incrementalImports = swiftBuckConfig.getIncrementalImports();

    if (incrementalBuild || incrementalImports) {
      ImmutableList.Builder<Path> objectPaths = ImmutableList.builder();
      Set<String> filenames = new HashSet<>();
      for (SourcePath sourceFilePath : srcs) {
        Path SourceRelPath =
            graphBuilder.getSourcePathResolver().getIdeallyRelativePath(sourceFilePath);
        String fileName = MorePaths.getNameWithoutExtension(SourceRelPath);

        Preconditions.checkArgument(
            !filenames.contains(fileName),
            "SwiftCompile %s should not have source files with identical names (%s) in incremental mode",
            this,
            fileName);
        filenames.add(fileName);
        objectPaths.add(outputPath.resolve(fileName + ".o"));
      }

      this.objectPaths = objectPaths.build();
    } else {
      this.objectPaths = ImmutableList.of(outputPath.resolve(moduleName + ".o"));
    }

    this.outputFileMapPath = outputPath.resolve("OutputFileMap.json");

    RelPath scratchDir =
        BuildTargetPaths.getScratchPath(getProjectFilesystem(), getBuildTarget(), "%s");

    this.useArgfile = swiftBuckConfig.getUseArgfile();
    this.argfilePath =
        (this.useArgfile
            ? Optional.of(
                getProjectFilesystem().getRootPath().resolve(scratchDir.resolve("swiftc.argfile")))
            : Optional.empty());

    this.modulePath = outputPath.resolve(moduleName + ".swiftmodule");

    this.shouldEmitSwiftdocs = swiftBuckConfig.getEmitSwiftdocs();
    this.swiftdocPath = outputPath.resolve(moduleName + ".swiftdoc");

    this.srcs = ImmutableSortedSet.copyOf(srcs);
    this.swiftTarget = swiftTarget;
    this.version = version;
    this.compilerFlags = compilerFlags;
    this.enableCxxInterop = enableCxxInterop;
    this.bridgingHeader = bridgingHeader;
    this.cPreprocessor = preprocessor;
    this.cxxDeps = cxxDeps;
    this.withDownwardApi = withDownwardApi;
    this.depsSupplier = BuildableSupport.buildDepsSupplier(this, graphBuilder);
    this.inputBasedEnabled = swiftBuckConfig.getInputBasedCompileEnabled();
    this.debugPrefixMap = debugPrefixMap;
    this.useDebugPrefixMap = swiftBuckConfig.getUseDebugPrefixMap();
    this.shouldEmitClangModuleBreadcrumbs = swiftBuckConfig.getEmitClangModuleBreadcrumbs();
    this.prefixSerializedDebugInfo =
        hasPrefixSerializedDebugInfo || swiftBuckConfig.getPrefixSerializedDebugInfo();
    this.serializeDebuggingOptions = serializeDebuggingOptions;

    ImmutableSortedSet.Builder<SourcePath> swiftmoduleDepPathBuilder =
        ImmutableSortedSet.naturalOrder();
    ImmutableSet.Builder<SourcePath> swiftIncludePathBuilder = ImmutableSet.builder();
    for (BuildRule dep : cxxDeps.getDeps(graphBuilder)) {
      if (dep instanceof SwiftCompile) {
        SwiftCompile swiftCompile = (SwiftCompile) dep;
        swiftmoduleDepPathBuilder.add(swiftCompile.getSwiftModuleOutputPath());
        swiftIncludePathBuilder.add(swiftCompile.getSourcePathToOutput());
      }
    }

    this.swiftmoduleDeps = swiftmoduleDepPathBuilder.build();
    this.swiftIncludePaths = swiftIncludePathBuilder.build();
    this.moduleDeps = moduleDependencies;

    performChecks(buildTarget);
  }

  @Override
  public boolean inputBasedRuleKeyIsEnabled() {
    return inputBasedEnabled;
  }

  private void performChecks(BuildTarget buildTarget) {
    Preconditions.checkArgument(
        !LinkerMapMode.FLAVOR_DOMAIN.containsAnyOf(buildTarget.getFlavors().getSet()),
        "SwiftCompile %s should not be created with LinkerMapMode flavor (%s)",
        this,
        LinkerMapMode.FLAVOR_DOMAIN);
    Preconditions.checkArgument(
        !buildTarget.getFlavors().contains(CxxDescriptionEnhancer.SHARED_FLAVOR));
  }

  /** Creates the list of arguments to pass to the Swift compiler */
  public ImmutableList<String> constructCompilerArgs(SourcePathResolverAdapter resolver) {
    String frontendFlag = "-Xfrontend";
    ImmutableList.Builder<String> argBuilder = ImmutableList.builder();

    argBuilder.add("-target", swiftTarget.getVersionedTriple());

    if (bridgingHeader.isPresent()) {
      // Disable bridging header -> PCH compilation to mitigate an issue in Xcode 13 beta.
      argBuilder.add(
          "-disable-bridging-pch",
          "-import-objc-header",
          resolver.getCellUnsafeRelPath(bridgingHeader.get()).toString());
    }

    if (importUnderlyingModule) {
      argBuilder.add("-import-underlying-module");
    }

    if (usesExplicitModules) {
      argBuilder.add(frontendFlag, "-disable-implicit-swift-modules");

      // Import the swiftmodule output of dependent apple_library rules
      for (SourcePath swiftmodulePath : swiftmoduleDeps) {
        argBuilder.add(
            frontendFlag,
            "-swift-module-file",
            frontendFlag,
            resolver.getIdeallyRelativePath(swiftmodulePath).toString());
      }

      // Import the swiftmodule and pcm output of SDK frameworks and Objc dependencies
      for (ExplicitModuleOutput module : moduleDeps) {
        String path = resolver.getIdeallyRelativePath(module.getOutputPath()).toString();
        if (module.getIsSwiftmodule()) {
          argBuilder.add(frontendFlag, "-swift-module-file", frontendFlag, path);
        } else {
          argBuilder.add("-Xcc", "-fmodule-file=" + module.getName() + "=" + path);
        }
      }
    } else {
      for (SourcePath includePath : swiftIncludePaths) {
        argBuilder.add(
            INCLUDE_FLAG,
            getProjectFilesystem().relativize(resolver.getAbsolutePath(includePath)).toString());
      }
    }

    argBuilder.addAll(
        MoreIterables.zipAndConcat(
            Iterables.cycle("-Fsystem"), Arg.stringify(systemFrameworkSearchPaths, resolver)));

    if (addXCTestImportPaths) {
      argBuilder.addAll(xctestImportArgs(resolver));
    }

    argBuilder.addAll(
        Streams.concat(frameworks.stream(), cxxDeps.getFrameworkPaths().stream())
            .filter(x -> !x.isSDKROOTFrameworkPath())
            .map(frameworkPathToSearchPath)
            .filter(Optional::isPresent)
            .map(Optional::get)
            .flatMap(searchPath -> ImmutableSet.of("-F", searchPath.toString()).stream())
            .iterator());

    argBuilder.addAll(
        MoreIterables.zipAndConcat(Iterables.cycle("-Xcc"), getSwiftIncludeArgs(resolver)));

    boolean hasMainEntry =
        srcs.stream()
            .map(input -> resolver.getAbsolutePath(input).getFileName().toString())
            .anyMatch(SwiftDescriptions.SWIFT_MAIN_FILENAME::equalsIgnoreCase);

    argBuilder.add(
        "-module-name",
        moduleName,
        "-emit-module",
        "-emit-module-path",
        modulePath.toString(),
        "-emit-objc-header-path",
        headerPath.toString(),
        "-emit-object");

    if (incrementalBuild || incrementalImports) {
      argBuilder.add("-incremental");
      argBuilder.add("-output-file-map", outputFileMapPath.toString());
      argBuilder.add("-enable-batch-mode");
      // Forcing Swift's driver to instantiate only one frontend job not to mess up with BUCK
      // processes.
      argBuilder.add("-driver-batch-count", "1");
      if (incrementalImports) {
        argBuilder.add("-enable-incremental-imports");
      }
    } else {
      argBuilder.add("-wmo");
      // With "-wmo" enabled, it's guaranteed to have one and only one object file per module.
      Path moduleObjectFile = objectPaths.stream().findFirst().get();
      argBuilder.add("-o", moduleObjectFile.toString());
    }

    if (enableCxxInterop) {
      argBuilder.add(frontendFlag, "-enable-cxx-interop");
    }

    if (!hasMainEntry) {
      argBuilder.add("-parse-as-library");
    }

    if (shouldEmitSwiftdocs) {
      argBuilder.add("-emit-module-doc", "-emit-module-doc-path", swiftdocPath.toString());
    }

    version.ifPresent(
        v -> {
          argBuilder.add("-swift-version", validVersionString(v));
        });

    for (Arg f : compilerFlags) {
      argBuilder.add(Arg.stringify(f, resolver));
    }

    for (SourcePath sourcePath : srcs) {
      argBuilder.add(resolver.getCellUnsafeRelPath(sourcePath).toString());
    }

    if (useDebugPrefixMap) {
      // The Swift compiler always adds an implicit -working-directory flag which we need to remap.
      argBuilder.add(DEBUG_PREFIX_MAP_FLAG, getProjectFilesystem().getRootPath().toString() + "=.");

      // We reverse sort the paths by length as we want the longer paths to take precedence over
      // the shorter paths.
      debugPrefixMap.entrySet().stream()
          .sorted(
              Comparator.<Map.Entry<Path, String>>comparingInt(
                      entry -> entry.getKey().getNameCount())
                  .reversed()
                  .thenComparing(entry -> entry.getKey()))
          .forEach(
              entry ->
                  argBuilder.add(
                      DEBUG_PREFIX_MAP_FLAG, entry.getKey().toString() + "=" + entry.getValue()));
    }

    if (!shouldEmitClangModuleBreadcrumbs) {
      // Disable Clang module breadcrumbs in the DWARF info. These will not be debug prefix mapped
      // and are not shareable across machines.
      argBuilder.add(frontendFlag, "-no-clang-module-breadcrumbs");
    }

    if (serializeDebuggingOptions) {
      // We only want to serialize debug info for top level modules. This reduces the amount of
      // work done by the debugger when constructing type contexts and speeds up attach time.
      // Tests will pass this in their constructor args explicitly.
      argBuilder.add(frontendFlag, "-serialize-debugging-options");
    }

    if (prefixSerializedDebugInfo) {
      // Apply path prefixes to swiftmodule debug info to make compiler output cacheable.
      // NOTE: not yet supported in upstream Swift
      argBuilder.add(frontendFlag, "-prefix-serialized-debug-info");
    }

    return argBuilder.build();
  }

  private Iterable<String> xctestImportArgs(SourcePathResolverAdapter resolver) {
    if (platformPath.isPresent()) {
      // XCTest.swiftmodule is in a different path to XCTest.framework which we need to import for
      // Swift specific API
      Path includePath =
          resolver.getIdeallyRelativePath(platformPath.get()).resolve("Developer/usr/lib");
      return ImmutableList.of("-I", includePath.toString());
    } else {
      return ImmutableList.of();
    }
  }

  @VisibleForTesting
  static String validVersionString(String originalVersionString) {
    // Swiftc officially only accepts the major version, but it respects the minor
    // version if the version is 4.2.
    String[] versions = originalVersionString.split("\\.");
    if (versions.length > 2) {
      versions = Arrays.copyOfRange(versions, 0, 2);
    }
    if (versions.length == 2) {
      Integer majorVersion = Integer.parseInt(versions[0]);
      Integer minorVersion = Integer.parseInt(versions[1]);

      if (majorVersion > 4 || (majorVersion >= 4 && minorVersion >= 2)) {
        return String.format("%d.%d", majorVersion, minorVersion);
      } else {
        return originalVersionString.length() > 1
            ? originalVersionString.substring(0, 1)
            : originalVersionString;
      }
    } else {
      return originalVersionString.length() > 1
          ? originalVersionString.substring(0, 1)
          : originalVersionString;
    }
  }

  @Override
  public SortedSet<BuildRule> getBuildDeps() {
    return depsSupplier.get();
  }

  @Override
  public void updateBuildRuleResolver(BuildRuleResolver ruleResolver) {
    this.depsSupplier = BuildableSupport.buildDepsSupplier(this, ruleResolver);
  }

  /** Returns the step that creates a filelist file to be passed to the compiler */
  protected Step makeFileListStep(SourcePathResolverAdapter resolver, AbsPath swiftFileListPath) {
    ImmutableList<String> relativePaths =
        srcs.stream()
            .map(sourcePath -> resolver.getCellUnsafeRelPath(sourcePath).toString())
            .collect(ImmutableList.toImmutableList());

    return new Step() {
      @Override
      public StepExecutionResult execute(StepExecutionContext context) throws IOException {
        if (Files.notExists(swiftFileListPath.getParent().getPath())) {
          Files.createDirectories(swiftFileListPath.getParent().getPath());
        }
        MostFiles.writeLinesToFile(relativePaths, swiftFileListPath);
        return StepExecutionResults.SUCCESS;
      }

      @Override
      public String getShortName() {
        return "swift-filelist";
      }

      @Override
      public String getDescription(StepExecutionContext context) {
        return "swift-filelist";
      }
    };
  }

  /**
   * @return the arguments to add to the preprocessor command line to include the given header packs
   *     in preprocessor search path.
   *     <p>We can't use CxxHeaders.getArgs() because 1. we don't need the system include roots. 2.
   *     swift doesn't like spaces after the "-I" flag.
   */
  @VisibleForTesting
  ImmutableList<String> getSwiftIncludeArgs(SourcePathResolverAdapter resolver) {
    ImmutableList.Builder<String> args = ImmutableList.builder();

    // Arg list can't simply be passed in since the current implementation of toToolFlags drops the
    // dependency information.
    Iterable<Arg> argsFromDeps =
        cxxDeps
            .toToolFlags(
                resolver,
                PathShortener.byRelativizingToWorkingDir(getProjectFilesystem().getRootPath()),
                frameworkPathToSearchPath,
                cPreprocessor,
                Optional.empty())
            .getAllFlags();
    for (Arg arg : argsFromDeps) {
      if (arg instanceof SourcePathArg) {
        // Use relative paths for SourcePathArg, eg the VFS overlay build rule output
        ((SourcePathArg) arg)
            .appendToCommandLineRel(args::add, getBuildTarget().getCell(), resolver, false);
      } else {
        arg.appendToCommandLine(args::add, resolver);
      }
    }

    if (bridgingHeader.isPresent()) {
      for (HeaderVisibility headerVisibility : HeaderVisibility.values()) {
        // We should probably pass in the correct symlink trees instead of guessing.
        RelPath headerPath =
            CxxDescriptionEnhancer.getHeaderSymlinkTreePath(
                getProjectFilesystem(), getBuildTarget().withFlavors(), headerVisibility, flavor);
        args.add(INCLUDE_FLAG.concat(headerPath.toString()));
      }
    }

    return args.build();
  }

  public ImmutableList<Arg> getAstLinkArgs() {
    return ImmutableList.<Arg>builder()
        .addAll(StringArg.from("-Xlinker", "-add_ast_path"))
        .add(StringArg.of("-Xlinker"))
        // NB: The paths to the .swiftmodule files will be relative to the cell, not absolute.
        //     This makes it non-machine specific but if we change the behavior, the OSO
        //     rewriting code needs to adjusted to also fix-up N_AST entries.
        .add(SourcePathArg.of(ExplicitBuildTargetSourcePath.of(getBuildTarget(), modulePath)))
        .build();
  }

  ImmutableList<Arg> getFileListLinkArg() {
    return FileListableLinkerInputArg.from(
        objectPaths.stream()
            .map(
                objectPath ->
                    SourcePathArg.of(
                        ExplicitBuildTargetSourcePath.of(getBuildTarget(), objectPath)))
            .collect(ImmutableList.toImmutableList()));
  }

  /** @return The name of the Swift module. */
  public String getModuleName() {
    return moduleName;
  }

  /** @return List of {@link SourcePath} to the output object file(s) (i.e., .o file) */
  public ImmutableList<SourcePath> getObjectPaths() {
    // Ensures that users of the object path can depend on this build target
    return objectPaths.stream()
        .map(objectPath -> ExplicitBuildTargetSourcePath.of(getBuildTarget(), objectPath))
        .collect(ImmutableList.toImmutableList());
  }

  /** @return File name of the Objective-C Generated Interface Header. */
  public String getObjCGeneratedHeaderFileName() {
    return headerPath.getFileName().toString();
  }

  /** @return {@link SourcePath} of the Objective-C Generated Interface Header. */
  public SourcePath getObjCGeneratedHeaderPath() {
    return ExplicitBuildTargetSourcePath.of(getBuildTarget(), headerPath);
  }

  /**
   * @return {@link SourcePath} to the directory containing outputs from the compilation process
   *     (object files, Swift module metadata, etc).
   */
  public SourcePath getOutputPath() {
    return ExplicitBuildTargetSourcePath.of(getBuildTarget(), outputPath);
  }

  /**
   * @return {@link SourcePath} to the .swiftmodule output from the compilation process. A
   *     swiftmodule file contains the public interface for a module, and is basically a binary file
   *     format equivalent to header files for a C framework or library.
   */
  public SourcePath getSwiftModuleOutputPath() {
    return ExplicitBuildTargetSourcePath.of(getBuildTarget(), modulePath);
  }
}

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

package com.facebook.buck.cxx;

import com.facebook.buck.core.description.arg.BuildRuleArg;
import com.facebook.buck.core.model.BuildTarget;
import com.facebook.buck.core.rules.ActionGraphBuilder;
import com.facebook.buck.core.rules.BuildRule;
import com.facebook.buck.core.rules.BuildRuleCreationContextWithTargetGraph;
import com.facebook.buck.core.rules.BuildRuleParams;
import com.facebook.buck.core.rules.DescriptionWithTargetGraph;
import com.facebook.buck.core.sourcepath.SourcePath;
import com.facebook.buck.core.toolchain.toolprovider.ToolProvider;
import com.facebook.buck.core.toolchain.toolprovider.impl.ToolProviders;
import com.facebook.buck.core.util.immutables.RuleArg;
import com.facebook.buck.cxx.config.CxxBuckConfig;
import com.facebook.buck.cxx.toolchain.ArchiveContents;
import com.facebook.buck.cxx.toolchain.ArchiverProvider;
import com.facebook.buck.cxx.toolchain.CompilerProvider;
import com.facebook.buck.cxx.toolchain.CxxPlatform;
import com.facebook.buck.cxx.toolchain.CxxToolProvider;
import com.facebook.buck.cxx.toolchain.DebugPathSanitizer;
import com.facebook.buck.cxx.toolchain.ElfSharedLibraryInterfaceParams;
import com.facebook.buck.cxx.toolchain.HeaderMode;
import com.facebook.buck.cxx.toolchain.HeaderVerification;
import com.facebook.buck.cxx.toolchain.HeadersAsRawHeadersMode;
import com.facebook.buck.cxx.toolchain.PicType;
import com.facebook.buck.cxx.toolchain.PosixNmSymbolNameTool;
import com.facebook.buck.cxx.toolchain.PrefixMapDebugPathSanitizer;
import com.facebook.buck.cxx.toolchain.PreprocessorProvider;
import com.facebook.buck.cxx.toolchain.SharedLibraryInterfaceParams;
import com.facebook.buck.cxx.toolchain.SharedLibraryInterfaceParams.Type;
import com.facebook.buck.cxx.toolchain.ToolType;
import com.facebook.buck.cxx.toolchain.linker.Linker.LinkableDepType;
import com.facebook.buck.cxx.toolchain.linker.LinkerProvider;
import com.facebook.buck.cxx.toolchain.linker.impl.DefaultLinkerProvider;
import com.facebook.buck.downwardapi.config.DownwardApiConfig;
import com.facebook.buck.rules.args.Arg;
import com.facebook.buck.rules.args.StringArg;
import com.facebook.buck.rules.macros.LocationMacroExpander;
import com.facebook.buck.rules.macros.StringWithMacros;
import com.facebook.buck.rules.macros.StringWithMacrosConverter;
import com.facebook.buck.util.environment.Platform;
import com.google.common.collect.ImmutableBiMap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Multimaps;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Optional;
import org.immutables.value.Value;

/**
 * Defines a cxx_toolchain rule that allows a {@link CxxPlatform} to be configured as a build
 * target.
 */
public class CxxToolchainDescription
    implements DescriptionWithTargetGraph<CxxToolchainDescriptionArg> {

  private final DownwardApiConfig downwardApiConfig;
  private final CxxBuckConfig cxxBuckConfig;

  public CxxToolchainDescription(DownwardApiConfig downwardApiConfig, CxxBuckConfig cxxBuckConfig) {
    this.downwardApiConfig = downwardApiConfig;
    this.cxxBuckConfig = cxxBuckConfig;
  }

  @Override
  public BuildRule createBuildRule(
      BuildRuleCreationContextWithTargetGraph context,
      BuildTarget buildTarget,
      BuildRuleParams params,
      CxxToolchainDescriptionArg args) {
    ActionGraphBuilder ruleResolver = context.getActionGraphBuilder();

    CxxPlatform.Builder cxxPlatform = CxxPlatform.builder();

    if (args.getUseHeaderMap()) {
      if (args.getPrivateHeadersSymlinksEnabled() || args.getPublicHeadersSymlinksEnabled()) {
        cxxPlatform.setHeaderMode(HeaderMode.SYMLINK_TREE_WITH_HEADER_MAP);
      } else {
        cxxPlatform.setHeaderMode(HeaderMode.HEADER_MAP_ONLY);
      }
    } else {
      cxxPlatform.setHeaderMode(HeaderMode.SYMLINK_TREE_ONLY);
    }

    // TODO(cjhopman): How to handle this?
    cxxPlatform.setFlagMacros(ImmutableMap.of());

    // Below here are all the things that we actually support configuration of. They mostly match
    // CxxPlatform, but (1) are in some cases more restrictive and (2) use more descriptive names.
    cxxPlatform.setPrivateHeadersSymlinksEnabled(args.getPrivateHeadersSymlinksEnabled());
    cxxPlatform.setPublicHeadersSymlinksEnabled(args.getPublicHeadersSymlinksEnabled());

    cxxPlatform.setUseArgFile(args.getUseArgFile());

    cxxPlatform.setSharedLibraryExtension(args.getSharedLibraryExtension());
    cxxPlatform.setSharedLibraryVersionedExtensionFormat(
        args.getSharedLibraryVersionedExtensionFormat());
    cxxPlatform.setStaticLibraryExtension(args.getStaticLibraryExtension());
    cxxPlatform.setObjectFileExtension(args.getObjectFileExtension());
    cxxPlatform.setBinaryExtension(args.getBinaryExtension());

    LinkerProvider.Type linkerType = args.getLinkerType();
    // This should be the same for all the tools that use it.
    final boolean preferDependencyTree = args.getDetailedUntrackedHeaderMessages();

    StringWithMacrosConverter macrosConverter =
        StringWithMacrosConverter.of(
            buildTarget,
            context.getCellPathResolver().getCellNameResolver(),
            ruleResolver,
            ImmutableList.of(LocationMacroExpander.INSTANCE),
            Optional.empty());
    cxxPlatform.setAs(
        new CompilerProvider(
            getToolProvider(args.getAssembler()),
            args.getAssemblerType(),
            ToolType.AS,
            preferDependencyTree));
    cxxPlatform.setAsflags(
        args.getAssemblerFlags().stream()
            .map(macrosConverter::convert)
            .collect(ImmutableList.toImmutableList()));

    cxxPlatform.setAspp(
        new PreprocessorProvider(
            getToolProvider(args.getAssemblerPreprocessor()),
            args.getAssemblerPreprocessorType(),
            ToolType.ASPP));
    cxxPlatform.setAsppflags(
        args.getAssemblerPreprocessorFlags().stream()
            .map(macrosConverter::convert)
            .collect(ImmutableList.toImmutableList()));

    cxxPlatform.setCc(
        new CompilerProvider(
            getToolProvider(args.getCCompiler()),
            args.getCCompilerType(),
            ToolType.CC,
            preferDependencyTree));
    cxxPlatform.setCflags(
        args.getCCompilerFlags().stream()
            .map(macrosConverter::convert)
            .collect(ImmutableList.toImmutableList()));

    cxxPlatform.setCxx(
        new CompilerProvider(
            getToolProvider(args.getCxxCompiler()),
            args.getCxxCompilerType(),
            ToolType.CXX,
            preferDependencyTree));
    cxxPlatform.setCxxflags(
        args.getCxxCompilerFlags().stream()
            .map(macrosConverter::convert)
            .collect(ImmutableList.toImmutableList()));

    cxxPlatform.setCpp(
        new PreprocessorProvider(
            getToolProvider(args.getCCompiler()), args.getCCompilerType(), ToolType.CPP));
    cxxPlatform.setCppflags(
        args.getCPreprocessorFlags().stream()
            .map(macrosConverter::convert)
            .collect(ImmutableList.toImmutableList()));

    cxxPlatform.setCxxpp(
        new PreprocessorProvider(
            getToolProvider(args.getCxxCompiler()), args.getCxxCompilerType(), ToolType.CXXPP));
    cxxPlatform.setCxxppflags(
        args.getCxxPreprocessorFlags().stream()
            .map(macrosConverter::convert)
            .collect(ImmutableList.toImmutableList()));

    // ASM
    args.getAsmCompiler()
        .ifPresent(
            compiler -> {
              cxxPlatform.setAsmpp(
                  new PreprocessorProvider(
                      getToolProvider(args.getAsmPreprocessor().orElse(compiler)),
                      args.getAsmPreprocessorType(),
                      ToolType.ASMPP));
              cxxPlatform.setAsm(
                  new CompilerProvider(
                      getToolProvider(compiler),
                      args.getAsmCompilerType(),
                      ToolType.ASM,
                      preferDependencyTree));
            });
    cxxPlatform.setAsmppflags(
        args.getAsmPreprocessorFlags().stream()
            .map(macrosConverter::convert)
            .collect(ImmutableList.toImmutableList()));
    cxxPlatform.setAsmflags(
        args.getAsmCompilerFlags().stream()
            .map(macrosConverter::convert)
            .collect(ImmutableList.toImmutableList()));

    // CUDA
    args.getCudaCompiler()
        .ifPresent(
            compiler -> {
              cxxPlatform.setCudapp(
                  new PreprocessorProvider(
                      getToolProvider(compiler), args.getCudaCompilerType(), ToolType.CUDAPP));
              cxxPlatform.setCuda(
                  new CompilerProvider(
                      getToolProvider(compiler),
                      args.getCudaCompilerType(),
                      ToolType.CUDA,
                      preferDependencyTree));
            });
    cxxPlatform.setCudappflags(
        args.getCudaPreprocessorFlags().stream()
            .map(macrosConverter::convert)
            .collect(ImmutableList.toImmutableList()));
    cxxPlatform.setCudaflags(
        args.getCudaCompilerFlags().stream()
            .map(macrosConverter::convert)
            .collect(ImmutableList.toImmutableList()));

    // HIP
    args.getHipCompiler()
        .ifPresent(
            compiler -> {
              cxxPlatform.setHippp(
                  new PreprocessorProvider(
                      getToolProvider(compiler), args.getHipCompilerType(), ToolType.HIPPP));
              cxxPlatform.setHip(
                  new CompilerProvider(
                      getToolProvider(compiler),
                      args.getHipCompilerType(),
                      ToolType.HIP,
                      preferDependencyTree));
            });
    cxxPlatform.setHipppflags(
        args.getHipPreprocessorFlags().stream()
            .map(macrosConverter::convert)
            .collect(ImmutableList.toImmutableList()));
    cxxPlatform.setHipflags(
        args.getHipCompilerFlags().stream()
            .map(macrosConverter::convert)
            .collect(ImmutableList.toImmutableList()));

    boolean scrubConcurrently = false;
    cxxPlatform.setLd(
        new DefaultLinkerProvider(
            linkerType,
            getToolProvider(args.getLinker()),
            args.getCacheLinks(),
            cxxBuckConfig.shouldUploadToCache(),
            cxxBuckConfig.getFocusedDebuggingEnabled(),
            scrubConcurrently,
            args.getLinkPathNormalizationArgsEnabled()));

    if (linkerType == LinkerProvider.Type.GNU) {
      cxxPlatform.setLdflags(
          ImmutableList.<Arg>builder()
              // Add a deterministic build ID.
              .add(StringArg.of("-Wl,--build-id"))
              .addAll(
                  args.getLinkerFlags().stream()
                      .map(macrosConverter::convert)
                      .collect(ImmutableList.toImmutableList()))
              .build());
    } else {
      // TODO(cjhopman): We should force build ids by default for all linkers.
      cxxPlatform.setLdflags(
          args.getLinkerFlags().stream()
              .map(macrosConverter::convert)
              .collect(ImmutableList.toImmutableList()));
    }

    cxxPlatform.setAr(
        ArchiverProvider.from(getToolProvider(args.getArchiver()), args.getArchiverType()));
    cxxPlatform.setArflags(
        args.getArchiverFlags().stream()
            .map(macrosConverter::convert)
            .collect(ImmutableList.toImmutableList()));
    cxxPlatform.setArchiveContents(args.getArchiveContents());
    cxxPlatform.setRequiresArchives(args.getRequiresArchives());

    cxxPlatform.setStrip(getToolProvider(args.getStrip()));
    args.getStripDebugFlags()
        .ifPresent(
            flags ->
                cxxPlatform.setStripDebugFlags(
                    flags.stream()
                        .map(macrosConverter::convert)
                        .collect(ImmutableList.toImmutableList())));
    args.getStripNonGlobalFlags()
        .ifPresent(
            flags ->
                cxxPlatform.setStripNonGlobalFlags(
                    flags.stream()
                        .map(macrosConverter::convert)
                        .collect(ImmutableList.toImmutableList())));
    args.getStripAllFlags()
        .ifPresent(
            flags ->
                cxxPlatform.setStripAllFlags(
                    flags.stream()
                        .map(macrosConverter::convert)
                        .collect(ImmutableList.toImmutableList())));

    cxxPlatform.setRanlib(args.getRanlib().map(ToolProviders::getToolProvider));
    cxxPlatform.setRanlibflags(
        args.getRanlibFlags().stream()
            .map(macrosConverter::convert)
            .collect(ImmutableList.toImmutableList()));

    ListMultimap<LinkableDepType, Arg> runtimeLdFlags =
        Multimaps.newListMultimap(new LinkedHashMap<>(), ArrayList::new);
    runtimeLdFlags.putAll(
        LinkableDepType.STATIC,
        args.getStaticDepRuntimeLdFlags().stream()
            .map(macrosConverter::convert)
            .collect(ImmutableList.toImmutableList()));
    runtimeLdFlags.putAll(
        LinkableDepType.STATIC_PIC,
        args.getStaticPicDepRuntimeLdFlags().stream()
            .map(macrosConverter::convert)
            .collect(ImmutableList.toImmutableList()));
    runtimeLdFlags.putAll(
        LinkableDepType.SHARED,
        args.getSharedDepRuntimeLdFlags().stream()
            .map(macrosConverter::convert)
            .collect(ImmutableList.toImmutableList()));
    cxxPlatform.setRuntimeLdflags(runtimeLdFlags);

    cxxPlatform.setPicTypeForSharedLinking(args.getPicTypeForSharedLinking());

    cxxPlatform.setSymbolNameTool(
        new PosixNmSymbolNameTool(
            getToolProvider(args.getNm()), downwardApiConfig.isEnabledForCxx()));

    // User-configured cxx platforms are required to handle path sanitization themselves.
    cxxPlatform.setCompilerDebugPathSanitizer(
        args.getDebugPathPrefixMapSanitizerFormat()
            .<DebugPathSanitizer>map(
                format -> new PrefixMapDebugPathSanitizer(".", ImmutableBiMap.of(), false, format))
            .orElse(NoopDebugPathSanitizer.INSTANCE));

    // We require that untracked headers are errors.
    cxxPlatform.setHeaderVerification(
        HeaderVerification.of(
            HeaderVerification.Mode.ERROR,
            ImmutableSortedSet.of(),
            // Ideally we don't allow any whitelisting (the user-configured platform can implement
            // its own filtering of the produced depfiles), but currently we are relaxing this
            // restriction.
            ImmutableSortedSet.copyOf(args.getHeadersWhitelist())));

    SharedLibraryInterfaceParams.Type sharedLibraryInterfaceType =
        args.getSharedLibraryInterfaceType();
    // TODO(cjhopman): We should change this to force users to implement all of the shared library
    // interface logic.
    if (sharedLibraryInterfaceType == Type.DISABLED) {
      cxxPlatform.setSharedLibraryInterfaceParams(Optional.empty());
    } else {
      cxxPlatform.setSharedLibraryInterfaceParams(
          ElfSharedLibraryInterfaceParams.of(
              getToolProvider(args.getObjcopyForSharedLibraryInterface()),
              args.getSharedLibraryInterfaceFlags(),
              sharedLibraryInterfaceType == Type.DEFINED_ONLY,
              args.getObjcopyRecalculatesLayout()));
    }

    // TODO(cjhopman): Is this reasonable?
    cxxPlatform.setConflictingHeaderBasenameWhitelist(
        args.getConflictingHeaderBasenameExemptions());

    cxxPlatform.setFilepathLengthLimited(args.getFilepathLengthLimited());

    cxxPlatform.setHeadersAsRawHeadersMode(args.getHeadersAsRawHeadersMode());
    cxxPlatform.setDetailedUntrackedHeaderMessages(args.getDetailedUntrackedHeaderMessages());

    return new CxxToolchainBuildRule(buildTarget, context, cxxPlatform);
  }

  private ToolProvider getToolProvider(SourcePath path) {
    // Only enable RE for tools that will execute on Linux
    return ToolProviders.getToolProvider(path, Platform.detect() == Platform.LINUX);
  }

  @Override
  public boolean producesCacheableSubgraph() {
    return true;
  }

  @Override
  public Class<CxxToolchainDescriptionArg> getConstructorArgType() {
    return CxxToolchainDescriptionArg.class;
  }

  /**
   * This is roughly analagous to the configuration provided by {@link CxxBuckConfig}. Some things
   * are not yet exposed/implemented, and others have been slightly renamed or exposed slightly
   * differently to be more restricted or more descriptive or more maintainable.
   */
  @RuleArg
  interface AbstractCxxToolchainDescriptionArg extends BuildRuleArg {
    /** When building or creating a project, create symlinks for the public headers if it's true. */
    @Value.Default
    default boolean getPrivateHeadersSymlinksEnabled() {
      return true;
    }

    /**
     * When building or creating a project, create symlinks for the public headers if it's true. It
     * would allow public headers to include an other public header with #include "foobar.h"\ even
     * if it's not in the same folder.
     */
    @Value.Default
    default boolean getPublicHeadersSymlinksEnabled() {
      return true;
    }

    /** Whether to use an argfile for long command lines. */
    @Value.Default
    default boolean getUseArgFile() {
      return true;
    }

    @Value.Default
    default boolean getCacheLinks() {
      return true;
    }

    @Value.Default
    default boolean getLinkPathNormalizationArgsEnabled() {
      return false;
    }

    /** Extension of shared library files. */
    String getSharedLibraryExtension();

    /** Extension format for versioned shared libraries. */
    // TODO(agallagher): Improve documentation.
    String getSharedLibraryVersionedExtensionFormat();

    /** Extension for static library files. */
    String getStaticLibraryExtension();

    /** Extension for object files. */
    String getObjectFileExtension();

    /** Extension for binary files. */
    Optional<String> getBinaryExtension();

    /** Default {@link CxxToolProvider.Type} of compiler binaries. */
    CxxToolProvider.Type getCompilerType();

    /** Assembler binary. */
    SourcePath getAssembler();

    /** {@link CxxToolProvider.Type} of the assembler. */
    @Value.Default
    default CxxToolProvider.Type getAssemblerType() {
      return getCompilerType();
    }

    /** Flags for the assembler. */
    ImmutableList<StringWithMacros> getAssemblerFlags();

    /** Assembler binary. */
    @Value.Default
    default SourcePath getAssemblerPreprocessor() {
      return getAssembler();
    }

    /** {@link CxxToolProvider.Type} of the assembler. */
    @Value.Default
    default CxxToolProvider.Type getAssemblerPreprocessorType() {
      return getAssemblerType();
    }

    /** Flags for the assembler preprocessor. */
    ImmutableList<StringWithMacros> getAssemblerPreprocessorFlags();

    /** C compiler binary. */
    SourcePath getCCompiler();

    /** {@link CxxToolProvider.Type} of C compiler. */
    @Value.Default
    default CxxToolProvider.Type getCCompilerType() {
      return getCompilerType();
    }

    /** C compiler flags. */
    ImmutableList<StringWithMacros> getCCompilerFlags();

    /** C preprocessor flags. */
    ImmutableList<StringWithMacros> getCPreprocessorFlags();

    /** C++ compiler binary. */
    SourcePath getCxxCompiler();

    /** {@link CxxToolProvider.Type} of C++ compiler. */
    @Value.Default
    default CxxToolProvider.Type getCxxCompilerType() {
      return getCompilerType();
    }

    /** C++ compiler flags. */
    ImmutableList<StringWithMacros> getCxxCompilerFlags();

    /** C++ preprocessor flags. */
    ImmutableList<StringWithMacros> getCxxPreprocessorFlags();

    /** ASM compiler binary. */
    Optional<SourcePath> getAsmCompiler();

    /** {@link CxxToolProvider.Type} of ASM compiler. */
    @Value.Default
    default CxxToolProvider.Type getAsmCompilerType() {
      return getCompilerType();
    }

    /** ASM compiler flags. */
    ImmutableList<StringWithMacros> getAsmCompilerFlags();

    /** ASM compiler binary. */
    Optional<SourcePath> getAsmPreprocessor();

    /** {@link CxxToolProvider.Type} of ASM compiler. */
    @Value.Default
    default CxxToolProvider.Type getAsmPreprocessorType() {
      return getAsmCompilerType();
    }

    /** ASM preprocessor flags. */
    ImmutableList<StringWithMacros> getAsmPreprocessorFlags();

    /** CUDA compiler binary. */
    Optional<SourcePath> getCudaCompiler();

    /** {@link CxxToolProvider.Type} of CUDA compiler. */
    @Value.Default
    default CxxToolProvider.Type getCudaCompilerType() {
      return getCompilerType();
    }

    /** CUDA compiler flags. */
    ImmutableList<StringWithMacros> getCudaCompilerFlags();

    /** CUDA preprocessor flags. */
    ImmutableList<StringWithMacros> getCudaPreprocessorFlags();

    /** HIP compiler binary. */
    Optional<SourcePath> getHipCompiler();

    /** {@link CxxToolProvider.Type} of HIP compiler. */
    @Value.Default
    default CxxToolProvider.Type getHipCompilerType() {
      return getCompilerType();
    }

    /** HIP compiler flags. */
    ImmutableList<StringWithMacros> getHipCompilerFlags();

    /** HIP preprocessor flags. */
    ImmutableList<StringWithMacros> getHipPreprocessorFlags();

    /** Linker binary. */
    SourcePath getLinker();

    /** {@link LinkerProvider.Type} of the linker. */
    LinkerProvider.Type getLinkerType();

    /** Linker flags. */
    ImmutableList<StringWithMacros> getLinkerFlags();

    /** Archiver binary. */
    SourcePath getArchiver();

    /** Archiver flags. */
    ImmutableList<StringWithMacros> getArchiverFlags();

    /** The type of archives (e.g. thin v. normal) to use. */
    @Value.Default
    default ArchiveContents getArchiveContents() {
      return ArchiveContents.NORMAL;
    }

    /**
     * Whether this platform should always use archives to package object files (e.g. as opposed to
     * wrapping in `--start-lib`/`--end-lib` flags).
     */
    @Value.Default
    default boolean getRequiresArchives() {
      return true;
    }

    /** {@link ArchiverProvider.Type} of the archiver. */
    ArchiverProvider.Type getArchiverType();

    /** Strip binary. */
    SourcePath getStrip();

    /** The flags to use when applying DEBUGGING_SYMBOLS strip style. */
    Optional<ImmutableList<StringWithMacros>> getStripDebugFlags();

    /** The flags to use when applying NON_GLOBAL_SYMBOLS strip style. */
    Optional<ImmutableList<StringWithMacros>> getStripNonGlobalFlags();

    /** The flags to use when applying ALL_SYMBOLS strip style. */
    Optional<ImmutableList<StringWithMacros>> getStripAllFlags();

    /** Ranlib binary. */
    Optional<SourcePath> getRanlib();

    /** Ranlib flags. */
    ImmutableList<StringWithMacros> getRanlibFlags();

    /** Flags for linking the c/c++ runtime for static libraries. */
    ImmutableList<StringWithMacros> getStaticDepRuntimeLdFlags();

    /** Flags for linking the c/c++ runtime for static-pic libraries. */
    ImmutableList<StringWithMacros> getStaticPicDepRuntimeLdFlags();

    /** Flags for linking the c/c++ runtime for shared libraries. */
    ImmutableList<StringWithMacros> getSharedDepRuntimeLdFlags();

    @Value.Default
    default PicType getPicTypeForSharedLinking() {
      return PicType.PIC;
    }

    /** nm binary. */
    SourcePath getNm();

    /** Type of shared library interfaces to create. */
    SharedLibraryInterfaceParams.Type getSharedLibraryInterfaceType();

    /** Objcopy binary to use for creating shared library interfaces. */
    SourcePath getObjcopyForSharedLibraryInterface();

    /** Linker flags to use when linking independent shared library interfaces. */
    ImmutableList<String> getSharedLibraryInterfaceFlags();

    /** Whether objcopy automatically recalculates binary layout. */
    @Value.Default
    default boolean getObjcopyRecalculatesLayout() {
      return false;
    }

    /** Whether to use header maps. */
    boolean getUseHeaderMap();

    /** Spit out more information when there are untracked headers */
    @Value.Default
    default boolean getDetailedUntrackedHeaderMessages() {
      return false;
    }

    /** Whether to use shorter intermediate files. */
    @Value.Default
    default boolean getFilepathLengthLimited() {
      return false;
    }

    /**
     * A list of regexes which match headers (belonging to the toolchain) to exempt from untracked
     * header verification.
     */
    ImmutableList<String> getHeadersWhitelist();

    /** A list of basenames to exempt from header conflict checking. */
    @Value.Default
    default ImmutableSortedSet<String> getConflictingHeaderBasenameExemptions() {
      return ImmutableSortedSet.of();
    }

    /** Format to use for debug prefix map path sanitization. */
    Optional<String> getDebugPathPrefixMapSanitizerFormat();

    Optional<HeadersAsRawHeadersMode> getHeadersAsRawHeadersMode();
  }
}

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

import com.facebook.buck.core.build.context.BuildContext;
import com.facebook.buck.core.filesystems.AbsPath;
import com.facebook.buck.core.filesystems.RelPath;
import com.facebook.buck.core.model.BuildTarget;
import com.facebook.buck.core.rulekey.AddToRuleKey;
import com.facebook.buck.core.rules.SourcePathRuleFinder;
import com.facebook.buck.core.sourcepath.SourcePath;
import com.facebook.buck.core.sourcepath.resolver.SourcePathResolverAdapter;
import com.facebook.buck.core.toolchain.tool.Tool;
import com.facebook.buck.cxx.ElfSharedLibraryInterface.AbstractBuildable;
import com.facebook.buck.cxx.toolchain.elf.ElfDynamicSection;
import com.facebook.buck.cxx.toolchain.linker.Linker;
import com.facebook.buck.cxx.toolchain.nativelink.NativeLinkTargetGroup;
import com.facebook.buck.io.filesystem.ProjectFilesystem;
import com.facebook.buck.io.filesystem.impl.ProjectFilesystemUtils;
import com.facebook.buck.rules.args.Arg;
import com.facebook.buck.rules.modern.BuildCellRelativePathFactory;
import com.facebook.buck.rules.modern.Buildable;
import com.facebook.buck.rules.modern.ModernBuildRule;
import com.facebook.buck.rules.modern.OutputPath;
import com.facebook.buck.rules.modern.OutputPathResolver;
import com.facebook.buck.step.Step;
import com.facebook.buck.step.fs.MakeWritableStep;
import com.facebook.buck.util.types.Pair;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableList.Builder;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import java.nio.file.Path;
import java.util.Optional;

/** Build a shared library interface from an ELF shared library. */
class ElfSharedLibraryInterface<T extends AbstractBuildable> extends ModernBuildRule<T> {

  private ElfSharedLibraryInterface(
      BuildTarget buildTarget,
      ProjectFilesystem projectFilesystem,
      SourcePathRuleFinder ruleFinder,
      T buildable) {
    super(buildTarget, projectFilesystem, ruleFinder, buildable);
  }

  /** @return a {@link ElfSharedLibraryInterface} distilled from an existing shared library. */
  public static ElfSharedLibraryInterface<ExistingBasedElfSharedLibraryImpl> from(
      BuildTarget target,
      ProjectFilesystem projectFilesystem,
      SourcePathRuleFinder ruleFinder,
      Tool objcopy,
      SourcePath input,
      boolean removeUndefinedSymbols,
      boolean objcopyRecalculatesLayout,
      boolean withDownwardApi) {
    String libName =
        ruleFinder.getSourcePathResolver().getCellUnsafeRelPath(input).getFileName().toString();
    return new ElfSharedLibraryInterface<>(
        target,
        projectFilesystem,
        ruleFinder,
        new ExistingBasedElfSharedLibraryImpl(
            target,
            objcopy,
            libName,
            removeUndefinedSymbols,
            objcopyRecalculatesLayout,
            input,
            withDownwardApi));
  }

  /**
   * @return a {@link ElfSharedLibraryInterface} built for the library represented by {@link
   *     NativeLinkTargetGroup}.
   */
  public static ElfSharedLibraryInterface<LinkerBasedElfSharedLibraryImpl> from(
      BuildTarget target,
      ProjectFilesystem projectFilesystem,
      SourcePathRuleFinder ruleFinder,
      Tool objcopy,
      String libName,
      Linker linker,
      ImmutableList<Arg> args,
      boolean removeUndefinedSymbols,
      boolean objcopyRecalculatesLayout,
      boolean withDownwardApi) {
    return new ElfSharedLibraryInterface<>(
        target,
        projectFilesystem,
        ruleFinder,
        new LinkerBasedElfSharedLibraryImpl(
            target,
            objcopy,
            libName,
            removeUndefinedSymbols,
            objcopyRecalculatesLayout,
            linker,
            args,
            withDownwardApi));
  }

  /**
   * Internal ElfSharedLibrary specific abstract class with general implementation for Buildable
   * interface
   */
  abstract static class AbstractBuildable implements Buildable {

    @AddToRuleKey protected final BuildTarget buildTarget;
    @AddToRuleKey private final Tool objcopy;
    @AddToRuleKey private final OutputPath outputPath;
    @AddToRuleKey protected final String libName;
    @AddToRuleKey private final boolean removeUndefinedSymbols;
    @AddToRuleKey private final boolean objcopyRecalculatesLayout;
    @AddToRuleKey protected final boolean withDownwardApi;

    private AbstractBuildable(
        BuildTarget buildTarget,
        Tool objcopy,
        String libName,
        boolean removeUndefinedSymbols,
        boolean objcopyRecalculatesLayout,
        boolean withDownwardApi) {
      this.buildTarget = buildTarget;
      this.objcopy = objcopy;
      this.libName = libName;
      this.outputPath = new OutputPath(libName);
      this.removeUndefinedSymbols = removeUndefinedSymbols;
      this.objcopyRecalculatesLayout = objcopyRecalculatesLayout;
      this.withDownwardApi = withDownwardApi;
    }

    @Override
    public ImmutableList<Step> getBuildSteps(
        BuildContext buildContext,
        ProjectFilesystem filesystem,
        OutputPathResolver outputPathResolver,
        BuildCellRelativePathFactory buildCellPathFactory) {
      RelPath output = outputPathResolver.resolvePath(outputPath);
      RelPath outputDir = outputPathResolver.getRootPath();
      RelPath outputScratch = outputPathResolver.resolvePath(new OutputPath(libName + ".scratch"));
      ImmutableList.Builder<Step> steps = ImmutableList.builder();

      SourcePathResolverAdapter sourcePathResolverAdapter = buildContext.getSourcePathResolver();
      ImmutableList<String> commandPrefix = objcopy.getCommandPrefix(sourcePathResolverAdapter);
      Pair<ProjectFilesystem, Path> input = getInput(buildContext, filesystem, outputDir, steps);
      steps.add(
          new ElfExtractSectionsStep(
              commandPrefix,
              getSections(),
              input.getFirst(),
              input.getSecond(),
              filesystem,
              outputScratch.getPath(),
              ProjectFilesystemUtils.relativize(
                  filesystem.getRootPath(), buildContext.getBuildCellRootPath()),
              withDownwardApi),
          new MakeWritableStep(filesystem, outputScratch.getPath()),
          ImmutableElfSymbolTableScrubberStep.ofImpl(
              filesystem,
              outputScratch.getPath(),
              /* section */ ".dynsym",
              /* versymSection */ Optional.of(".gnu.version"),
              /* allowMissing */ false,
              /* scrubUndefinedSymbols */ removeUndefinedSymbols),
          ImmutableElfSymbolTableScrubberStep.ofImpl(
              filesystem,
              outputScratch.getPath(),
              /* section */ ".symtab",
              /* versymSection */ Optional.empty(),
              /* allowMissing */ true,
              /* scrubUndefinedSymbols */ true),
          ImmutableElfDynamicSectionScrubberStep.ofImpl(
              filesystem,
              outputScratch.getPath(),
              // When scrubbing undefined symbols, drop the `DT_NEEDED` tags from the whitelist,
              // as these leak information about undefined references in the shared library.
              /* whitelistedTags */ removeUndefinedSymbols
                  ? ImmutableSet.of(ElfDynamicSection.DTag.DT_SONAME)
                  : ImmutableSet.of(
                      ElfDynamicSection.DTag.DT_NEEDED, ElfDynamicSection.DTag.DT_SONAME),
              /* removeScrubbedTags */ removeUndefinedSymbols),
          ImmutableElfScrubFileHeaderStep.ofImpl(filesystem, outputScratch.getPath()));
      // If we're removing undefined symbols, rewrite the dynamic string table so that strings for
      // undefined symbol names are removed.
      if (removeUndefinedSymbols) {
        steps.add(ImmutableElfRewriteDynStrSectionStep.ofImpl(filesystem, outputScratch.getPath()));
      }
      steps.add(
          // objcopy doesn't like the section-address shuffling chicanery we're doing in
          // the ElfCompactSectionsStep, since the new addresses may not jive with the current
          // segment locations.  So kill the segments (program headers) in the scratch file
          // prior to compacting sections, and _again_ in the interface .so file.
          ImmutableElfClearProgramHeadersStep.ofImpl(filesystem, outputScratch.getPath()),
          ImmutableElfCompactSectionsStep.ofImpl(
              buildTarget,
              commandPrefix,
              filesystem,
              outputScratch.getPath(),
              filesystem,
              output.getPath(),
              objcopyRecalculatesLayout,
              withDownwardApi),
          ImmutableElfClearProgramHeadersStep.ofImpl(filesystem, output.getPath()));
      return steps.build();
    }

    // We only care about sections relevant to dynamic linking.
    private ImmutableSet<String> getSections() {
      ImmutableSet.Builder<String> sections = ImmutableSet.builder();
      sections.add(".dynamic", ".dynsym", ".dynstr", ".gnu.version", ".gnu.version_d");
      // The `.gnu.version_r` contains version information about undefined symbols, and so is only
      // relevant if we're not removing undefined symbols.
      if (!removeUndefinedSymbols) {
        sections.add(".gnu.version_r");
      }
      return sections.build();
    }

    /**
     * @return add any necessary steps to generate the input shared library we'll use to generate
     *     the interface and return it's path.
     */
    protected abstract Pair<ProjectFilesystem, Path> getInput(
        BuildContext context,
        ProjectFilesystem filesystem,
        RelPath outputPath,
        Builder<Step> steps);
  }

  private static class ExistingBasedElfSharedLibraryImpl extends AbstractBuildable {

    @AddToRuleKey private final SourcePath input;

    ExistingBasedElfSharedLibraryImpl(
        BuildTarget buildTarget,
        Tool objcopy,
        String libName,
        boolean removeUndefinedSymbols,
        boolean objcopyRecalculatesLayout,
        SourcePath input,
        boolean withDownwardApi) {
      super(
          buildTarget,
          objcopy,
          libName,
          removeUndefinedSymbols,
          objcopyRecalculatesLayout,
          withDownwardApi);
      this.input = input;
    }

    @Override
    protected Pair<ProjectFilesystem, Path> getInput(
        BuildContext context,
        ProjectFilesystem filesystem,
        RelPath outputPath,
        Builder<Step> steps) {
      SourcePathResolverAdapter sourcePathResolverAdapter = context.getSourcePathResolver();
      return new Pair<>(
          sourcePathResolverAdapter.getFilesystem(input),
          sourcePathResolverAdapter.getCellUnsafeRelPath(input).getPath());
    }
  }

  private static class LinkerBasedElfSharedLibraryImpl extends AbstractBuildable {

    @AddToRuleKey private final Linker linker;
    @AddToRuleKey private final ImmutableList<Arg> args;

    LinkerBasedElfSharedLibraryImpl(
        BuildTarget buildTarget,
        Tool objcopy,
        String libName,
        boolean removeUndefinedSymbols,
        boolean objcopyRecalculatesLayout,
        Linker linker,
        ImmutableList<Arg> args,
        boolean withDownwardApi) {
      super(
          buildTarget,
          objcopy,
          libName,
          removeUndefinedSymbols,
          objcopyRecalculatesLayout,
          withDownwardApi);
      this.linker = linker;
      this.args = args;
    }

    @Override
    protected Pair<ProjectFilesystem, Path> getInput(
        BuildContext context,
        ProjectFilesystem filesystem,
        RelPath outputPath,
        Builder<Step> steps) {
      String shortNameAndFlavorPostfix = buildTarget.getShortNameAndFlavorPostfix();
      AbsPath outputDirPath = filesystem.getRootPath().resolve(outputPath);

      AbsPath argFilePath =
          outputDirPath.resolve(String.format("%s.argsfile", shortNameAndFlavorPostfix));
      AbsPath fileListPath =
          outputDirPath.resolve(String.format("%s__filelist.txt", shortNameAndFlavorPostfix));
      Path output = outputPath.resolve(libName);
      SourcePathResolverAdapter sourcePathResolverAdapter = context.getSourcePathResolver();
      steps
          .addAll(
              CxxPrepareForLinkStep.create(
                  argFilePath.getPath(),
                  fileListPath.getPath(),
                  linker.fileList(fileListPath),
                  output,
                  args,
                  linker,
                  buildTarget.getCell(),
                  filesystem.getRootPath().getPath(),
                  sourcePathResolverAdapter,
                  ImmutableMap.of(),
                  ImmutableList.of()))
          .add(
              new CxxLinkStep(
                  filesystem.getRootPath(),
                  ProjectFilesystemUtils.relativize(
                      filesystem.getRootPath(), context.getBuildCellRootPath()),
                  linker.getEnvironment(sourcePathResolverAdapter),
                  linker.getCommandPrefix(sourcePathResolverAdapter),
                  argFilePath.getPath(),
                  outputDirPath.getPath(),
                  withDownwardApi));
      return new Pair<>(filesystem, output);
    }
  }

  @Override
  public SourcePath getSourcePathToOutput() {
    AbstractBuildable buildable = getBuildable();
    return getSourcePath(buildable.outputPath);
  }
}

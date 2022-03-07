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

import com.facebook.buck.core.build.buildable.context.BuildableContext;
import com.facebook.buck.core.build.context.BuildContext;
import com.facebook.buck.core.build.execution.context.StepExecutionContext;
import com.facebook.buck.core.filesystems.RelPath;
import com.facebook.buck.core.model.BuildTarget;
import com.facebook.buck.core.model.impl.BuildTargetPaths;
import com.facebook.buck.core.rules.BuildRule;
import com.facebook.buck.core.rules.BuildRuleParams;
import com.facebook.buck.core.rules.impl.AbstractBuildRuleWithDeclaredAndExtraDeps;
import com.facebook.buck.core.sourcepath.ExplicitBuildTargetSourcePath;
import com.facebook.buck.core.sourcepath.SourcePath;
import com.facebook.buck.io.filesystem.BuildCellRelativePath;
import com.facebook.buck.io.filesystem.ProjectFilesystem;
import com.facebook.buck.io.pathformat.PathFormatter;
import com.facebook.buck.step.Step;
import com.facebook.buck.step.StepExecutionResult;
import com.facebook.buck.step.StepExecutionResults;
import com.facebook.buck.step.fs.MakeCleanDirectoryStep;
import com.facebook.buck.util.zip.CustomZipOutputStream;
import com.facebook.buck.util.zip.ZipOutputStreams;
import com.facebook.buck.zip.ZipScrubberStep;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;

/** Rule for trimming unnecessary ids from R.java files. */
class TrimUberRDotJava extends AbstractBuildRuleWithDeclaredAndExtraDeps {
  /** Interface for build rules that keep track of referenced Android resources */
  interface UsesResources extends BuildRule {
    ImmutableList<String> getReferencedResources();
  }
  /**
   * If the app has resources, aapt will have generated an R.java in this directory. If there are no
   * resources, this should be empty and we'll create a placeholder R.java below.
   */
  private final Optional<SourcePath> pathToRDotJavaDir;

  private final Collection<? extends UsesResources> allPreDexRules;

  private static final Pattern R_DOT_JAVA_LINE_PATTERN =
      Pattern.compile("^ *public static final int(?:\\[\\])? (\\w+)=");

  private static final Pattern R_DOT_JAVA_PACKAGE_NAME_PATTERN =
      Pattern.compile("^ *package ([\\w.]+);");

  TrimUberRDotJava(
      BuildTarget buildTarget,
      ProjectFilesystem projectFilesystem,
      BuildRuleParams buildRuleParams,
      Optional<SourcePath> pathToRDotJavaDir,
      Collection<? extends UsesResources> allPreDexRules) {
    super(buildTarget, projectFilesystem, buildRuleParams);
    this.pathToRDotJavaDir = pathToRDotJavaDir;
    this.allPreDexRules = allPreDexRules;
  }

  @Override
  public ImmutableList<Step> getBuildSteps(
      BuildContext context, BuildableContext buildableContext) {
    RelPath output = context.getSourcePathResolver().getCellUnsafeRelPath(getSourcePathToOutput());
    Optional<RelPath> input =
        pathToRDotJavaDir.map(context.getSourcePathResolver()::getCellUnsafeRelPath);
    buildableContext.recordArtifact(output.getPath());
    return new ImmutableList.Builder<Step>()
        .addAll(
            MakeCleanDirectoryStep.of(
                BuildCellRelativePath.fromCellRelativePath(
                    context.getBuildCellRootPath(), getProjectFilesystem(), output.getParent())))
        .add(new PerformTrimStep(output, input))
        .add(
            ZipScrubberStep.of(
                context.getSourcePathResolver().getAbsolutePath(getSourcePathToOutput())))
        .build();
  }

  @Override
  public SourcePath getSourcePathToOutput() {
    return ExplicitBuildTargetSourcePath.of(
        getBuildTarget(),
        BuildTargetPaths.getGenPath(
            getProjectFilesystem().getBuckPaths(),
            getBuildTarget(),
            "%s/_trimmed_r_dot_java.src.zip"));
  }

  private class PerformTrimStep implements Step {
    private final RelPath pathToOutput;
    private final Optional<RelPath> pathToInput;

    public PerformTrimStep(RelPath pathToOutput, Optional<RelPath> pathToInput) {
      this.pathToOutput = pathToOutput;
      this.pathToInput = pathToInput;
    }

    @Override
    public StepExecutionResult execute(StepExecutionContext context) throws IOException {
      ImmutableSet.Builder<String> allReferencedResourcesBuilder = ImmutableSet.builder();
      for (UsesResources preDexRule : allPreDexRules) {
        allReferencedResourcesBuilder.addAll(preDexRule.getReferencedResources());
      }
      ImmutableSet<String> allReferencedResources = allReferencedResourcesBuilder.build();

      ProjectFilesystem projectFilesystem = getProjectFilesystem();
      try (CustomZipOutputStream output =
          ZipOutputStreams.newOutputStream(projectFilesystem.resolve(pathToOutput).getPath())) {
        if (!pathToInput.isPresent()) {
          // dx fails if its input contains no classes.  Rather than add empty input handling
          // to DxStep, the dex merger, and every other step of this chain, just generate a
          // stub class.  This will be stripped by ProGuard in release builds and have a minimal
          // effect on debug builds.
          output.putNextEntry(new ZipEntry("com/facebook/buck/AppWithoutResourcesStub.java"));
          output.write(
              ("package com.facebook.buck_generated;\n" + "final class AppWithoutResourcesStub {}")
                  .getBytes());
        } else {
          Preconditions.checkState(projectFilesystem.exists(pathToInput.get()));
          projectFilesystem.walkRelativeFileTree(
              pathToInput.get().getPath(),
              new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
                    throws IOException {
                  if (attrs.isDirectory()) {
                    return FileVisitResult.CONTINUE;
                  }
                  if (!attrs.isRegularFile()) {
                    throw new RuntimeException(
                        String.format(
                            "Found unknown file type while looking for R.java: %s (%s)",
                            file, attrs));
                  }
                  if (!file.getFileName().toString().endsWith(".java")) {
                    throw new RuntimeException(
                        String.format("Found unknown file while looking for R.java: %s", file));
                  }

                  output.putNextEntry(
                      new ZipEntry(
                          PathFormatter.pathWithUnixSeparators(
                              pathToInput.get().relativize(file))));
                  if (allPreDexRules.isEmpty()) {
                    // If there are no pre-dexed inputs, we don't yet support trimming
                    // R.java, so just copy it verbatim (instead of trimming it down to nothing).
                    projectFilesystem.copyToOutputStream(file, output);
                  } else {
                    filterRDotJava(
                        projectFilesystem.readLines(file), output, allReferencedResources);
                  }
                  return FileVisitResult.CONTINUE;
                }
              });
        }
      }
      return StepExecutionResults.SUCCESS;
    }

    @Override
    public String getShortName() {
      return "trim_uber_r_dot_java";
    }

    @Override
    public String getDescription(StepExecutionContext context) {
      return String.format("trim_uber_r_dot_java %s > %s", pathToInput, pathToOutput);
    }
  }

  private static void filterRDotJava(
      List<String> rDotJavaLines, OutputStream output, ImmutableSet<String> allReferencedResources)
      throws IOException {
    String packageName = null;
    Matcher m;

    for (String line : rDotJavaLines) {
      if (packageName == null) {
        m = R_DOT_JAVA_PACKAGE_NAME_PATTERN.matcher(line);
        if (m.find()) {
          packageName = m.group(1);
        } else {
          continue;
        }
      }
      m = R_DOT_JAVA_LINE_PATTERN.matcher(line);
      // We match on the package name + resource name.
      // This can cause us to keep (for example) R.layout.foo when only R.string.foo
      // is referenced.  That is a very rare case, though, and not worth the complexity to fix.
      if (m.find()) {
        String resource = m.group(1);
        boolean shouldWriteLine = allReferencedResources.contains(packageName + "." + resource);
        if (!shouldWriteLine) {
          continue;
        }
      }
      output.write(line.getBytes(StandardCharsets.UTF_8));
      output.write('\n');
    }
  }
}

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

package com.facebook.buck.jvm.java;

import com.facebook.buck.core.config.BuckConfig;
import com.facebook.buck.core.config.ConfigView;
import com.facebook.buck.core.exceptions.HumanReadableException;
import com.facebook.buck.core.model.TargetConfiguration;
import com.facebook.buck.core.sourcepath.PathSourcePath;
import com.facebook.buck.core.sourcepath.SourcePath;
import com.facebook.buck.core.toolchain.tool.Tool;
import com.facebook.buck.core.toolchain.tool.impl.CommandTool;
import com.facebook.buck.core.util.log.Logger;
import com.facebook.buck.io.filesystem.ProjectFilesystem;
import com.facebook.buck.javacd.model.AbiGenerationMode;
import com.facebook.buck.javacd.model.BaseCommandParams.SpoolMode;
import com.facebook.buck.javacd.model.UnusedDependenciesParams;
import com.facebook.buck.jvm.java.abi.AbiGenerationModeUtils;
import com.facebook.buck.jvm.java.version.JavaVersion;
import com.facebook.buck.rules.args.SourcePathArg;
import com.facebook.buck.rules.args.StringArg;
import com.facebook.buck.util.environment.Platform;
import com.facebook.buck.util.java.JavaRuntimeUtils;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.function.Function;
import java.util.logging.Level;

/** A java-specific "view" of BuckConfig. */
public class JavaBuckConfig implements ConfigView<BuckConfig> {

  private static final Logger LOGGER = Logger.get(JavaBuckConfig.class);

  private static final boolean IS_WINDOWS = Platform.detect() == Platform.WINDOWS;

  public static final String SECTION = "java";
  public static final String PROPERTY_COMPILE_AGAINST_ABIS = "compile_against_abis";
  public static final String PROPERTY_JAVACD_ENABLED = "javacd_enabled";
  static final String PROPERTY_JAVACD_DISABLED_FOR_WINDOWS = "javacd_disabled_for_windows";

  private static final CommandTool DEFAULT_JAVA_TOOL =
      new CommandTool.Builder().addArg(JavaRuntimeUtils.getBucksJavaBinCommand()).build();

  static final JavaOptions DEFAULT_JAVA_OPTIONS = JavaOptions.of(DEFAULT_JAVA_TOOL);
  private static final String BOOTCLASSPATH_PREFIX = "bootclasspath-";

  private final BuckConfig delegate;
  private final Function<TargetConfiguration, JavacSpec> javacSpecSupplier;

  // Interface for reflection-based ConfigView to instantiate this class.
  public static JavaBuckConfig of(BuckConfig delegate) {
    return new JavaBuckConfig(delegate);
  }

  private JavaBuckConfig(BuckConfig delegate) {
    this.delegate = delegate;
    this.javacSpecSupplier = targetConfiguration -> JavacSpec.of(getJavacPath(targetConfiguration));
  }

  @Override
  public BuckConfig getDelegate() {
    return delegate;
  }

  public JavaOptions getDefaultJavaOptions() {
    return getToolForExecutable("java").map(JavaOptions::of).orElse(DEFAULT_JAVA_OPTIONS);
  }

  public JavaOptions getDefaultJavaOptionsForTests() {
    return getToolForExecutable("java_for_tests")
        .map(
            tool ->
                JavaOptions.of(tool, getDelegate().getInteger("test", "java_for_tests_version")))
        .orElseGet(this::getDefaultJavaOptions);
  }

  public JavaOptions getDefaultJava11OptionsForTests() {
    return getToolForExecutable("java11_for_tests")
        .map(tool -> JavaOptions.of(tool, OptionalInt.of(11)))
        .orElseGet(this::getDefaultJavaOptionsForTests);
  }

  public JavaOptions getDefaultJavaOptionsForCodeCoverage() {
    return getToolForExecutable("java_for_code_coverage")
        .map(JavaOptions::of)
        .orElseGet(this::getDefaultJavaOptions);
  }

  public JavacLanguageLevelOptions getJavacLanguageLevelOptions() {
    JavacLanguageLevelOptions.Builder builder = JavacLanguageLevelOptions.builder();
    delegate
        .getFloat(SECTION, "source_level")
        .map(f -> f >= 5 ? Integer.toString(f.intValue()) : f.toString())
        .map(builder::setSourceLevel);
    delegate
        .getFloat(SECTION, "target_level")
        .map(f -> f >= 5 ? Integer.toString(f.intValue()) : f.toString())
        .map(builder::setTargetLevel);
    return builder.build();
  }

  public JavacOptions getDefaultJavacOptions(TargetConfiguration targetConfiguration) {
    JavacOptions.Builder builder = JavacOptions.builderForUseInJavaBuckConfig();

    builder.setLanguageLevelOptions(getJavacLanguageLevelOptions());

    ImmutableList<String> extraArguments =
        delegate.getListWithoutComments(SECTION, "extra_arguments");

    builder.setTrackClassUsage(trackClassUsage(targetConfiguration));
    Optional<Boolean> trackJavacPhaseEvents =
        delegate.getBoolean(SECTION, "track_javac_phase_events");
    trackJavacPhaseEvents.ifPresent(builder::setTrackJavacPhaseEvents);

    Optional<SpoolMode> spoolMode = delegate.getEnum(SECTION, "jar_spool_mode", SpoolMode.class);
    spoolMode.ifPresent(builder::setSpoolMode);

    ImmutableMap<String, String> allEntries = delegate.getEntriesForSection(SECTION);
    ImmutableMap.Builder<JavaVersion, ImmutableList<PathSourcePath>> sourceToBootclasspathBuilder =
        ImmutableMap.builder();
    Set<JavaVersion> versionsSeen = new HashSet<>();
    ProjectFilesystem filesystem = delegate.getFilesystem();
    for (Map.Entry<String, String> entry : allEntries.entrySet()) {
      String key = entry.getKey();
      if (!key.startsWith(BOOTCLASSPATH_PREFIX)) {
        continue;
      }

      String version = key.substring(BOOTCLASSPATH_PREFIX.length());
      JavaVersion javaVersion = JavaVersion.toJavaLanguageVersion(version);
      if (!versionsSeen.contains(javaVersion)) {
        versionsSeen.add(javaVersion);
      } else {
        LOGGER.warn("Multiple entries with same java version: " + javaVersion);
        continue;
      }

      String bootclasspathEntry = entry.getValue();
      String[] values = bootclasspathEntry.split(":");
      if (IS_WINDOWS) {
        // try to use windows file separator `;`
        String[] parts = bootclasspathEntry.split(File.pathSeparator);
        if (parts.length > values.length) {
          values = parts;
        }
      }
      ImmutableList.Builder<PathSourcePath> pathsBuilder =
          ImmutableList.builderWithExpectedSize(values.length);
      for (String value : values) {
        Preconditions.checkState(value != null);
        pathsBuilder.add(PathSourcePath.of(filesystem, Paths.get(value)));
      }

      sourceToBootclasspathBuilder.put(javaVersion, pathsBuilder.build());
    }

    return builder
        .putAllSourceToBootclasspath(sourceToBootclasspathBuilder.build())
        .addAllExtraArguments(extraArguments)
        .build();
  }

  public AbiGenerationMode getAbiGenerationMode() {
    return delegate
        .getEnum(SECTION, "abi_generation_mode", AbiGenerationMode.class)
        .orElse(AbiGenerationMode.CLASS);
  }

  public ImmutableSet<String> getSrcRoots() {
    return ImmutableSet.copyOf(delegate.getListWithoutComments(SECTION, "src_roots"));
  }

  public DefaultJavaPackageFinder createDefaultJavaPackageFinder() {
    return DefaultJavaPackageFinder.createDefaultJavaPackageFinder(
        delegate.getFilesystem(), getSrcRoots());
  }

  public boolean trackClassUsage(TargetConfiguration targetConfiguration) {
    // This is just to make it possible to turn off dep-based rulekeys in case anything goes wrong
    // and can be removed when we're sure class usage tracking and dep-based keys for Java
    // work fine.
    Optional<Boolean> trackClassUsage = delegate.getBoolean(SECTION, "track_class_usage");
    if (trackClassUsage.isPresent() && !trackClassUsage.get()) {
      return false;
    }

    ResolvedJavac.Source javacSource = getJavacSpec(targetConfiguration).getJavacSource();
    return javacSource == ResolvedJavac.Source.JDK;
  }

  public boolean shouldDesugarInterfaceMethods() {
    return delegate.getBoolean(SECTION, "desugar_interface_methods").orElse(false);
  }

  public boolean shouldDesugarInterfaceMethodsInPrebuiltJars() {
    return delegate.getBoolean(SECTION, "desugar_interface_methods_in_prebuilt_jars").orElse(false);
  }

  public boolean shouldAddBuckLDSymlinkTree() {
    return delegate.getBoolean(SECTION, "add_buck_ld_symlink_tree").orElse(false);
  }

  public JavacSpec getJavacSpec(TargetConfiguration targetConfiguration) {
    return javacSpecSupplier.apply(targetConfiguration);
  }

  @VisibleForTesting
  Optional<SourcePath> getJavacPath(TargetConfiguration targetConfiguration) {
    Optional<SourcePath> sourcePath = delegate.getSourcePath("tools", "javac", targetConfiguration);
    if (sourcePath.isPresent() && sourcePath.get() instanceof PathSourcePath) {
      PathSourcePath pathSourcePath = (PathSourcePath) sourcePath.get();
      if (!pathSourcePath.getFilesystem().isExecutable(pathSourcePath.getRelativePath())) {
        throw new HumanReadableException("javac is not executable: %s", pathSourcePath);
      }
    }
    return sourcePath;
  }

  private Optional<Tool> getToolForExecutable(String executableName) {
    return delegate
        // Make sure to pass `false` for `isCellRootRelative` so that we get a relative path back,
        // instead of an absolute one.  Otherwise, we can't preserve the original value.
        .getPath("tools", executableName, false)
        .map(
            path -> {
              if (!Files.isExecutable(
                  delegate.resolvePathThatMayBeOutsideTheProjectFilesystem(path))) {
                throw new HumanReadableException(executableName + " is not executable: " + path);
              }

              // Build the tool object.  For absolute paths, just add the raw string and avoid
              // hashing the contents, as this would require all users to have identical system
              // binaries, when what we probably only care about is the version.
              return new CommandTool.Builder()
                  .addArg(
                      path.isAbsolute()
                          ? StringArg.of(path.toString())
                          : SourcePathArg.of(
                              Objects.requireNonNull(delegate.getPathSourcePath(path))))
                  .build();
            });
  }

  public boolean shouldCacheBinaries() {
    return delegate.getBooleanValue(SECTION, "cache_binaries", true);
  }

  public OptionalInt getDxThreadCount() {
    return delegate.getInteger(SECTION, "dx_threads");
  }

  /**
   * Controls a special verification mode that generates ABIs both from source and from class files
   * and diffs them. This is a test hook for use during development of the source ABI feature. This
   * only has meaning when {@link #getAbiGenerationMode()} is one of the source modes.
   */
  public SourceAbiVerificationMode getSourceAbiVerificationMode() {
    if (!AbiGenerationModeUtils.isSourceAbi(getAbiGenerationMode())) {
      return SourceAbiVerificationMode.OFF;
    }

    return delegate
        .getEnum(SECTION, "source_abi_verification_mode", SourceAbiVerificationMode.class)
        .orElse(SourceAbiVerificationMode.OFF);
  }

  public boolean shouldCompileAgainstAbis() {
    return delegate.getBooleanValue(SECTION, PROPERTY_COMPILE_AGAINST_ABIS, false);
  }

  public Optional<String> getDefaultCxxPlatform() {
    return delegate.getValue(SECTION, "default_cxx_platform");
  }

  public UnusedDependenciesConfig getUnusedDependenciesAction() {
    return delegate
        .getEnum(SECTION, "unused_dependencies_action", UnusedDependenciesConfig.class)
        .orElse(UnusedDependenciesConfig.IGNORE);
  }

  public Optional<String> getUnusedDependenciesBuildozerString() {
    return delegate.getValue(SECTION, "unused_dependencies_buildozer_path");
  }

  public boolean isUnusedDependenciesOnlyPrintCommands() {
    return delegate.getBooleanValue(SECTION, "unused_dependencies_only_print_commands", false);
  }

  public boolean isUnusedDependenciesUltralightChecking() {
    return delegate.getBooleanValue(SECTION, "unused_dependencies_ultralight_checking", false);
  }

  public Optional<String> getJavaTempDir() {
    return delegate.getValue(SECTION, "test_temp_dir");
  }

  public Level getDuplicatesLogLevel() {
    return delegate
        .getEnum(SECTION, "duplicates_log_level", DuplicatesLogLevel.class)
        .orElse(DuplicatesLogLevel.INFO)
        .getLevel();
  }

  public boolean isJavaCDEnabled() {
    if (IS_WINDOWS && isDisabledForWindows()) {
      LOGGER.info("javacd disabled on windows");
      return false;
    }

    return getDelegate().getBooleanValue(SECTION, PROPERTY_JAVACD_ENABLED, false);
  }

  public boolean isDisabledForWindows() {
    return getDelegate().getBooleanValue(SECTION, PROPERTY_JAVACD_DISABLED_FOR_WINDOWS, false);
  }

  public boolean isPipeliningDisabled() {
    return getDelegate().getBooleanValue(SECTION, "pipelining_disabled", false);
  }

  public boolean useDependencyOrderClasspathForTests() {
    return delegate.getBoolean(SECTION, "use_dependency_order_classpath_for_tests").orElse(false);
  }

  public enum SourceAbiVerificationMode {
    /** Don't verify ABI jars. */
    OFF,
    /** Generate ABI jars from classes and from source. Log any differences. */
    LOG,
    /** Generate ABI jars from classes and from source. Fail on differences. */
    FAIL,
  }

  /**
   * The same as {@link UnusedDependenciesParams.UnusedDependenciesAction} with a couple of extra
   * options to give greater flexibility.
   */
  public enum UnusedDependenciesConfig {
    FAIL,
    WARN,
    IGNORE,
    // This means that every target will be ignored, even if they are marked as WARN or FAIL
    IGNORE_ALWAYS,
    // This means that an individual target marked as FAIL will actually be WARN.
    WARN_IF_FAIL,
  }

  /** Logging level duplicates are reported at */
  public enum DuplicatesLogLevel {
    WARN(Level.WARNING),
    INFO(Level.INFO),
    FINE(Level.FINE),
    ;

    private final Level level;

    DuplicatesLogLevel(Level level) {
      this.level = level;
    }

    public Level getLevel() {
      return level;
    }
  }
}

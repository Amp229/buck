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

package com.facebook.buck.features.rust;

import com.facebook.buck.core.config.BuckConfig;
import com.facebook.buck.core.model.Flavor;
import com.facebook.buck.core.model.InternalFlavor;
import com.facebook.buck.core.model.RuleType;
import com.facebook.buck.core.toolchain.toolprovider.ToolProvider;
import com.facebook.buck.cxx.toolchain.linker.LinkerProvider;
import com.facebook.buck.io.ExecutableFinder;
import com.facebook.buck.rules.tool.config.ToolConfig;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableList.Builder;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.function.Supplier;

public class RustBuckConfig {

  private static final String SECTION = "rust";
  private static final String RUSTC_FLAGS = "rustc_flags";
  private static final String RUSTC_BINARY_FLAGS = "rustc_binary_flags";
  private static final String RUSTC_LIBRARY_FLAGS = "rustc_library_flags";
  private static final String RUSTC_CHECK_FLAGS = "rustc_check_flags";
  private static final String RUSTDOC_FLAGS = "rustdoc_flags";
  private static final String RUSTDOC_EXTERN_HTML_ROOT_URL_PREFIX =
      "rustdoc_extern_html_root_url_prefix";
  private static final String RUSTC_TEST_FLAGS = "rustc_test_flags";
  private static final String UNFLAVORED_BINARIES = "unflavored_binaries";
  private static final String REMAP_SRC_PATHS = "remap_src_paths";
  private static final String FORCE_RLIB = "force_rlib";
  private static final String PREFER_STATIC_LIBS = "prefer_static_libs";
  private static final String RUSTC_INCREMENTAL = "incremental";
  private static final String DEFAULT_EDITION = "default_edition";
  private static final String RUSTC_PLUGIN_PLATFORM = "rustc_plugin_platform";
  private static final String RUSTC_TARGET_TRIPLE = "rustc_target_triple";
  private static final String NATIVE_UNBUNDLE_DEPS = "native_unbundle_deps";
  private static final String USE_RUSTC_TARGET_TRIPLE = "use_rustc_target_triple";

  public static final String DEFAULT_FLAVOR_LIBRARY_TYPE = "type";

  enum RemapSrcPaths {
    NO, // no path remapping
    YES, // remap using stable command-line option
    ;

    public void addRemapOption(Builder<String> cmd, String cwd, String basedir) {
      switch (this) {
        case NO:
          break;
        case YES:
          cmd.add("--remap-path-prefix", basedir + "=");
          cmd.add("--remap-path-prefix", cwd + "=./");
          break;
        default:
          throw new RuntimeException("addRemapOption() not implemented for " + this);
      }
    }
  }

  private final BuckConfig delegate;

  public RustBuckConfig(BuckConfig delegate) {
    this.delegate = delegate;
  }

  private <T> Optional<T> firstOf(Supplier<Optional<T>> first, Supplier<Optional<T>> second) {
    for (Supplier<Optional<T>> optional : ImmutableList.of(first, second)) {
      Optional<T> val = optional.get();
      if (val.isPresent()) {
        return val;
      }
    }
    return Optional.empty();
  }

  private static String platformSection(String platform) {
    return SECTION + '#' + platform;
  }

  private ImmutableList<String> getFlags(String platform, String field, char delim) {
    return delegate.getValue(platformSection(platform), field).isPresent()
        ? delegate.getListWithoutComments(platformSection(platform), field, delim)
        : delegate.getListWithoutComments(SECTION, field, delim);
  }

  private ImmutableList<String> getCompilerFlags(String platform, String field) {
    return getFlags(platform, field, ' ');
  }

  private Optional<ToolProvider> getRustTool(String platform, String field) {
    return firstOf(
        () -> delegate.getView(ToolConfig.class).getToolProvider(platformSection(platform), field),
        () -> delegate.getView(ToolConfig.class).getToolProvider(SECTION, field));
  }

  public Optional<ToolProvider> getRustCompiler(String platform) {
    return getRustTool(platform, "compiler");
  }

  public Optional<ToolProvider> getRustdoc(String platform) {
    return getRustTool(platform, "rustdoc");
  }

  /**
   * Get platform name suitable for building rustc plugins/procedual macros. This platform is
   * configured to be compatible with the way rustc itself was built - same architecture, compiler
   * flags, etc.
   *
   * @param platform for which we're getting the corresponding plugin platform
   * @return Plugin platform name,
   */
  public Optional<String> getRustcPluginPlatform(String platform) {
    return delegate.getValue(platformSection(platform), RUSTC_PLUGIN_PLATFORM);
  }

  /**
   * Get the rustc target triple for this build.
   *
   * @see <a href="https://doc.rust-lang.org/rustc/platform-support.html">rustc Platform Support</a>
   */
  public Optional<String> getRustcTargetTriple(String platform) {
    return delegate.getValue(platformSection(platform), RUSTC_TARGET_TRIPLE);
  }

  /**
   * Get common set of rustc flags. These are used for all rules that invoke rustc.
   *
   * @return List of rustc option flags.
   */
  private ImmutableList<String> getRustCompilerFlags(String platform) {
    return getCompilerFlags(platform, RUSTC_FLAGS);
  }

  /**
   * Get rustc flags for rust_library() rules.
   *
   * @return List of rustc_library_flags, as well as common rustc_flags.
   */
  public ImmutableList<String> getRustcLibraryFlags(String platform) {
    return ImmutableList.<String>builder()
        .addAll(getRustCompilerFlags(platform))
        .addAll(getCompilerFlags(platform, RUSTC_LIBRARY_FLAGS))
        .build();
  }

  /**
   * Get rustc flags for rust_binary() rules.
   *
   * @return List of rustc_binary_flags, as well as common rustc_flags.
   */
  public ImmutableList<String> getRustcBinaryFlags(String platform) {
    return ImmutableList.<String>builder()
        .addAll(getRustCompilerFlags(platform))
        .addAll(getCompilerFlags(platform, RUSTC_BINARY_FLAGS))
        .build();
  }

  /**
   * Get rustc flags for rust_test() rules.
   *
   * @return List of rustc_test_flags, as well as common rustc_flags.
   */
  public ImmutableList<String> getRustcTestFlags(String platform) {
    return ImmutableList.<String>builder()
        .addAll(getRustCompilerFlags(platform))
        .addAll(getCompilerFlags(platform, RUSTC_TEST_FLAGS))
        .build();
  }

  /**
   * Get rustc flags for #check flavored builds. Caller must also include rule-dependent flags and
   * common flags.
   *
   * @return List of rustc_check_flags.
   */
  public ImmutableList<String> getRustcCheckFlags(String platform) {
    return getCompilerFlags(platform, RUSTC_CHECK_FLAGS);
  }

  /** Preliminary: get rust flags for #doc flavored builds. */
  public ImmutableList<String> getRustDocFlags(String platform) {
    return getCompilerFlags(platform, RUSTDOC_FLAGS);
  }

  /**
   * URL prefix at which generated rustdoc is to be hosted, used for cross-crate links.
   *
   * <p>Generated cross-crate links are of the form "$PREFIX/$TARGET". For example if our
   * rustdoc_extern_html_root_url_prefix is set to "/intern/rustdoc" and some generated
   * documentation wants to link to data structures from the target "build_infra/buck_client:buck",
   * those links would point to "/intern/rustdoc/build_infra/buck_client:buck/...".
   */
  public Optional<String> getRustdocExternHtmlRootUrlPrefix() {
    return delegate.getValue(SECTION, RUSTDOC_EXTERN_HTML_ROOT_URL_PREFIX);
  }

  public Optional<ToolProvider> getRustLinker(String platform) {
    return getRustTool(platform, "linker");
  }

  public Optional<LinkerProvider.Type> getLinkerPlatform(String platform) {
    return firstOf(
        () ->
            delegate.getEnum(
                platformSection(platform), "linker_platform", LinkerProvider.Type.class),
        () -> delegate.getEnum(SECTION, "linker_platform", LinkerProvider.Type.class));
  }

  public ImmutableList<String> getLinkerFlags(String platform) {
    return getFlags(platform, "linker_args", ',');
  }

  /**
   * Get unflavored_binaries option. This controls whether executables have the build flavor in
   * their path. This is useful for making the path more deterministic (though really external tools
   * should be asking what the path is).
   *
   * @return Boolean of whether to use unflavored paths.
   */
  boolean getUnflavoredBinaries() {
    return delegate.getBooleanValue(SECTION, UNFLAVORED_BINARIES, false);
  }

  /**
   * Get source path remapping option. This controls whether we ask rustc to remap source paths in
   * all output (ie, compiler messages, file!() macros, debug info, etc).
   *
   * @return Remapping mode
   */
  RemapSrcPaths getRemapSrcPaths() {
    return delegate.getEnum(SECTION, REMAP_SRC_PATHS, RemapSrcPaths.class).orElse(RemapSrcPaths.NO);
  }

  /**
   * Get "force_rlib" config. When set, always generate rlib (static) libraries, even for otherwise
   * shared targets.
   *
   * @return force_rlib flag
   */
  boolean getForceRlib() {
    return delegate.getBooleanValue(SECTION, FORCE_RLIB, false);
  }

  /**
   * Get "prefer_static_libs" config. When set, always use rlib (static) libraries, even for
   * otherwise shared targets. This primarily affects whether to use static or shared standard
   * libraries.
   *
   * @return prefer_static_libs flag
   */
  boolean getPreferStaticLibs() {
    return delegate.getBooleanValue(SECTION, PREFER_STATIC_LIBS, false);
  }

  /**
   * Get "native_unbundle_deps" config. When set, `rlib` crate type will be used instead of the
   * default `staticlib`. That will make rust not include all upstream dependencies into the
   * resulting archive, but only symbols from the compiled target. But we will need to supply
   * top-level link rules with direct and transitive linkable rust rules.
   *
   * @return native_unbundle_deps flag
   */
  boolean getNativeUnbundleDeps() {
    return delegate.getBooleanValue(SECTION, NATIVE_UNBUNDLE_DEPS, false);
  }

  /**
   * Get default flavor map for a specific type of rule.
   *
   * @param rule type
   * @return map of default flavors
   */
  public ImmutableMap<String, Flavor> getDefaultFlavorsForRuleType(RuleType type) {
    return ImmutableMap.copyOf(
        Maps.transformValues(
            delegate.getEntriesForSection("defaults." + type.getName()), InternalFlavor::of));
  }

  /**
   * Get "incremental" config - when present, incremental mode is enabled, and the string is used to
   * make sure rustc's incremental database is set to a mode, platform and flavor-specific path.
   * Rustc guarantees that the output of an incremental build it bit-for-bit identical to a
   * non-incremental one, so in principle we don't need to add this to the rulekey.
   *
   * @param platform
   */
  Optional<String> getIncremental(String platform) {
    return firstOf(
        () -> delegate.getValue(platformSection(platform), RUSTC_INCREMENTAL),
        () -> delegate.getValue(SECTION, RUSTC_INCREMENTAL));
  }

  /** Default edition when not specified in a rule. Use "2015" if not specified. */
  String getEdition() {
    return delegate.getValue(SECTION, DEFAULT_EDITION).orElse("2015");
  }

  private Optional<Path> getOptionalPath(String sectionName, String propertyName) {
    Optional<String> pathString = delegate.getValue(sectionName, propertyName);
    return pathString.map(
        path -> delegate.resolvePathThatMayBeOutsideTheProjectFilesystem(Paths.get(path)));
  }

  public Optional<Path> getAppleXcrunPath() {
    Optional<Path> xcrunPath = getOptionalPath("apple", "xcrun_path");
    return xcrunPath.flatMap(
        path -> new ExecutableFinder().getOptionalExecutable(path, delegate.getEnvironment()));
  }

  public Optional<Path> getAppleDeveloperDirIfSet() {
    return getOptionalPath("apple", "xcode_developer_dir");
  }

  // T125799685: Temporary while we migrate from implicit to explicit target triples.
  boolean getUseRustcTargetTriple() {
    return delegate.getBooleanValue(SECTION, USE_RUSTC_TARGET_TRIPLE, false);
  }
}

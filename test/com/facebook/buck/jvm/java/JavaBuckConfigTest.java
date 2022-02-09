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

import static com.facebook.buck.jvm.java.JavacLanguageLevelOptions.TARGETED_JAVA_VERSION;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeThat;

import com.facebook.buck.cd.model.java.AbiGenerationMode;
import com.facebook.buck.core.config.BuckConfig;
import com.facebook.buck.core.config.BuckConfigTestUtils;
import com.facebook.buck.core.config.FakeBuckConfig;
import com.facebook.buck.core.exceptions.HumanReadableException;
import com.facebook.buck.core.model.BuildTarget;
import com.facebook.buck.core.model.BuildTargetFactory;
import com.facebook.buck.core.model.UnconfiguredTargetConfiguration;
import com.facebook.buck.core.rules.BuildRuleResolver;
import com.facebook.buck.core.rules.resolver.impl.TestActionGraphBuilder;
import com.facebook.buck.core.sourcepath.BuildTargetSourcePath;
import com.facebook.buck.core.sourcepath.DefaultBuildTargetSourcePath;
import com.facebook.buck.core.sourcepath.SourcePath;
import com.facebook.buck.core.sourcepath.resolver.SourcePathResolverAdapter;
import com.facebook.buck.core.sourcepath.resolver.impl.AbstractSourcePathResolver;
import com.facebook.buck.io.filesystem.ProjectFilesystem;
import com.facebook.buck.io.filesystem.TestProjectFilesystems;
import com.facebook.buck.jvm.java.version.JavaVersion;
import com.facebook.buck.parser.exceptions.NoSuchBuildTargetException;
import com.facebook.buck.testutil.TemporaryPaths;
import com.facebook.buck.util.environment.Architecture;
import com.facebook.buck.util.environment.EnvVariablesProvider;
import com.facebook.buck.util.environment.Platform;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSortedSet;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public class JavaBuckConfigTest {

  public static final BuildRuleResolver RULE_RESOLVER = new TestActionGraphBuilder();
  private static final SourcePathResolverAdapter PATH_RESOLVER =
      RULE_RESOLVER.getSourcePathResolver();

  @Rule public TemporaryPaths temporaryFolder = new TemporaryPaths();
  private ProjectFilesystem defaultFilesystem;

  @Before
  public void setUpDefaultFilesystem() {
    defaultFilesystem = TestProjectFilesystems.createProjectFilesystem(temporaryFolder.getRoot());
  }

  @Test
  public void whenJavaIsNotSetThenJavaFromPathIsReturned() throws IOException {
    JavaBuckConfig config = createWithDefaultFilesystem(new StringReader(""));
    JavaOptions javaOptions = config.getDefaultJavaOptions();
    String binaryExtension = Platform.detect() == Platform.WINDOWS ? ".exe" : "";
    String javaPath =
        Paths.get(System.getProperty("java.home"), "bin", "java" + binaryExtension).toString();
    assertEquals(
        ImmutableList.of(javaPath), javaOptions.getJavaRuntime().getCommandPrefix(PATH_RESOLVER));

    JavaOptions javaForTestsOptions = config.getDefaultJavaOptionsForTests();
    assertEquals(
        ImmutableList.of(javaPath),
        javaForTestsOptions.getJavaRuntime().getCommandPrefix(PATH_RESOLVER));

    JavaOptions java11ForTestsOptions = config.getDefaultJava11OptionsForTests();
    assertEquals(
        ImmutableList.of(javaPath),
        java11ForTestsOptions.getJavaRuntime().getCommandPrefix(PATH_RESOLVER));
  }

  @Test
  public void whenJavaExistsAndIsExecutableThenItIsReturned() throws IOException {
    Path java = temporaryFolder.newExecutableFile().getPath();
    Path javaForTests = temporaryFolder.newExecutableFile().getPath();
    Path java11ForTests = temporaryFolder.newExecutableFile().getPath();
    String javaCommand = java.toString();
    String javaForTestsCommand = javaForTests.toString();
    String java11ForTestsCommand = java11ForTests.toString();
    JavaBuckConfig config =
        FakeBuckConfig.builder()
            .setFilesystem(defaultFilesystem)
            .setSections(
                ImmutableMap.of(
                    "tools",
                    ImmutableMap.of(
                        "java",
                        javaCommand,
                        "java_for_tests",
                        javaForTestsCommand,
                        "java11_for_tests",
                        java11ForTestsCommand)))
            .build()
            .getView(JavaBuckConfig.class);

    JavaOptions javaOptions = config.getDefaultJavaOptions();
    assertEquals(
        ImmutableList.of(javaCommand),
        javaOptions.getJavaRuntime().getCommandPrefix(PATH_RESOLVER));

    JavaOptions javaForTestsOptions = config.getDefaultJavaOptionsForTests();
    assertEquals(
        ImmutableList.of(javaForTestsCommand),
        javaForTestsOptions.getJavaRuntime().getCommandPrefix(PATH_RESOLVER));

    JavaOptions java11ForTestsOptions = config.getDefaultJava11OptionsForTests();
    assertEquals(
        ImmutableList.of(java11ForTestsCommand),
        java11ForTestsOptions.getJavaRuntime().getCommandPrefix(PATH_RESOLVER));
  }

  @Test
  public void whenJavaExistsAndIsRelativePathThenItsAbsolutePathIsReturned() throws IOException {
    Path java = temporaryFolder.newExecutableFile().getPath();
    String javaFilename = java.getFileName().toString();
    JavaBuckConfig config =
        FakeBuckConfig.builder()
            .setFilesystem(defaultFilesystem)
            .setSections(ImmutableMap.of("tools", ImmutableMap.of("java", javaFilename)))
            .build()
            .getView(JavaBuckConfig.class);

    JavaOptions options = config.getDefaultJavaOptions();
    assertEquals(
        ImmutableList.of(java.toString()),
        options.getJavaRuntime().getCommandPrefix(PATH_RESOLVER));
  }

  @Test
  public void whenJavaForTestsIsNotSetThenJavaIsReturned() throws IOException {
    Path java = temporaryFolder.newExecutableFile().getPath();
    String javaCommand = java.toString();
    JavaBuckConfig config =
        FakeBuckConfig.builder()
            .setFilesystem(defaultFilesystem)
            .setSections(ImmutableMap.of("tools", ImmutableMap.of("java", javaCommand)))
            .build()
            .getView(JavaBuckConfig.class);

    JavaOptions options = config.getDefaultJavaOptionsForTests();
    assertEquals(
        ImmutableList.of(javaCommand), options.getJavaRuntime().getCommandPrefix(PATH_RESOLVER));

    JavaOptions java11options = config.getDefaultJava11OptionsForTests();
    assertEquals(
        ImmutableList.of(javaCommand),
        java11options.getJavaRuntime().getCommandPrefix(PATH_RESOLVER));
  }

  @Test
  public void whenJava11ForTestsIsNotSetThenJavaForTestsIsReturned() throws IOException {
    Path javaForTests = temporaryFolder.newExecutableFile().getPath();
    String javaForTestsCommand = javaForTests.toString();
    JavaBuckConfig config =
        FakeBuckConfig.builder()
            .setFilesystem(defaultFilesystem)
            .setSections(
                ImmutableMap.of("tools", ImmutableMap.of("java_for_tests", javaForTestsCommand)))
            .build()
            .getView(JavaBuckConfig.class);

    JavaOptions java11options = config.getDefaultJava11OptionsForTests();
    assertEquals(
        ImmutableList.of(javaForTestsCommand),
        java11options.getJavaRuntime().getCommandPrefix(PATH_RESOLVER));
  }

  @Test
  public void whenJavacIsNotSetThenAbsentIsReturned() throws IOException {
    JavaBuckConfig config = createWithDefaultFilesystem(new StringReader(""));
    assertEquals(Optional.empty(), config.getJavacPath(UnconfiguredTargetConfiguration.INSTANCE));
  }

  @Test
  public void whenJavacExistsAndIsExecutableThenCorrectPathIsReturned() throws IOException {
    Path javac = temporaryFolder.newExecutableFile().getPath();

    Reader reader =
        new StringReader(
            Joiner.on('\n')
                .join("[tools]", "    javac = " + javac.toString().replace("\\", "\\\\")));
    JavaBuckConfig config = createWithDefaultFilesystem(reader);

    assertEquals(
        config.getDelegate().getPathSourcePath(javac),
        config.getJavacPath(UnconfiguredTargetConfiguration.INSTANCE).get());
  }

  @Test
  public void whenJavacIsABuildTargetThenCorrectPathIsReturned() throws IOException {
    BuildTarget javacTarget = BuildTargetFactory.newInstance("//:javac");
    Reader reader =
        new StringReader(
            Joiner.on('\n').join("[tools]", "    javac = " + javacTarget.getFullyQualifiedName()));
    JavaBuckConfig config = createWithDefaultFilesystem(reader);
    assertEquals(
        DefaultBuildTargetSourcePath.of(javacTarget),
        config.getJavacPath(UnconfiguredTargetConfiguration.INSTANCE).get());
  }

  @Test
  public void whenJavacDoesNotExistThenHumanReadableExceptionIsThrown() throws IOException {
    String invalidPath = temporaryFolder.getRoot() + "DoesNotExist";
    Reader reader =
        new StringReader(
            Joiner.on('\n').join("[tools]", "    javac = " + invalidPath.replace("\\", "\\\\")));
    JavaBuckConfig config = createWithDefaultFilesystem(reader);
    try {
      config.getJavacPath(UnconfiguredTargetConfiguration.INSTANCE);
      fail("Should throw exception as javac file does not exist.");
    } catch (HumanReadableException e) {
      assertEquals(
          "Overridden tools:javac path not found: " + invalidPath,
          e.getHumanReadableErrorMessage());
    }
  }

  @Test
  public void whenJavacIsNotExecutableThenHumanReadableExeceptionIsThrown() throws IOException {
    assumeThat(
        "Files on Windows are executable by default.",
        Platform.detect(),
        is(not(Platform.WINDOWS)));
    Path javac = temporaryFolder.newFile().getPath();

    Reader reader = new StringReader(Joiner.on('\n').join("[tools]", "    javac = " + javac));
    JavaBuckConfig config = createWithDefaultFilesystem(reader);
    try {
      config
          .getJavacSpec(UnconfiguredTargetConfiguration.INSTANCE)
          .getJavacProvider()
          .resolve(new TestActionGraphBuilder());
      fail("Should throw exception as javac file is not executable.");
    } catch (HumanReadableException e) {
      assertEquals(e.getHumanReadableErrorMessage(), "javac is not executable: " + javac);
    }
  }

  @Test
  public void shouldThrowIfJavaTargetOrSourceVersionFromConfigAreNotFloatType() throws IOException {
    String sourceLevel = "source-level";
    String targetLevel = "target-level";

    String localConfig =
        String.format("[java]\nsource_level = %s\ntarget_level = %s", sourceLevel, targetLevel);

    JavaBuckConfig config = createWithDefaultFilesystem(new StringReader(localConfig));

    assertThrows(
        "Malformed value for source_level in [java]: source-level; expecting a floating point number.",
        HumanReadableException.class,
        () -> config.getJavacLanguageLevelOptions());
  }

  @Test
  public void verifyJavaVersions() throws IOException {

    var javaVersions =
        ImmutableMap.<JavaVersion, ImmutableList<String>>builder()
            .put(JavaVersion.VERSION_1_1, ImmutableList.of("1.1"))
            .put(JavaVersion.VERSION_1_2, ImmutableList.of("1.2"))
            .put(JavaVersion.VERSION_1_3, ImmutableList.of("1.3"))
            .put(JavaVersion.VERSION_1_4, ImmutableList.of("1.4"))
            .put(JavaVersion.VERSION_5, ImmutableList.of("1.5", "5", "5.0"))
            .put(JavaVersion.VERSION_6, ImmutableList.of("1.6", "6", "6.0"))
            .put(JavaVersion.VERSION_7, ImmutableList.of("1.7", "7", "7.0"))
            .put(JavaVersion.VERSION_8, ImmutableList.of("8", "8.0"))
            .put(JavaVersion.VERSION_9, ImmutableList.of("9", "9.0"))
            .put(JavaVersion.VERSION_10, ImmutableList.of("10", "10.0"))
            .put(JavaVersion.VERSION_11, ImmutableList.of("11", "11.0", "11.0000"))
            .build();

    for (var e : javaVersions.entrySet()) {
      JavaVersion expectedVersion = e.getKey();
      for (String version : e.getValue()) {
        String localConfig =
            String.format("[java]\nsource_level = %s\ntarget_level = %s", version, version);

        JavaBuckConfig config = createWithDefaultFilesystem(new StringReader(localConfig));
        JavaVersion sourceLevelValue = config.getJavacLanguageLevelOptions().getSourceLevelValue();
        JavaVersion targetLevelValue = config.getJavacLanguageLevelOptions().getTargetLevelValue();

        assertThat(sourceLevelValue, equalTo(targetLevelValue));
        assertThat(targetLevelValue, equalTo(expectedVersion));
      }
    }
  }

  @Test
  public void shouldSetJavaTargetAndSourceVersionDefaultToSaneValues() throws IOException {
    JavaBuckConfig config = createWithDefaultFilesystem(new StringReader(""));

    JavacLanguageLevelOptions options = config.getJavacLanguageLevelOptions();

    assertEquals(TARGETED_JAVA_VERSION, options.getSourceLevel());
    assertEquals(TARGETED_JAVA_VERSION, options.getTargetLevel());
  }

  @Test
  public void shouldPopulateTheMapOfSourceLevelToBootclasspath() throws IOException {
    String localConfig = "[java]\nbootclasspath-6 = one.jar\nbootclasspath-7 = two.jar";
    JavaBuckConfig config = createWithDefaultFilesystem(new StringReader(localConfig));

    JavacOptions options = config.getDefaultJavacOptions(UnconfiguredTargetConfiguration.INSTANCE);

    JavacOptions jse5 =
        JavacOptions.builder(options)
            .setLanguageLevelOptions(
                JavacLanguageLevelOptions.builder().setSourceLevel("5").build())
            .build();
    JavacOptions jse6 =
        JavacOptions.builder(options)
            .setLanguageLevelOptions(
                JavacLanguageLevelOptions.builder().setSourceLevel("6").build())
            .build();
    JavacOptions jse_1_7 =
        JavacOptions.builder(options)
            .setLanguageLevelOptions(
                JavacLanguageLevelOptions.builder().setSourceLevel("1.7").build())
            .build();

    JavacOptions jse7 =
        JavacOptions.builder(options)
            .setLanguageLevelOptions(
                JavacLanguageLevelOptions.builder().setSourceLevel("7").build())
            .build();

    assertOptionKeyAbsent(jse5, "bootclasspath");
    assertOptionsContains(jse6, "bootclasspath", "one.jar");
    assertOptionsContains(jse_1_7, "bootclasspath", "two.jar");
    assertOptionsContains(jse7, "bootclasspath", "two.jar");
  }

  @Test
  public void whenJavacIsNotSetInBuckConfigConfiguredRulesCreateJavaLibraryRuleWithJsr199Javac()
      throws NoSuchBuildTargetException {
    BuckConfig buckConfig = FakeBuckConfig.empty();
    JavaBuckConfig javaConfig = buckConfig.getView(JavaBuckConfig.class);

    Javac javac =
        JavacFactoryHelper.createJavacFactory(javaConfig)
            .create(null, null, UnconfiguredTargetConfiguration.INSTANCE);
    assertTrue(javac.getClass().toString(), javac instanceof Jsr199Javac);
  }

  @Test
  public void whenJavacIsSetInBuckConfigConfiguredRulesCreateJavaLibraryRuleWithJavacSet()
      throws IOException {
    assumeThat(
        "Windows can't create a process for a fake executable.",
        Platform.detect(),
        is(not(Platform.WINDOWS)));

    final String javac = temporaryFolder.newExecutableFile().toString();

    ImmutableMap<String, ImmutableMap<String, String>> sections =
        ImmutableMap.of("tools", ImmutableMap.of("javac", javac));
    BuckConfig buckConfig =
        FakeBuckConfig.builder().setFilesystem(defaultFilesystem).setSections(sections).build();
    JavaBuckConfig javaConfig = buckConfig.getView(JavaBuckConfig.class);

    TestActionGraphBuilder ruleFinder = new TestActionGraphBuilder();
    assertEquals(
        javac,
        JavacFactoryHelper.createJavacFactory(javaConfig)
            .create(ruleFinder, null, UnconfiguredTargetConfiguration.INSTANCE)
            .resolve(ruleFinder.getSourcePathResolver(), defaultFilesystem.getRootPath())
            .getShortName());
  }

  @Test
  public void trackClassUsageCanBeDisabled() {
    JavaBuckConfig config =
        FakeBuckConfig.builder()
            .setSections(ImmutableMap.of("java", ImmutableMap.of("track_class_usage", "false")))
            .build()
            .getView(JavaBuckConfig.class);

    assumeThat(
        config.getJavacSpec(UnconfiguredTargetConfiguration.INSTANCE).getJavacSource(),
        is(ResolvedJavac.Source.JDK));
    assertFalse(config.trackClassUsage(UnconfiguredTargetConfiguration.INSTANCE));
  }

  @Test
  public void desugarInterfaceMethodsCanBeEnabled() {
    JavaBuckConfig config =
        FakeBuckConfig.builder()
            .setSections(
                ImmutableMap.of("java", ImmutableMap.of("desugar_interface_methods", "true")))
            .build()
            .getView(JavaBuckConfig.class);

    assertTrue(config.shouldDesugarInterfaceMethods());
  }

  @Test
  public void doNotTrackClassUsageByDefaultForExternJavac() throws IOException {
    JavaBuckConfig config =
        FakeBuckConfig.builder()
            .setFilesystem(defaultFilesystem)
            .setSections(
                ImmutableMap.of(
                    "tools",
                    ImmutableMap.of("javac", temporaryFolder.newExecutableFile().toString())))
            .build()
            .getView(JavaBuckConfig.class);

    assumeThat(
        config.getJavacSpec(UnconfiguredTargetConfiguration.INSTANCE).getJavacSource(),
        is(ResolvedJavac.Source.EXTERNAL));

    assertFalse(config.trackClassUsage(UnconfiguredTargetConfiguration.INSTANCE));
  }

  @Test
  public void doNotTrackClassUsageEvenIfAskedForExternJavac() throws IOException {
    JavaBuckConfig config =
        FakeBuckConfig.builder()
            .setFilesystem(defaultFilesystem)
            .setSections(
                ImmutableMap.of(
                    "tools",
                    ImmutableMap.of("javac", temporaryFolder.newExecutableFile().toString()),
                    "java",
                    ImmutableMap.of("track_class_usage", "true")))
            .build()
            .getView(JavaBuckConfig.class);

    assumeThat(
        config.getJavacSpec(UnconfiguredTargetConfiguration.INSTANCE).getJavacSource(),
        is(ResolvedJavac.Source.EXTERNAL));
    assertFalse(config.trackClassUsage(UnconfiguredTargetConfiguration.INSTANCE));
  }

  @Test
  public void trackClassUsageByDefaultForJavacFromJDK() {
    JavaBuckConfig config = FakeBuckConfig.empty().getView(JavaBuckConfig.class);

    assumeThat(
        config.getJavacSpec(UnconfiguredTargetConfiguration.INSTANCE).getJavacSource(),
        is(ResolvedJavac.Source.JDK));

    assertTrue(config.trackClassUsage(UnconfiguredTargetConfiguration.INSTANCE));
  }

  @Test
  public void testCompileFullJarsByDefault() throws IOException {
    JavaBuckConfig config = createWithDefaultFilesystem(new StringReader(""));
    assertThat(config.getAbiGenerationMode(), equalTo(AbiGenerationMode.CLASS));
  }

  @Test
  public void useDependencyOrderClasspathForTestsDefault() {
    JavaBuckConfig config = FakeBuckConfig.builder().build().getView(JavaBuckConfig.class);

    assertFalse(config.useDependencyOrderClasspathForTests());
  }

  @Test
  public void useDependencyOrderClasspathForTestsCanBeEnabled() {
    JavaBuckConfig config =
        FakeBuckConfig.builder()
            .setSections(
                ImmutableMap.of(
                    "java", ImmutableMap.of("use_dependency_order_classpath_for_tests", "true")))
            .build()
            .getView(JavaBuckConfig.class);

    assertTrue(config.useDependencyOrderClasspathForTests());
  }

  @Test
  public void useDependencyOrderClasspathForTestsCanBeDisabled() {
    JavaBuckConfig config =
        FakeBuckConfig.builder()
            .setSections(
                ImmutableMap.of(
                    "java", ImmutableMap.of("use_dependency_order_classpath_for_tests", "false")))
            .build()
            .getView(JavaBuckConfig.class);

    assertFalse(config.useDependencyOrderClasspathForTests());
  }

  private void assertOptionKeyAbsent(JavacOptions options, String key) {
    OptionAccumulator optionsConsumer = visitOptions(options);
    assertThat(optionsConsumer.keyVals, not(hasKey(key)));
  }

  private void assertOptionsContains(JavacOptions options, String key, String value) {
    OptionAccumulator optionsConsumer = visitOptions(options);
    assertThat(optionsConsumer.keyVals, hasEntry(key, value));
  }

  private OptionAccumulator visitOptions(JavacOptions options) {
    OptionAccumulator optionsConsumer = new OptionAccumulator();
    options.appendOptionsTo(
        optionsConsumer,
        new SourcePathResolverAdapter(
            new AbstractSourcePathResolver() {
              @Override
              protected ImmutableSortedSet<SourcePath> resolveDefaultBuildTargetSourcePath(
                  DefaultBuildTargetSourcePath targetSourcePath) {
                throw new UnsupportedOperationException();
              }

              @Override
              public String getSourcePathName(BuildTarget target, SourcePath sourcePath) {
                throw new UnsupportedOperationException();
              }

              @Override
              protected ProjectFilesystem getBuildTargetSourcePathFilesystem(
                  BuildTargetSourcePath sourcePath) {
                throw new UnsupportedOperationException();
              }
            }),
        defaultFilesystem.getRootPath());
    return optionsConsumer;
  }

  private JavaBuckConfig createWithDefaultFilesystem(Reader reader) throws IOException {
    BuckConfig raw =
        BuckConfigTestUtils.createFromReader(
            reader,
            defaultFilesystem,
            Architecture.detect(),
            Platform.detect(),
            EnvVariablesProvider.getSystemEnv());
    return raw.getView(JavaBuckConfig.class);
  }

  @Test
  public void disabledForWindowsIsFalseByDefault() {
    JavaBuckConfig javaBuckConfig =
        FakeBuckConfig.builder()
            .setSections(
                ImmutableMap.of(
                    JavaBuckConfig.SECTION,
                    ImmutableMap.of(
                        JavaBuckConfig.PROPERTY_JAVACD_ENABLED, Boolean.toString(true))))
            .build()
            .getView(JavaBuckConfig.class);
    assertThat(javaBuckConfig.isDisabledForWindows(), is(false));
    assertThat(javaBuckConfig.isJavaCDEnabled(), is(true));
  }

  @Test
  public void disabledForWindowsIfSet() {
    JavaBuckConfig javaBuckConfig =
        FakeBuckConfig.builder()
            .setSections(
                ImmutableMap.of(
                    JavaBuckConfig.SECTION,
                    ImmutableMap.of(
                        JavaBuckConfig.PROPERTY_JAVACD_ENABLED,
                        Boolean.toString(true),
                        JavaBuckConfig.PROPERTY_JAVACD_DISABLED_FOR_WINDOWS,
                        Boolean.toString(true))))
            .build()
            .getView(JavaBuckConfig.class);
    assertThat(javaBuckConfig.isDisabledForWindows(), is(true));
    boolean isWindows = Platform.detect() == Platform.WINDOWS;
    // enabled for everything except windows
    assertThat(javaBuckConfig.isJavaCDEnabled(), is(!isWindows));
  }
}

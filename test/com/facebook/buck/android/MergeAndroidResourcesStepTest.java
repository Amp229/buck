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

import static com.facebook.buck.android.aapt.RDotTxtEntry.RType.ATTR;
import static com.facebook.buck.android.aapt.RDotTxtEntry.RType.ID;
import static com.facebook.buck.android.aapt.RDotTxtEntry.RType.STYLEABLE;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import com.facebook.buck.android.aapt.RDotTxtEntry;
import com.facebook.buck.android.aapt.RDotTxtEntry.RType;
import com.facebook.buck.android.aapt.RDotTxtEntryUtil;
import com.facebook.buck.android.aapt.RDotTxtEntryUtil.FakeEntry;
import com.facebook.buck.android.resources.MergeAndroidResources;
import com.facebook.buck.android.resources.MergeAndroidResources.DuplicateResourceException;
import com.facebook.buck.core.build.execution.context.StepExecutionContext;
import com.facebook.buck.core.filesystems.AbsPath;
import com.facebook.buck.core.filesystems.RelPath;
import com.facebook.buck.core.model.BuildTarget;
import com.facebook.buck.core.model.BuildTargetFactory;
import com.facebook.buck.core.rules.ActionGraphBuilder;
import com.facebook.buck.core.rules.BuildRuleResolver;
import com.facebook.buck.core.rules.resolver.impl.TestActionGraphBuilder;
import com.facebook.buck.core.sourcepath.FakeSourcePath;
import com.facebook.buck.core.sourcepath.resolver.SourcePathResolverAdapter;
import com.facebook.buck.io.filesystem.impl.FakeProjectFilesystem;
import com.facebook.buck.io.filesystem.impl.ProjectFilesystemUtils;
import com.facebook.buck.parser.exceptions.NoSuchBuildTargetException;
import com.facebook.buck.step.StepExecutionResult;
import com.facebook.buck.step.TestExecutionContext;
import com.facebook.buck.testutil.TemporaryPaths;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.SortedSetMultimap;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.hamcrest.CoreMatchers;
import org.hamcrest.Matchers;
import org.hamcrest.core.StringContains;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class MergeAndroidResourcesStepTest {
  @Rule public ExpectedException thrown = ExpectedException.none();
  @Rule public TemporaryPaths tmpFolder = new TemporaryPaths();

  private FakeProjectFilesystem filesystem;

  @Before
  public void setUp() throws NoSuchBuildTargetException {
    filesystem = new FakeProjectFilesystem(tmpFolder.getRoot());
  }

  @Test
  public void testGenerateRDotJavaForWithStyleables()
      throws DuplicateResourceException, IOException {
    RDotTxtEntryBuilder entriesBuilder = new RDotTxtEntryBuilder(filesystem);

    // Merge everything into the same package space.
    String sharedPackageName = "com.facebook.abc";
    entriesBuilder.add(
        new RDotTxtFile(
            sharedPackageName,
            ProjectFilesystemUtils.getPathForRelativePath(filesystem.getRootPath(), "a-R.txt")
                .getPath(),
            ImmutableList.of(
                "int attr android_layout 0x010100f2",
                "int attr buttonPanelSideLayout 0x7f01003a",
                "int attr listLayout 0x7f01003b",
                "int[] styleable AlertDialog { 0x7f01003a, 0x7f01003b, 0x010100f2 }",
                "int styleable AlertDialog_android_layout 2",
                "int styleable AlertDialog_buttonPanelSideLayout 0",
                "int styleable AlertDialog_multiChoiceItemLayout 1")));
    entriesBuilder.add(
        new RDotTxtFile(
            sharedPackageName,
            ProjectFilesystemUtils.getPathForRelativePath(filesystem.getRootPath(), "b-R.txt")
                .getPath(),
            ImmutableList.of(
                "int id a1 0x7f010001",
                "int id a2 0x7f010002",
                "int attr android_layout_gravity 0x7f078008",
                "int attr background 0x7f078009",
                "int attr backgroundSplit 0x7f078008",
                "int attr backgroundStacked 0x7f078010",
                "int attr layout_heightPercent 0x7f078012",
                "int[] styleable ActionBar {  }",
                "int styleable ActionBar_background 10",
                "int styleable ActionBar_backgroundSplit 12",
                "int styleable ActionBar_backgroundStacked 11",
                "int[] styleable ActionBarLayout { 0x7f060008 }",
                "int styleable ActionBarLayout_android_layout 0",
                "int styleable ActionBarLayout_android_layout_gravity 1",
                "int[] styleable PercentLayout_Layout { }",
                "int styleable PercentLayout_Layout_layout_aspectRatio 9",
                "int styleable PercentLayout_Layout_layout_heightPercent 1")));

    SortedSetMultimap<String, RDotTxtEntry> packageNameToResources =
        MergeAndroidResources.sortSymbols(
            entriesBuilder.buildFilePathToPackageNameSet(),
            Optional.empty(),
            ImmutableMap.of(),
            Optional.empty(),
            /* bannedDuplicateResourceTypes */ EnumSet.noneOf(RType.class),
            ImmutableSet.of());

    assertEquals(24, packageNameToResources.size());

    ArrayList<RDotTxtEntry> resources =
        new ArrayList<>(packageNameToResources.get(sharedPackageName));
    assertEquals(24, resources.size());

    System.out.println(resources);

    ImmutableSet<RDotTxtEntry> fakeRDotTxtEntryWithIDS =
        ImmutableSet.of(
            FakeEntry.create(RDotTxtEntry.IdType.INT, ATTR, "android_layout"),
            FakeEntry.create(RDotTxtEntry.IdType.INT, ATTR, "android_layout_gravity"),
            FakeEntry.create(RDotTxtEntry.IdType.INT, ATTR, "background"),
            FakeEntry.create(RDotTxtEntry.IdType.INT, ATTR, "backgroundSplit"),
            FakeEntry.create(RDotTxtEntry.IdType.INT, ATTR, "backgroundStacked"),
            FakeEntry.create(RDotTxtEntry.IdType.INT, ATTR, "buttonPanelSideLayout"),
            FakeEntry.create(RDotTxtEntry.IdType.INT, ATTR, "layout_heightPercent"),
            FakeEntry.create(RDotTxtEntry.IdType.INT, ATTR, "listLayout"),
            FakeEntry.create(RDotTxtEntry.IdType.INT, ID, "a1"),
            FakeEntry.create(RDotTxtEntry.IdType.INT, ID, "a2"),
            FakeEntry.create(RDotTxtEntry.IdType.INT_ARRAY, STYLEABLE, "ActionBar"),
            FakeEntry.create(RDotTxtEntry.IdType.INT, STYLEABLE, "ActionBar_background"),
            FakeEntry.create(RDotTxtEntry.IdType.INT, STYLEABLE, "ActionBar_backgroundSplit"),
            FakeEntry.create(RDotTxtEntry.IdType.INT, STYLEABLE, "ActionBar_backgroundStacked"),
            FakeEntry.create(RDotTxtEntry.IdType.INT_ARRAY, STYLEABLE, "ActionBarLayout"),
            FakeEntry.create(RDotTxtEntry.IdType.INT, STYLEABLE, "ActionBarLayout_android_layout"),
            FakeEntry.create(
                RDotTxtEntry.IdType.INT, STYLEABLE, "ActionBarLayout_android_layout_gravity"),
            FakeEntry.create(RDotTxtEntry.IdType.INT_ARRAY, STYLEABLE, "AlertDialog"),
            FakeEntry.create(RDotTxtEntry.IdType.INT, STYLEABLE, "AlertDialog_android_layout"),
            FakeEntry.create(
                RDotTxtEntry.IdType.INT, STYLEABLE, "AlertDialog_buttonPanelSideLayout"),
            FakeEntry.create(
                RDotTxtEntry.IdType.INT, STYLEABLE, "AlertDialog_multiChoiceItemLayout"),
            FakeEntry.create(RDotTxtEntry.IdType.INT_ARRAY, STYLEABLE, "PercentLayout_Layout"),
            FakeEntry.create(
                RDotTxtEntry.IdType.INT, STYLEABLE, "PercentLayout_Layout_layout_aspectRatio"),
            FakeEntry.create(
                RDotTxtEntry.IdType.INT, STYLEABLE, "PercentLayout_Layout_layout_heightPercent"));

    assertEquals(createTestingFakesWithoutIds(resources), fakeRDotTxtEntryWithIDS);
  }

  private Set<RDotTxtEntry> createTestingFakesWithoutIds(List<RDotTxtEntry> ls) {
    return ls.stream().map(RDotTxtEntryUtil::matchDefault).collect(Collectors.toSet());
  }

  @Test
  public void testGenerateRDotJavaForMultipleSymbolsFilesWithDuplicates()
      throws DuplicateResourceException, IOException {
    RDotTxtEntryBuilder entriesBuilder = new RDotTxtEntryBuilder(filesystem);

    // Merge everything into the same package space.
    String sharedPackageName = "com.facebook.abc";
    entriesBuilder.add(
        new RDotTxtFile(
            sharedPackageName,
            ProjectFilesystemUtils.getPathForRelativePath(filesystem.getRootPath(), "a-R.txt")
                .getPath(),
            ImmutableList.of("int id a1 0x7f010001", "int string a1 0x7f020001")));

    entriesBuilder.add(
        new RDotTxtFile(
            sharedPackageName,
            ProjectFilesystemUtils.getPathForRelativePath(filesystem.getRootPath(), "b-R.txt")
                .getPath(),
            ImmutableList.of(
                "int id a1 0x7f010001", "int string a1 0x7f010002", "int string c1 0x7f010003")));

    entriesBuilder.add(
        new RDotTxtFile(
            sharedPackageName,
            ProjectFilesystemUtils.getPathForRelativePath(filesystem.getRootPath(), "c-R.txt")
                .getPath(),
            ImmutableList.of(
                "int id a1 0x7f010001",
                "int string a1 0x7f010002",
                "int string b1 0x7f010003",
                "int string c1 0x7f010004")));

    thrown.expect(MergeAndroidResources.DuplicateResourceException.class);
    thrown.expectMessage("Resource 'a1' (string) is duplicated across: ");
    thrown.expectMessage("Resource 'c1' (string) is duplicated across: ");

    MergeAndroidResources.sortSymbols(
        entriesBuilder.buildFilePathToPackageNameSet(),
        Optional.empty(),
        ImmutableMap.of(
            filesystem.getPath("a-R.txt"), "//:resA",
            filesystem.getPath("b-R.txt"), "//:resB",
            filesystem.getPath("c-R.txt"), "//:resC"),
        Optional.empty(),
        /* bannedDuplicateResourceTypes */ EnumSet.of(RType.STRING),
        ImmutableSet.of());
  }

  @Test
  public void testGenerateRDotJavaForLibrary() throws Exception {
    BuildTarget resTarget = BuildTargetFactory.newInstance("//:res1");

    RDotTxtEntryBuilder entriesBuilder = new RDotTxtEntryBuilder(filesystem);

    ActionGraphBuilder graphBuilder = new TestActionGraphBuilder();

    AndroidResource res =
        AndroidResourceRuleBuilder.newBuilder()
            .setRuleFinder(graphBuilder)
            .setBuildTarget(resTarget)
            .setRes(FakeSourcePath.of("res"))
            .setRDotJavaPackage("com.res1")
            .build();
    graphBuilder.addToIndex(res);

    entriesBuilder.add(
        new RDotTxtFile(
            "com.res1",
            graphBuilder
                .getSourcePathResolver()
                .getRelativePath(filesystem, res.getPathToTextSymbolsFile())
                .getPath()
                .toString(),
            ImmutableList.of("int id id1 0x7f020000")));

    MergeAndroidResourcesStep mergeStep =
        MergeAndroidResourcesStep.createStepForDummyRDotJava(
            graphBuilder.getSourcePathResolver(),
            ImmutableList.of(res),
            ProjectFilesystemUtils.getPathForRelativePath(
                filesystem.getRootPath(), Paths.get("output")),
            /* forceFinalResourceIds */ false,
            /* unionPackage */ Optional.empty());

    StepExecutionContext executionContext = TestExecutionContext.newInstance();

    assertEquals(0, mergeStep.execute(executionContext).getExitCode());

    // Verify that the correct Java code is generated.
    assertThat(
        ProjectFilesystemUtils.readFileIfItExists(
                filesystem.getRootPath(), Paths.get("output/com/res1/R.java"))
            .get(),
        CoreMatchers.containsString("{\n    public static int id1=0x7f020000;"));
  }

  @Test
  public void testGenerateRDotJavaForOneSymbolsFile() throws Exception {
    BuildTarget target = BuildTargetFactory.newInstance("//android_res/com/facebook/http:res");
    ActionGraphBuilder graphBuilder = new TestActionGraphBuilder();

    AndroidResource resource =
        AndroidResourceRuleBuilder.newBuilder()
            .setRuleFinder(graphBuilder)
            .setBuildTarget(target)
            .setRes(FakeSourcePath.of("res"))
            .setRDotJavaPackage("com.facebook")
            .build();
    graphBuilder.addToIndex(resource);

    RDotTxtEntryBuilder entriesBuilder = new RDotTxtEntryBuilder(filesystem);
    Path symbolsFile =
        graphBuilder
            .getSourcePathResolver()
            .getRelativePath(filesystem, resource.getPathToTextSymbolsFile())
            .getPath();
    String rDotJavaPackage = "com.facebook";
    ImmutableList<String> outputTextSymbols =
        ImmutableList.<String>builder()
            .add("int id placeholder 0x7f020000")
            .add("int string debug_http_proxy_dialog_title 0x7f030004")
            .add("int string debug_http_proxy_hint 0x7f030005")
            .add("int string debug_http_proxy_summary 0x7f030003")
            .add("int string debug_http_proxy_title 0x7f030002")
            .add("int string debug_ssl_cert_check_summary 0x7f030001")
            .add("int string debug_ssl_cert_check_title 0x7f030000")
            .add("int styleable SherlockMenuItem_android_visible 4")
            .add(
                "int[] styleable SherlockMenuView { 0x7f010026, 0x7f010027, 0x7f010028, 0x7f010029, "
                    + "0x7f01002a, 0x7f01002b, 0x7f01002c, 0x7f01002d }")
            .build();
    entriesBuilder.add(new RDotTxtFile(rDotJavaPackage, symbolsFile, outputTextSymbols));

    AbsPath uberRDotTxt =
        ProjectFilesystemUtils.getPathForRelativePath(filesystem.getRootPath(), "R.txt");
    ProjectFilesystemUtils.writeLinesToPath(
        filesystem.getRootPath(), outputTextSymbols, uberRDotTxt.getPath());

    MergeAndroidResourcesStep mergeStep =
        new MergeAndroidResourcesStep(
            graphBuilder.getSourcePathResolver(),
            ImmutableList.of(resource),
            ImmutableList.of(uberRDotTxt.getPath()),
            ProjectFilesystemUtils.getPathForRelativePath(
                filesystem.getRootPath(), Paths.get("output")),
            /* forceFinalResourceIds */ true,
            /* bannedDuplicateResourceTypes */ EnumSet.noneOf(RType.class),
            /* filteredResourcesProvider */ Optional.empty(),
            /* overrideSymbolsPath */ ImmutableList.of(),
            /* unionPackage */ Optional.empty());

    StepExecutionContext executionContext = TestExecutionContext.newInstance();

    assertEquals(0, mergeStep.execute(executionContext).getExitCode());

    // Verify that the correct Java code is generated.
    assertEquals(
        "package com.facebook;\n"
            + "\n"
            + "public class R {\n"
            + "  public static class id {\n"
            + "    public static final int placeholder=0x7f020000;\n"
            + "  }\n"
            + "\n"
            + "  public static class string {\n"
            + "    public static final int debug_http_proxy_dialog_title=0x7f030004;\n"
            + "    public static final int debug_http_proxy_hint=0x7f030005;\n"
            + "    public static final int debug_http_proxy_summary=0x7f030003;\n"
            + "    public static final int debug_http_proxy_title=0x7f030002;\n"
            + "    public static final int debug_ssl_cert_check_summary=0x7f030001;\n"
            + "    public static final int debug_ssl_cert_check_title=0x7f030000;\n"
            + "  }\n"
            + "\n"
            + "  public static class styleable {\n"
            + "    public static final int SherlockMenuItem_android_visible=4;\n"
            + "    public static final int[] SherlockMenuView={ 0x7f010026, 0x7f010027, 0x7f010028, "
            + "0x7f010029, 0x7f01002a, 0x7f01002b, 0x7f01002c, 0x7f01002d };\n"
            + "  }\n"
            + "\n"
            + "}\n",
        ProjectFilesystemUtils.readFileIfItExists(
                filesystem.getRootPath(), Paths.get("output/com/facebook/R.java"))
            .get()
            .replace("\r", ""));
  }

  @Test
  public void testGenerateRDotJavaForCustomDrawables() throws Exception {
    BuildTarget target = BuildTargetFactory.newInstance("//android_res/com/facebook/http:res");

    ActionGraphBuilder graphBuilder = new TestActionGraphBuilder();

    AndroidResource resource =
        AndroidResourceRuleBuilder.newBuilder()
            .setRuleFinder(graphBuilder)
            .setBuildTarget(target)
            .setRes(FakeSourcePath.of("res"))
            .setRDotJavaPackage("com.facebook")
            .build();
    graphBuilder.addToIndex(resource);

    RDotTxtEntryBuilder entriesBuilder = new RDotTxtEntryBuilder(filesystem);
    Path symbolsFile =
        graphBuilder
            .getSourcePathResolver()
            .getRelativePath(filesystem, resource.getPathToTextSymbolsFile())
            .getPath();
    String rDotJavaPackage = "com.facebook";
    ImmutableList<String> outputTextSymbols =
        ImmutableList.<String>builder()
            .add("int drawable android_drawable 0x7f010000")
            .add("int drawable fb_drawable 0x7f010001 #")
            .build();
    entriesBuilder.add(new RDotTxtFile(rDotJavaPackage, symbolsFile, outputTextSymbols));

    AbsPath uberRDotTxt =
        ProjectFilesystemUtils.getPathForRelativePath(filesystem.getRootPath(), "R.txt");
    ProjectFilesystemUtils.writeLinesToPath(
        filesystem.getRootPath(), outputTextSymbols, uberRDotTxt.getPath());

    MergeAndroidResourcesStep mergeStep =
        new MergeAndroidResourcesStep(
            graphBuilder.getSourcePathResolver(),
            ImmutableList.of(resource),
            ImmutableList.of(uberRDotTxt.getPath()),
            ProjectFilesystemUtils.getPathForRelativePath(
                filesystem.getRootPath(), Paths.get("output")),
            /* forceFinalResourceIds */ true,
            /* bannedDuplicateResourceTypes */ EnumSet.noneOf(RType.class),
            /* filteredResourcesProvider */ Optional.empty(),
            /* overrideSymbolsPath */ ImmutableList.of(),
            /* unionPackage */ Optional.empty());

    StepExecutionContext executionContext = TestExecutionContext.newInstance();

    assertEquals(0, mergeStep.execute(executionContext).getExitCode());

    // Verify that the correct Java code is generated.
    assertEquals(
        "package com.facebook;\n"
            + "\n"
            + "public class R {\n"
            + "  public static class drawable {\n"
            + "    public static final int android_drawable=0x7f010000;\n"
            + "    public static final int fb_drawable=0x7f010001;\n"
            + "  }\n"
            + "\n"
            + "  public static final int[] custom_drawables = { 0x7f010001 };\n"
            + "\n"
            + "}\n",
        ProjectFilesystemUtils.readFileIfItExists(
                filesystem.getRootPath(), Paths.get("output/com/facebook/R.java"))
            .get()
            .replace("\r", ""));
  }

  @Test
  public void testGetRDotJavaFilesWithoutSkipPrebuiltRDotJava() {
    BuildTarget res1Target = BuildTargetFactory.newInstance("//:res1");
    BuildTarget res2Target = BuildTargetFactory.newInstance("//:res2");

    BuildRuleResolver buildRuleResolver = new TestActionGraphBuilder();

    AndroidResource res1 =
        AndroidResourceRuleBuilder.newBuilder()
            .setRuleFinder(buildRuleResolver)
            .setBuildTarget(res1Target)
            .setRes(FakeSourcePath.of("res1"))
            .setRDotJavaPackage("com.package1")
            .build();

    AndroidResource res2 =
        AndroidResourceRuleBuilder.newBuilder()
            .setRuleFinder(buildRuleResolver)
            .setBuildTarget(res2Target)
            .setRes(FakeSourcePath.of("res2"))
            .setRDotJavaPackage("com.package2")
            .build();

    ImmutableList<HasAndroidResourceDeps> resourceDeps = ImmutableList.of(res1, res2);
    Path outputDir =
        ProjectFilesystemUtils.getPathForRelativePath(
            filesystem.getRootPath(), Paths.get("output"));
    MergeAndroidResourcesStep mergeStep =
        MergeAndroidResourcesStep.createStepForDummyRDotJava(
            buildRuleResolver.getSourcePathResolver(),
            resourceDeps,
            outputDir,
            /* forceFinalResourceIds */ false,
            Optional.of("com.package"));

    ImmutableSortedSet<RelPath> rDotJavaFiles = mergeStep.getRDotJavaFiles(filesystem);
    assertEquals(rDotJavaFiles.size(), 3);

    ImmutableSortedSet<RelPath> expected =
        ImmutableSortedSet.orderedBy(RelPath.comparator())
            .add(
                ProjectFilesystemUtils.relativize(
                    filesystem.getRootPath(),
                    MergeAndroidResources.getPathToRDotJava(outputDir, "com.package")))
            .add(
                ProjectFilesystemUtils.relativize(
                    filesystem.getRootPath(),
                    MergeAndroidResources.getPathToRDotJava(outputDir, "com.package1")))
            .add(
                ProjectFilesystemUtils.relativize(
                    filesystem.getRootPath(),
                    MergeAndroidResources.getPathToRDotJava(outputDir, "com.package2")))
            .build();

    assertEquals(expected, rDotJavaFiles);
  }

  @Test
  public void testGenerateRDotJavaWithResourceUnionPackage() throws Exception {
    BuildTarget res1Target = BuildTargetFactory.newInstance("//:res1");
    BuildTarget res2Target = BuildTargetFactory.newInstance("//:res2");

    ActionGraphBuilder graphBuilder = new TestActionGraphBuilder();

    AndroidResource res1 =
        AndroidResourceRuleBuilder.newBuilder()
            .setRuleFinder(graphBuilder)
            .setBuildTarget(res1Target)
            .setRes(FakeSourcePath.of("res1"))
            .setRDotJavaPackage("res1")
            .build();
    graphBuilder.addToIndex(res1);

    AndroidResource res2 =
        AndroidResourceRuleBuilder.newBuilder()
            .setRuleFinder(graphBuilder)
            .setBuildTarget(res2Target)
            .setRes(FakeSourcePath.of("res2"))
            .setRDotJavaPackage("res2")
            .build();
    graphBuilder.addToIndex(res2);

    RDotTxtEntryBuilder entriesBuilder = new RDotTxtEntryBuilder(filesystem);
    entriesBuilder.add(
        new RDotTxtFile(
            "com.res1",
            graphBuilder
                .getSourcePathResolver()
                .getRelativePath(filesystem, res1.getPathToTextSymbolsFile())
                .getPath(),
            ImmutableList.of("int id id1 0x7f020000")));
    entriesBuilder.add(
        new RDotTxtFile(
            "com.res2",
            graphBuilder
                .getSourcePathResolver()
                .getRelativePath(filesystem, res2.getPathToTextSymbolsFile())
                .getPath(),
            ImmutableList.of("int id id2 0x7f020000")));

    MergeAndroidResourcesStep mergeStep =
        MergeAndroidResourcesStep.createStepForDummyRDotJava(
            graphBuilder.getSourcePathResolver(),
            ImmutableList.of(res1, res2),
            ProjectFilesystemUtils.getPathForRelativePath(
                filesystem.getRootPath(), Paths.get("output")),
            /* forceFinalResourceIds */ false,
            Optional.of("res1"));

    StepExecutionContext executionContext = TestExecutionContext.newInstance();

    assertEquals(0, mergeStep.execute(executionContext).getExitCode());

    String res1java =
        ProjectFilesystemUtils.readFileIfItExists(
                filesystem.getRootPath(), Paths.get("output/res1/R.java"))
            .get();
    String res2java =
        ProjectFilesystemUtils.readFileIfItExists(
                filesystem.getRootPath(), Paths.get("output/res2/R.java"))
            .get();
    assertThat(res1java, StringContains.containsString("id1"));
    assertThat(res1java, StringContains.containsString("id2"));
    assertThat(res2java, CoreMatchers.not(StringContains.containsString("id1")));
    assertThat(res2java, StringContains.containsString("id2"));
  }

  @Test
  public void testGenerateRDotJavaWithPreviouslyEmptyResourceUnionPackage() throws Exception {
    BuildTarget res1Target = BuildTargetFactory.newInstance("//:res1");
    ActionGraphBuilder graphBuilder = new TestActionGraphBuilder();

    AndroidResource res1 =
        AndroidResourceRuleBuilder.newBuilder()
            .setRuleFinder(graphBuilder)
            .setBuildTarget(res1Target)
            .setRes(FakeSourcePath.of("res1"))
            .setRDotJavaPackage("res1")
            .build();
    graphBuilder.addToIndex(res1);

    RDotTxtEntryBuilder entriesBuilder = new RDotTxtEntryBuilder(filesystem);
    entriesBuilder.add(
        new RDotTxtFile(
            "com.res1",
            graphBuilder
                .getSourcePathResolver()
                .getRelativePath(filesystem, res1.getPathToTextSymbolsFile())
                .getPath(),
            ImmutableList.of("int id id1 0x7f020000")));

    MergeAndroidResourcesStep mergeStep =
        MergeAndroidResourcesStep.createStepForDummyRDotJava(
            graphBuilder.getSourcePathResolver(),
            ImmutableList.of(res1),
            ProjectFilesystemUtils.getPathForRelativePath(
                filesystem.getRootPath(), Paths.get("output")),
            /* forceFinalResourceIds */ false,
            Optional.of("resM"));

    StepExecutionContext executionContext = TestExecutionContext.newInstance();

    assertEquals(0, mergeStep.execute(executionContext).getExitCode());

    String res1java =
        ProjectFilesystemUtils.readFileIfItExists(
                filesystem.getRootPath(), Paths.get("output/res1/R.java"))
            .get();
    String resMjava =
        ProjectFilesystemUtils.readFileIfItExists(
                filesystem.getRootPath(), Paths.get("output/resM/R.java"))
            .get();
    assertThat(res1java, StringContains.containsString("id1"));
    assertThat(resMjava, StringContains.containsString("id1"));
  }

  @Test
  public void testDuplicateBanning() throws Exception {
    BuildTarget res1Target = BuildTargetFactory.newInstance("//:res1");
    BuildTarget res2Target = BuildTargetFactory.newInstance("//:res2");

    ActionGraphBuilder graphBuilder = new TestActionGraphBuilder();
    AndroidResource res1 =
        AndroidResourceRuleBuilder.newBuilder()
            .setRuleFinder(graphBuilder)
            .setBuildTarget(res1Target)
            .setRes(FakeSourcePath.of("res1"))
            .setRDotJavaPackage("package")
            .build();
    graphBuilder.addToIndex(res1);

    AndroidResource res2 =
        AndroidResourceRuleBuilder.newBuilder()
            .setRuleFinder(graphBuilder)
            .setBuildTarget(res2Target)
            .setRes(FakeSourcePath.of("res2"))
            .setRDotJavaPackage("package")
            .build();
    graphBuilder.addToIndex(res2);

    RDotTxtEntryBuilder entriesBuilder = new RDotTxtEntryBuilder(filesystem);
    entriesBuilder.add(
        new RDotTxtFile(
            "package",
            graphBuilder
                .getSourcePathResolver()
                .getRelativePath(filesystem, res1.getPathToTextSymbolsFile())
                .getPath(),
            ImmutableList.of(
                "int string app_name 0x7f020000", "int drawable android_drawable 0x7f010000")));
    entriesBuilder.add(
        new RDotTxtFile(
            "package",
            graphBuilder
                .getSourcePathResolver()
                .getRelativePath(filesystem, res2.getPathToTextSymbolsFile())
                .getPath(),
            ImmutableList.of(
                "int string app_name 0x7f020000", "int drawable android_drawable 0x7f010000")));

    ImmutableList<HasAndroidResourceDeps> resourceDeps = ImmutableList.of(res1, res2);

    checkDuplicatesDetected(
        graphBuilder.getSourcePathResolver(),
        filesystem,
        resourceDeps,
        EnumSet.noneOf(RType.class),
        ImmutableList.of(),
        ImmutableList.of("app_name", "android_drawable"),
        Optional.empty());

    checkDuplicatesDetected(
        graphBuilder.getSourcePathResolver(),
        filesystem,
        resourceDeps,
        EnumSet.of(RType.STRING),
        ImmutableList.of("app_name"),
        ImmutableList.of("android_drawable"),
        Optional.empty());

    checkDuplicatesDetected(
        graphBuilder.getSourcePathResolver(),
        filesystem,
        resourceDeps,
        EnumSet.allOf(RType.class),
        ImmutableList.of("app_name", "android_drawable"),
        ImmutableList.of(),
        Optional.empty());

    checkDuplicatesDetected(
        graphBuilder.getSourcePathResolver(),
        filesystem,
        resourceDeps,
        EnumSet.allOf(RType.class),
        ImmutableList.of("android_drawable"),
        ImmutableList.of("app_name"),
        Optional.of(ImmutableList.of("string app_name", "color android_drawable")));
  }

  private void checkDuplicatesDetected(
      SourcePathResolverAdapter resolver,
      FakeProjectFilesystem filesystem,
      ImmutableList<HasAndroidResourceDeps> resourceDeps,
      EnumSet<RType> rtypes,
      ImmutableList<String> duplicateResources,
      ImmutableList<String> ignoredDuplicates,
      Optional<List<String>> duplicateWhitelist)
      throws IOException {

    Optional<Path> duplicateWhitelistPath =
        duplicateWhitelist.map(
            whitelist -> {
              AbsPath whitelistPath =
                  ProjectFilesystemUtils.getPathForRelativePath(
                      filesystem.getRootPath(), "duplicate-whitelist.txt");
              try {
                ProjectFilesystemUtils.writeLinesToPath(
                    filesystem.getRootPath(), whitelist, whitelistPath.getPath());
              } catch (IOException e) {
                throw new RuntimeException(e);
              }
              return whitelistPath.getPath();
            });

    MergeAndroidResourcesStep mergeStep =
        new MergeAndroidResourcesStep(
            resolver,
            resourceDeps,
            /* uberRDotTxt */ ImmutableList.of(),
            ProjectFilesystemUtils.getPathForRelativePath(
                filesystem.getRootPath(), Paths.get("output")),
            true,
            rtypes,
            duplicateWhitelistPath,
            /* overrideSymbolsPath */ ImmutableList.of(),
            Optional.empty());

    StepExecutionResult result = mergeStep.execute(TestExecutionContext.newInstance());
    String message = result.getStderr().orElse("");
    if (duplicateResources.isEmpty()) {
      assertEquals(0, result.getExitCode());
    } else {
      assertNotEquals(0, result.getExitCode());
      assertThat(message, Matchers.containsString("duplicated"));
    }
    for (String duplicateResource : duplicateResources) {
      assertThat(message, Matchers.containsString(duplicateResource));
    }
    for (String ignoredDuplicate : ignoredDuplicates) {
      assertThat(message, Matchers.not(Matchers.containsString(ignoredDuplicate)));
    }
  }

  // sortSymbols has a goofy API.  This will help.
  private static class RDotTxtEntryBuilder {
    private final FakeProjectFilesystem filesystem;
    private final ImmutableMap.Builder<Path, String> filePathToPackageName = ImmutableMap.builder();

    RDotTxtEntryBuilder(FakeProjectFilesystem filesystem) {
      this.filesystem = filesystem;
    }

    public void add(RDotTxtFile entry) throws IOException {
      if (entry.filePath.getParent() != null) {
        ProjectFilesystemUtils.mkdirs(filesystem.getRootPath(), entry.filePath.getParent());
      }
      ProjectFilesystemUtils.writeLinesToPath(
          filesystem.getRootPath(), entry.contents, entry.filePath);
      filePathToPackageName.put(entry.filePath, entry.packageName);
    }

    Map<Path, String> buildFilePathToPackageNameSet() {
      return filePathToPackageName.build();
    }

    public FakeProjectFilesystem getProjectFilesystem() {
      return filesystem;
    }
  }

  static class RDotTxtFile {
    public final ImmutableList<String> contents;

    final String packageName;
    final Path filePath;

    RDotTxtFile(String packageName, String filePath, ImmutableList<String> contents) {
      this(packageName, Paths.get(filePath), contents);
    }

    RDotTxtFile(String packageName, Path filePath, ImmutableList<String> contents) {
      this.packageName = packageName;
      this.filePath = filePath;
      this.contents = contents;
    }
  }
}

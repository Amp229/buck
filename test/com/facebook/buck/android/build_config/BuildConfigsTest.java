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

package com.facebook.buck.android.build_config;

import static org.junit.Assert.assertEquals;

import com.facebook.buck.core.model.BuildTargetFactory;
import com.facebook.buck.core.model.UnflavoredBuildTarget;
import com.google.common.collect.ImmutableList;
import org.junit.Test;

/** Unit test for {@link BuildConfigs}. */
public class BuildConfigsTest {

  @Test
  public void testDefaultGenerateBuildConfigDotJava() {
    String expectedJavaCode =
        "// Generated by //java/com/example:build_config. DO NOT MODIFY.\n"
            + "package com.example.buck;\n"
            + "public class BuildConfig {\n"
            + "  private BuildConfig() {}\n"
            + "  public static final boolean DEBUG = !Boolean.parseBoolean(null);\n"
            + "  public static final boolean IS_EXOPACKAGE = Boolean.parseBoolean(null);\n"
            + "  public static final int EXOPACKAGE_FLAGS = !Boolean.parseBoolean(null) ? 0 : 0;\n"
            + "}\n";
    UnflavoredBuildTarget source =
        BuildTargetFactory.newInstance("//java/com/example:build_config")
            .getUnflavoredBuildTarget();
    String observedJavaCode =
        BuildConfigs.generateBuildConfigDotJava(
            source.toString(), "com.example.buck", false, BuildConfigFields.of());
    assertEquals(
        "Because the 'temporary' BuildConfig.java might be used in a unit test, "
            + "DEBUG should default to true, IS_EXOPACKAGE should default to false and ."
            + "EXOPACKAGE_FLAGS should default to an empty HashSet.",
        expectedJavaCode,
        observedJavaCode);
  }

  @Test
  public void testCustomGenerateBuildConfigDotJavaWithoutConstantExpressions() {
    BuildConfigFields customValues =
        BuildConfigFields.fromFieldDeclarations(
            ImmutableList.of(
                "String KEYSTORE_TYPE = \"release\"",
                "int BUILD_NUMBER = 42",
                "long BUILD_DATE = 1404321113076000L",
                "float THREE = 3.0F",
                "boolean DEBUG = false",
                "boolean IS_EXOPACKAGE = true"));
    String expectedJavaCode =
        "// Generated by //java/com/example:build_config. DO NOT MODIFY.\n"
            + "package com.example;\n"
            + "public class BuildConfig {\n"
            + "  private BuildConfig() {}\n"
            + "  public static final String KEYSTORE_TYPE = "
            + "!Boolean.parseBoolean(null) ? \"release\" : null;\n"
            + "  public static final int BUILD_NUMBER = "
            + "!Boolean.parseBoolean(null) ? 42 : 0;\n"
            + "  public static final long BUILD_DATE = "
            + "!Boolean.parseBoolean(null) ? 1404321113076000L : 0;\n"
            + "  public static final float THREE = "
            + "!Boolean.parseBoolean(null) ? 3.0F : 0;\n"
            + "  public static final boolean DEBUG = Boolean.parseBoolean(null);\n"
            + "  public static final boolean IS_EXOPACKAGE = !Boolean.parseBoolean(null);\n"
            + "  public static final int EXOPACKAGE_FLAGS = !Boolean.parseBoolean(null) ? 0 : 0;\n"
            + "}\n";
    UnflavoredBuildTarget source =
        BuildTargetFactory.newInstance("//java/com/example:build_config")
            .getUnflavoredBuildTarget();
    String observedJavaCode =
        BuildConfigs.generateBuildConfigDotJava(
            source.toString(), "com.example", /* useConstantExpressions */ false, customValues);
    assertEquals(expectedJavaCode, observedJavaCode);
  }

  @Test
  public void testCustomGenerateBuildConfigDotJavaWithConstantExpressions() {
    BuildConfigFields customValues =
        BuildConfigFields.fromFieldDeclarations(
            ImmutableList.of(
                "String KEYSTORE_TYPE = \"release\"",
                "int BUILD_NUMBER = 42",
                "long BUILD_DATE = 1404321113076000L",
                "float THREE = 3.0F",
                "boolean DEBUG = false",
                "int EXOPACKAGE_FLAGS = 0"));
    String expectedJavaCode =
        "// Generated by //java/com/example:build_config. DO NOT MODIFY.\n"
            + "package com.example;\n"
            + "public class BuildConfig {\n"
            + "  private BuildConfig() {}\n"
            + "  public static final String KEYSTORE_TYPE = \"release\";\n"
            + "  public static final int BUILD_NUMBER = 42;\n"
            + "  public static final long BUILD_DATE = 1404321113076000L;\n"
            + "  public static final float THREE = 3.0F;\n"
            + "  public static final boolean DEBUG = false;\n"
            + "  public static final int EXOPACKAGE_FLAGS = 0;\n"
            + "  public static final boolean IS_EXOPACKAGE = false;\n"
            + "}\n";
    UnflavoredBuildTarget source =
        BuildTargetFactory.newInstance("//java/com/example:build_config")
            .getUnflavoredBuildTarget();
    String observedJavaCode =
        BuildConfigs.generateBuildConfigDotJava(
            source.toString(), "com.example", /* useConstantExpressions */ true, customValues);
    assertEquals(expectedJavaCode, observedJavaCode);
  }
}

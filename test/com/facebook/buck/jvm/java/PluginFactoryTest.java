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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.facebook.buck.core.filesystems.AbsPath;
import com.facebook.buck.core.rules.resolver.impl.TestActionGraphBuilder;
import com.facebook.buck.core.sourcepath.FakeSourcePath;
import com.facebook.buck.core.sourcepath.SourcePath;
import com.facebook.buck.core.sourcepath.resolver.SourcePathResolverAdapter;
import com.facebook.buck.io.filesystem.impl.FakeProjectFilesystem;
import com.facebook.buck.jvm.java.JavacPluginProperties.Type;
import com.facebook.buck.jvm.java.javax.SynchronizedToolProvider;
import com.facebook.buck.util.ClassLoaderCache;
import java.util.List;
import org.junit.Test;

public class PluginFactoryTest {

  @Test
  public void testPluginClassloadersNotReusedIfAnyMarkedUnsafe() {
    assertFalse(isPluginClassLoaderReused(false)); // safe processors
  }

  @Test
  public void testPluginClassloadersReusedIfAllMarkedSafe() {
    assertTrue(isPluginClassLoaderReused(true)); // safe processors
  }

  private boolean isPluginClassLoaderReused(boolean canReuseClasspath) {
    SourcePath controlClasspath = FakeSourcePath.of("some/path/to.jar");
    SourcePath variableClasspath = FakeSourcePath.of("some/path/to_other.jar");

    ClassLoader baseClassLoader = SynchronizedToolProvider.getSystemToolClassLoader();
    ClassLoaderCache classLoaderCache = new ClassLoaderCache();

    AbsPath rootPath = new FakeProjectFilesystem().getRootPath();

    SourcePathResolverAdapter sourcePathResolver =
        new TestActionGraphBuilder().getSourcePathResolver();
    ResolvedJavacPluginProperties controlPluginGroup =
        ResolvedJavacPluginProperties.of(
            JavacPluginProperties.builder()
                .setType(Type.JAVAC_PLUGIN)
                .addClasspathEntries(controlClasspath)
                .addProcessorNames("controlPlugin")
                .setCanReuseClassLoader(true) // control can always reuse
                .setDoesNotAffectAbi(false)
                .setSupportsAbiGenerationFromSource(false)
                .build(),
            sourcePathResolver,
            rootPath);

    ResolvedJavacPluginProperties variablePluginGroup =
        ResolvedJavacPluginProperties.of(
            JavacPluginProperties.builder()
                .setType(Type.JAVAC_PLUGIN)
                .addClasspathEntries(variableClasspath)
                .addProcessorNames("variablePlugin")
                .setCanReuseClassLoader(canReuseClasspath)
                .setDoesNotAffectAbi(false)
                .setSupportsAbiGenerationFromSource(false)
                .build(),
            sourcePathResolver,
            rootPath);

    try (PluginFactory factory1 = new PluginFactory(baseClassLoader, classLoaderCache);
        PluginFactory factory2 = new PluginFactory(baseClassLoader, classLoaderCache)) {

      JavacPluginParams pluginParams =
          JavacPluginParams.builder()
              .setPluginProperties(List.of(controlPluginGroup, variablePluginGroup))
              .build();
      ClassLoader classLoader1 = factory1.getClassLoaderForProcessorGroups(pluginParams, rootPath);
      ClassLoader classLoader2 = factory2.getClassLoaderForProcessorGroups(pluginParams, rootPath);
      return classLoader1 == classLoader2;
    } catch (Exception e) {
      throw new AssertionError(e);
    }
  }
}

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

import com.facebook.buck.cd.model.java.ResolvedJavacOptions.JavacPluginJsr199Fields;
import com.facebook.buck.util.ClassLoaderCache;
import com.google.common.collect.ImmutableList;
import java.net.MalformedURLException;
import java.net.URL;

// Counter part of AnnotationProcessorFactory
public class PluginFactory implements AutoCloseable {

  private final ClassLoader compilerClassLoader;
  private final ClassLoaderCache globalClassLoaderCache;
  private final ClassLoaderCache localClassLoaderCache = new ClassLoaderCache();

  PluginFactory(ClassLoader compilerClassLoader, ClassLoaderCache globalClassLoaderCache) {
    this.compilerClassLoader = compilerClassLoader;
    this.globalClassLoaderCache = globalClassLoaderCache;
  }

  @Override
  public void close() throws Exception {
    localClassLoaderCache.close();
  }

  ClassLoader getClassLoaderForProcessorGroups(
      ImmutableList<JavacPluginJsr199Fields> pluginGroups) {
    ClassLoaderCache cache;
    // We can avoid lots of overhead in large builds by reusing the same classloader for java
    // plugins. However, some plugins use static variables in a way that assumes
    // there is only one instance running in the process at a time (or at all), and such plugin
    // would break running inside of Buck. So we default to creating a new ClassLoader
    // if any plugins meets those requirements.
    if (pluginGroups.stream().allMatch(JavacPluginJsr199Fields::getCanReuseClassLoader)) {
      cache = globalClassLoaderCache;
    } else {
      cache = localClassLoaderCache;
    }
    return cache.getClassLoaderForClassPath(
        compilerClassLoader,
        pluginGroups.stream()
            .flatMap(pluginGroup -> pluginGroup.getClasspathList().stream())
            .map(this::toURL)
            .collect(ImmutableList.toImmutableList()));
  }

  private URL toURL(JavacPluginJsr199Fields.URL url) {
    try {
      return new URL(url.getValue());
    } catch (MalformedURLException e) {
      throw new RuntimeException(e);
    }
  }
}

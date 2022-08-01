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

package com.facebook.buck.features.project.intellij;

import com.facebook.buck.core.util.immutables.BuckStyleValue;
import com.facebook.buck.features.project.intellij.model.IjLibrary;
import com.facebook.buck.features.project.intellij.model.IjModule;
import com.facebook.buck.features.project.intellij.model.IjProjectElement;
import com.facebook.buck.features.project.intellij.writer.IjProjectWriter;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedSet;
import java.util.Optional;
import javax.annotation.Nullable;
import org.immutables.value.Value;

/**
 * Represents the dependencies of an {@link IjModule} in a form intended to be consumable by the
 * {@link IjProjectWriter}.
 */
public class IjDependencyListBuilder {

  /** Set of scopes supported by IntelliJ for the "orderEntry" element. */
  public enum Scope {
    COMPILE("COMPILE"),
    PROVIDED("PROVIDED"),
    RUNTIME("RUNTIME"),
    TEST("TEST");

    private final String value;

    Scope(String value) {
      this.value = value;
    }

    @Override
    public String toString() {
      return value;
    }
  }

  /** Set of types supported by IntelliJ for the "orderEntry" element. */
  public enum Type {
    MODULE("module"),
    LIBRARY("library"),
    MODULE_LIBRARY("module-library");

    private final String value;

    Type(String value) {
      this.value = value;
    }

    @Override
    public String toString() {
      return value;
    }
  }

  public enum SortOrder {
    // Declaration order defines sorting order.
    MODULE,
    MODULE_LIBRARY,
    LIBRARY,
    SOURCE_FOLDER,
    COMPILED_SHADOW
  }

  /**
   * The design of this classes API is tied to how the corresponding StringTemplate template
   * interacts with it.
   */
  @BuckStyleValue
  public abstract static class DependencyEntry implements Comparable<DependencyEntry> {

    public abstract Type getType();

    public abstract SortOrder getSortOrder();

    public abstract Optional<DependencyEntryData> getData();

    @Nullable
    public DependencyEntryData getModule() {
      if (getType().equals(Type.MODULE)) {
        return getData().get();
      } else {
        return null;
      }
    }

    @Nullable
    public DependencyEntryData getLibrary() {
      if (getType().equals(Type.LIBRARY)) {
        return getData().get();
      } else {
        return null;
      }
    }

    @Nullable
    public DependencyEntryData getModuleLibrary() {
      if (getType().equals(Type.MODULE_LIBRARY)) {
        return getData().get();
      } else {
        return null;
      }
    }

    @Value.Check
    protected void dataOnlyAbsentForSourceFolder() {
      Preconditions.checkArgument(getData().isPresent());
    }

    @Override
    public int compareTo(DependencyEntry o) {
      if (this == o) {
        return 0;
      }

      int sortComparison = getSortOrder().compareTo(o.getSortOrder());
      if (sortComparison != 0) {
        return sortComparison;
      }
      if (getData().isPresent() && o.getData().isPresent()) {
        return getData().get().getName().compareTo(o.getData().get().getName());
      }
      // We can only get here if there is more than one SOURCE_FOLDER entry in the list or if
      // a new data-less type was added without updating this code.
      Preconditions.checkState(false);
      return 0;
    }
  }

  @BuckStyleValue
  public abstract static class DependencyEntryData {
    public abstract String getName();

    public abstract Scope getScope();

    public abstract boolean getExported();

    @Nullable
    public abstract IjProjectElement getElement();
  }

  private final ImmutableSet.Builder<DependencyEntry> builder;

  public IjDependencyListBuilder(boolean sorted) {
    if (sorted) {
      builder = ImmutableSortedSet.naturalOrder();
    } else {
      builder = ImmutableSet.builder();
    }
  }

  public void addModule(String name, Scope scope, boolean exported) {
    builder.add(
        ImmutableDependencyEntry.ofImpl(
            Type.MODULE,
            SortOrder.MODULE,
            Optional.of(ImmutableDependencyEntryData.ofImpl(name, scope, exported, null))));
  }

  public void addCompiledShadow(String name) {
    builder.add(
        ImmutableDependencyEntry.ofImpl(
            Type.LIBRARY,
            SortOrder.COMPILED_SHADOW,
            Optional.of(ImmutableDependencyEntryData.ofImpl(name, Scope.PROVIDED, true, null))));
  }

  public void addLibrary(String name, Scope scope, boolean exported) {
    builder.add(
        ImmutableDependencyEntry.ofImpl(
            Type.LIBRARY,
            SortOrder.LIBRARY,
            Optional.of(ImmutableDependencyEntryData.ofImpl(name, scope, exported, null))));
  }

  public void addModuleLibrary(String name, Scope scope, boolean exported, IjLibrary library) {
    builder.add(
        ImmutableDependencyEntry.ofImpl(
            Type.MODULE_LIBRARY,
            SortOrder.MODULE_LIBRARY,
            Optional.of(ImmutableDependencyEntryData.ofImpl(name, scope, exported, library))));
  }

  public ImmutableSet<DependencyEntry> build() {
    return builder.build();
  }
}

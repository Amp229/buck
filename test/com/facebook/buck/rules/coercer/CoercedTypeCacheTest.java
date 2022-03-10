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

package com.facebook.buck.rules.coercer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.facebook.buck.core.description.arg.Hint;
import com.facebook.buck.core.util.immutables.RuleArg;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Ordering;
import java.util.Optional;
import org.immutables.value.Value;
import org.junit.Test;

public class CoercedTypeCacheTest {
  private final CoercedTypeCache coercedTypeCache =
      new DefaultTypeCoercerFactory().getCoercedTypeCache();

  @Test
  public void requiredIsNotOptional() {
    assertFalse(getParamInfo("required").isOptional());
  }

  @Test
  public void optionalIsOptional() {
    assertTrue(getParamInfo("optional").isOptional());
  }

  @Test
  public void optionalIsInheritedOptional() {
    assertTrue(getParamInfo("interface_optional").isOptional());
  }

  @Test
  public void defaultValuesAreOptional() {
    assertTrue(getParamInfo("default").isOptional());
  }

  @Test
  public void defaultValuesAreOptionalThroughInheritence() {
    assertTrue(getParamInfo("interface_default").isOptional());
  }

  @Test
  public void getName() {
    assertEquals(
        ImmutableSortedSet.of(
            "consistent_overridden_interface_non_dep",
            "consistent_overridden_interface_non_input",
            "default",
            "interface_default",
            "interface_non_dep",
            "interface_non_input",
            "interface_optional",
            "optional",
            "overridden_interface_non_dep",
            "overridden_interface_non_input",
            "non_dep",
            "non_input",
            "required"),
        ImmutableSortedSet.copyOf(
            coercedTypeCache
                .extractForImmutableBuilder(Dto.Builder.class)
                .getParamInfosByStarlarkName()
                .keySet()));
  }

  @Test
  public void getPythonName() {
    assertEquals(
        ImmutableSortedSet.of(
            "consistent_overridden_interface_non_dep",
            "consistent_overridden_interface_non_input",
            "default",
            "interface_default",
            "interface_non_dep",
            "interface_non_input",
            "interface_optional",
            "optional",
            "overridden_interface_non_dep",
            "overridden_interface_non_input",
            "non_dep",
            "non_input",
            "required"),
        coercedTypeCache.extractForImmutableBuilder(Dto.Builder.class).getParamInfosSorted()
            .stream()
            .map(p -> p.getName().getSnakeCase())
            .collect(ImmutableSortedSet.toImmutableSortedSet(Ordering.natural())));
  }

  @Test
  public void isDep() {
    assertFalse(getParamInfo("non_dep").isDep());
    assertTrue(getParamInfo("optional").isDep());
  }

  @Test
  public void isDepInherited() {
    assertFalse(getParamInfo("interface_non_dep").isDep());
    assertFalse(getParamInfo("consistent_overridden_interface_non_dep").isDep());
    assertTrue(getParamInfo("overridden_interface_non_dep").isDep());
    assertTrue(getParamInfo("interface_optional").isDep());
  }

  @Test
  public void isInput() {
    assertFalse(getParamInfo("non_input").isInput());
    assertTrue(getParamInfo("optional").isInput());
  }

  @Test
  public void isInputInherited() {
    assertFalse(getParamInfo("interface_non_input").isInput());
    assertFalse(getParamInfo("consistent_overridden_interface_non_input").isInput());
    assertTrue(getParamInfo("overridden_interface_non_input").isInput());
    assertTrue(getParamInfo("interface_optional").isInput());
  }

  interface DtoInterface {
    Optional<String> getInterfaceOptional();

    @Value.Default
    default String getInterfaceDefault() {
      return "blue";
    }

    @Hint(isDep = false)
    String getInterfaceNonDep();

    @Hint(isDep = false)
    String getOverriddenInterfaceNonDep();

    @Hint(isDep = false)
    String getConsistentOverriddenInterfaceNonDep();

    @Hint(isInput = false)
    String getInterfaceNonInput();

    @Hint(isInput = false)
    String getOverriddenInterfaceNonInput();

    @Hint(isInput = false)
    String getConsistentOverriddenInterfaceNonInput();
  }

  @RuleArg
  abstract static class AbstractDto implements DtoInterface {
    abstract Optional<String> getOptional();

    abstract String getRequired();

    @Value.Default
    String getDefault() {
      return "purple";
    }

    @Hint(isDep = false)
    abstract String getNonDep();

    @Override
    public abstract String getOverriddenInterfaceNonDep();

    @Override
    @Hint(isDep = false)
    public abstract String getConsistentOverriddenInterfaceNonDep();

    @Hint(isInput = false)
    abstract String getNonInput();

    @Override
    public abstract String getOverriddenInterfaceNonInput();

    @Override
    @Hint(isInput = false)
    public abstract String getConsistentOverriddenInterfaceNonInput();
  }

  private ParamInfo<?> getParamInfo(String name) {
    return coercedTypeCache.extractForImmutableBuilder(Dto.Builder.class).getByStarlarkName(name);
  }
}

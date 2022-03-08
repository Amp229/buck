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

package com.facebook.buck.core.starlark.rule.attr.impl;

import com.facebook.buck.core.rules.analysis.RuleAnalysisContext;
import com.facebook.buck.core.starlark.rule.attr.Attribute;
import com.facebook.buck.core.starlark.rule.attr.PostCoercionTransform;
import com.facebook.buck.core.util.immutables.BuckStyleValue;
import com.facebook.buck.rules.coercer.CoerceFailedException;
import com.facebook.buck.rules.coercer.TypeCoercer;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.reflect.TypeToken;
import net.starlark.java.eval.Printer;
import net.starlark.java.eval.StarlarkList;

/** Class that represents a sorted set of uniq strings */
@BuckStyleValue
public abstract class StringSortedSetAttribute extends Attribute<ImmutableSortedSet<String>> {

  private static final TypeCoercer<?, ImmutableSortedSet<String>> coercer =
      TypeCoercerFactoryForStarlark.typeCoercerForType(
          new TypeToken<ImmutableSortedSet<String>>() {});

  @Override
  public abstract ImmutableSortedSet<String> getPreCoercionDefaultValue();

  @Override
  public abstract String getDoc();

  @Override
  public abstract boolean getMandatory();

  /** Whether or not the set can be empty */
  public abstract boolean getAllowEmpty();

  @Override
  public void repr(Printer printer) {
    printer.append("<attr.string_set>");
  }

  @Override
  public TypeCoercer<?, ImmutableSortedSet<String>> getTypeCoercer() {
    return coercer;
  }

  @Override
  public void validateCoercedValue(ImmutableSortedSet<String> values) throws CoerceFailedException {
    if (!getAllowEmpty() && values.isEmpty()) {
      throw new CoerceFailedException("List of strings may not be empty");
    }
  }

  @Override
  public PostCoercionTransform<
          RuleAnalysisContext, ImmutableSortedSet<String>, StarlarkList<String>>
      getPostCoercionTransform() {
    return (coercedValue, ctx) -> StarlarkList.immutableCopyOf(coercedValue);
  }

  public static StringSortedSetAttribute of(
      ImmutableSortedSet<String> preCoercionDefaultValue,
      String doc,
      boolean mandatory,
      boolean allowEmpty) {
    return ImmutableStringSortedSetAttribute.ofImpl(
        preCoercionDefaultValue, doc, mandatory, allowEmpty);
  }
}

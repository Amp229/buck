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
import com.google.common.reflect.TypeToken;
import java.util.List;
import net.starlark.java.eval.Printer;
import net.starlark.java.eval.StarlarkInt;

/** Class that represents an Integer attribute to a user defined rule */
@BuckStyleValue
public abstract class IntAttribute extends Attribute<Integer> {

  private static final TypeCoercer<?, Integer> coercer =
      TypeCoercerFactoryForStarlark.typeCoercerForType(TypeToken.of(Integer.class));

  @Override
  public abstract Integer getPreCoercionDefaultValue();

  @Override
  public abstract String getDoc();

  @Override
  public abstract boolean getMandatory();

  /** The list of values that are acceptable. If empty, allow any values */
  abstract List<Integer> getValues();

  @Override
  public void repr(Printer printer) {
    printer.append("<attr.int>");
  }

  @Override
  public TypeCoercer<?, Integer> getTypeCoercer() {
    return coercer;
  }

  @Override
  protected void validateCoercedValue(Integer value) throws CoerceFailedException {
    validateValueInList(getValues(), value);
  }

  @Override
  public PostCoercionTransform<RuleAnalysisContext, Integer, ?> getPostCoercionTransform() {
    return (coercedValue, additionalData) -> StarlarkInt.of(coercedValue);
  }

  public static IntAttribute of(
      Integer preCoercionDefaultValue, String doc, boolean mandatory, List<Integer> values) {
    return ImmutableIntAttribute.ofImpl(preCoercionDefaultValue, doc, mandatory, values);
  }
}

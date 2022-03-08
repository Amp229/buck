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

package com.facebook.buck.core.starlark.rule.attr;

/**
 * Simple wrapper object to hold onto Attribute objects and get around some type erasure problems in
 * {@link net.starlark.java.eval.Dict#cast(Object, Class, Class, String)}. If using {@code
 * com.facebook.buck.core.starlark.rule.attr.Attribute<T>}, then a {@code Class<T>} cannot be
 * instantiated. If the generic {@link com.facebook.buck.core.starlark.rule.attr.Attribute} type is
 * passed and used, then it is a compile error. We need {@link
 * net.starlark.java.eval.Dict#cast(Object, Class, Class, String)} to succeed in order to validate
 * the types of the objects in that dictionary, so add an intermediate class. This is terrible.
 */
public interface AttributeHolder {
  /** Get the actual attribute object */
  Attribute<?> getAttribute();
}

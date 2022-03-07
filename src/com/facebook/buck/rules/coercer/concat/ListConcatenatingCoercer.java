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

package com.facebook.buck.rules.coercer.concat;

import com.google.common.collect.ImmutableList;
import java.util.List;
import java.util.stream.StreamSupport;

/** Concatenate {@link List}s of unknown types. */
final class ListConcatenatingCoercer extends JsonTypeConcatenatingCoercer {

  @SuppressWarnings("unchecked")
  @Override
  public Object concat(Iterable<Object> elements) {
    return StreamSupport.stream(elements.spliterator(), false)
        .flatMap(o -> ((List<Object>) o).stream())
        .collect(ImmutableList.toImmutableList());
  }

  @Override
  public boolean equals(Object obj) {
    return obj instanceof ListConcatenatingCoercer;
  }

  @Override
  public String toString() {
    return ListConcatenatingCoercer.class.getSimpleName();
  }

  @Override
  public int hashCode() {
    return ListConcatenatingCoercer.class.hashCode();
  }
}

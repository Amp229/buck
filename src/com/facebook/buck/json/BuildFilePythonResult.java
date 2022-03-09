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

package com.facebook.buck.json;

import com.facebook.buck.core.util.immutables.BuckStyleValue;
import com.facebook.buck.util.collect.TwoArraysImmutableHashMap;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.google.common.collect.ImmutableList;
import java.util.Optional;

/**
 * Immutable value type used to hold the parsed and deserialized output of {@code buck.py}.
 *
 * <p>{@see BuildFilePythonResultDeserializer} which specializes in deserializing this type from
 * JSON.
 */
@BuckStyleValue
@JsonDeserialize(as = BuildFilePythonResult.class, using = BuildFilePythonResultDeserializer.class)
public interface BuildFilePythonResult {
  ImmutableList<TwoArraysImmutableHashMap<String, Object>> getValues();

  ImmutableList<TwoArraysImmutableHashMap<String, Object>> getDiagnostics();

  Optional<String> getProfile();

  static BuildFilePythonResult of(
      ImmutableList<TwoArraysImmutableHashMap<String, Object>> values,
      ImmutableList<TwoArraysImmutableHashMap<String, Object>> diagnostics,
      Optional<String> profile) {
    return ImmutableBuildFilePythonResult.ofImpl(values, diagnostics, profile);
  }
}

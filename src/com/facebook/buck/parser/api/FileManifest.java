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

package com.facebook.buck.parser.api;

import com.facebook.buck.parser.exceptions.ParsingError;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

/** The {@link FileManifest} output as a result of parsing a file with a {@link FileParser}. */
public interface FileManifest {

  /** @return a set of extension files read during parsing. */
  ImmutableSet<String> getIncludes();

  /**
   * @return a map from configuration section to configuration key to the value returned during
   *     parsing. This is used only for testing, and currently only includes read_config calls from
   *     BUCK file, but not from included bzl files.
   */
  ImmutableMap<String, Object> getReadConfigurationOptionsForTest();

  /**
   * @return A list of fatal errors occurred during parsing a file, i.e. errors that might render
   *     manifest incomplete. It is up for the parser to decide if still wants to fill this object
   *     with unaffected targets
   */
  ImmutableList<ParsingError> getErrors();
}

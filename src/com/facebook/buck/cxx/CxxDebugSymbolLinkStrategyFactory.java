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

package com.facebook.buck.cxx;

import com.facebook.buck.core.cell.CellPathResolver;
import com.facebook.buck.rules.args.Arg;
import com.google.common.collect.ImmutableList;

/**
 * Defines the factory interface to create debug symbol linking strategy. Used by {@link
 * CxxLinkableEnhancer} to create the {@link CxxDebugSymbolLinkStrategy} that will be used to
 * perform linking.
 */
public interface CxxDebugSymbolLinkStrategyFactory {

  /**
   * Creates the strategy for loading limited debug info.
   *
   * @param cellPathResolver
   * @param linkerArgs arguments passed to the linker, used to identify build output paths for
   *     focused targets
   * @return A {@link CxxDebugSymbolLinkStrategy}
   */
  CxxDebugSymbolLinkStrategy createStrategy(
      CellPathResolver cellPathResolver, ImmutableList<Arg> linkerArgs);
}

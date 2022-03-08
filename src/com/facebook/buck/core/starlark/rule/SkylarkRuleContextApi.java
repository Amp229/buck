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

package com.facebook.buck.core.starlark.rule;

import com.facebook.buck.core.model.label.Label;
import net.starlark.java.annot.StarlarkBuiltin;
import net.starlark.java.annot.StarlarkMethod;

/** The `ctx` variable that is passed to user implementation functions */
@StarlarkBuiltin(
    name = "ctx",
    doc =
        "The ctx variable that is passed to rule implementation functions. "
            + "Provides information about dependencies, attributes, actions, etc")
interface SkylarkRuleContextApi {

  @StarlarkMethod(
      name = "actions",
      doc =
          "Struct containing methods to create and interact with actions within a rule's "
              + "implementation method",
      structField = true)
  SkylarkRuleContextActionsApi getActions();

  @StarlarkMethod(
      name = "attr",
      doc =
          "A struct is used to access the parameters that were passed in by a user. \n"
              + "Each parameter is given a field on this struct. So, a parameter named `deps` "
              + "would be available via `ctx.attr.deps`.\n"
              + "At this point, all `select()`s have been resolved, and all parameters validated "
              + "and converted tot he right type (e.g. build target strings are converted to "
              + "`Dependency` objects).",
      structField = true)
  SkylarkRuleContextAttr getAttr();

  @StarlarkMethod(
      name = "label",
      doc = "The `Label` for the target that is currently being evaluated",
      structField = true)
  Label getLabel();
}

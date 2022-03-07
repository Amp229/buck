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

package com.facebook.buck.skylark.function;

import net.starlark.java.annot.Param;
import net.starlark.java.annot.ParamType;
import net.starlark.java.annot.StarlarkMethod;
import net.starlark.java.eval.Dict;
import net.starlark.java.eval.EvalException;
import net.starlark.java.eval.NoneType;
import net.starlark.java.eval.StarlarkList;
import net.starlark.java.eval.StarlarkThread;
import net.starlark.java.eval.StarlarkValue;

/**
 * Interface for a global Skylark library containing the provider method.
 *
 * <p>This is only separate from {@link SkylarkRuleFunctionsApi} at the moment so that new
 * extensibility functionality can be disabled behind configuration until it has stabilized.
 */
public interface SkylarkProviderFunctionApi {

  @StarlarkMethod(
      name = "provider",
      doc =
          "Creates a declared provider, which is both an identifier of, and constructor "
              + "used to create, \"struct-like\" values called Infos. Example:<br>"
              + "<pre class=\"language-python\">data = provider()\n"
              + "d = data(x = 2, y = 3)\n"
              + "print(d.x + d.y) # prints 5</pre>",
      parameters = {
        @Param(
            name = "doc",
            allowedTypes = @ParamType(type = String.class),
            named = true,
            defaultValue = "''",
            doc =
                "A description of the provider that can be extracted by documentation generating tools."),
        @Param(
            name = "fields",
            doc =
                "If specified, restricts the set of allowed fields. <br>"
                    + "Possible values are:"
                    + "<ul>"
                    + "  <li> list of fields:<br>"
                    + "       <pre class=\"language-python\">provider(fields = ['a', 'b'])</pre><p>"
                    + "  <li> dictionary field name -> documentation:<br>"
                    + "       <pre class=\"language-python\">provider(\n"
                    + "       fields = { 'a' : 'Documentation for a', 'b' : 'Documentation for b' })</pre>"
                    + "</ul>"
                    + "All fields are optional.",
            allowedTypes = {
              @ParamType(type = StarlarkList.class, generic1 = String.class),
              @ParamType(type = Dict.class),
              @ParamType(type = NoneType.class),
            },
            named = true,
            positional = false,
            defaultValue = "None")
      },
      useStarlarkThread = true)
  StarlarkValue provider(String doc, Object fields, StarlarkThread thread) throws EvalException;
}

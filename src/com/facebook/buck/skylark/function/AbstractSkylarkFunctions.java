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

import com.facebook.buck.core.exceptions.BuckUncheckedExecutionException;
import com.facebook.buck.skylark.function.select.SelectorList;
import com.facebook.buck.skylark.function.select.SelectorValue;
import com.facebook.buck.skylark.parser.context.ReadConfigContext;
import com.google.common.collect.ImmutableMap;
import com.google.common.hash.Hashing;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import javax.annotation.Nullable;
import net.starlark.java.annot.FnPurity;
import net.starlark.java.annot.Param;
import net.starlark.java.annot.ParamType;
import net.starlark.java.annot.StarlarkMethod;
import net.starlark.java.eval.Dict;
import net.starlark.java.eval.EvalException;
import net.starlark.java.eval.NoneType;
import net.starlark.java.eval.Starlark;
import net.starlark.java.eval.StarlarkCallable;
import net.starlark.java.eval.StarlarkThread;
import net.starlark.java.eval.StarlarkValue;
import net.starlark.java.eval.Tuple;

/**
 * Abstract class containing function definitions shared by {@link SkylarkBuildModule} and {@link
 * SkylarkPackageModule}.
 */
public abstract class AbstractSkylarkFunctions extends StarlarkValue {

  /**
   * Exposes a {@code read_config} for Skylark parser.
   *
   * <p>This is a temporary solution to simplify migration from Python DSL to Skylark and allows
   * clients to query values from {@code .buckconfig} files and {@code --config} command line
   * arguments.
   *
   * <p>Example, when buck is invoked with {@code --config user.value=my_value} an invocation of
   * {@code read_config("user", "value", "default_value")} will return {@code my_value}.
   */
  @StarlarkMethod(
      name = "read_config",
      doc =
          "Returns a configuration value of <code>.buckconfig</code> or <code>--config</code> flag."
              + " For example, <code>read_config('foo', 'bar', 'baz')</code> returns"
              + " <code>bazz</code> if Buck is invoked with <code>--config foo.bar=bazz</code> flag.",
      parameters = {
        @Param(
            name = "section",
            allowedTypes = @ParamType(type = String.class),
            doc = "the name of the .buckconfig section with the desired value."),
        @Param(
            name = "field",
            allowedTypes = @ParamType(type = String.class),
            doc = "the name of the .buckconfig field with the desired value."),
        @Param(
            name = "defaultValue",
            allowedTypes = {@ParamType(type = String.class), @ParamType(type = NoneType.class)},
            defaultValue = "None",
            doc = "the value to return if the desired value is not set in the .buckconfig."),
      },
      documented = false, // this is an API that we should remove once select is available
      useStarlarkThread = true,
      purity = FnPurity.SPEC_SAFE,
      trustReturnsValid = true)
  public Object readConfig(String section, String field, Object defaultValue, StarlarkThread env)
      throws EvalException {
    ReadConfigContext configContext = ReadConfigContext.getContext(env);
    @Nullable
    String value = configContext.getRawConfig().getOrDefault(section, ImmutableMap.of()).get(field);

    configContext.recordReadConfigurationOption(section, field, value);
    return value != null ? value : defaultValue;
  }

  /** {@code sha256} */
  @StarlarkMethod(
      name = "sha256",
      doc = "Computes a sha256 digest for a string. Returns the hex representation of the digest.",
      parameters = {
        @Param(name = "value", allowedTypes = @ParamType(type = String.class), named = true)
      })
  public String sha256(String value) {
    return Hashing.sha256().hashString(value, StandardCharsets.UTF_8).toString();
  }

  /** {@code load_symbols} */
  @StarlarkMethod(
      name = "load_symbols",
      doc = "Loads symbols into the current build context.",
      parameters = {
        @Param(name = "symbols", allowedTypes = @ParamType(type = Dict.class), named = true)
      },
      useStarlarkThread = true)
  public void loadSymbols(Dict<?, ?> symbols /* <String, Any> */, StarlarkThread env) {
    LoadSymbolsContext loadSymbolsContext = env.getThreadLocal(LoadSymbolsContext.class);
    if (loadSymbolsContext == null) {
      throw new BuckUncheckedExecutionException(
          "%s is not specified", LoadSymbolsContext.class.getSimpleName());
    }
    for (Object keyObj : symbols) {
      if (keyObj instanceof String) {
        String key = (String) keyObj;
        loadSymbolsContext.putSymbol(key, symbols.get(keyObj));
      }
    }
  }

  /** {@code partial} */
  @StarlarkMethod(
      name = "partial",
      doc =
          "new function with partial application of the given arguments and keywords. "
              + "Roughly equivalent to functools.partial.",
      parameters = {
        @Param(name = "func", allowedTypes = @ParamType(type = StarlarkCallable.class))
      },
      extraPositionals = @Param(name = "args"),
      extraKeywords = @Param(name = "kwargs"))
  public StarlarkCallable partial(StarlarkCallable func, Tuple args, Dict<String, Object> kwargs) {
    return new StarlarkCallable() {
      @Override
      public Object call(StarlarkThread thread, Tuple inner_args, Dict<String, Object> inner_kwargs)
          throws EvalException, InterruptedException {
        // Sadly, neither Dict.plus() nor MethodLibrary.dict() are accessible.
        Dict<String, Object> merged_args = Dict.copyOf(thread.mutability(), kwargs);
        merged_args.update(inner_kwargs, Dict.empty());
        return Starlark.call(thread, func, Tuple.concat(args, inner_args), merged_args);
      }

      @Override
      public String getName() {
        return "<partial>";
      }
    };
  }

  /**
   * Returns a function-value implementing "select" (i.e. configurable attributes) in the specified
   * package context.
   */
  @StarlarkMethod(
      name = "select",
      doc =
          "<code>select()</code> is the helper function that makes a rule attribute "
              + "<a href=\"$BE_ROOT/common-definitions.html#configurable-attributes\">"
              + "configurable</a>. See "
              + "<a href=\"$BE_ROOT/functions.html#select\">build encyclopedia</a> for details.",
      parameters = {
        @Param(
            name = "x",
            allowedTypes = @ParamType(type = Dict.class),
            doc = "The parameter to convert."),
        @Param(
            name = "no_match_error",
            allowedTypes = @ParamType(type = String.class),
            defaultValue = "''",
            doc = "Optional custom error to report if no condition matches.",
            named = true)
      },
      purity = FnPurity.PURE)
  public SelectorList select(Dict<?, ?> dict, String noMatchError) throws EvalException {
    if (dict.isEmpty()) {
      throw Starlark.errorf(
          "select({}) with an empty dictionary can never resolve because it includes no conditions"
              + " to match");
    }
    for (Object key : dict.keySet()) {
      if (!(key instanceof String)) {
        throw Starlark.errorf("Invalid key: %s. select keys must be label references", key);
      }
    }
    // TODO(nga): use our version of selectors
    return SelectorList.of(new SelectorValue(dict, noMatchError));
  }

  /** {@code selectEqualInternal} */
  @StarlarkMethod(
      name = "select_equal_internal",
      doc = "Test Only. Check equality between two select expressions",
      parameters = {
        @Param(name = "first", allowedTypes = @ParamType(type = SelectorList.class)),
        @Param(name = "other", allowedTypes = @ParamType(type = SelectorList.class)),
      },
      documented = false)
  public boolean selectEqualInternal(SelectorList first, SelectorList other) {
    if (!(Objects.equals(first.getType(), other.getType()))) {
      return false;
    }

    Iterator<Object> it1 = first.getElements().iterator();
    Iterator<Object> it2 = other.getElements().iterator();

    while (it1.hasNext() && it2.hasNext()) {
      Object o1 = it1.next();
      Object o2 = it2.next();

      if (o1 == o2) {
        continue;
      }

      if (!o1.getClass().equals(o2.getClass())) {
        return false;
      }

      if (o1 instanceof SelectorValue && o2 instanceof SelectorValue) {
        SelectorValue s1 = (SelectorValue) o1;
        SelectorValue s2 = (SelectorValue) o2;

        if (!Objects.equals(s1.getDictionary(), s2.getDictionary())) {
          return false;
        }

      } else {
        if (!Objects.equals(o1, o2)) {
          return false;
        }
      }
    }

    return !(it1.hasNext() || it2.hasNext());
  }

  /**
   * Exposes a {@code select_map} for Skylark parser.
   *
   * <p>This API allows modifying values in a select expression. The mapping function operates on
   * each individual value in the select expression, and generates a new select expression with the
   * updated values.
   *
   * <p>Example 1: Inject a value:
   *
   * <pre>{@code
   * def add_foo(flags):
   *   if "-DBAR" not in flags:
   *     return flags + ["-DFOO"]
   *
   *   return flags
   *
   * flags = select({":config": ["-DBAR"], "DEFAULT": []})
   *
   * select_map(flags, add_foo) # returns select({":config": ["-DBAR"], "DEFAULT": ["-DFOO"]})
   * }</pre>
   *
   * Example 2: Filter a value.
   *
   * <pre>{@code
   * def drop_foo(flags):
   *   return [f for f in flags if f != "-DFOO"]
   *
   * flags = select({":config": ["-DFOO", "-DBAR"], "DEFAULT": ["-DBAZ"]})
   *
   * select_map(flags, drop_foo) # returns select({":config": ["-DBAR"], "DEFAULT": ["-DBAZ"]}
   * }</pre>
   *
   * Example 3: Update values.
   *
   * <pre>{@code
   * def convert_foo(flags):
   *   new_flags = []
   *   for flag in flags:
   *     if flag == "-DFOO":
   *       new_flags.append("-DFOOBAR")
   *    else:
   *       new_flags.append(flag)
   *
   *   return new_flags
   *
   * flags = select({":config": ["-DFOO"], "DEFAULT": ["-DBAZ"]})
   *
   * select_map(flags, convert_foo) # returns select({":config": ["-DFOOBAR"], "DEFAULT": ["-DBAZ"]}
   * }</pre>
   */
  @StarlarkMethod(
      name = "select_map",
      doc = "Iterate over and modify a select expression using the map function",
      parameters = {
        @Param(name = "selector_list", allowedTypes = @ParamType(type = SelectorList.class)),
        @Param(name = "func", allowedTypes = @ParamType(type = StarlarkCallable.class))
      },
      useStarlarkThread = true)
  public SelectorList select_map(
      SelectorList selectorList, StarlarkCallable func, StarlarkThread thread)
      throws EvalException, InterruptedException {
    List<Object> new_elements = new ArrayList<>();
    for (Object element : selectorList.getElements()) {
      if (element instanceof SelectorValue) {
        SelectorValue sval = (SelectorValue) element;
        Map<Object, Object> dictionary = new HashMap<>();

        for (Map.Entry<?, ?> entry : sval.getDictionary().entrySet()) {
          dictionary.put(
              entry.getKey(),
              Starlark.call(thread, func, Tuple.of(entry.getValue()), ImmutableMap.of()));
        }

        new_elements.add(new SelectorValue(dictionary, sval.getNoMatchError()));
      } else {
        new_elements.add(Starlark.call(thread, func, Tuple.of(element), ImmutableMap.of()));
      }
    }
    return SelectorList.of(new_elements);
  }

  /**
   * Exposes a {@code select_test} for Skylark parser.
   *
   * <p>This API allows testing values in a select expression. The API returns true if any value in
   * the select expression passes the given test function.
   *
   * <p>Example 1: Check if the expression contains a specific value.
   *
   * <pre>{@code
   * def has_foo(val):
   *   return "foo" in val
   *
   * attr1 = ["foo"] + select({":config": ["bar"], "DEFAULT": []})
   * attr2 = ["bar"] + select({":config": ["foo"], "DEFAULT": []})
   * attr3 = ["bar"] + select({":config": ["baz"], "DEFAULT": []})
   *
   * select_test(attr1, has_foo) # True
   * select_test(attr2, has_foo) # True
   * select_test(attr3, has_foo) # False
   * }</pre>
   *
   * Example 2: Check all values conform to restrictions.
   *
   * <pre>{@code
   * def has_non_c_files(srcs):
   *   for src in srcs:
   *     if not src.endswith(".c"):
   *        return True
   *   return False
   *
   * def assert_only_c_sources(srcs):
   *   if is_select(srcs):
   *     if select_test(srcs, has_non_c_files):
   *       fail("Error")
   *   else:
   *     if has_non_c_files(srcs):
   *       fail("Error")
   *
   * base_srcs = ["foo.c", "bar.c"]
   * select_srcs = select({":windows": ["foo_windows.c", "bar.c"], "DEFAULT": []})
   * mixed_srcs = base_srcs + select_srcs
   * cpp_srcs = ["baz.cpp"]
   *
   * assert_only_c_sources(base_srcs) # Doesn't fail
   * assert_only_c_sources(select_srcs) # Doesn't fail
   * assert_only_c_sources(mixed_srcs) # Doesn't fail
   *
   * assert_only_c_sources(cpp_srcs) # fails
   * assert_only_c_sources(select({":android": cpp_srcs} + mixed_srcs)) # fails
   *
   * }</pre>
   */
  @StarlarkMethod(
      name = "select_test",
      doc = "Test values in the select expression using the given function",
      parameters = {
        @Param(name = "selector_list", allowedTypes = @ParamType(type = SelectorList.class)),
        @Param(name = "func", allowedTypes = @ParamType(type = StarlarkCallable.class))
      },
      useStarlarkThread = true)
  public boolean select_test(
      SelectorList selectorList, StarlarkCallable func, StarlarkThread thread)
      throws EvalException, InterruptedException {
    for (Object element : selectorList.getElements()) {
      if (element instanceof SelectorValue) {
        SelectorValue sval = (SelectorValue) element;

        for (Map.Entry<?, ?> entry : sval.getDictionary().entrySet()) {
          Boolean result =
              (Boolean) Starlark.call(thread, func, Tuple.of(entry.getValue()), ImmutableMap.of());
          if (result) {
            return true;
          }
        }

      } else {
        Boolean result =
            (Boolean) Starlark.call(thread, func, Tuple.of(element), ImmutableMap.of());
        if (result) {
          return true;
        }
      }
    }
    return false;
  }
}

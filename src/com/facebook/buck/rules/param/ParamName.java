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

package com.facebook.buck.rules.param;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.google.common.base.CaseFormat;
import com.google.common.base.Preconditions;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/** Rule param name. */
public class ParamName implements Comparable<ParamName>, ParamNameOrSpecial {

  /** Matches python identifiers in the parser */
  private static final Pattern VALID_IDENTIFIER_REGEX = Pattern.compile("^[a-z_][a-z0-9_]*$");

  private final String camelCase;
  private final String snakeCase;

  // We only have a hundred params or so, so it's OK to store them permanently.
  // Even if UDR authors create a million distinct param names, it would still be
  // fine to store them permanently.
  private static final ConcurrentHashMap<String, ParamName> bySnakeCase = new ConcurrentHashMap<>();

  private ParamName(String camelCase, String snakeCase) {
    this.camelCase = camelCase;
    this.snakeCase = snakeCase;
  }

  /** Param name by camel case. */
  @Override
  public String getCamelCase() {
    return camelCase;
  }

  /** Param name by snake case. */
  @Override
  public String getSnakeCase() {
    return snakeCase;
  }

  private static String validateCase(
      String string, CaseFormat expected, CaseFormat validateAgainst) {
    boolean underscore;
    if (string.startsWith("_")) {
      underscore = true;
      string = string.substring("_".length());
    } else {
      underscore = false;
    }

    Preconditions.checkArgument(!string.isEmpty(), "param name must not be empty");

    String converted = expected.to(validateAgainst, string);
    String convertedBack = validateAgainst.to(expected, converted);
    Preconditions.checkArgument(
        convertedBack.equals(string), "converted parameter is not %s: %s", expected, string);

    return (underscore ? "_" : "") + converted;
  }

  /**
   * Get a param name object by camel case.
   *
   * <p>This function fails is supplied string is not camel case.
   *
   * <p>This function is slow.
   */
  public static ParamName byUpperCamelCase(String upperCamelCase) {
    return bySnakeCase(
        validateCase(upperCamelCase, CaseFormat.UPPER_CAMEL, CaseFormat.LOWER_UNDERSCORE));
  }

  /**
   * Get a param name object by snake case.
   *
   * <p>This function fails is supplied string is not snake case.
   */
  public static ParamName bySnakeCase(String snakeCase) {
    // fast path
    ParamName paramName = bySnakeCase.get(snakeCase);
    if (paramName != null) {
      return paramName;
    }

    String camelCase = validateCase(snakeCase, CaseFormat.LOWER_UNDERSCORE, CaseFormat.LOWER_CAMEL);

    Preconditions.checkArgument(!snakeCase.isEmpty(), "param name must not be empty");
    Preconditions.checkArgument(!snakeCase.equals("_"), "param name must not be underscore");
    Preconditions.checkArgument(
        !snakeCase.contains("."), "param name must not contain dot: %s", snakeCase);
    Preconditions.checkArgument(
        VALID_IDENTIFIER_REGEX.matcher(snakeCase).matches(),
        "param name is not a valid identifier: %s",
        snakeCase);

    return bySnakeCase.computeIfAbsent(snakeCase, k -> new ParamName(camelCase, snakeCase));
  }

  // using identity equals and hashCode

  @Override
  public String toString() {
    return snakeCase;
  }

  @Override
  public int compareTo(ParamName that) {
    boolean thisIsName = this.getSnakeCase().equals("name");
    boolean thatIsName = that.getSnakeCase().equals("name");
    if (thisIsName || thatIsName) {
      // "name" is smaller than all other strings
      return Boolean.compare(!thisIsName, !thatIsName);
    }

    return this.snakeCase.compareTo(that.snakeCase);
  }

  @JsonCreator
  private static ParamName deserializeFromJson(String object) {
    return bySnakeCase(object);
  }
}

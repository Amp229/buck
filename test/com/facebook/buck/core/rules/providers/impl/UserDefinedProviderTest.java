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

package com.facebook.buck.core.rules.providers.impl;

import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;

import com.facebook.buck.core.model.label.Label;
import com.facebook.buck.core.model.label.LabelSyntaxException;
import com.facebook.buck.core.starlark.compatible.TestMutableEnv;
import com.google.common.collect.ImmutableMap;
import net.starlark.java.eval.EvalException;
import net.starlark.java.eval.Printer;
import net.starlark.java.eval.Starlark;
import net.starlark.java.eval.Tuple;
import net.starlark.java.syntax.Location;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class UserDefinedProviderTest {
  @Rule public ExpectedException thrown = ExpectedException.none();

  final Location location = Location.fromFileLineColumn("package/file.bzl", 5, 6);

  @Test
  public void reprIsReasonable() throws LabelSyntaxException, EvalException {
    UserDefinedProvider provider =
        new UserDefinedProvider(location, new String[] {"foo", "bar", "baz"});
    provider.export(Label.parseAbsolute("//package:file.bzl"), "FooInfo");
    String expectedRepr = "FooInfo(foo, bar, baz) defined at package/file.bzl:5:6";

    assertEquals(expectedRepr, new Printer().repr(provider).toString());
  }

  @Test
  public void nameIsCorrect() throws LabelSyntaxException, EvalException {
    UserDefinedProvider provider =
        new UserDefinedProvider(location, new String[] {"foo", "bar", "baz"});
    provider.export(Label.parseAbsolute("//package:file.bzl"), "FooInfo");

    assertEquals("FooInfo", provider.toString());
    assertEquals("FooInfo", provider.getName());
  }

  @Test
  public void mutabilityIsCorrect() throws LabelSyntaxException, EvalException {
    UserDefinedProvider provider =
        new UserDefinedProvider(location, new String[] {"foo", "bar", "baz"});

    assertFalse(provider.isImmutable());
    assertFalse(provider.isExported());

    provider.export(Label.parseAbsolute("//package:file.bzl"), "FooInfo");

    assertTrue(provider.isImmutable());
    assertTrue(provider.isExported());
  }

  @Test
  public void mutabilityOfInfoIsCorrect()
      throws InterruptedException, EvalException, LabelSyntaxException {
    UserDefinedProvider provider =
        new UserDefinedProvider(location, new String[] {"foo", "bar", "baz"});
    provider.export(Label.parseAbsolute("//package:file.bzl"), "FooInfo");

    try (TestMutableEnv env = new TestMutableEnv()) {
      UserDefinedProviderInfo providerInfo =
          (UserDefinedProviderInfo)
              Starlark.call(
                  env.getEnv(),
                  provider,
                  Tuple.of(),
                  ImmutableMap.of("foo", "val_1", "bar", "val_2", "baz", "val_3"));
      Assert.assertTrue(providerInfo.isImmutable());
    }
  }

  @Test
  public void getNameFailsIfNotExported() {
    UserDefinedProvider provider =
        new UserDefinedProvider(location, new String[] {"foo", "bar", "baz"});

    thrown.expect(NullPointerException.class);
    provider.getName();
  }

  @Test
  public void callFailsIfNotExported() throws InterruptedException, EvalException {
    UserDefinedProvider provider = new UserDefinedProvider(location, new String[] {"foo"});

    try (TestMutableEnv env = new TestMutableEnv()) {
      thrown.expect(Exception.class);
      thrown.expectMessage(
          "Tried to get name before function has been assigned to a variable and exported");
      Starlark.call(env.getEnv(), provider, Tuple.of(), ImmutableMap.of("foo", "foo_value"));
    }
  }

  @Test
  public void callGetsNoneForValuesNotProvided()
      throws InterruptedException, EvalException, LabelSyntaxException {
    UserDefinedProvider provider =
        new UserDefinedProvider(location, new String[] {"foo", "bar", "baz"});
    provider.export(Label.parseAbsolute("//package:file.bzl"), "FooInfo");

    try (TestMutableEnv env = new TestMutableEnv()) {
      Object rawInfo =
          Starlark.call(
              env.getEnv(),
              provider,
              Tuple.of(),
              ImmutableMap.of("foo", "foo_value", "baz", "baz_value"));

      assertTrue(rawInfo instanceof UserDefinedProviderInfo);

      UserDefinedProviderInfo info = (UserDefinedProviderInfo) rawInfo;
      assertEquals("foo_value", info.getField("foo"));
      assertEquals(Starlark.NONE, info.getField("bar"));
      assertEquals("baz_value", info.getField("baz"));
    }
  }

  @Test
  public void callReturnsCorrectUserDefinedProviderInfo()
      throws LabelSyntaxException, InterruptedException, EvalException {
    UserDefinedProvider provider =
        new UserDefinedProvider(location, new String[] {"foo", "bar", "baz"});
    provider.export(Label.parseAbsolute("//package:file.bzl"), "FooInfo");

    try (TestMutableEnv env = new TestMutableEnv()) {
      Object rawInfo =
          Starlark.call(
              env.getEnv(),
              provider,
              Tuple.of(),
              ImmutableMap.of("foo", "foo_value", "bar", "bar_value", "baz", "baz_value"));

      assertTrue(rawInfo instanceof UserDefinedProviderInfo);

      UserDefinedProviderInfo info = (UserDefinedProviderInfo) rawInfo;
      assertEquals("foo_value", info.getField("foo"));
      assertEquals("bar_value", info.getField("bar"));
      assertEquals("baz_value", info.getField("baz"));
    }
  }

  @Test
  public void keysAreDifferentForSameNameAndLocation() {
    UserDefinedProvider provider1 = new UserDefinedProvider(location, new String[] {"foo"});
    UserDefinedProvider provider2 = new UserDefinedProvider(location, new String[] {"foo"});

    assertNotEquals(provider1.getKey(), provider2.getKey());
  }
}

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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.facebook.buck.core.rules.providers.annotations.ImmutableInfo;
import com.facebook.buck.core.starlark.compatible.BuckSkylarkTypes;
import com.facebook.buck.core.starlark.compatible.TestMutableEnv;
import com.facebook.buck.core.starlark.testutil.TestStarlarkParser;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import java.lang.reflect.InvocationTargetException;
import java.util.Map;
import java.util.Set;
import net.starlark.java.eval.Dict;
import net.starlark.java.eval.EvalException;
import net.starlark.java.eval.StarlarkInt;
import net.starlark.java.eval.StarlarkList;
import org.hamcrest.Matchers;
import org.immutables.value.Value;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class BuiltInProviderInfoTest {

  @Rule public ExpectedException thrown = ExpectedException.none();

  @ImmutableInfo(
      args = {"str", "my_info"},
      defaultSkylarkValues = {"\"default value\"", "1"})
  public abstract static class SomeInfo extends BuiltInProviderInfo<SomeInfo> {
    public static final BuiltInProvider<SomeInfo> PROVIDER =
        BuiltInProvider.of(ImmutableSomeInfo.class);

    public abstract String str();

    public abstract StarlarkInt myInfo();
  }

  @ImmutableInfo(
      args = {"str"},
      defaultSkylarkValues = {""})
  public abstract static class OtherInfo extends BuiltInProviderInfo<OtherInfo> {
    public static final BuiltInProvider<OtherInfo> PROVIDER =
        BuiltInProvider.of(ImmutableOtherInfo.class);

    public abstract String str();
  }

  @ImmutableInfo(
      args = {"set"},
      defaultSkylarkValues = "[]")
  public abstract static class InfoWithSet extends BuiltInProviderInfo<InfoWithSet> {
    public static final BuiltInProvider<InfoWithSet> PROVIDER =
        BuiltInProvider.of(ImmutableInfoWithSet.class);

    @Value.Parameter(order = 0)
    public abstract Set<String> set();
  }

  @ImmutableInfo(
      args = {"map"},
      defaultSkylarkValues = "{}")
  public abstract static class InfoWithMap extends BuiltInProviderInfo<InfoWithMap> {
    public static final BuiltInProvider<InfoWithMap> PROVIDER =
        BuiltInProvider.of(ImmutableInfoWithMap.class);

    @Value.Parameter(order = 0)
    public abstract Dict<String, StarlarkInt> map();
  }

  @ImmutableInfo(args = {"val"})
  public abstract static class InfoWithNoDefaultValOnAnnotation
      extends BuiltInProviderInfo<InfoWithNoDefaultValOnAnnotation> {
    public static final BuiltInProvider<InfoWithNoDefaultValOnAnnotation> PROVIDER =
        BuiltInProvider.of(ImmutableInfoWithNoDefaultValOnAnnotation.class);

    public abstract StarlarkInt val();
  }

  @ImmutableInfo(
      args = {"str_list", "my_info"},
      defaultSkylarkValues = {"{}", "1"})
  public abstract static class SomeInfoWithInstantiate
      extends BuiltInProviderInfo<SomeInfoWithInstantiate> {
    public static final BuiltInProvider<SomeInfoWithInstantiate> PROVIDER =
        BuiltInProvider.of(ImmutableSomeInfoWithInstantiate.class);

    public abstract ImmutableList<String> str_list();

    public abstract String myInfo();

    public static SomeInfoWithInstantiate instantiateFromSkylark(
        Dict<String, String> s, StarlarkInt myInfo) throws EvalException {
      Map<String, String> validated = Dict.cast(s, String.class, String.class, "stuff");
      return new ImmutableSomeInfoWithInstantiate(
          ImmutableList.copyOf(validated.keySet()), myInfo.toString());
    }
  }

  @ImmutableInfo(args = {"some_val"})
  public abstract static class SomeInfoWithNonAbstractMethod
      extends BuiltInProviderInfo<SomeInfoWithNonAbstractMethod> {
    public static final BuiltInProvider<SomeInfoWithNonAbstractMethod> PROVIDER =
        BuiltInProvider.of(ImmutableSomeInfoWithNonAbstractMethod.class);

    public abstract String someVal();

    public String getSomeComputedValue() {
      return "bleh";
    }
  }

  @ImmutableInfo(args = {"str_list", "other_list", "val"})
  public abstract static class SomeInfoWithMutableAndImmutable
      extends BuiltInProviderInfo<SomeInfoWithMutableAndImmutable> {
    public static final BuiltInProvider<SomeInfoWithMutableAndImmutable> PROVIDER =
        BuiltInProvider.of(ImmutableSomeInfoWithMutableAndImmutable.class);

    public abstract StarlarkList<String> strList();

    public abstract ImmutableList<Object> otherList();

    public abstract String val();

    public static SomeInfoWithMutableAndImmutable instantiateFromSkylark(
        StarlarkList<String> strList, StarlarkList<Object> otherList, String val) {
      return new ImmutableSomeInfoWithMutableAndImmutable(
          strList, otherList.getImmutableList(), val);
    }
  }

  @Test
  public void someInfoProviderCreatesCorrectInfo() throws Exception {
    SomeInfo someInfo1 = new ImmutableSomeInfo("a", StarlarkInt.of(1));
    assertEquals("a", someInfo1.str());
    assertEquals(StarlarkInt.of(1), someInfo1.myInfo());

    SomeInfo someInfo2 = someInfo1.getProvider().createInfo("b", StarlarkInt.of(2));
    assertEquals("b", someInfo2.str());
    assertEquals(StarlarkInt.of(2), someInfo2.myInfo());

    SomeInfo someInfo3 = SomeInfo.PROVIDER.createInfo("c", StarlarkInt.of(3));
    assertEquals("c", someInfo3.str());
    assertEquals(StarlarkInt.of(3), someInfo3.myInfo());

    Object o =
        TestStarlarkParser.eval(
            "SomeInfo(str='d', my_info=4)",
            ImmutableMap.of(SomeInfo.PROVIDER.getName(), SomeInfo.PROVIDER));

    assertThat(o, Matchers.instanceOf(SomeInfo.class));
    SomeInfo someInfo4 = (SomeInfo) o;
    assertEquals("d", someInfo4.str());
    assertEquals(StarlarkInt.of(4), someInfo4.myInfo());
  }

  @Test
  public void infoWithSetCanBeCreatedProperly()
      throws IllegalAccessException, InstantiationException, InvocationTargetException {
    InfoWithSet someInfo1 = new ImmutableInfoWithSet(ImmutableSet.of("a"));
    assertEquals(ImmutableSet.of("a"), someInfo1.set());

    InfoWithSet someInfo2 = someInfo1.getProvider().createInfo(ImmutableSet.of("b"));
    assertEquals(ImmutableSet.of("b"), someInfo2.set());

    InfoWithSet someInfo3 = InfoWithSet.PROVIDER.createInfo(ImmutableSet.of());
    assertEquals(ImmutableSet.of(), someInfo3.set());
  }

  @Test
  public void infoWithMapCanBeCreatedProperly()
      throws IllegalAccessException, InstantiationException, InvocationTargetException {
    InfoWithMap someInfo1 =
        new ImmutableInfoWithMap(Dict.copyOf(null, ImmutableMap.of("a", StarlarkInt.of(1))));
    assertEquals(Dict.copyOf(null, ImmutableMap.of("a", StarlarkInt.of(1))), someInfo1.map());

    InfoWithMap someInfo2 =
        someInfo1
            .getProvider()
            .createInfo(Dict.copyOf(null, ImmutableMap.of("b", StarlarkInt.of(2))));
    assertEquals(Dict.copyOf(null, ImmutableMap.of("b", StarlarkInt.of(2))), someInfo2.map());

    InfoWithMap someInfo3 = InfoWithMap.PROVIDER.createInfo(Dict.of(null));
    assertEquals(Dict.<String, Integer>of(null), someInfo3.map());
  }

  @Test
  public void differentInfoInstanceProviderKeyEquals()
      throws IllegalAccessException, InstantiationException, InvocationTargetException {
    SomeInfo someInfo1 = new ImmutableSomeInfo("a", StarlarkInt.of(1));

    SomeInfo someInfo2 = SomeInfo.PROVIDER.createInfo("b", StarlarkInt.of(2));

    assertEquals(SomeInfo.PROVIDER.getKey(), someInfo1.getProvider().getKey());
    assertEquals(someInfo1.getProvider().getKey(), someInfo2.getProvider().getKey());
  }

  @Test
  public void differentInfoTypeProviderKeyNotEquals() {
    assertNotEquals(SomeInfo.PROVIDER.getKey(), OtherInfo.PROVIDER.getKey());
  }

  @Test
  public void defaultValuesWorkInStarlarkContext() throws Exception {

    assertEquals(
        new ImmutableSomeInfo("default value", StarlarkInt.of(2)),
        TestStarlarkParser.eval(
            ImmutableSomeInfo.PROVIDER.getName() + "(my_info=2)",
            ImmutableMap.of(ImmutableSomeInfo.PROVIDER.getName(), ImmutableSomeInfo.PROVIDER)));
  }

  @Test
  public void infoWithNoDefaultValueOnAnnotationWorks() throws Exception {

    assertEquals(
        new ImmutableInfoWithNoDefaultValOnAnnotation(StarlarkInt.of(2)),
        TestStarlarkParser.eval(
            ImmutableInfoWithNoDefaultValOnAnnotation.PROVIDER.getName() + "(val=2)",
            ImmutableMap.of(
                InfoWithNoDefaultValOnAnnotation.PROVIDER.getName(),
                InfoWithNoDefaultValOnAnnotation.PROVIDER)));
  }

  @Test
  public void instantiatesFromStaticMethodIfPresent() throws Exception {
    Object o =
        TestStarlarkParser.eval(
            "SomeInfoWithInstantiate(str_list={'d': 'd_value'}, my_info=4)",
            ImmutableMap.of(
                SomeInfoWithInstantiate.PROVIDER.getName(), SomeInfoWithInstantiate.PROVIDER));

    assertThat(o, Matchers.instanceOf(SomeInfoWithInstantiate.class));
    SomeInfoWithInstantiate someInfo4 = (SomeInfoWithInstantiate) o;
    assertEquals(ImmutableList.of("d"), someInfo4.str_list());
    assertEquals("4", someInfo4.myInfo());
  }

  @Test
  public void instantiatesFromStaticMethodWithDefaultValuesIfPresent() throws Exception {
    Object o =
        TestStarlarkParser.eval(
            "SomeInfoWithInstantiate(my_info=4)",
            ImmutableMap.of(
                SomeInfoWithInstantiate.PROVIDER.getName(), SomeInfoWithInstantiate.PROVIDER));

    assertThat(o, Matchers.instanceOf(SomeInfoWithInstantiate.class));
    SomeInfoWithInstantiate someInfo4 = (SomeInfoWithInstantiate) o;
    assertEquals(ImmutableList.<String>of(), someInfo4.str_list());
    assertEquals("4", someInfo4.myInfo());
  }

  @Test
  public void validatesTypesWhenInstantiatingFromStaticMethod() throws Exception {
    thrown.expect(EvalException.class);
    thrown.expectMessage("got value of type 'string', want 'int'");

    TestStarlarkParser.eval(
        "SomeInfoWithInstantiate(my_info='not a number')",
        ImmutableMap.of(
            SomeInfoWithInstantiate.PROVIDER.getName(), SomeInfoWithInstantiate.PROVIDER));
  }

  @Test
  public void fieldNamesComeFromAbstractMethodsOnly()
      throws IllegalAccessException, InstantiationException, InvocationTargetException {
    SomeInfoWithNonAbstractMethod providerInfo =
        SomeInfoWithNonAbstractMethod.PROVIDER.createInfo("some string");

    assertEquals(ImmutableSet.of("some_val"), providerInfo.getFieldNames());
  }

  @Test
  public void isImmutableWorks() throws Exception {

    String buildFile =
        "SomeInfoWithMutableAndImmutable("
            + "val='foo', str_list=mutable_list, other_list=[1,mutable_list])";

    SomeInfoWithMutableAndImmutable out;

    try (TestMutableEnv env =
        new TestMutableEnv(
            ImmutableMap.of(
                "SomeInfoWithMutableAndImmutable", SomeInfoWithMutableAndImmutable.PROVIDER))) {
      StarlarkList<StarlarkInt> mutableList =
          StarlarkList.of(
              env.getEnv().mutability(), StarlarkInt.of(1), StarlarkInt.of(2), StarlarkInt.of(3));
      env.getModule().setGlobal("mutable_list", mutableList);

      out =
          (SomeInfoWithMutableAndImmutable)
              TestStarlarkParser.eval(env.getEnv(), env.getModule(), buildFile);

      assertNotNull(out);
      assertFalse(out.isImmutable());
      assertFalse(BuckSkylarkTypes.isImmutable(out.strList()));
      assertFalse(BuckSkylarkTypes.isImmutable(out.otherList()));
      assertTrue(BuckSkylarkTypes.isImmutable(out.val()));
    }

    assertTrue(out.isImmutable());
    assertTrue(BuckSkylarkTypes.isImmutable(out.strList()));
    assertTrue(BuckSkylarkTypes.isImmutable(out.otherList()));
    assertTrue(BuckSkylarkTypes.isImmutable(out.val()));
  }
}

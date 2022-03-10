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

import static org.junit.Assert.assertEquals;

import com.facebook.buck.core.cell.impl.DefaultCellPathResolver;
import com.facebook.buck.core.cell.nameresolver.CellNameResolver;
import com.facebook.buck.core.config.FakeBuckConfig;
import com.facebook.buck.core.exceptions.DependencyStack;
import com.facebook.buck.core.model.BuildTarget;
import com.facebook.buck.core.model.BuildTargetFactory;
import com.facebook.buck.core.model.ConstantHostTargetConfigurationResolver;
import com.facebook.buck.core.model.TargetConfiguration;
import com.facebook.buck.core.model.TargetConfigurationTransformer;
import com.facebook.buck.core.model.UnconfiguredTargetConfiguration;
import com.facebook.buck.core.model.impl.MultiPlatformTargetConfigurationTransformer;
import com.facebook.buck.core.model.platform.TargetPlatformResolver;
import com.facebook.buck.core.model.platform.ThrowingPlatformResolver;
import com.facebook.buck.core.model.platform.impl.UnconfiguredPlatform;
import com.facebook.buck.core.select.LabelledAnySelectable;
import com.facebook.buck.core.select.SelectableConfigurationContext;
import com.facebook.buck.core.select.SelectableConfigurationContextFactory;
import com.facebook.buck.core.select.SelectorListResolver;
import com.facebook.buck.core.select.TestSelectableResolver;
import com.facebook.buck.core.select.impl.DefaultSelectorListResolver;
import com.facebook.buck.core.sourcepath.DefaultBuildTargetSourcePath;
import com.facebook.buck.core.starlark.knowntypes.KnownUserDefinedRuleTypes;
import com.facebook.buck.core.starlark.rule.attr.impl.IntAttribute;
import com.facebook.buck.io.filesystem.impl.FakeProjectFilesystem;
import com.facebook.buck.rules.coercer.DataTransferObjectDescriptor;
import com.facebook.buck.rules.coercer.DefaultConstructorArgMarshaller;
import com.facebook.buck.rules.coercer.DefaultTypeCoercerFactory;
import com.facebook.buck.rules.param.ParamName;
import com.facebook.buck.skylark.function.FakeSkylarkUserDefinedRuleFactory;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedSet;
import net.starlark.java.eval.Starlark;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class SkylarkDescriptionArgTest {

  @Rule public ExpectedException expected = ExpectedException.none();

  @Test
  public void throwsWhenInvalidFieldIsRequested() throws Exception {

    SkylarkDescriptionArg arg =
        new SkylarkDescriptionArg(FakeSkylarkUserDefinedRuleFactory.createSimpleRule());

    expected.expect(NullPointerException.class);
    arg.getPostCoercionValue(ParamName.bySnakeCase("baz"));
  }

  @Test
  public void throwsWhenSettingWithAnInvalidName() throws Exception {
    SkylarkDescriptionArg arg =
        new SkylarkDescriptionArg(FakeSkylarkUserDefinedRuleFactory.createSimpleRule());

    expected.expect(IllegalStateException.class);
    expected.expectMessage("it was not one of the attributes");
    arg.setPostCoercionValue(ParamName.bySnakeCase("not_declared"), 1);
  }

  @Test
  public void throwsWhenSettingAfterBuilding() throws Exception {
    SkylarkDescriptionArg arg =
        new SkylarkDescriptionArg(FakeSkylarkUserDefinedRuleFactory.createSimpleRule());
    arg.setPostCoercionValue(ParamName.bySnakeCase("name"), "ohmy");
    arg.build();

    expected.expect(IllegalStateException.class);
    expected.expectMessage("after building an instance");
    arg.setPostCoercionValue(ParamName.bySnakeCase("baz"), 1);
  }

  @Test
  public void getsValuesThatHaveBeenSet() throws Exception {
    SkylarkDescriptionArg arg =
        new SkylarkDescriptionArg(FakeSkylarkUserDefinedRuleFactory.createSimpleRule());

    arg.setPostCoercionValue(ParamName.bySnakeCase("baz"), 1);
    assertEquals(1, arg.getPostCoercionValue(ParamName.bySnakeCase("baz")));
  }

  @Test
  public void getsLabelsAndLicenses() throws Exception {
    ImmutableSortedSet<DefaultBuildTargetSourcePath> licenses =
        ImmutableSortedSet.of(
            DefaultBuildTargetSourcePath.of(BuildTargetFactory.newInstance("//:LICENSE")),
            DefaultBuildTargetSourcePath.of(BuildTargetFactory.newInstance("//:LICENSE2")));
    SkylarkDescriptionArg arg =
        new SkylarkDescriptionArg(FakeSkylarkUserDefinedRuleFactory.createSimpleRule());
    arg.setPostCoercionValue(ParamName.bySnakeCase("labels"), ImmutableSortedSet.of("foo", "bar"));
    arg.setPostCoercionValue(ParamName.bySnakeCase("licenses"), licenses);

    assertEquals(ImmutableSortedSet.of("bar", "foo"), arg.getLabels());
    assertEquals(licenses, arg.getLicenses());
  }

  @Test
  public void defaultValuesUsedWhenMarshalling() throws Exception {
    DefaultConstructorArgMarshaller marshaller = new DefaultConstructorArgMarshaller();
    FakeProjectFilesystem filesystem = new FakeProjectFilesystem();
    CellNameResolver cellNameResolver =
        DefaultCellPathResolver.create(filesystem.getRootPath(), FakeBuckConfig.empty().getConfig())
            .getCellNameResolver();
    SelectorListResolver selectorListResolver =
        new DefaultSelectorListResolver(new TestSelectableResolver());
    TargetConfigurationTransformer targetConfigurationTransformer =
        new MultiPlatformTargetConfigurationTransformer(
            (configuration, dependencyStack) -> UnconfiguredPlatform.INSTANCE);
    SelectableConfigurationContext configurationContext =
        SelectableConfigurationContextFactory.UNCONFIGURED;
    BuildTarget target = BuildTargetFactory.newInstance("//foo:bar");
    TargetConfiguration hostConfiguration = UnconfiguredTargetConfiguration.INSTANCE;
    DependencyStack dependencyStack = DependencyStack.root();
    KnownUserDefinedRuleTypes knownRuleTypes = new KnownUserDefinedRuleTypes();

    SkylarkUserDefinedRule fakeRule =
        FakeSkylarkUserDefinedRuleFactory.createRuleFromCallable(
            "some_rule",
            ImmutableMap.of(
                "defaulted", IntAttribute.of(5, "", false, ImmutableList.of()),
                "_hidden", IntAttribute.of(10, "", false, ImmutableList.of())),
            "//foo:bar.bzl",
            (ctx) -> Starlark.NONE);
    knownRuleTypes.addRule(fakeRule);

    DataTransferObjectDescriptor<SkylarkDescriptionArg> constructorArgDescriptor =
        knownRuleTypes
            .getDescriptorByNameChecked("//foo:bar.bzl:some_rule", SkylarkDescriptionArg.class)
            .getDtoDescriptor()
            .apply(new DefaultTypeCoercerFactory());

    ImmutableMap<ParamName, Object> attributes =
        ImmutableMap.of(ParamName.bySnakeCase("name"), "bar");
    ImmutableMap<ParamName, Object> attributes2 =
        ImmutableMap.of(
            ParamName.bySnakeCase("name"), "bar", ParamName.bySnakeCase("defaulted"), 1);

    TargetPlatformResolver platformResolver = new ThrowingPlatformResolver();
    SkylarkDescriptionArg populated1 =
        marshaller.populate(
            cellNameResolver,
            filesystem,
            selectorListResolver,
            configurationContext,
            targetConfigurationTransformer,
            platformResolver,
            target,
            new ConstantHostTargetConfigurationResolver(hostConfiguration),
            dependencyStack,
            constructorArgDescriptor,
            ImmutableSet.builder(),
            ImmutableSet.builder(),
            attributes,
            LabelledAnySelectable.any());

    SkylarkDescriptionArg populated2 =
        marshaller.populate(
            cellNameResolver,
            filesystem,
            selectorListResolver,
            configurationContext,
            targetConfigurationTransformer,
            platformResolver,
            target,
            new ConstantHostTargetConfigurationResolver(hostConfiguration),
            dependencyStack,
            constructorArgDescriptor,
            ImmutableSet.builder(),
            ImmutableSet.builder(),
            attributes2,
            LabelledAnySelectable.any());

    assertEquals(
        ImmutableSortedSet.of(), populated1.getPostCoercionValue(ParamName.bySnakeCase("labels")));
    assertEquals(5, populated1.getPostCoercionValue(ParamName.bySnakeCase("defaulted")));
    assertEquals(10, populated1.getPostCoercionValue(ParamName.bySnakeCase("_hidden")));

    assertEquals(
        ImmutableSortedSet.of(), populated2.getPostCoercionValue(ParamName.bySnakeCase("labels")));
    assertEquals(1, populated2.getPostCoercionValue(ParamName.bySnakeCase("defaulted")));
    assertEquals(10, populated2.getPostCoercionValue(ParamName.bySnakeCase("_hidden")));
  }
}

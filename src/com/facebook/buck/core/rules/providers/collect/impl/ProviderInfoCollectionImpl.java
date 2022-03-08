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

package com.facebook.buck.core.rules.providers.collect.impl;

import com.facebook.buck.core.rules.providers.Provider;
import com.facebook.buck.core.rules.providers.ProviderInfo;
import com.facebook.buck.core.rules.providers.collect.ProviderInfoCollection;
import com.facebook.buck.core.rules.providers.lib.DefaultInfo;
import com.facebook.buck.core.starlark.compatible.BuckSkylarkTypes;
import com.facebook.buck.core.starlark.compatible.MutableObjectException;
import com.google.common.collect.ImmutableMap;
import java.util.Optional;
import javax.annotation.Nullable;
import net.starlark.java.eval.EvalException;
import net.starlark.java.eval.Starlark;
import net.starlark.java.eval.StarlarkSemantics;
import net.starlark.java.eval.StarlarkValue;

/** Implementation of {@link ProviderInfoCollection}. */
public class ProviderInfoCollectionImpl extends ProviderInfoCollection {

  private final ImmutableMap<Provider.Key<?>, ? extends ProviderInfo<?>> infoMap;

  protected ProviderInfoCollectionImpl(
      ImmutableMap<Provider.Key<?>, ? extends ProviderInfo<?>> infoMap) {
    this.infoMap = infoMap;
  }

  @Override
  public Object getIndex(StarlarkSemantics semantics, Object key) throws EvalException {
    verifyKeyIsProvider(
        key, "Type Target only supports indexing by object constructors, got %s instead");
    return BuckSkylarkTypes.skylarkValueFromNullable(getNullable(((Provider<?>) key)));
  }

  @Override
  public boolean containsKey(StarlarkSemantics semantics, Object key) throws EvalException {
    verifyKeyIsProvider(
        key, "Type Target only supports querying by object constructors, got %s instead");
    return contains((Provider<?>) key);
  }

  public static Builder builder() {
    return new Builder();
  }

  public static Builder builderWithExpectedSize(int expectedSize) {
    return new Builder(expectedSize);
  }

  @Override
  public <T extends ProviderInfo<T>> Optional<T> get(Provider<T> provider) {
    return Optional.ofNullable(getNullable(provider));
  }

  @Override
  public <T extends ProviderInfo<T>> boolean contains(Provider<T> provider) {
    return infoMap.containsKey(provider.getKey());
  }

  @Override
  public DefaultInfo getDefaultInfo() {
    return get(DefaultInfo.PROVIDER).get();
  }

  @SuppressWarnings("unchecked")
  @Nullable
  private <T extends ProviderInfo<T>> T getNullable(Provider<T> provider) {
    return (T) infoMap.get(provider.getKey());
  }

  private void verifyKeyIsProvider(Object key, String s) throws EvalException {
    if (!(key instanceof Provider)) {
      throw new EvalException(String.format(s, Starlark.type(key)));
    }
  }

  public static class Builder implements ProviderInfoCollection.Builder {

    protected final ImmutableMap.Builder<Provider.Key<?>, ProviderInfo<?>> mapBuilder;

    protected Builder() {
      mapBuilder = ImmutableMap.builder();
    }

    protected Builder(int expectedSize) {
      mapBuilder = ImmutableMap.builderWithExpectedSize(expectedSize);
    }

    @Override
    public ProviderInfoCollection.Builder put(ProviderInfo<?> info) {
      if (!((StarlarkValue) info).isImmutable()) {
        throw new MutableObjectException(info);
      }
      mapBuilder.put(info.getProvider().getKey(), info);
      return this;
    }

    @Override
    public ProviderInfoCollection build(DefaultInfo info) {
      put(info);
      return new ProviderInfoCollectionImpl(mapBuilder.build());
    }
  }
}

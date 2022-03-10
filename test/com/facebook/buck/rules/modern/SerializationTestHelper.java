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

package com.facebook.buck.rules.modern;

import com.facebook.buck.core.cell.CellPathResolver;
import com.facebook.buck.core.rulekey.AddsToRuleKey;
import com.facebook.buck.core.rules.SourcePathRuleFinder;
import com.facebook.buck.core.sourcepath.resolver.SourcePathResolverAdapter;
import com.facebook.buck.core.toolchain.ToolchainProvider;
import com.facebook.buck.io.filesystem.ProjectFilesystem;
import com.facebook.buck.rules.modern.Deserializer.DataProvider;
import com.facebook.buck.rules.modern.Serializer.Delegate;
import com.facebook.buck.rules.modern.impl.DefaultClassInfoFactory;
import com.facebook.buck.util.types.Either;
import com.google.common.base.Preconditions;
import com.google.common.hash.HashCode;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

public class SerializationTestHelper {

  /**
   * Serialize and deserialize an object.
   *
   * <p>The ruleFinder and cellResolver are used for serialization, the rest for deserialization.
   */
  public static <T extends AddsToRuleKey> T serializeAndDeserialize(
      T instance,
      SourcePathRuleFinder ruleFinder,
      CellPathResolver cellResolver,
      SourcePathResolverAdapter resolver,
      ToolchainProvider toolchainProvider,
      Function<Optional<String>, ProjectFilesystem> filesystemFunction)
      throws IOException {
    Class<T> tClass = (Class<T>) instance.getClass();
    verifyClass(tClass);

    Map<HashCode, byte[]> dataMap = new HashMap<>();
    Delegate serializerDelegate =
        (value, data, children) -> {
          int id = dataMap.size();
          HashCode hash = HashCode.fromInt(id);
          dataMap.put(hash, data);
          return hash;
        };

    Either<HashCode, byte[]> serialized =
        new Serializer(ruleFinder, cellResolver, serializerDelegate)
            .serialize(instance, DefaultClassInfoFactory.forInstance(instance));

    return new Deserializer(filesystemFunction, Class::forName, () -> resolver, toolchainProvider)
        .deserialize(
            new DataProvider() {
              @Override
              public InputStream getData() {
                return new ByteArrayInputStream(serialized.transform(dataMap::get, right -> right));
              }

              @Override
              public DataProvider getChild(HashCode hash) {
                return getDataProvider(dataMap, hash);
              }
            },
            tClass);
  }

  private static <T extends AddsToRuleKey> void verifyClass(Class<T> tClass) {
    Preconditions.checkArgument(
        !tClass.isAnonymousClass(), "Cannot be or reference anonymous classes.");
    Preconditions.checkArgument(!tClass.isLocalClass(), "Cannot be or reference local classes.");
    Preconditions.checkArgument(!tClass.isSynthetic(), "Cannot be or reference synthetic classes.");
    // We don't want to have to deal with inner non-static classes (and verifying usage of state
    // from the outer class).
    Preconditions.checkArgument(
        !tClass.isMemberClass() || Modifier.isStatic(tClass.getModifiers()),
        "Cannot be or reference inner non-static classes.");
  }

  private static DataProvider getDataProvider(Map<HashCode, byte[]> dataMap, HashCode hash) {
    return new DataProvider() {
      @Override
      public InputStream getData() {
        return new ByteArrayInputStream(Preconditions.checkNotNull(dataMap.get(hash)));
      }

      @Override
      public DataProvider getChild(HashCode hash) {
        return getDataProvider(dataMap, hash);
      }
    };
  }
}

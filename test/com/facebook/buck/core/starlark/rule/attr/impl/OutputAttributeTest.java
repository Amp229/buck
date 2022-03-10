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

package com.facebook.buck.core.starlark.rule.attr.impl;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import com.facebook.buck.core.artifact.Artifact;
import com.facebook.buck.core.artifact.ArtifactDeclarationException;
import com.facebook.buck.core.cell.TestCellPathResolver;
import com.facebook.buck.core.cell.nameresolver.CellNameResolver;
import com.facebook.buck.core.filesystems.ForwardRelPath;
import com.facebook.buck.core.model.BuildTarget;
import com.facebook.buck.core.model.BuildTargetFactory;
import com.facebook.buck.core.model.ConstantHostTargetConfigurationResolver;
import com.facebook.buck.core.model.UnconfiguredTargetConfiguration;
import com.facebook.buck.core.model.impl.BuildTargetPaths;
import com.facebook.buck.core.rules.analysis.impl.FakeRuleAnalysisContextImpl;
import com.facebook.buck.core.starlark.rule.attr.Attribute;
import com.facebook.buck.io.filesystem.impl.FakeProjectFilesystem;
import com.facebook.buck.rules.coercer.CoerceFailedException;
import com.google.common.collect.ImmutableMap;
import java.nio.file.Paths;
import net.starlark.java.eval.Starlark;
import org.hamcrest.Matchers;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class OutputAttributeTest {

  private final FakeProjectFilesystem filesystem = new FakeProjectFilesystem();
  private final CellNameResolver cellNameResolver =
      TestCellPathResolver.get(filesystem).getCellNameResolver();

  private final OutputAttribute attr = ImmutableOutputAttribute.of(Starlark.NONE, "", true);

  @Rule public ExpectedException thrown = ExpectedException.none();

  @Test
  public void coercesProperly() throws CoerceFailedException {
    String coercedPath =
        attr.getValue(
            cellNameResolver,
            filesystem,
            ForwardRelPath.of(""),
            UnconfiguredTargetConfiguration.INSTANCE,
            new ConstantHostTargetConfigurationResolver(UnconfiguredTargetConfiguration.INSTANCE),
            "foo/bar.cpp");

    assertEquals("foo/bar.cpp", coercedPath);
  }

  @Test
  public void failsMandatoryCoercionProperly() throws CoerceFailedException {
    thrown.expect(CoerceFailedException.class);

    attr.getValue(
        cellNameResolver,
        filesystem,
        ForwardRelPath.of(""),
        UnconfiguredTargetConfiguration.INSTANCE,
        new ConstantHostTargetConfigurationResolver(UnconfiguredTargetConfiguration.INSTANCE),
        1);
  }

  @Test
  public void failsMandatoryCoercionIfNoneProvided() throws CoerceFailedException {
    thrown.expect(CoerceFailedException.class);

    attr.getValue(
        cellNameResolver,
        filesystem,
        ForwardRelPath.of(""),
        UnconfiguredTargetConfiguration.INSTANCE,
        new ConstantHostTargetConfigurationResolver(UnconfiguredTargetConfiguration.INSTANCE),
        Starlark.NONE);
  }

  @Test
  @SuppressWarnings("unchecked")
  public void failsTransformIfInvalidCoercedTypeProvided() {
    thrown.expect(Exception.class);

    ((Attribute<Object>) (Attribute<?>) attr)
        .getPostCoercionTransform()
        .postCoercionTransform(1, new FakeRuleAnalysisContextImpl(ImmutableMap.of()));
  }

  @Test
  public void failsTransformationOnInvalidPath() throws Throwable {
    thrown.expect(ArtifactDeclarationException.class);

    String value =
        attr.getValue(
            cellNameResolver,
            filesystem,
            ForwardRelPath.of(""),
            UnconfiguredTargetConfiguration.INSTANCE,
            new ConstantHostTargetConfigurationResolver(UnconfiguredTargetConfiguration.INSTANCE),
            "foo/bar\0");
    attr.getPostCoercionTransform()
        .postCoercionTransform(value, new FakeRuleAnalysisContextImpl(ImmutableMap.of()));
  }

  @Test
  public void failsTransformationOnAbsolutePath() throws CoerceFailedException {
    thrown.expect(ArtifactDeclarationException.class);

    String value =
        attr.getValue(
            cellNameResolver,
            filesystem,
            ForwardRelPath.of(""),
            UnconfiguredTargetConfiguration.INSTANCE,
            new ConstantHostTargetConfigurationResolver(UnconfiguredTargetConfiguration.INSTANCE),
            Paths.get("").toAbsolutePath().toString());
    attr.getPostCoercionTransform()
        .postCoercionTransform(value, new FakeRuleAnalysisContextImpl(ImmutableMap.of()));
  }

  @Test
  public void failsTransformationOnParentPath() throws CoerceFailedException {
    thrown.expect(ArtifactDeclarationException.class);

    String value =
        attr.getValue(
            cellNameResolver,
            filesystem,
            ForwardRelPath.of(""),
            UnconfiguredTargetConfiguration.INSTANCE,
            new ConstantHostTargetConfigurationResolver(UnconfiguredTargetConfiguration.INSTANCE),
            "../foo.txt");
    attr.getPostCoercionTransform()
        .postCoercionTransform(value, new FakeRuleAnalysisContextImpl(ImmutableMap.of()));
  }

  @Test
  public void transformsToArtifact() throws CoerceFailedException {
    BuildTarget target = BuildTargetFactory.newInstance("//foo:bar");

    String outputPath =
        attr.getValue(
            cellNameResolver,
            filesystem,
            ForwardRelPath.of(""),
            UnconfiguredTargetConfiguration.INSTANCE,
            new ConstantHostTargetConfigurationResolver(UnconfiguredTargetConfiguration.INSTANCE),
            "subdir/main.cpp");
    Object coerced =
        attr.getPostCoercionTransform()
            .postCoercionTransform(
                outputPath, new FakeRuleAnalysisContextImpl(target, ImmutableMap.of()));

    assertThat(coerced, Matchers.instanceOf(Artifact.class));
    Artifact artifact = (Artifact) coerced;
    assertFalse(artifact.isBound());
    assertFalse(artifact.isSource());
    assertEquals(
        BuildTargetPaths.getBasePath(
                filesystem
                    .getBuckPaths()
                    .shouldIncludeTargetConfigHash(target.getCellRelativeBasePath()),
                target,
                "%s__")
            .toPath(filesystem.getFileSystem())
            .resolve("subdir")
            .resolve("main.cpp")
            .toString(),
        artifact.getShortPath());
  }
}

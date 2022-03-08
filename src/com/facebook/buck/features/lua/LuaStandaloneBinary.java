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

package com.facebook.buck.features.lua;

import com.facebook.buck.core.build.buildable.context.BuildableContext;
import com.facebook.buck.core.build.context.BuildContext;
import com.facebook.buck.core.build.execution.context.IsolatedExecutionContext;
import com.facebook.buck.core.model.BuildTarget;
import com.facebook.buck.core.rulekey.AddToRuleKey;
import com.facebook.buck.core.rules.BuildRuleParams;
import com.facebook.buck.core.rules.impl.AbstractBuildRuleWithDeclaredAndExtraDeps;
import com.facebook.buck.core.sourcepath.ExplicitBuildTargetSourcePath;
import com.facebook.buck.core.sourcepath.SourcePath;
import com.facebook.buck.core.sourcepath.resolver.SourcePathResolverAdapter;
import com.facebook.buck.core.toolchain.tool.Tool;
import com.facebook.buck.io.filesystem.BuildCellRelativePath;
import com.facebook.buck.io.filesystem.ProjectFilesystem;
import com.facebook.buck.io.filesystem.impl.ProjectFilesystemUtils;
import com.facebook.buck.step.Step;
import com.facebook.buck.step.fs.MkdirStep;
import com.facebook.buck.step.fs.RmStep;
import com.facebook.buck.step.isolatedsteps.shell.IsolatedShellStep;
import com.facebook.buck.util.json.ObjectMappers;
import com.google.common.base.Functions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Path;

/** Builds a Lua executable into a standalone package using a given packager tool. */
public class LuaStandaloneBinary extends AbstractBuildRuleWithDeclaredAndExtraDeps {

  @AddToRuleKey private final Tool builder;

  @AddToRuleKey(stringify = true)
  private final Path output;

  @AddToRuleKey private final SourcePath starter;

  @AddToRuleKey private final LuaPackageComponents components;

  @AddToRuleKey private final String mainModule;

  private final boolean cache;

  @AddToRuleKey private final boolean withDownwardApi;

  public LuaStandaloneBinary(
      BuildTarget buildTarget,
      ProjectFilesystem projectFilesystem,
      BuildRuleParams buildRuleParams,
      Tool builder,
      Path output,
      SourcePath starter,
      LuaPackageComponents components,
      String mainModule,
      boolean cache,
      boolean withDownwardApi) {
    super(buildTarget, projectFilesystem, buildRuleParams);
    this.builder = builder;
    this.output = output;
    this.starter = starter;
    this.components = components;
    this.mainModule = mainModule;
    this.cache = cache;
    this.withDownwardApi = withDownwardApi;
  }

  @Override
  public ImmutableList<Step> getBuildSteps(
      BuildContext context, BuildableContext buildableContext) {

    ImmutableList.Builder<Step> steps = ImmutableList.builder();

    buildableContext.recordArtifact(output);

    // Make sure the parent directory exists.
    steps.add(
        MkdirStep.of(
            BuildCellRelativePath.fromCellRelativePath(
                context.getBuildCellRootPath(), getProjectFilesystem(), output.getParent())));

    // Delete any other pex that was there (when switching between pex styles).
    steps.add(
        RmStep.of(
            BuildCellRelativePath.fromCellRelativePath(
                context.getBuildCellRootPath(), getProjectFilesystem(), output),
            true));

    SourcePathResolverAdapter resolver = context.getSourcePathResolver();

    steps.add(
        new IsolatedShellStep(
            getProjectFilesystem().getRootPath(),
            ProjectFilesystemUtils.relativize(
                getProjectFilesystem().getRootPath(), context.getBuildCellRootPath()),
            withDownwardApi) {

          @Override
          public void writeStdin(OutputStream stream) throws IOException {
            ObjectMappers.WRITER.writeValue(
                stream,
                ImmutableMap.of(
                    "modules",
                    Maps.transformValues(
                        components.getModules(),
                        Functions.compose(Object::toString, resolver::getAbsolutePath)),
                    "pythonModules",
                    Maps.transformValues(
                        components.getPythonModules(),
                        Functions.compose(Object::toString, resolver::getAbsolutePath)),
                    "nativeLibraries",
                    Maps.transformValues(
                        components.getNativeLibraries(),
                        Functions.compose(Object::toString, resolver::getAbsolutePath))));
          }

          @Override
          protected ImmutableList<String> getShellCommandInternal(
              IsolatedExecutionContext context) {
            ImmutableList.Builder<String> command = ImmutableList.builder();
            command.addAll(builder.getCommandPrefix(resolver));
            command.add("--entry-point", mainModule);
            command.add("--interpreter");
            command.add(resolver.getAbsolutePath(starter).toString());
            command.add(getProjectFilesystem().resolve(output).toString());
            return command.build();
          }

          @Override
          public String getShortName() {
            return "lua_package";
          }
        });

    return steps.build();
  }

  @Override
  public SourcePath getSourcePathToOutput() {
    return ExplicitBuildTargetSourcePath.of(getBuildTarget(), output);
  }

  @Override
  public boolean isCacheable() {
    return cache;
  }
}

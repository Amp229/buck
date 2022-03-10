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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.junit.Assert.fail;

import com.facebook.buck.core.filesystems.AbsPath;
import com.facebook.buck.core.starlark.compatible.BuckStarlark;
import com.facebook.buck.core.starlark.eventhandler.Event;
import com.facebook.buck.core.starlark.eventhandler.EventKind;
import com.facebook.buck.core.starlark.eventhandler.PrintingEventHandler;
import com.facebook.buck.io.file.MostFiles;
import com.facebook.buck.io.filesystem.ProjectFilesystem;
import com.facebook.buck.io.filesystem.impl.FakeProjectFilesystem;
import com.facebook.buck.skylark.parser.context.ReadConfigContext;
import com.google.common.collect.ImmutableMap;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.EnumSet;
import net.starlark.java.eval.Module;
import net.starlark.java.eval.Mutability;
import net.starlark.java.eval.Starlark;
import net.starlark.java.eval.StarlarkThread;
import net.starlark.java.syntax.ParserInput;
import net.starlark.java.syntax.Program;
import net.starlark.java.syntax.StarlarkFile;
import net.starlark.java.syntax.SyntaxError;
import org.junit.Before;
import org.junit.Test;

public class ReadConfigTest {

  private AbsPath root;
  private PrintingEventHandler eventHandler;
  private ImmutableMap<String, ImmutableMap<String, String>> rawConfig;

  @Before
  public void setUp() {
    ProjectFilesystem projectFilesystem = FakeProjectFilesystem.createRealTempFilesystem();
    root = projectFilesystem.getRootPath();
    eventHandler = new PrintingEventHandler(EnumSet.allOf(EventKind.class));
    rawConfig = ImmutableMap.of();
  }

  @Test
  public void defaultValueIsReturned() throws Exception {
    assertThat(
        evaluate("value = read_config('foo', 'bar', 'baz')").getGlobals().get("value"),
        equalTo("baz"));
  }

  @Test
  public void defaultValueNoneIfPresent() throws Exception {
    rawConfig = ImmutableMap.of("foo", ImmutableMap.of("bar", "baz"));
    assertThat(
        evaluate("value = read_config('foo', 'bar', None)").getGlobals().get("value"),
        equalTo("baz"));
  }

  @Test
  public void defaultValueNoneIfAbsent() throws Exception {
    assertThat(
        evaluate("value = read_config('foo', 'bar', None)").getGlobals().get("value"),
        equalTo(Starlark.NONE));
  }

  @Test
  public void noneIsReturnedWhenFieldIsNotPresent() throws Exception {
    assertThat(
        evaluate("value = read_config('foo', 'bar')").getGlobals().get("value"),
        equalTo(Starlark.NONE));
  }

  @Test
  public void configValueIsReturnedIfExists() throws Exception {
    rawConfig = ImmutableMap.of("foo", ImmutableMap.of("bar", "value"));
    assertThat(
        evaluate("value = read_config('foo', 'bar')").getGlobals().get("value"), equalTo("value"));
  }

  private Module evaluate(String expression) throws IOException, InterruptedException {
    AbsPath buildFile = root.resolve("BUCK");
    MostFiles.write(buildFile, expression);
    return evaluate(buildFile);
  }

  private Module evaluate(AbsPath buildFile) throws IOException, InterruptedException {
    try (Mutability mutability = Mutability.create("BUCK")) {
      return evaluate(buildFile, mutability);
    }
  }

  private Module evaluate(AbsPath buildFile, Mutability mutability)
      throws IOException, InterruptedException {
    byte[] buildFileContent = Files.readAllBytes(buildFile.getPath());
    StarlarkFile buildFileAst =
        StarlarkFile.parse(
            ParserInput.fromString(
                new String(buildFileContent, StandardCharsets.UTF_8), buildFile.toString()));

    ImmutableMap.Builder<String, Object> vars = ImmutableMap.builder();
    vars.putAll(Starlark.UNIVERSE);
    Starlark.addMethods(vars, SkylarkBuildModule.BUILD_MODULE);

    Module module = Module.withPredeclared(vars.build());

    StarlarkThread env = new StarlarkThread(mutability, BuckStarlark.BUCK_STARLARK_SEMANTICS);
    ReadConfigContext readConfigContext = new ReadConfigContext(rawConfig);
    readConfigContext.setup(env);

    if (!buildFileAst.errors().isEmpty()) {
      for (SyntaxError error : buildFileAst.errors()) {
        eventHandler.handle(Event.error(error.location(), error.message()));
      }
      fail();
    }

    try {
      Program program = Program.compileFile(buildFileAst, module.getResolverModule());
      module.allocateGlobalsAfterResolution();
      Starlark.execFileProgram(program, module, env);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
    return module;
  }
}

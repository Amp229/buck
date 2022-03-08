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

package com.facebook.buck.features.ocaml;

import com.facebook.buck.core.build.execution.context.IsolatedExecutionContext;
import com.facebook.buck.core.filesystems.AbsPath;
import com.facebook.buck.core.filesystems.RelPath;
import com.facebook.buck.core.toolchain.tool.Tool;
import com.facebook.buck.step.isolatedsteps.shell.IsolatedShellStep;
import com.google.common.collect.ImmutableList;
import java.nio.file.Path;

/** A yacc step which processes .mly files and outputs .ml and mli files */
public class OcamlYaccStep extends IsolatedShellStep {

  private final ImmutableList<String> ocamlYaccCommand;

  public static class Args {
    public final Tool yaccCompiler;
    public final Path output;
    public final Path input;

    public Args(Tool yaccCompiler, Path output, Path input) {
      this.yaccCompiler = yaccCompiler;
      this.output = output;
      this.input = input;
    }
  }

  private final Args args;

  public OcamlYaccStep(
      AbsPath workingDirectory,
      boolean withDownwardApi,
      ImmutableList<String> ocamlYaccCommand,
      RelPath cellPath,
      Args args) {
    super(workingDirectory, cellPath, withDownwardApi);
    this.args = args;
    this.ocamlYaccCommand = ocamlYaccCommand;
  }

  @Override
  public String getShortName() {
    return "OCaml yacc";
  }

  @Override
  protected ImmutableList<String> getShellCommandInternal(IsolatedExecutionContext context) {
    return ImmutableList.<String>builder()
        .addAll(ocamlYaccCommand)
        .add("-b", OcamlUtil.stripExtension(args.output.toString()))
        .add(args.input.toString())
        .build();
  }
}

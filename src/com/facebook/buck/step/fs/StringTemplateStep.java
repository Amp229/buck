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

package com.facebook.buck.step.fs;

import com.facebook.buck.core.build.execution.context.StepExecutionContext;
import com.facebook.buck.core.exceptions.HumanReadableException;
import com.facebook.buck.step.Step;
import com.facebook.buck.step.StepExecutionResult;
import com.facebook.buck.step.isolatedsteps.common.WriteFileIsolatedStep;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import org.stringtemplate.v4.ST;
import org.stringtemplate.v4.STGroup;

/**
 * A step that creates an {@link ST} by reading a template from {@code templatePath}, calls {@code
 * configure} to configure it, renders it and writes it out to {@code outputPath}.
 */
public class StringTemplateStep implements Step {

  private final String template;
  private final Path templatePath;
  private final Path outputPath;
  private final ImmutableMap<String, ?> values;
  private final Consumer<String> verifier;

  public StringTemplateStep(Path templatePath, Path outputPath, ImmutableMap<String, ?> values) {
    this(templatePath, outputPath, values, noop -> {});
  }

  public StringTemplateStep(
      String template,
      Path templatePath,
      Path outputPath,
      ImmutableMap<String, ?> values,
      Consumer<String> verifier) {
    Preconditions.checkArgument(
        !outputPath.isAbsolute(), "Output must be specified as a relative path: %s", outputPath);
    this.template = template;
    this.templatePath = templatePath;
    this.outputPath = outputPath;
    this.values = values;
    this.verifier = verifier;
  }

  public StringTemplateStep(
      Path templatePath,
      Path outputPath,
      ImmutableMap<String, ?> values,
      Consumer<String> verifier) {
    Preconditions.checkArgument(
        !outputPath.isAbsolute(), "Output must be specified as a relative path: %s", outputPath);
    this.template = readTemplateAsString(templatePath);
    this.templatePath = templatePath;
    this.outputPath = outputPath;
    this.values = values;
    this.verifier = verifier;
  }

  /** Reads template as String from the given template path */
  public static String readTemplateAsString(Path templatePath) {
    try {
      return Files.readString(templatePath);
    } catch (IOException e) {
      throw new HumanReadableException(e, "Can not read template file from file: " + templatePath);
    }
  }

  @Override
  public StepExecutionResult execute(StepExecutionContext context)
      throws IOException, InterruptedException {
    ST st = new ST(new STGroup(), template);
    for (Map.Entry<String, ?> ent : values.entrySet()) {
      st = st.add(ent.getKey(), ent.getValue());
    }
    String content = Objects.requireNonNull(st.render());
    verifier.accept(content);

    return WriteFileIsolatedStep.of(content, outputPath, /* executable */ false)
        .executeIsolatedStep(context);
  }

  @Override
  public String getShortName() {
    return "stringtemplate";
  }

  @Override
  public String getDescription(StepExecutionContext context) {
    return String.format("Render template '%s' to '%s'", templatePath, outputPath);
  }
}

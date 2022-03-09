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

package net.starlark.java.annot.processor;

import java.io.IOException;
import java.io.Writer;
import java.util.Locale;

class SourceWriter {

  private final Writer writer;
  private int indent = 0;

  SourceWriter(Writer writer) {
    this.writer = writer;
  }

  interface IoRunnable {
    void run() throws IOException;
  }

  void indented(IoRunnable runnable) throws IOException {
    ++indent;
    runnable.run();
    --indent;
  }

  void writeLine(String line) throws IOException {
    if (!line.isEmpty()) {
      for (int i = 0; i != indent; ++i) {
        writer.write("  ");
      }
    }
    writer.write(line);
    writer.write("\n");
  }

  void writeLineF(String line, Object... args) throws IOException {
    writeLine(String.format(Locale.US, line, args));
  }

  void ifBlock(String cond, IoRunnable body) throws IOException {
    writeLineF("if (%s) {", cond);
    indented(body);
    writeLineF("}");
  }

  void ifElse(String cond, IoRunnable thenBody, IoRunnable elseBody) throws IOException {
    writeLineF("if (%s) {", cond);
    indented(thenBody);
    writeLineF("} else {");
    indented(elseBody);
    writeLineF("}");
  }

  void whileBlock(String cond, IoRunnable body) throws IOException {
    writeLineF("while (%s) {", cond);
    indented(body);
    writeLineF("}");
  }

  void switchBlock(String cond, IoRunnable body) throws IOException {
    writeLineF("switch (%s) {", cond);
    indented(body);
    writeLineF("}");
  }
}

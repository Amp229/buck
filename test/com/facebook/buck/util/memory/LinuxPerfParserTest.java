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

package com.facebook.buck.util.memory;

import org.junit.Assert;
import org.junit.Test;

public class LinuxPerfParserTest {
  @Test
  public void parsingSmokeTest() {
    String contents = "# comment\n\n119407423;;instructions:uP;70771103;100.00;;";
    ResourceUsage rusage = LinuxPerfParser.parse(contents);
    Assert.assertEquals(119407423, (long) rusage.getInstructionCount().get());
  }

  @Test
  public void parsingNoExceptionsTest() {
    String contents = "<not an number>;;instructions:uP;;;;";
    ResourceUsage rusage = LinuxPerfParser.parse(contents); // no exception thrown
    Assert.assertEquals(0, (long) rusage.getInstructionCount().get());
  }
}

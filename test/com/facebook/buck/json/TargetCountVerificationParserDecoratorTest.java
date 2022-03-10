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

package com.facebook.buck.json;

import static java.util.Collections.singletonList;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;

import com.facebook.buck.core.filesystems.AbsPath;
import com.facebook.buck.core.filesystems.ForwardRelPath;
import com.facebook.buck.core.filesystems.RelPath;
import com.facebook.buck.event.BuckEventBus;
import com.facebook.buck.event.BuckEventBusForTests;
import com.facebook.buck.event.BuckEventBusForTests.CapturingEventListener;
import com.facebook.buck.parser.api.BuildFileManifest;
import com.facebook.buck.parser.api.ProjectBuildFileParser;
import com.facebook.buck.parser.api.RawTargetNode;
import com.facebook.buck.util.collect.TwoArraysImmutableHashMap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSortedSet;
import java.util.HashMap;
import java.util.Map;
import org.easymock.EasyMock;
import org.junit.Before;
import org.junit.Test;

public class TargetCountVerificationParserDecoratorTest {

  private CapturingEventListener capturingEventListener;
  private AbsPath root;
  private ForwardRelPath path;
  private AbsPath pathAbs;
  private ProjectBuildFileParser parserMock;
  private ImmutableMap<String, RawTargetNode> rawTargets;
  private BuckEventBus eventBus;

  @Before
  public void setUp() {
    eventBus = BuckEventBusForTests.newInstance();
    capturingEventListener = new CapturingEventListener();
    eventBus.register(capturingEventListener);
    root = RelPath.get(".").toAbsolutePath().normalize();
    path = ForwardRelPath.of("bar");
    pathAbs = root.resolve(path);
    parserMock = EasyMock.createMock(ProjectBuildFileParser.class);

    Map<String, Object> retMap1 = new HashMap<>();
    retMap1.put("a", "a");
    retMap1.put("b", "b");
    retMap1.put("c", "c");
    retMap1.put("d", "d");
    retMap1.put("e", "e");

    String[] names = {"a", "b", "c", "d", "e"};
    ImmutableMap.Builder<String, RawTargetNode> builder =
        ImmutableMap.builderWithExpectedSize(names.length);
    for (String name : names) {
      builder.put(
          name,
          RawTargetNode.copyOf(
              ForwardRelPath.EMPTY,
              "java_library",
              ImmutableList.of(),
              ImmutableList.of(),
              retMap1));
    }

    rawTargets = builder.build();
  }

  private void assertWarningIsEmitted() {
    EasyMock.verify(parserMock);

    String expectedWarning =
        String.format(
            "Number of expanded targets - %1$d - in file %2$s exceeds the threshold of %3$d. This could result in really slow builds.",
            5, pathAbs.toString(), 3);

    assertThat(
        capturingEventListener.getConsoleEventLogMessages(),
        equalTo(singletonList(expectedWarning)));
  }

  private void assertWarningIsNotEmitted() {
    EasyMock.verify(parserMock);

    assertThat(capturingEventListener.getConsoleEventLogMessages().size(), equalTo(0));
  }

  private TargetCountVerificationParserDecorator newParserDelegate(int threshold) {
    return new TargetCountVerificationParserDecorator(parserMock, threshold, eventBus, root);
  }

  @Test
  public void givenTargetCountExceedingLimitWhenGetBuildFileManifestIsInvokedAWarningIsEmitted()
      throws Exception {
    EasyMock.expect(parserMock.getManifest(path)).andReturn(toBuildFileManifest(this.rawTargets));

    TargetCountVerificationParserDecorator parserDelegate = newParserDelegate(3);
    EasyMock.replay(parserMock);
    parserDelegate.getManifest(path);

    assertWarningIsEmitted();
  }

  private BuildFileManifest toBuildFileManifest(ImmutableMap<String, RawTargetNode> rawTargets) {
    return BuildFileManifest.of(
        TwoArraysImmutableHashMap.copyOf(rawTargets),
        ImmutableSortedSet.of(),
        ImmutableMap.of(),
        ImmutableList.of(),
        ImmutableList.of());
  }

  @Test
  public void
      givenTargetCountNotExceedingLimitWhenGetBuildFileManifestIsInvokedAWarningIsNotEmitted()
          throws Exception {
    EasyMock.expect(parserMock.getManifest(path)).andReturn(toBuildFileManifest(rawTargets));

    TargetCountVerificationParserDecorator parserDelegate = newParserDelegate(6);
    EasyMock.replay(parserMock);
    parserDelegate.getManifest(path);

    assertWarningIsNotEmitted();
  }

  @Test
  public void parserReportProfileCalled() throws Exception {
    TargetCountVerificationParserDecorator parserDelegate = newParserDelegate(6);
    parserMock.reportProfile();
    EasyMock.expectLastCall();
    EasyMock.replay(parserMock);
    parserDelegate.reportProfile();
    EasyMock.verify(parserMock);
  }

  @Test
  public void parserCloseCalled() throws Exception {
    TargetCountVerificationParserDecorator parserDelegate = newParserDelegate(6);
    parserMock.close();
    EasyMock.expectLastCall();
    EasyMock.replay(parserMock);
    parserDelegate.close();
    EasyMock.verify(parserMock);
  }
}

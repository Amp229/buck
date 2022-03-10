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

package com.facebook.buck.event;

import static org.hamcrest.MatcherAssert.assertThat;

import com.facebook.buck.util.timing.SettableFakeClock;
import com.google.common.base.CaseFormat;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.google.common.eventbus.Subscribe;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.hamcrest.Matchers;
import org.junit.Test;

public class SimplePerfEventTest {

  private void assertPerfEvent(
      BuckEvent event,
      SimplePerfEvent.PerfEventTitle id,
      SimplePerfEvent.Type type,
      ImmutableMap<String, String> info) {
    assertThat(event, Matchers.instanceOf(SimplePerfEvent.class));

    SimplePerfEvent perfEvent = (SimplePerfEvent) event;

    assertThat(
        perfEvent.getTitle(),
        Matchers.equalTo(
            SimplePerfEvent.PerfEventTitle.of(
                CaseFormat.UPPER_CAMEL
                    .converterTo(CaseFormat.LOWER_UNDERSCORE)
                    .convert(id.getValue()))));
    assertThat(perfEvent.getEventType(), Matchers.equalTo(type));
    assertThat(
        Maps.transformValues(perfEvent.getEventInfo(), Object::toString), Matchers.equalTo(info));
  }

  @Test
  public void testManuallyCreatedStartEvents() {
    SimplePerfEvent.PerfEventTitle testEventId = SimplePerfEvent.PerfEventTitle.of("Test");

    assertPerfEvent(
        SimplePerfEvent.started(testEventId),
        testEventId,
        SimplePerfEvent.Type.STARTED,
        ImmutableMap.of());

    assertPerfEvent(
        SimplePerfEvent.started(testEventId, "k1", "v1"),
        testEventId,
        SimplePerfEvent.Type.STARTED,
        ImmutableMap.of("k1", "v1"));

    assertPerfEvent(
        SimplePerfEvent.started(testEventId, "k1", "v1", "k2", "v2"),
        testEventId,
        SimplePerfEvent.Type.STARTED,
        ImmutableMap.of("k1", "v1", "k2", "v2"));

    assertPerfEvent(
        SimplePerfEvent.started(testEventId, ImmutableMap.of("k1", "v1", "k2", "v2", "k3", "v3")),
        testEventId,
        SimplePerfEvent.Type.STARTED,
        ImmutableMap.of("k1", "v1", "k2", "v2", "k3", "v3"));
  }

  private SimplePerfEvent.Started newStartedEvent(SimplePerfEvent.PerfEventTitle testEventId) {
    return SimplePerfEvent.started(testEventId, "XX", "YY");
  }

  @Test
  public void testManuallyCreatedUpdateEvents() {
    SimplePerfEvent.PerfEventTitle testEventId = SimplePerfEvent.PerfEventTitle.of("Test");
    // Info from the started event does not get folded into the update/finished ones.

    assertPerfEvent(
        newStartedEvent(testEventId).createUpdateEvent(ImmutableMap.of()),
        testEventId,
        SimplePerfEvent.Type.UPDATED,
        ImmutableMap.of());

    assertPerfEvent(
        newStartedEvent(testEventId).createUpdateEvent("k1", "v1"),
        testEventId,
        SimplePerfEvent.Type.UPDATED,
        ImmutableMap.of("k1", "v1"));

    assertPerfEvent(
        newStartedEvent(testEventId).createUpdateEvent("k1", "v1", "k2", "v2"),
        testEventId,
        SimplePerfEvent.Type.UPDATED,
        ImmutableMap.of("k1", "v1", "k2", "v2"));

    assertPerfEvent(
        newStartedEvent(testEventId)
            .createUpdateEvent(ImmutableMap.of("k1", "v1", "k2", "v2", "k3", "v3")),
        testEventId,
        SimplePerfEvent.Type.UPDATED,
        ImmutableMap.of("k1", "v1", "k2", "v2", "k3", "v3"));
  }

  @Test
  public void testManuallyCreatedFinshedEvents() {
    SimplePerfEvent.PerfEventTitle testEventId = SimplePerfEvent.PerfEventTitle.of("Test");

    assertPerfEvent(
        newStartedEvent(testEventId).createFinishedEvent(ImmutableMap.of()),
        testEventId,
        SimplePerfEvent.Type.FINISHED,
        ImmutableMap.of());

    assertPerfEvent(
        newStartedEvent(testEventId).createFinishedEvent("k1", "v1"),
        testEventId,
        SimplePerfEvent.Type.FINISHED,
        ImmutableMap.of("k1", "v1"));

    assertPerfEvent(
        newStartedEvent(testEventId).createFinishedEvent("k1", "v1", "k2", "v2"),
        testEventId,
        SimplePerfEvent.Type.FINISHED,
        ImmutableMap.of("k1", "v1", "k2", "v2"));

    assertPerfEvent(
        newStartedEvent(testEventId)
            .createFinishedEvent(ImmutableMap.of("k1", "v1", "k2", "v2", "k3", "v3")),
        testEventId,
        SimplePerfEvent.Type.FINISHED,
        ImmutableMap.of("k1", "v1", "k2", "v2", "k3", "v3"));
  }

  @Test(expected = IllegalStateException.class)
  public void testThrowsOnDoubleFinish() {
    SimplePerfEvent.Started started = newStartedEvent(SimplePerfEvent.PerfEventTitle.of("test"));

    started.createFinishedEvent();
    started.createFinishedEvent();
  }

  private static class SimplePerfEventListener {
    private final ImmutableList.Builder<SimplePerfEvent> perfEventBuilder = ImmutableList.builder();

    public ImmutableList<SimplePerfEvent> getPerfEvents() {
      return perfEventBuilder.build();
    }

    @Subscribe
    public void buckEvent(SimplePerfEvent perfEvent) {
      perfEventBuilder.add(perfEvent);
    }
  }

  @Test
  public void testScopedEvents() {
    SimplePerfEvent.PerfEventTitle testEventId = SimplePerfEvent.PerfEventTitle.of("Unicorn");

    SimplePerfEventListener listener = new SimplePerfEventListener();
    BuckEventBus eventBus = BuckEventBusForTests.newInstance();
    eventBus.register(listener);

    // This does absolutely nothing, but shouldn't crash either.
    try (SimplePerfEvent.Scope scope = SimplePerfEvent.scope(Optional.empty(), testEventId)) {
      scope.appendFinishedInfo("finished", "info");
      scope.update(ImmutableMap.of("update", "updateValue"));
    }

    try (SimplePerfEvent.Scope scope = SimplePerfEvent.scope(eventBus.isolated(), testEventId)) {
      scope.appendFinishedInfo("finished", "info");
      scope.update(ImmutableMap.of("update", "updateValue"));
      scope.update(ImmutableMap.of("update", "laterUpdate"));
    }

    ImmutableList<SimplePerfEvent> perfEvents = listener.getPerfEvents();
    assertThat(perfEvents, Matchers.hasSize(4));

    assertPerfEvent(
        perfEvents.get(0), testEventId, SimplePerfEvent.Type.STARTED, ImmutableMap.of());

    assertPerfEvent(
        perfEvents.get(1),
        testEventId,
        SimplePerfEvent.Type.UPDATED,
        ImmutableMap.of("update", "updateValue"));

    assertPerfEvent(
        perfEvents.get(2),
        testEventId,
        SimplePerfEvent.Type.UPDATED,
        ImmutableMap.of("update", "laterUpdate"));

    assertPerfEvent(
        perfEvents.get(3),
        testEventId,
        SimplePerfEvent.Type.FINISHED,
        ImmutableMap.of("finished", "info"));
  }

  @Test
  public void testMinimumTimeScope() {
    SimplePerfEvent.PerfEventTitle ignoredEventId = SimplePerfEvent.PerfEventTitle.of("IgnoreMe");
    SimplePerfEvent.PerfEventTitle loggedEventId = SimplePerfEvent.PerfEventTitle.of("LogMe");
    SimplePerfEvent.PerfEventTitle parentId = SimplePerfEvent.PerfEventTitle.of("Parent");

    SimplePerfEventListener listener = new SimplePerfEventListener();
    SettableFakeClock clock = SettableFakeClock.DO_NOT_CARE;
    BuckEventBus eventBus = BuckEventBusForTests.newInstance(clock);
    eventBus.register(listener);

    try (SimplePerfEvent.Scope parent = SimplePerfEvent.scope(eventBus.isolated(), parentId)) {
      clock.advanceTimeNanos(10L);

      try (SimplePerfEvent.Scope scope =
          SimplePerfEvent.scopeIgnoringShortEvents(
              eventBus.isolated(), ignoredEventId, parent, 1, TimeUnit.SECONDS)) {
        clock.advanceTimeNanos(10L);
      }

      clock.advanceTimeNanos(10L);

      try (SimplePerfEvent.Scope scope =
          SimplePerfEvent.scopeIgnoringShortEvents(
              eventBus.isolated(), loggedEventId, parent, 1, TimeUnit.MILLISECONDS)) {
        clock.advanceTimeNanos(TimeUnit.MILLISECONDS.toNanos(2));
      }
    }

    ImmutableList<SimplePerfEvent> perfEvents = listener.getPerfEvents();
    assertThat(perfEvents, Matchers.hasSize(4));

    assertPerfEvent(perfEvents.get(0), parentId, SimplePerfEvent.Type.STARTED, ImmutableMap.of());

    assertPerfEvent(
        perfEvents.get(1), loggedEventId, SimplePerfEvent.Type.STARTED, ImmutableMap.of());

    assertPerfEvent(
        perfEvents.get(2), loggedEventId, SimplePerfEvent.Type.FINISHED, ImmutableMap.of());

    assertPerfEvent(
        perfEvents.get(3),
        parentId,
        SimplePerfEvent.Type.FINISHED,
        ImmutableMap.of(
            "ignore_me_accumulated_count", "1",
            "ignore_me_accumulated_duration_ns", "10"));
  }
}

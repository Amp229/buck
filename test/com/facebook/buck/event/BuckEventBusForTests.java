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

import com.facebook.buck.core.model.BuildId;
import com.facebook.buck.test.external.ExternalTestRunnerSelectionEvent;
import com.facebook.buck.util.timing.Clock;
import com.facebook.buck.util.timing.DefaultClock;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Streams;
import com.google.common.eventbus.Subscribe;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Factory to create a {@link BuckEventBus} for tests.
 *
 * <p>Also provides access to fields of a {@link BuckEventBus} that are not visible to the business
 * logic.
 */
public class BuckEventBusForTests {

  public static final BuildId BUILD_ID_FOR_TEST = new BuildId("CAFEBABE");

  /** Utility class: do not instantiate. */
  private BuckEventBusForTests() {}

  @VisibleForTesting
  public static BuckEventBus newInstance() {
    return newInstance(new DefaultClock(), BUILD_ID_FOR_TEST);
  }

  @VisibleForTesting
  public static BuckEventBus newInstance(Clock clock) {
    return newInstance(clock, BUILD_ID_FOR_TEST);
  }

  /**
   * This registers an {@link ErrorListener}. This is helpful when errors are logged during tests
   * that would not otherwise be noticed.
   */
  public static BuckEventBus newInstance(Clock clock, BuildId buildId) {
    BuckEventBus buckEventBus =
        new DefaultBuckEventBus(
            clock, false, buildId, DefaultBuckEventBus.DEFAULT_SHUTDOWN_TIMEOUT_MS);
    buckEventBus.register(new ErrorListener());
    return buckEventBus;
  }

  /** Error listener that prints events at level {@link Level#WARNING} or higher. */
  private static class ErrorListener {
    @Subscribe
    public void logEvent(ConsoleEvent event) {
      Level level = event.getLevel();
      if (level.intValue() >= Level.WARNING.intValue()) {
        System.err.println(event.getMessage());
      }
    }
  }

  public static class CapturingEventListener {
    private final List<ConsoleEvent> consoleEvents = new ArrayList<>();
    private final List<StepEvent> stepEvents = new ArrayList<>();
    private final List<SimplePerfEvent> simplePerfEvents = new ArrayList<>();
    private final List<ExternalTestRunnerSelectionEvent> externalRunnerSelectionEvents =
        new ArrayList<>();

    @Subscribe
    public void consoleEvent(ConsoleEvent event) {
      consoleEvents.add(event);
    }

    @Subscribe
    public void stepEvent(StepEvent event) {
      stepEvents.add(event);
    }

    @Subscribe
    public void simplePerfEvent(SimplePerfEvent event) {
      simplePerfEvents.add(event);
    }

    @Subscribe
    public void externalRunnerSelectionEvent(ExternalTestRunnerSelectionEvent event) {
      externalRunnerSelectionEvents.add(event);
    }

    public List<String> getConsoleEventLogMessages() {
      return toLogMessages(consoleEvents);
    }

    public List<String> getStepEventLogMessages() {
      return toLogMessages(stepEvents);
    }

    public List<String> getSimplePerfEvents() {
      return toLogMessages(simplePerfEvents);
    }

    public List<ExternalTestRunnerSelectionEvent> getExternalRunnerSelectionEvents() {
      return externalRunnerSelectionEvents;
    }

    private List<String> toLogMessages(List<? extends AbstractBuckEvent> events) {
      return events.stream().map(Object::toString).collect(ImmutableList.toImmutableList());
    }

    public Set<Long> getEventsThreadIds() {
      Stream<Long> stream1 = stepEvents.stream().map(AbstractBuckEvent::getThreadId);
      Stream<Long> stream2 = simplePerfEvents.stream().map(AbstractBuckEvent::getThreadId);
      return Streams.concat(stream1, stream2).collect(Collectors.toSet());
    }
  }
}

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

package com.facebook.buck.jvm.java.tracing;

import com.facebook.buck.event.EventKey;
import com.facebook.buck.event.SimplePerfEvent;
import com.facebook.buck.event.WorkAdvanceEvent;
import com.google.common.collect.ImmutableMap;

/** Base class for events about the phases of compilation within javac. */
public abstract class JavacPhaseEvent extends SimplePerfEvent implements WorkAdvanceEvent {

  public enum Phase {
    /** Parsing a single source file. Filename will be in the args. */
    PARSE(Constants.PARSE),
    /**
     * Entering all parse trees into the compiler's symbol tables. All filenames will be in the
     * args.
     */
    ENTER(Constants.ENTER),
    /** Overall annotation processing phase, including all rounds. No args. */
    ANNOTATION_PROCESSING(Constants.ANNOTATION_PROCESSING),
    /**
     * A single round of annotation processing, including running the relevant processors, parsing
     * any source files they create, then re-entering all previously parsed files into new symbol
     * tables. Round number and whether it's the last round will be in the args.
     */
    ANNOTATION_PROCESSING_ROUND(Constants.ANNOTATION_PROCESSING_ROUND),
    /**
     * Just running the annotation processors that are relevant to the sources being compiled. No
     * args.
     */
    RUN_ANNOTATION_PROCESSORS(Constants.RUN_ANNOTATION_PROCESSORS),
    /**
     * Analyze and transform the AST for a single type to prepare for code generation. Source file
     * and type being analyzed will be in the args.
     */
    ANALYZE(Constants.ANALYZE),
    /**
     * Generate a class file for a single type. Source file and type being generated will be in the
     * args.
     */
    GENERATE(Constants.GENERATE);

    private final String name;

    Phase(String name) {
      this.name = name;
    }

    public static Phase fromString(String value) {
      switch (value) {
        case Constants.PARSE:
          return PARSE;
        case Constants.ENTER:
          return ENTER;
        case Constants.ANNOTATION_PROCESSING:
          return ANNOTATION_PROCESSING;
        case Constants.ANNOTATION_PROCESSING_ROUND:
          return ANNOTATION_PROCESSING_ROUND;
        case Constants.RUN_ANNOTATION_PROCESSORS:
          return RUN_ANNOTATION_PROCESSORS;
        case Constants.ANALYZE:
          return ANALYZE;
        case Constants.GENERATE:
          return GENERATE;
        default:
          throw new IllegalArgumentException(
              Phase.class.getName() + " cannot be created from value " + value);
      }
    }

    @Override
    public String toString() {
      return name;
    }
  }

  public static class Constants {

    public static final String PARSE = "parse";
    public static final String ENTER = "enter";
    public static final String ANNOTATION_PROCESSING = "annotation processing";
    public static final String ANNOTATION_PROCESSING_ROUND = "annotation processing round";
    public static final String RUN_ANNOTATION_PROCESSORS = "run annotation processors";
    public static final String ANALYZE = "analyze";
    public static final String GENERATE = "generate";
    public static final String EVENT_CATEGORY_NAME = "javac";
  }

  private final String buildTargetFulyQualifiedName;
  private final Phase phase;
  private final ImmutableMap<String, String> args;

  protected JavacPhaseEvent(
      EventKey eventKey, String buildTargetName, Phase phase, ImmutableMap<String, String> args) {
    super(eventKey);
    this.buildTargetFulyQualifiedName = buildTargetName;
    this.phase = phase;
    this.args = args;
  }

  public String getBuildTargetFulyQualifiedName() {
    return buildTargetFulyQualifiedName;
  }

  public Phase getPhase() {
    return phase;
  }

  public ImmutableMap<String, String> getArgs() {
    return args;
  }

  @Override
  protected String getValueString() {
    return buildTargetFulyQualifiedName;
  }

  @Override
  public String getCategory() {
    return Constants.EVENT_CATEGORY_NAME;
  }

  @Override
  public PerfEventTitle getTitle() {
    return PerfEventTitle.of(getPhase().toString());
  }

  @Override
  public ImmutableMap<String, Object> getEventInfo() {
    return ImmutableMap.copyOf(getArgs()); // upcast values from String to Object
  }

  public static Started started(
      String buildTargetName, Phase phase, ImmutableMap<String, String> args) {
    return new Started(buildTargetName, phase, args);
  }

  public static Finished finished(Started startedEvent, ImmutableMap<String, String> args) {
    return new Finished(startedEvent, args);
  }

  public static class Started extends JavacPhaseEvent {

    public Started(String buildTargetName, Phase phase, ImmutableMap<String, String> args) {
      super(EventKey.unique(), buildTargetName, phase, args);
    }

    @Override
    public String getEventName() {
      return String.format("javac.%sStarted", getPhase().toString());
    }

    @Override
    public Type getEventType() {
      return Type.STARTED;
    }
  }

  public static class Finished extends JavacPhaseEvent {

    public Finished(JavacPhaseEvent.Started startedEvent, ImmutableMap<String, String> args) {
      super(
          startedEvent.getEventKey(),
          startedEvent.getBuildTargetFulyQualifiedName(),
          startedEvent.getPhase(),
          args);
    }

    @Override
    public String getEventName() {
      return String.format("javac.%sFinished", getPhase().toString());
    }

    @Override
    public Type getEventType() {
      return Type.FINISHED;
    }
  }
}

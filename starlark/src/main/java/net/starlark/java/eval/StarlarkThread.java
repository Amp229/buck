/*
 * Portions Copyright (c) Meta Platforms, Inc. and affiliates.
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

// Copyright 2014 The Bazel Authors. All rights reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//    http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package net.starlark.java.eval;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;
import net.starlark.java.syntax.Location;

/**
 * An StarlarkThread represents a Starlark thread.
 *
 * <p>It holds the stack of active Starlark and built-in function calls. In addition, it may hold
 * per-thread application state (see {@link #setThreadLocal}) that passes through Starlark functions
 * but does not directly affect them, such as information about the BUILD file being loaded.
 *
 * <p>StarlarkThreads are not thread-safe: they should be confined to a single Java thread.
 *
 * <p>Every StarlarkThread has an associated {@link Mutability}, which should be created for that
 * thread, and closed once the thread's work is done. (A try-with-resources statement is handy for
 * this purpose.) Starlark values created by the thread are associated with the thread's Mutability,
 * so that when the Mutability is closed at the end of the computation, all the values created by
 * the thread become frozen. This pattern ensures that all Starlark values are frozen before they
 * are published to another thread, and thus that concurrently executing Starlark threads are free
 * from data races. Once a thread's mutability is frozen, the thread is unlikely to be useful for
 * further computation because it can no longer create mutable values. (This is occasionally
 * valuable in tests.)
 */
public final class StarlarkThread {

  /** The mutability of values created by this thread. */
  private final Mutability mutability;

  // profiler state
  //
  // The profiler field (and savedThread) are set when we first observe during a
  // push (function call entry) that the profiler is active. They are unset
  // not in the corresponding pop, but when the last frame is popped, because
  // the profiler session might start in the middle of a call and/or run beyond
  // the lifetime of this thread.
  final AtomicInteger cpuTicks = new AtomicInteger();
  @Nullable private CpuProfiler profiler;
  StarlarkThread savedThread; // saved StarlarkThread, when profiling reentrant evaluation

  private final Map<Class<?>, Object> threadLocals = new HashMap<>();

  private boolean interruptible = true;

  long steps; // count of logical computation steps executed so far
  long stepLimit = Long.MAX_VALUE; // limit on logical computation steps

  /** Current evaluation produced external side effect, e. g. print or rule creation. */
  private boolean wasSideEffect = true;

  /** Clear side effect flag for the current thread and return the previous flag value. */
  boolean pushSideEffect() {
    boolean prevExternalSideEffect = wasSideEffect;
    wasSideEffect = false;
    return prevExternalSideEffect;
  }

  /** Query side effect flag. */
  boolean wasSideEffect() {
    return wasSideEffect;
  }

  /** Restore previous side effect flag. */
  void popSideEffect(boolean saved) {
    // If nested invocation performed side effect, then outer invocation also has side effect.
    wasSideEffect |= saved;
  }

  /** Mark current thread as performing side effects. */
  void recordSideEffect() {
    wasSideEffect = true;
  }

  /**
   * Returns the number of Starlark computation steps executed by this thread according to a
   * small-step semantics. (Today, that means exec, eval, and assign operations executed by the
   * tree-walking evaluator, but in future will mean byte code instructions; the two are not
   * commensurable.)
   */
  public long getExecutedSteps() {
    return steps;
  }

  /**
   * Sets the maximum number of Starlark computation steps that may be executed by this thread (see
   * {@link #getExecutedSteps}). When the step counter reaches or exceeds this value, execution
   * fails with an EvalException.
   */
  public void setMaxExecutionSteps(long steps) {
    this.stepLimit = steps;
  }

  /**
   * Disables polling of the {@link java.lang.Thread#interrupted} flag during Starlark evaluation.
   */
  // TODO(adonovan): expose a public API for this if we can establish a stronger semantics. (There
  // are other ways besides polling for evaluation to be interrupted, such as calling certain
  // built-in functions.)
  void ignoreThreadInterrupts() {
    interruptible = false;
  }

  void checkInterrupt() throws InterruptedException {
    if (interruptible && Thread.interrupted()) {
      throw new InterruptedException();
    }
  }

  /**
   * setThreadLocal saves {@code value} as a thread-local variable of this Starlark thread, keyed by
   * {@code key}, so that it can later be retrieved by {@code getThreadLocal(key)}.
   */
  public <T> void setThreadLocal(Class<T> key, T value) {
    threadLocals.put(key, value);
  }

  /**
   * getThreadLocal returns the value {@code v} supplied to the most recent {@code
   * setThreadLocal(key, v)} call, or null if there was no prior call.
   */
  public <T> T getThreadLocal(Class<T> key) {
    Object v = threadLocals.get(key);
    return v == null ? null : key.cast(v);
  }

  /** A Frame records information about an active function call. */
  static final class Frame {

    final StarlarkCallable fn; // the called function

    // Current PC location. Initially fn.getLocation(); for Starlark functions,
    // it is updated at key points when it may be observed: calls, breakpoints, errors.
    @Nullable BcEval bcEval;

    private Frame(StarlarkCallable fn) {
      this.fn = fn;
    }

    public StarlarkCallable getFunction() {
      return fn;
    }

    public ImmutableList<CallStackEntry> getLocation() {
      if (bcEval != null) {
        return bcEval.location();
      } else {
        return ImmutableList.of(new CallStackEntry(fn.getName(), Location.BUILTIN));
      }
    }

    @Override
    public String toString() {
      return fn.getName() + "@" + getLocation();
    }
  }

  /** The semantics options that affect how Starlark code is evaluated. */
  private final StarlarkSemantics semantics;

  /** Whether recursive calls are allowed (cached from semantics). */
  private final boolean allowRecursion;

  /** PrintHandler for Starlark print statements. */
  private PrintHandler printHandler = StarlarkThread::defaultPrintHandler;

  /** Loader for Starlark load statements. Null if loading is disallowed. */
  @Nullable private Loader loader = null;

  /** Stack of active function calls. */
  private final ArrayList<Frame> callstack = new ArrayList<>();

  /** A hook for notifications of assignments at top level. */
  PostAssignHook postAssignHook;

  /** Pushes a function onto the call stack. */
  void push(StarlarkCallable fn) {
    Frame fr = new Frame(fn);
    callstack.add(fr);
  }

  /** Pops a function off the call stack. */
  void pop() {
    callstack.remove(callstack.size() - 1); // pop
  }

  /** Returns the mutability for values created by this thread. */
  public Mutability mutability() {
    return mutability;
  }

  /**
   * A PrintHandler determines how a Starlark thread deals with print statements. It is invoked by
   * the built-in {@code print} function. Its default behavior is to write the message to standard
   * error, preceded by the location of the print statement, {@code thread.getCallerLocation()}.
   */
  @FunctionalInterface
  public interface PrintHandler {
    void print(StarlarkThread thread, String msg);
  }

  /** Returns the PrintHandler for Starlark print statements. */
  PrintHandler getPrintHandler() {
    return printHandler;
  }

  /** Sets the behavior of Starlark print statements executed by this thread. */
  public void setPrintHandler(PrintHandler h) {
    this.printHandler = Preconditions.checkNotNull(h);
  }

  private static void defaultPrintHandler(StarlarkThread thread, String msg) {
    System.err.println(thread.getCallerLocation() + ": " + msg);
  }

  /**
   * A Loader determines the behavior of load statements executed by this thread. It returns the
   * named module, or null if not found.
   */
  @FunctionalInterface
  public interface Loader {
    @Nullable
    LoadedModule load(String module);
  }

  /** Returns the loader for Starlark load statements. */
  Loader getLoader() {
    return loader;
  }

  /** Sets the behavior of Starlark load statements executed by this thread. */
  public void setLoader(Loader loader) {
    this.loader = Preconditions.checkNotNull(loader);
  }

  /** Reports whether {@code fn} has been recursively reentered within this thread. */
  boolean isRecursiveCall(StarlarkFunction fn) {
    // Find fn buried within stack. (The top of the stack is assumed to be fn.)
    for (int i = callstack.size() - 2; i >= 0; --i) {
      Frame fr = callstack.get(i);
      // We compare code, not closure values, otherwise one can defeat the
      // check by writing the Y combinator.
      // We use identity comparison of location because rfn object is lost,
      // and location pointer is a good enough function identifier.
      if (fr.fn instanceof StarlarkFunction && fr.fn.getLocation() == fn.getLocation()) {
        return true;
      }
    }
    return false;
  }

  /**
   * Returns the location of the program counter in the enclosing call frame. If called from within
   * a built-in function, this is the location of the call expression that called the built-in. It
   * returns BUILTIN if called with fewer than two frames (such as within a test).
   */
  public Location getCallerLocation() {
    if (toplevel()) {
      return Location.BUILTIN;
    } else {
      ImmutableList<CallStackEntry> callerLocations = frame(1).getLocation();
      if (callerLocations.isEmpty()) {
        // Stack should be non-empty, but it's safer to return something than crash.
        return Location.BUILTIN;
      } else {
        return callerLocations.get(callerLocations.size() - 1).location;
      }
    }
  }

  /**
   * Reports whether the call stack has less than two frames. Zero frames means an idle thread. One
   * frame means the function for the top-level statements of a file is active. More than that means
   * a function call is in progress.
   *
   * <p>Every use of this function is a hack to work around the lack of proper local vs global
   * identifier resolution at top level.
   */
  boolean toplevel() {
    return callstack.size() < 2;
  }

  // Returns the stack frame at the specified depth. 0 means top of stack, 1 is its caller, etc.
  Frame frame(int depth) {
    return callstack.get(callstack.size() - 1 - depth);
  }

  /**
   * Constructs a StarlarkThread.
   *
   * @param mu the (non-frozen) mutability of values created by this thread.
   * @param semantics the StarlarkSemantics for this thread.
   */
  public StarlarkThread(Mutability mu, StarlarkSemantics semantics) {
    Preconditions.checkArgument(!mu.isFrozen());
    this.mutability = mu;
    this.semantics = semantics;
    this.allowRecursion = semantics.getBool(StarlarkSemantics.ALLOW_RECURSION);
  }

  /**
   * Specifies a hook function to be run after each assignment at top level.
   *
   * <p>This is a short-term hack to allow us to consolidate all StarlarkFile execution in one place
   * even while BzlLoadFunction implements the old "export" behavior, in which rules, aspects and
   * providers are "exported" as soon as they are assigned, not at the end of file execution.
   */
  public void setPostAssignHook(PostAssignHook postAssignHook) {
    this.postAssignHook = postAssignHook;
  }

  /** A hook for notifications of assignments at top level. */
  @FunctionalInterface
  public interface PostAssignHook {
    void assign(String name, Object value);
  }

  public StarlarkSemantics getSemantics() {
    return semantics;
  }

  /** Reports whether this thread is allowed to make recursive calls. */
  public boolean isRecursionAllowed() {
    return allowRecursion;
  }

  /** Returns the size of the callstack. This is needed for the debugger. */
  int getCallStackSize() {
    return callstack.size();
  }

  /**
   * A CallStackEntry describes the name and PC location of an active function call. See {@link
   * #getCallStack}.
   */
  @Immutable
  public static final class CallStackEntry {
    public final String name;
    public final Location location;

    public CallStackEntry(String name, Location location) {
      this.location = location;
      this.name = name;
    }

    @Override
    public String toString() {
      return name + "@" + location;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      CallStackEntry that = (CallStackEntry) o;
      return Objects.equals(name, that.name) && Objects.equals(location, that.location);
    }

    @Override
    public int hashCode() {
      return Objects.hash(name, location);
    }
  }

  /**
   * Returns information about this thread's current stack of active function calls, outermost call
   * first. For each function, it reports its name, and the location of its current program counter.
   * The result is immutable and does not reference interpreter data structures, so it may retained
   * indefinitely and safely shared with other threads.
   */
  public ImmutableList<CallStackEntry> getCallStack() {
    ImmutableList.Builder<CallStackEntry> stack = ImmutableList.builder();
    for (Frame fr : callstack) {
      stack.addAll(fr.getLocation());
    }
    return stack.build();
  }

  @Override
  public int hashCode() {
    throw new UnsupportedOperationException(); // avoid nondeterminism
  }

  @Override
  public boolean equals(Object that) {
    throw new UnsupportedOperationException();
  }

  @Override
  public String toString() {
    return String.format("<StarlarkThread%s>", mutability);
  }

  /** CallProfiler records the start and end wall times of function calls. */
  public interface CallProfiler {
    Object start(StarlarkCallable fn);

    void end(Object span);
  }

  /** Installs a global hook that will be notified of function calls. */
  public static synchronized void setCallProfiler(@Nullable CallProfiler p) {
    CallProfiler oldProfiler = callProfiler;
    callProfiler = p;
    DebugProfile.add((p != null ? 1 : 0) - (oldProfiler != null ? 1 : 0));
  }

  @Nullable private static CallProfiler callProfiler = null;
}

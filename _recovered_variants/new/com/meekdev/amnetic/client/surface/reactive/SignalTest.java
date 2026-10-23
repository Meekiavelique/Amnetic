# Surface Phase 0: Reactivity Core Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** The Signal/Computed/Effect reactive core plus the settling Motion driver — pure Java, no GL, fully unit-tested — per `docs/superpowers/specs/2026-07-12-surface-ui-v1-design.md`.

**Architecture:** Fine-grained reactivity (SolidJS-style): `Signal.get()` inside a running computation records a dependency edge; `set()` marks dependents dirty; a batched scheduler flushes once per frame (`Reactive.flush()`), re-running dirty effects. `Computed` is a cached, lazily evaluated node. `Motion` drives a value toward a target signal and detaches from the frame clock when settled.

**Tech Stack:** Java 21 source (project targets Java 21 via `options.release`, toolchain 25), JUnit 5 (new dependency), existing `client.anim` package (`EasingFunction`, `Easing`, `Interpolator`).

## Global Constraints

- Package: `com.meekdev.amnetic.client.surface.reactive` (public API), no `internal` split needed in phase 0.
- Comment style: sparse, lowercase, casual one-liners only where non-obvious (project convention). No javadoc boilerplate, no `@param`/`@return` restating names.
- No fully-qualified class names inline — always import.
- No unicode in code or comments.
- Commit style: `type(scope): lowercase summary`, no body. Scope for this work: `surface`.
- Thread model: the reactive core is single-threaded (render thread). No locks; document nothing — tests run on one thread too.
- Signal equality: `Objects.equals` — equal `set()` is a no-op.
- Cycle safety: a flush pass that exceeds 1000 iterations disables the offending effect and logs via slf4j logger `Amnetic/Surface` (spec: "detected by re-entrancy depth cap, effect disabled with a log").

---

### Task 1: JUnit infrastructure + Signal + Effect (immediate mode)

**Files:**
- Modify: `build.gradle` (test deps + platform)
- Create: `src/main/java/com/meekdev/amnetic/client/surface/reactive/Signal.java`
- Create: `src/main/java/com/meekdev/amnetic/client/surface/reactive/Effect.java`
- Create: `src/main/java/com/meekdev/amnetic/client/surface/reactive/internal/Tracking.java`
- Test: `src/test/java/com/meekdev/amnetic/client/surface/reactive/SignalTest.java`

**Interfaces:**
- Consumes: nothing.
- Produces: `new Signal<T>(T initial)`, `T get()`, `void set(T)`, `void update(UnaryOperator<T>)`; `new Effect(Runnable body)` (runs immediately, re-runs on dependency change), `void dispose()`; package-internal `Tracking.current()` / `Tracking.run(Computation)` used by Task 3.

- [ ] **Step 1: Add JUnit to build.gradle**

In the `dependencies` block add:

```groovy
    testImplementation "org.junit.jupiter:junit-jupiter:5.10.2"
    testRuntimeOnly "org.junit.platform:junit-platform-launcher"
```

After the dependencies block (top level):

```groovy
test {
    useJUnitPlatform()
}
```

- [ ] **Step 2: Write the failing tests**

`src/test/java/com/meekdev/amnetic/client/surface/reactive/SignalTest.java`:

```java
package com.meekdev.amnetic.client.surface.reactive;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SignalTest {

    @Test
    void getReturnsInitialValue() {
        Signal<Integer> s = new Signal<>(5);
        assertEquals(5, s.get());
    }

    @Test
    void effectRunsOnceOnCreation() {
        List<Integer> seen = new ArrayList<>();
        Signal<Integer> s = new Signal<>(1);
        new Effect(() -> seen.add(s.get()));
        assertEquals(List.of(1), seen);
    }

    @Test
    void effectRerunsWhenDependencyChanges() {
        List<Integer> seen = new ArrayList<>();
        Signal<Integer> s = new Signal<>(1);
        new Effect(() -> seen.add(s.get()));
        s.set(2);
        assertEquals(List.of(1, 2), seen);
    }

    @Test
    void equalSetIsNoOp() {
        List<Integer> seen = new ArrayList<>();
        Signal<Integer> s = new Signal<>(1);
        new Effect(() -> seen.add(s.get()));
        s.set(1);
        assertEquals(List.of(1), seen);
    }

    @Test
    void dynamicDependenciesSwitchBranches() {
        Signal<Boolean> flag = new Signal<>(true);
        Signal<Integer> a = new Signal<>(10);
        Signal<Integer> b = new Signal<>(20);
        List<Integer> seen = new ArrayList<>();
        new Effect(() -> seen.add(flag.get() ? a.get() : b.get()));
        flag.set(false);              // now depends on b, not a
        a.set(11);                    // must NOT rerun
        b.set(21);                    // must rerun
        assertEquals(List.of(10, 20, 21), seen);
    }

    @Test
    void disposedEffectStopsRerunning() {
        List<Integer> seen = new ArrayList<>();
        Signal<Integer> s = new Signal<>(1);
        Effect e = new Effect(() -> seen.add(s.get()));
        e.dispose();
        s.set(2);
        assertEquals(List.of(1), seen);
    }

    @Test
    void updateAppliesOperator() {
        Signal<Integer> s = new Signal<>(2);
        s.update(v -> v * 3);
        assertEquals(6, s.get());
    }
}
```

- [ ] **Step 3: Run tests to verify they fail**

Run: `./gradlew test --tests "com.meekdev.amnetic.client.surface.reactive.SignalTest" 2>&1 | tail -20`
Expected: compilation failure ("package com.meekdev.amnetic.client.surface.reactive does not exist" / cannot find Signal).

- [ ] **Step 4: Implement Tracking, Signal, Effect**

`src/main/java/com/meekdev/amnetic/client/surface/reactive/internal/Tracking.java`:

```java
package com.meekdev.amnetic.client.surface.reactive.internal;

import java.util.ArrayDeque;
import java.util.Deque;

// thread-local stack of the computation currently running, Signal.get() records edges against it
public final class Tracking {

    public interface Computation {
        void invalidate();
        void addSubscription(Runnable unsubscribe);
    }

    private static final Deque<Computation> STACK = new ArrayDeque<>();

    private Tracking() {}

    public static Computation current() {
        return STACK.peek();
    }

    public static void run(Computation c, Runnable body) {
        STACK.push(c);
        try {
            body.run();
        } finally {
            STACK.pop();
        }
    }
}
```

`src/main/java/com/meekdev/amnetic/client/surface/reactive/Signal.java`:

```java
package com.meekdev.amnetic.client.surface.reactive;

import com.meekdev.amnetic.client.surface.reactive.internal.Tracking;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.function.UnaryOperator;

public class Signal<T> {

    private T value;
    private final Set<Tracking.Computation> subscribers = new LinkedHashSet<>();

    public Signal(T initial) {
        this.value = initial;
    }

    public T get() {
        Tracking.Computation current = Tracking.current();
        if (current != null && subscribers.add(current)) {
            current.addSubscription(() -> subscribers.remove(current));
        }
        return value;
    }

    // read without creating a dependency edge
    public T peek() {
        return value;
    }

    public void set(T newValue) {
        if (Objects.equals(value, newValue)) return;
        value = newValue;
        // copy, subscribers resubscribe while rerunning
        for (Tracking.Computation c : subscribers.toArray(new Tracking.Computation[0])) {
            c.invalidate();
        }
    }

    public void update(UnaryOperator<T> op) {
        set(op.apply(peek()));
    }
}
```

`src/main/java/com/meekdev/amnetic/client/surface/reactive/Effect.java`:

```java
package com.meekdev.amnetic.client.surface.reactive;

import com.meekdev.amnetic.client.surface.reactive.internal.Tracking;
import java.util.ArrayList;
import java.util.List;

public class Effect implements Tracking.Computation {

    private final Runnable body;
    private final List<Runnable> subscriptions = new ArrayList<>();
    private boolean disposed;

    public Effect(Runnable body) {
        this.body = body;
        run();
    }

    private void run() {
        clearSubscriptions();
        Tracking.run(this, body);
    }

    private void clearSubscriptions() {
        for (Runnable unsub : subscriptions) unsub.run();
        subscriptions.clear();
    }

    @Override
    public void invalidate() {
        if (!disposed) run();
    }

    @Override
    public void addSubscription(Runnable unsubscribe) {
        subscriptions.add(unsubscribe);
    }

    public void dispose() {
        disposed = true;
        clearSubscriptions();
    }
}
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `./gradlew test --tests "com.meekdev.amnetic.client.surface.reactive.SignalTest" 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL, 7 tests passing.

- [ ] **Step 6: Commit**

```bash
git add build.gradle src/main/java/com/meekdev/amnetic/client/surface src/test
git commit -m "feat(surface): signal and effect reactive primitives"
```

---

### Task 2: Batched scheduler (Reactive facade)

**Files:**
- Create: `src/main/java/com/meekdev/amnetic/client/surface/reactive/Reactive.java`
- Modify: `src/main/java/com/meekdev/amnetic/client/surface/reactive/Effect.java` (route invalidate through the scheduler)
- Test: `src/test/java/com/meekdev/amnetic/client/surface/reactive/SchedulerTest.java`

**Interfaces:**
- Consumes: `Effect.invalidate()` from Task 1.
- Produces: `Reactive.flush()` (runs pending effects until stable), `Reactive.enqueue(Effect)` (internal use by Effect). After this task, `Signal.set()` no longer reruns effects synchronously — callers observe changes after `flush()`. `Reactive.flush()` disables any effect still dirty after 1000 iterations and logs to slf4j logger `Amnetic/Surface`.

- [ ] **Step 1: Write the failing tests**

`src/test/java/com/meekdev/amnetic/client/surface/reactive/SchedulerTest.java`:

```java
package com.meekdev.amnetic.client.surface.reactive;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SchedulerTest {

    @Test
    void setDefersUntilFlush() {
        List<Integer> seen = new ArrayList<>();
        Signal<Integer> s = new Signal<>(1);
        new Effect(() -> seen.add(s.get()));
        s.set(2);
        assertEquals(List.of(1), seen);   // not yet
        Reactive.flush();
        assertEquals(List.of(1, 2), seen);
    }

    @Test
    void multipleSetsCoalesceIntoOneRerun() {
        List<Integer> seen = new ArrayList<>();
        Signal<Integer> s = new Signal<>(0);
        new Effect(() -> seen.add(s.get()));
        s.set(1);
        s.set(2);
        s.set(3);
        Reactive.flush();
        assertEquals(List.of(0, 3), seen);
    }

    @Test
    void setsAcrossMultipleSignalsCoalesce() {
        Signal<Integer> a = new Signal<>(1);
        Signal<Integer> b = new Signal<>(2);
        List<Integer> sums = new ArrayList<>();
        new Effect(() -> sums.add(a.get() + b.get()));
        a.set(10);
        b.set(20);
        Reactive.flush();
        assertEquals(List.of(3, 30), sums);   // one rerun, not two
    }

    @Test
    void cascadedInvalidationSettlesInOneFlush() {
        Signal<Integer> a = new Signal<>(1);
        Signal<Integer> mirror = new Signal<>(0);
        List<Integer> seen = new ArrayList<>();
        new Effect(() -> mirror.set(a.get() * 2));   // effect writing a signal
        new Effect(() -> seen.add(mirror.get()));
        a.set(5);
        Reactive.flush();
        assertEquals(10, mirror.peek());
        assertTrue(seen.contains(10));
    }

    @Test
    void infiniteCycleDisablesEffectInsteadOfHanging() {
        Signal<Integer> s = new Signal<>(0);
        new Effect(() -> s.set(s.get() + 1));   // self-perpetuating
        s.set(100);
        Reactive.flush();                        // must return
        int settled = s.peek();
        s.set(settled + 1);
        Reactive.flush();
        assertEquals(settled + 1, s.peek());     // disabled effect no longer increments
    }

    @Test
    void flushWithNothingPendingIsCheapNoop() {
        Reactive.flush();   // must not throw
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew test --tests "com.meekdev.amnetic.client.surface.reactive.SchedulerTest" 2>&1 | tail -10`
Expected: compile failure (Reactive not found).

- [ ] **Step 3: Implement Reactive, modify Effect**

`src/main/java/com/meekdev/amnetic/client/surface/reactive/Reactive.java`:

```java
package com.meekdev.amnetic.client.surface.reactive;

import java.util.LinkedHashSet;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class Reactive {

    private static final Logger LOG = LoggerFactory.getLogger("Amnetic/Surface");
    private static final int MAX_FLUSH_ITERATIONS = 1000;

    private static final Set<Effect> PENDING = new LinkedHashSet<>();

    private Reactive() {}

    static void enqueue(Effect effect) {
        PENDING.add(effect);
    }

    public static void flush() {
        int iterations = 0;
        while (!PENDING.isEmpty()) {
            if (++iterations > MAX_FLUSH_ITERATIONS) {
                // a cycle, kill whatever is still dirty so the frame survives
                for (Effect e : PENDING.toArray(new Effect[0])) {
                    LOG.warn("reactive cycle detected, disabling effect {}", e);
                    e.dispose();
                }
                PENDING.clear();
                return;
            }
            Effect next = PENDING.iterator().next();
            PENDING.remove(next);
            next.runFromScheduler();
        }
    }
}
```

Modify `Effect.java` — replace `invalidate()` and expose the scheduler entry:

```java
    @Override
    public void invalidate() {
        if (!disposed) Reactive.enqueue(this);
    }

    // called by Reactive.flush, never directly
    void runFromScheduler() {
        if (!disposed) run();
    }
```

(The initial run in the constructor stays synchronous — creation still runs the body once immediately.)

- [ ] **Step 4: Run tests, including Task 1's, to verify all pass**

Task 1's `effectRerunsWhenDependencyChanges` and `dynamicDependenciesSwitchBranches` now need a `Reactive.flush()` after each `set(...)` — update `SignalTest` accordingly (add `Reactive.flush();` after every `set` before the assertion; in `dynamicDependenciesSwitchBranches` flush after each of the three sets; in `disposedEffectStopsRerunning` flush after `s.set(2)`).

Run: `./gradlew test 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL, 13 tests passing.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/meekdev/amnetic/client/surface src/test
git commit -m "feat(surface): batched reactive scheduler with cycle guard"
```

---

### Task 3: Computed

**Files:**
- Create: `src/main/java/com/meekdev/amnetic/client/surface/reactive/Computed.java`
- Test: `src/test/java/com/meekdev/amnetic/client/surface/reactive/ComputedTest.java`

**Interfaces:**
- Consumes: `Tracking` (Task 1), `Signal` semantics.
- Produces: `new Computed<T>(Supplier<T> fn)`, `T get()` (tracked like a signal, lazily recomputed, cached), `T peek()`. A `Computed` read inside an `Effect` subscribes the effect; changing an upstream signal invalidates the computed and (after flush) reruns the effect.

- [ ] **Step 1: Write the failing tests**

`src/test/java/com/meekdev/amnetic/client/surface/reactive/ComputedTest.java`:

```java
package com.meekdev.amnetic.client.surface.reactive;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ComputedTest {

    @Test
    void computesLazilyAndCaches() {
        AtomicInteger runs = new AtomicInteger();
        Signal<Integer> s = new Signal<>(2);
        Computed<Integer> doubled = new Computed<>(() -> {
            runs.incrementAndGet();
            return s.get() * 2;
        });
        assertEquals(0, runs.get());        // lazy
        assertEquals(4, doubled.get());
        assertEquals(4, doubled.get());
        assertEquals(1, runs.get());        // cached
    }

    @Test
    void recomputesAfterUpstreamChange() {
        Signal<Integer> s = new Signal<>(2);
        Computed<Integer> doubled = new Computed<>(() -> s.get() * 2);
        assertEquals(4, doubled.get());
        s.set(5);
        assertEquals(10, doubled.get());
    }

    @Test
    void effectSubscribesThroughComputed() {
        Signal<Integer> s = new Signal<>(1);
        Computed<Integer> plusOne = new Computed<>(() -> s.get() + 1);
        List<Integer> seen = new ArrayList<>();
        new Effect(() -> seen.add(plusOne.get()));
        s.set(9);
        Reactive.flush();
        assertEquals(List.of(2, 10), seen);
    }

    @Test
    void diamondDependencyRunsEffectOnce() {
        Signal<Integer> s = new Signal<>(1);
        Computed<Integer> left = new Computed<>(() -> s.get() + 1);
        Computed<Integer> right = new Computed<>(() -> s.get() * 10);
        AtomicInteger effectRuns = new AtomicInteger();
        new Effect(() -> {
            left.get();
            right.get();
            effectRuns.incrementAndGet();
        });
        s.set(2);
        Reactive.flush();
        assertEquals(2, effectRuns.get());   // initial + one rerun, not two
    }

    @Test
    void unchangedComputedResultDoesNotRerunEffect() {
        Signal<Integer> s = new Signal<>(4);
        Computed<Boolean> even = new Computed<>(() -> s.get() % 2 == 0);
        AtomicInteger effectRuns = new AtomicInteger();
        new Effect(() -> {
            even.get();
            effectRuns.incrementAndGet();
        });
        s.set(6);   // still even, computed value identical
        Reactive.flush();
        assertEquals(1, effectRuns.get());
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew test --tests "com.meekdev.amnetic.client.surface.reactive.ComputedTest" 2>&1 | tail -10`
Expected: compile failure (Computed not found).

- [ ] **Step 3: Implement Computed**

`src/main/java/com/meekdev/amnetic/client/surface/reactive/Computed.java`:

```java
package com.meekdev.amnetic.client.surface.reactive;

import com.meekdev.amnetic.client.surface.reactive.internal.Tracking;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

// cached derived value, recomputes lazily on read after an upstream change
public class Computed<T> implements Tracking.Computation {

    private final Supplier<T> fn;
    private final Signal<T> out = new Signal<>(null);
    private final List<Runnable> subscriptions = new ArrayList<>();
    private boolean stale = true;

    public Computed(Supplier<T> fn) {
        this.fn = fn;
    }

    public T get() {
        if (stale) recompute();
        return out.get();
    }

    public T peek() {
        if (stale) recompute();
        return out.peek();
    }

    private void recompute() {
        for (Runnable unsub : subscriptions) unsub.run();
        subscriptions.clear();
        stale = false;
        Tracking.run(this, () -> out.set(fn.get()));
    }

    @Override
    public void invalidate() {
        stale = true;
        // eagerly recompute so out.set() propagates (and equality-filters) downstream
        recompute();
    }

    @Override
    public void addSubscription(Runnable unsubscribe) {
        subscriptions.add(unsubscribe);
    }
}
```

Note: `out.set` inside `Tracking.run` must not record `out` as this computed's own dependency — `Signal.set` does no tracking (only `get` does), so this is safe as written.

- [ ] **Step 4: Run all tests to verify they pass**

Run: `./gradlew test 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL, 18 tests passing.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/meekdev/amnetic/client/surface src/test
git commit -m "feat(surface): computed derived values"
```

---

### Task 4: Frame clock (Reactive.tick)

**Files:**
- Modify: `src/main/java/com/meekdev/amnetic/client/surface/reactive/Reactive.java`
- Test: `src/test/java/com/meekdev/amnetic/client/surface/reactive/ClockTest.java`

**Interfaces:**
- Consumes: Task 2 scheduler.
- Produces: `Reactive.tick(float dt)` — advances the clock then flushes; `Signal<Float> Reactive.clock()` — total seconds, starts at 0; `float Reactive.dt()` — last tick's delta (plain accessor, not a signal). Motion (Tasks 5-6) attaches to `clock()` while in flight. Later phases call `tick` once per frame from the client; tests call it manually.

- [ ] **Step 1: Write the failing tests**

`src/test/java/com/meekdev/amnetic/client/surface/reactive/ClockTest.java`:

```java
package com.meekdev.amnetic.client.surface.reactive;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ClockTest {

    @Test
    void tickAdvancesClockAndFlushes() {
        float start = Reactive.clock().peek();
        List<Float> seen = new ArrayList<>();
        Effect e = new Effect(() -> seen.add(Reactive.clock().get()));
        Reactive.tick(0.05f);
        Reactive.tick(0.05f);
        assertEquals(3, seen.size());   // initial + two ticks
        assertEquals(start + 0.1f, Reactive.clock().peek(), 1e-6);
        assertEquals(0.05f, Reactive.dt(), 1e-6);
        e.dispose();
    }

    @Test
    void tickClampsHugeDeltas() {
        float start = Reactive.clock().peek();
        Reactive.tick(5f);   // tab-out frame, clamp to 0.1 like the anim engine
        assertEquals(start + 0.1f, Reactive.clock().peek(), 1e-6);
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew test --tests "com.meekdev.amnetic.client.surface.reactive.ClockTest" 2>&1 | tail -10`
Expected: compile failure (`clock()` not found).

- [ ] **Step 3: Implement in Reactive**

Add to `Reactive.java`:

```java
    private static final Signal<Float> CLOCK = new Signal<>(0f);
    private static float lastDt;

    public static Signal<Float> clock() {
        return CLOCK;
    }

    public static float dt() {
        return lastDt;
    }

    // one call per frame from the client, tests drive it manually
    public static void tick(float dt) {
        lastDt = Math.min(dt, 0.1f);
        CLOCK.set(CLOCK.peek() + lastDt);
        flush();
    }
```

- [ ] **Step 4: Run all tests to verify they pass**

Run: `./gradlew test 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL, 20 tests passing.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/meekdev/amnetic/client/surface src/test
git commit -m "feat(surface): reactive frame clock"
```

---

### Task 5: Motion, tween mode

**Files:**
- Create: `src/main/java/com/meekdev/amnetic/client/surface/reactive/Motion.java`
- Test: `src/test/java/com/meekdev/amnetic/client/surface/reactive/MotionTweenTest.java`

**Interfaces:**
- Consumes: `Reactive.clock()`/`tick` (Task 4), `Effect`, `Signal`; `com.meekdev.amnetic.client.anim.EasingFunction` (`float apply(float t)`), `com.meekdev.amnetic.client.anim.Easing` enum constants, `com.meekdev.amnetic.client.anim.Interpolator<T>` (`T interpolate(T a, T b, float t)`).
- Produces: `Motion.tween(float initial, float duration, EasingFunction easing)` returning `Motion<Float>`; generic `Motion.tween(T initial, float duration, EasingFunction easing, Interpolator<T> lerp)`; `void target(T)` and `void follow(Signal<T>)`; `Signal<T> value()`; `boolean inFlight()`; `void dispose()`. While settled the motion holds **zero** clock subscriptions (assertable via `inFlight()` plus behavior: clock ticks cause no value writes). Spring mode is Task 6 on the same class.

- [ ] **Step 1: Write the failing tests**

`src/test/java/com/meekdev/amnetic/client/surface/reactive/MotionTweenTest.java`:

```java
package com.meekdev.amnetic.client.surface.reactive;

import com.meekdev.amnetic.client.anim.Easing;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MotionTweenTest {

    @Test
    void restsUntilTargetChanges() {
        Motion<Float> m = Motion.tween(0f, 1f, Easing.LINEAR);
        assertFalse(m.inFlight());
        Reactive.tick(0.5f);
        assertEquals(0f, m.value().peek(), 1e-6);
        m.dispose();
    }

    @Test
    void tweensLinearlyTowardTarget() {
        Motion<Float> m = Motion.tween(0f, 1f, Easing.LINEAR);
        m.target(10f);
        assertTrue(m.inFlight());
        Reactive.tick(0.05f);   // dt caps at 0.1, use small steps
        Reactive.tick(0.05f);
        Reactive.tick(0.05f);
        Reactive.tick(0.05f);
        Reactive.tick(0.05f);
        assertEquals(2.5f, m.value().peek(), 0.01f);   // 0.25s of a 1s tween
        m.dispose();
    }

    @Test
    void snapsAndSettlesAtDurationEnd() {
        Motion<Float> m = Motion.tween(0f, 0.2f, Easing.LINEAR);
        m.target(4f);
        for (int i = 0; i < 6; i++) Reactive.tick(0.05f);
        assertEquals(4f, m.value().peek(), 1e-6);   // exact snap, not epsilon-close
        assertFalse(m.inFlight());
        m.dispose();
    }

    @Test
    void retargetMidFlightRestartsFromCurrentValue() {
        Motion<Float> m = Motion.tween(0f, 1f, Easing.LINEAR);
        m.target(10f);
        for (int i = 0; i < 10; i++) Reactive.tick(0.05f);   // 0.5s -> value 5
        m.target(0f);                                        // reverse from ~5
        Reactive.tick(0.05f);
        float v = m.value().peek();
        assertTrue(v < 5f && v > 0f);
        m.dispose();
    }

    @Test
    void followTracksASignal() {
        Signal<Float> goal = new Signal<>(0f);
        Motion<Float> m = Motion.tween(0f, 0.2f, Easing.LINEAR);
        m.follow(goal);
        goal.set(2f);
        Reactive.flush();       // follow effect picks up the change
        assertTrue(m.inFlight());
        for (int i = 0; i < 6; i++) Reactive.tick(0.05f);
        assertEquals(2f, m.value().peek(), 1e-6);
        m.dispose();
    }

    @Test
    void settledMotionDoesNotWriteOnTicks() {
        Motion<Float> m = Motion.tween(1f, 0.1f, Easing.LINEAR);
        m.target(2f);
        for (int i = 0; i < 5; i++) Reactive.tick(0.05f);
        assertFalse(m.inFlight());
        int[] writes = {0};
        Effect probe = new Effect(() -> { m.value().get(); writes[0]++; });
        Reactive.tick(0.05f);
        Reactive.tick(0.05f);
        assertEquals(1, writes[0]);   // only the probe's initial run
        probe.dispose();
        m.dispose();
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew test --tests "com.meekdev.amnetic.client.surface.reactive.MotionTweenTest" 2>&1 | tail -10`
Expected: compile failure (Motion not found).

- [ ] **Step 3: Implement Motion (tween mode only)**

`src/main/java/com/meekdev/amnetic/client/surface/reactive/Motion.java`:

```java
package com.meekdev.amnetic.client.surface.reactive;

import com.meekdev.amnetic.client.anim.EasingFunction;
import com.meekdev.amnetic.client.anim.Interpolator;

// drives a value toward a target, attached to the frame clock only while moving
public class Motion<T> {

    private final Signal<T> value;
    private final float duration;
    private final EasingFunction easing;
    private final Interpolator<T> lerp;

    private T from;
    private T target;
    private float elapsed;
    private Effect clockDriver;   // non-null only while in flight
    private Effect followEffect;

    private Motion(T initial, float duration, EasingFunction easing, Interpolator<T> lerp) {
        this.value = new Signal<>(initial);
        this.duration = Math.max(duration, 1e-4f);
        this.easing = easing;
        this.lerp = lerp;
        this.target = initial;
    }

    public static Motion<Float> tween(float initial, float duration, EasingFunction easing) {
        return new Motion<>(initial, duration, easing, (a, b, t) -> a + (b - a) * t);
    }

    public static <T> Motion<T> tween(T initial, float duration, EasingFunction easing, Interpolator<T> lerp) {
        return new Motion<>(initial, duration, easing, lerp);
    }

    public Signal<T> value() {
        return value;
    }

    public boolean inFlight() {
        return clockDriver != null;
    }

    public void target(T newTarget) {
        if (newTarget.equals(target) && !inFlight()) return;
        from = value.peek();
        target = newTarget;
        elapsed = 0f;
        attach();
    }

    public void follow(Signal<T> goal) {
        if (followEffect != null) followEffect.dispose();
        followEffect = new Effect(() -> target(goal.get()));
    }

    private void attach() {
        if (clockDriver != null) return;
        // skip the effect's synchronous initial run, only real ticks advance time
        boolean[] primed = {false};
        clockDriver = new Effect(() -> {
            Reactive.clock().get();
            if (!primed[0]) { primed[0] = true; return; }
            step(Reactive.dt());
        });
    }

    private void detach() {
        if (clockDriver != null) {
            clockDriver.dispose();
            clockDriver = null;
        }
    }

    void step(float dt) {
        elapsed += dt;
        float t = Math.min(elapsed / duration, 1f);
        value.set(lerp.interpolate(from, target, easing.apply(t)));
        if (t >= 1f) {
            value.set(target);   // exact snap
            detach();
        }
    }

    public void dispose() {
        detach();
        if (followEffect != null) followEffect.dispose();
    }
}
```

- [ ] **Step 4: Run all tests to verify they pass**

Run: `./gradlew test 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL, 26 tests passing.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/meekdev/amnetic/client/surface src/test
git commit -m "feat(surface): settling tween motion driver"
```

---

### Task 6: Motion, spring mode

**Files:**
- Modify: `src/main/java/com/meekdev/amnetic/client/surface/reactive/Motion.java`
- Test: `src/test/java/com/meekdev/amnetic/client/surface/reactive/MotionSpringTest.java`

**Interfaces:**
- Consumes: Task 5 Motion internals.
- Produces: `Motion.spring(float initial, float stiffness, float damping)` returning `Motion<Float>`. Spring is Float-only (per spec). Same `target/follow/value/inFlight/dispose` surface. Settle: `|value - target| < 0.0001` and `|velocity| < 0.0001` snaps to target and detaches.

- [ ] **Step 1: Write the failing tests**

`src/test/java/com/meekdev/amnetic/client/surface/reactive/MotionSpringTest.java`:

```java
package com.meekdev.amnetic.client.surface.reactive;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MotionSpringTest {

    @Test
    void springMovesTowardTargetAndSettles() {
        Motion<Float> m = Motion.spring(0f, 170f, 26f);
        m.target(1f);
        assertTrue(m.inFlight());
        for (int i = 0; i < 200 && m.inFlight(); i++) Reactive.tick(0.016f);
        assertEquals(1f, m.value().peek(), 1e-6);   // exact snap on settle
        assertFalse(m.inFlight());
        m.dispose();
    }

    @Test
    void underdampedSpringOvershoots() {
        Motion<Float> m = Motion.spring(0f, 400f, 8f);
        m.target(1f);
        float max = 0f;
        for (int i = 0; i < 300 && m.inFlight(); i++) {
            Reactive.tick(0.016f);
            max = Math.max(max, m.value().peek());
        }
        assertTrue(max > 1.01f, "expected overshoot, max=" + max);
        assertEquals(1f, m.value().peek(), 1e-6);
        m.dispose();
    }

    @Test
    void retargetMidFlightKeepsVelocity() {
        Motion<Float> m = Motion.spring(0f, 170f, 26f);
        m.target(1f);
        for (int i = 0; i < 5; i++) Reactive.tick(0.016f);
        float before = m.value().peek();
        m.target(-1f);   // reverse, should not teleport
        Reactive.tick(0.016f);
        assertTrue(Math.abs(m.value().peek() - before) < 0.2f);
        m.dispose();
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew test --tests "com.meekdev.amnetic.client.surface.reactive.MotionSpringTest" 2>&1 | tail -10`
Expected: compile failure (`spring` not found).

- [ ] **Step 3: Implement spring mode**

Modify `Motion.java`. Add fields and factory:

```java
    private final boolean springMode;
    private final float stiffness;
    private final float damping;
    private float velocity;
    private static final float SETTLE_EPS = 1e-4f;
```

Constructor gains the spring params (tween factories pass `false, 0, 0`); adjust the existing private constructor:

```java
    private Motion(T initial, float duration, EasingFunction easing, Interpolator<T> lerp,
                   boolean springMode, float stiffness, float damping) {
        this.value = new Signal<>(initial);
        this.duration = Math.max(duration, 1e-4f);
        this.easing = easing;
        this.lerp = lerp;
        this.target = initial;
        this.springMode = springMode;
        this.stiffness = stiffness;
        this.damping = damping;
    }

    public static Motion<Float> spring(float initial, float stiffness, float damping) {
        return new Motion<>(initial, 0f, null, null, true, stiffness, damping);
    }
```

(Update the two tween factories to pass `false, 0f, 0f`.)

`target(...)` in spring mode must not reset progress the tween way; split inside `target`:

```java
    public void target(T newTarget) {
        if (newTarget.equals(target) && !inFlight()) return;
        target = newTarget;
        if (!springMode) {
            from = value.peek();
            elapsed = 0f;
        }
        attach();
    }
```

`step(...)` branches; spring is semi-implicit Euler on floats:

```java
    void step(float dt) {
        if (springMode) {
            float current = (Float) value.peek();
            float goal = (Float) target;
            float accel = stiffness * (goal - current) - damping * velocity;
            velocity += accel * dt;
            float next = current + velocity * dt;
            if (Math.abs(next - goal) < SETTLE_EPS && Math.abs(velocity) < SETTLE_EPS) {
                velocity = 0f;
                @SuppressWarnings("unchecked") T snapped = (T) Float.valueOf(goal);
                value.set(snapped);
                detach();
            } else {
                @SuppressWarnings("unchecked") T v = (T) Float.valueOf(next);
                value.set(v);
            }
            return;
        }
        elapsed += dt;
        float t = Math.min(elapsed / duration, 1f);
        value.set(lerp.interpolate(from, target, easing.apply(t)));
        if (t >= 1f) {
            value.set(target);
            detach();
        }
    }
```

Note: the settle epsilon (1e-4) with stiffness 170/damping 26 settles in well under 200 frames at 16 ms; the test loop bound is generous.

- [ ] **Step 4: Run all tests to verify they pass**

Run: `./gradlew test 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL, 29 tests passing.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/meekdev/amnetic/client/surface src/test
git commit -m "feat(surface): settling spring motion"
```

---

### Task 7: Client wiring + compile gate

**Files:**
- Modify: `src/main/java/com/meekdev/amnetic/client/AmneticClient.java`
- Test: none new (behavioral coverage exists; this is glue)

**Interfaces:**
- Consumes: `Reactive.tick(float dt)` (Task 4).
- Produces: the reactive clock advances once per client frame. Later phases hang everything off this.

- [ ] **Step 1: Wire tick into the client**

`AmneticClient.java` pumps the anim engine each frame: `Animations.update()` at line ~191, registered on `END_MAIN`. `Animations.update()` takes no arguments and derives dt internally from `System.nanoTime()` (see `Animations.java:49-55`). `Reactive.tick(float)` needs its own dt the same way. Directly below the `Animations.update();` line add:

```java
            Reactive.tick(surfaceDt());
```

and add to `AmneticClient` (private, next to the other small helpers):

```java
    private static long surfaceLastNano;

    private static float surfaceDt() {
        long now = System.nanoTime();
        float dt = surfaceLastNano == 0L ? 0f : (now - surfaceLastNano) / 1.0e9f;
        surfaceLastNano = now;
        return dt;
    }
```

Import `com.meekdev.amnetic.client.surface.reactive.Reactive` (no inline FQDN, project rule). `Reactive.tick` already clamps to 0.1s internally.

- [ ] **Step 2: Full build + tests**

Run: `./gradlew compileJava test 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL, 29 tests passing.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/meekdev/amnetic/client/AmneticClient.java
git commit -m "feat(surface): pump the reactive clock from the client frame"
```

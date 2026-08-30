package com.meekdev.amnetic.client.surface.reactive;

import java.util.LinkedHashSet;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class Reactive {

    private static final Logger LOG = LoggerFactory.getLogger("Amnetic/Surface");
    private static final int MAX_FLUSH_ITERATIONS = 1000;

    private static final Set<Effect> PENDING = new LinkedHashSet<>();
    private static final Signal<Float> CLOCK = new Signal<>(0f);
    private static float lastDt;

    private Reactive() {}

    static void enqueue(Effect effect) {
        PENDING.add(effect);
    }

    public static Signal<Float> clock() {
        return CLOCK;
    }

    public static float dt() {
        return lastDt;
    }

    public static void tick(float dt) {
        lastDt = Math.min(dt, 0.1f);
        CLOCK.set(CLOCK.peek() + lastDt);
        flush();
    }

    public static void flush() {
        int iterations = 0;
        while (!PENDING.isEmpty()) {
            if (++iterations > MAX_FLUSH_ITERATIONS) {
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

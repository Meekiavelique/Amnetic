package com.meekdev.amnetic.client.surface.reactive.internal;

import java.util.ArrayDeque;
import java.util.Deque;

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

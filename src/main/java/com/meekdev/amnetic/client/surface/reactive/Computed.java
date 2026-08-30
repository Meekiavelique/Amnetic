package com.meekdev.amnetic.client.surface.reactive;

import com.meekdev.amnetic.client.surface.reactive.internal.Tracking;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

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
        recompute();
    }

    @Override
    public void addSubscription(Runnable unsubscribe) {
        subscriptions.add(unsubscribe);
    }
}

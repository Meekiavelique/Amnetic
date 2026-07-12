package com.meekdev.amnetic.client.surface.reactive;

import com.meekdev.amnetic.client.surface.reactive.internal.Tracking;
import java.util.ArrayList;
import java.util.List;

// side effect that reruns (batched, on the next flush) when any signal it read changes
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
        if (!disposed) Reactive.enqueue(this);
    }

    @Override
    public void addSubscription(Runnable unsubscribe) {
        subscriptions.add(unsubscribe);
    }

    void runFromScheduler() {
        if (!disposed) run();
    }

    public void dispose() {
        disposed = true;
        clearSubscriptions();
    }
}

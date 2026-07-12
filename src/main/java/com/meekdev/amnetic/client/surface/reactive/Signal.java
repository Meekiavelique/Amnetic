package com.meekdev.amnetic.client.surface.reactive;

import com.meekdev.amnetic.client.surface.reactive.internal.Tracking;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.function.UnaryOperator;

// mutable reactive state, get() inside an Effect/Computed subscribes it automatically
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

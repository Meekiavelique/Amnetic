package com.meekdev.amnetic.client.anim.internal;

import java.util.ArrayList;
import java.util.List;

public final class TweenRegistry {

    private final List<Updatable> active = new ArrayList<>();
    private final List<Updatable> pending = new ArrayList<>();

    public void add(Updatable u) {
        if (u != null) pending.add(u);
    }

    public void update(float dt) {
        if (!pending.isEmpty()) {
            active.addAll(pending);
            pending.clear();
        }
        for (int i = active.size() - 1; i >= 0; i--) {
            if (active.get(i).update(dt)) {
                active.remove(i);
            }
        }
    }

    public int activeCount() {
        return active.size() + pending.size();
    }

    public void clear() {
        active.clear();
        pending.clear();
    }
}

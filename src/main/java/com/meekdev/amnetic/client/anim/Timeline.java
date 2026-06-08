package com.meekdev.amnetic.client.anim;

import com.meekdev.amnetic.client.anim.internal.Updatable;
import java.util.ArrayList;
import java.util.List;

public final class Timeline implements Updatable {

    private static final class Entry {
        final float offset;
        final Updatable child;
        boolean activated;
        boolean finished;

        Entry(float offset, Updatable child) {
            this.offset = offset;
            this.child = child;
        }
    }

    private final List<Entry> entries = new ArrayList<>();
    private float cursor;       // where the next append() lands
    private float lastOffset;   // where the previous append() landed (for with())
    private float total;
    private float speed = 1f;
    private boolean loop;

    private float time;
    private boolean paused;
    private boolean cancelled;
    private boolean done;
    private Runnable onComplete;

    public Timeline append(Updatable item) {
        return add(cursor, item, true);
    }

    public Timeline append(float gap, Updatable item) {
        cursor += Math.max(0f, gap);
        return add(cursor, item, true);
    }

    public Timeline with(Updatable item) {
        return add(lastOffset, item, false);
    }

    public Timeline stagger(float step, Iterable<? extends Updatable> items) {
        float at = cursor;
        for (Updatable item : items) {
            add(at, item, false);
            at += Math.max(0f, step);
        }
        cursor = Math.max(cursor, at);
        return this;
    }

    public Timeline call(Runnable callback) {
        return add(cursor, new Callback(callback), false);
    }

    public Timeline add(float offset, Updatable item) {
        return add(offset, item, false);
    }

    public Timeline loop(boolean loop) {
        this.loop = loop;
        return this;
    }

    public Timeline speed(float multiplier) {
        this.speed = Math.max(0f, multiplier);
        return this;
    }

    public Timeline onComplete(Runnable cb) {
        this.onComplete = cb;
        return this;
    }

    private Timeline add(float offset, Updatable item, boolean advanceCursor) {
        if (item == null) return this;
        float at = Math.max(0f, offset);
        entries.add(new Entry(at, item));
        lastOffset = at;
        float endOfItem = at + item.duration();
        total = Math.max(total, endOfItem);
        if (advanceCursor) cursor = endOfItem;
        return this;
    }

    public Timeline start() {
        Animations.registry().add(this);
        return this;
    }

    public void pause() { this.paused = true; }

    public void resume() { this.paused = false; }

    public void cancel() { this.cancelled = true; }

    public void seek(float seconds) {
        rewind();
        float target = Math.max(0f, seconds);
        // replay from zero in one step so children land at the right local time
        time = 0f;
        update(target);
    }

    public boolean isDone() { return done || cancelled; }

    @Override
    public boolean update(float dt) {
        if (done || cancelled) return true;
        if (paused) return false;

        time += dt * speed;

        boolean allFinished = true;
        for (Entry e : entries) {
            if (e.finished) continue;
            if (time + 1.0e-6f < e.offset) {
                allFinished = false;
                continue;
            }
            float step = e.activated ? dt * speed : (time - e.offset);
            e.activated = true;
            if (e.child.update(step)) {
                e.finished = true;
            } else {
                allFinished = false;
            }
        }

        if (allFinished && time >= total) {
            if (loop) {
                rewind();
                return false;
            }
            done = true;
            if (onComplete != null) onComplete.run();
            return true;
        }
        return false;
    }

    @Override
    public float duration() {
        return total;
    }

    private void rewind() {
        time = 0f;
        for (Entry e : entries) {
            e.activated = false;
            e.finished = false;
        }
    }

    private static final class Callback implements Updatable {
        private final Runnable runnable;

        Callback(Runnable runnable) {
            this.runnable = runnable;
        }

        @Override
        public boolean update(float dt) {
            if (runnable != null) runnable.run();
            return true;
        }

        @Override
        public float duration() {
            return 0f;
        }
    }
}

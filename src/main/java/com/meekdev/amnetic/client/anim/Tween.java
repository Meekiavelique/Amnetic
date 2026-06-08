package com.meekdev.amnetic.client.anim;

import com.meekdev.amnetic.client.anim.internal.Updatable;
import java.util.function.Consumer;

public final class Tween<T> implements Updatable {

    private final T start;
    private final T end;
    private final float duration;
    private final Interpolator<T> interpolator;

    private EasingFunction easing = Easing.LINEAR;
    private float delay;
    private int repeat;          // additional cycles after the first; -1 == infinite
    private boolean yoyo;
    private float speed = 1f;

    private Runnable onStart;
    private Consumer<T> onUpdate;
    private Runnable onComplete;

    // runtime state
    private float delayRemaining;
    private float elapsed;
    private int cycle;
    private boolean reversed;
    private boolean begun;
    private boolean paused;
    private boolean cancelled;
    private boolean done;
    private T value;

    public Tween(T start, T end, float duration, Interpolator<T> interpolator) {
        if (interpolator == null) throw new IllegalArgumentException("interpolator must not be null");
        this.start = start;
        this.end = end;
        this.duration = Math.max(0f, duration);
        this.interpolator = interpolator;
        this.value = interpolator.interpolate(start, end, 0f);
    }

    public Tween<T> ease(EasingFunction easing) {
        this.easing = easing != null ? easing : Easing.LINEAR;
        return this;
    }

    public Tween<T> delay(float seconds) {
        this.delay = Math.max(0f, seconds);
        this.delayRemaining = this.delay;
        return this;
    }

    public Tween<T> repeat(int count) {
        this.repeat = Math.max(0, count);
        return this;
    }

    public Tween<T> repeatForever() {
        this.repeat = -1;
        return this;
    }

    public Tween<T> yoyo(boolean yoyo) {
        this.yoyo = yoyo;
        return this;
    }

    public Tween<T> speed(float multiplier) {
        this.speed = Math.max(0f, multiplier);
        return this;
    }

    public Tween<T> onStart(Runnable cb) {
        this.onStart = cb;
        return this;
    }

    public Tween<T> onUpdate(Consumer<T> cb) {
        this.onUpdate = cb;
        return this;
    }

    public Tween<T> onComplete(Runnable cb) {
        this.onComplete = cb;
        return this;
    }

    public Tween<T> start() {
        Animations.registry().add(this);
        return this;
    }

    public void pause() { this.paused = true; }

    public void resume() { this.paused = false; }

    public void cancel() { this.cancelled = true; }

    public void seek(float seconds) {
        this.elapsed = Math.max(0f, seconds);
        this.delayRemaining = 0f;
        recompute();
    }

    public boolean isDone() { return done || cancelled; }

    public T value() { return value; }

    public float progress() {
        return duration <= 0f ? 1f : Interpolators.clamp01(elapsed / duration);
    }

    @Override
    public boolean update(float dt) {
        if (done || cancelled) return true;
        if (paused) return false;

        dt *= speed;

        if (!begun) {
            begun = true;
            if (onStart != null) onStart.run();
        }

        if (delayRemaining > 0f) {
            delayRemaining -= dt;
            if (delayRemaining > 0f) return false;
            dt = -delayRemaining; // carry the remainder into the first real step
            delayRemaining = 0f;
        }

        elapsed += dt;
        recompute();

        if (elapsed >= duration) {
            if (repeat == -1 || cycle < repeat) {
                cycle++;
                elapsed = 0f;
                if (yoyo) reversed = !reversed;
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
        return delay + duration;
    }

    private void recompute() {
        float t = progress();
        float p = reversed ? 1f - t : t;
        value = interpolator.interpolate(start, end, easing.apply(p));
        if (onUpdate != null) onUpdate.accept(value);
    }
}

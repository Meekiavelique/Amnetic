package com.meekdev.amnetic.client.surface.reactive;

import com.meekdev.amnetic.client.anim.EasingFunction;
import com.meekdev.amnetic.client.anim.Interpolator;

// drives a value toward a target, attached to the frame clock only while moving
public class Motion<T> {

    private static final float SETTLE_EPS = 1e-4f;

    private final Signal<T> value;
    private final float duration;
    private final EasingFunction easing;
    private final Interpolator<T> lerp;
    private final boolean springMode;
    private final float stiffness;
    private final float damping;

    private T from;
    private T target;
    private float elapsed;
    private float velocity;
    private Effect clockDriver; // non-null only while in flight
    private Effect followEffect;

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

    public static Motion<Float> tween(float initial, float duration, EasingFunction easing) {
        return new Motion<>(initial, duration, easing, (a, b, t) -> a + (b - a) * t, false, 0f, 0f);
    }

    public static <T> Motion<T> tween(T initial, float duration, EasingFunction easing, Interpolator<T> lerp) {
        return new Motion<>(initial, duration, easing, lerp, false, 0f, 0f);
    }

    public static Motion<Float> spring(float initial, float stiffness, float damping) {
        return new Motion<>(initial, 0f, null, null, true, stiffness, damping);
    }

    public Signal<T> value() {
        return value;
    }

    public boolean inFlight() {
        return clockDriver != null;
    }

    public void target(T newTarget) {
        if (newTarget.equals(target) && !inFlight()) return;
        target = newTarget;
        if (!springMode) {
            from = value.peek();
            elapsed = 0f;
        }
        attach();
    }

    public void follow(Signal<T> goal) {
        if (followEffect != null) followEffect.dispose();
        followEffect = new Effect(() -> target(goal.get()));
    }

    private void attach() {
        if (clockDriver != null) return;
        // skip the effect's synchronous first run, only real ticks advance time
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

    @SuppressWarnings("unchecked")
    private void step(float dt) {
        if (springMode) {
            float current = (Float) value.peek();
            float goal = (Float) target;
            float accel = stiffness * (goal - current) - damping * velocity;
            velocity += accel * dt;
            float next = current + velocity * dt;
            if (Math.abs(next - goal) < SETTLE_EPS && Math.abs(velocity) < SETTLE_EPS) {
                velocity = 0f;
                value.set((T) Float.valueOf(goal));
                detach();
            } else {
                value.set((T) Float.valueOf(next));
            }
            return;
        }
        elapsed += dt;
        float t = Math.min(elapsed / duration, 1f);
        value.set(lerp.interpolate(from, target, easing.apply(t)));
        if (t >= 1f) {
            value.set(target); // exact snap
            detach();
        }
    }

    public void dispose() {
        detach();
        if (followEffect != null) followEffect.dispose();
    }
}

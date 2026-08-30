package com.meekdev.amnetic.client.surface;

import com.meekdev.amnetic.client.anim.Easing;
import com.meekdev.amnetic.client.anim.EasingFunction;
import com.meekdev.amnetic.client.surface.reactive.Effect;
import com.meekdev.amnetic.client.surface.reactive.Motion;
import com.meekdev.amnetic.client.surface.reactive.Reactive;
import com.meekdev.amnetic.client.surface.widget.Widget;
import java.util.function.Consumer;

public final class Fx {

    private Fx() {}

    public static Motion<Float> spring(Consumer<Float> setter, float initial, float stiffness, float damping) {
        Motion<Float> m = Motion.spring(initial, stiffness, damping);
        new Effect(() -> setter.accept(m.value().get()));
        return m;
    }

    public static Motion<Float> tween(Consumer<Float> setter, float from, float to, float seconds) {
        return tween(setter, from, to, seconds, Easing.CUBIC_OUT);
    }

    public static Motion<Float> tween(Consumer<Float> setter, float from, float to, float seconds, EasingFunction easing) {
        Motion<Float> m = Motion.tween(from, seconds, easing);
        new Effect(() -> setter.accept(m.value().get()));
        m.target(to);
        return m;
    }

    public static Effect perFrame(Consumer<Float> body) {
        float start = Reactive.clock().peek();
        return new Effect(() -> body.accept(Reactive.clock().get() - start));
    }

    public static Effect timed(float seconds, Consumer<Float> body) {
        float start = Reactive.clock().peek();
        Effect[] ref = new Effect[1];
        ref[0] = new Effect(() -> {
            float t = Reactive.clock().get() - start;
            if (t >= seconds) {
                body.accept(seconds);
                if (ref[0] != null) ref[0].dispose();
                return;
            }
            body.accept(t);
        });
        return ref[0];
    }

    public static Motion<Float> fadeIn(Widget w, float seconds) {
        return tween(w::opacity, 0f, 1f, seconds);
    }

    public static Motion<Float> fadeOut(Widget w, float seconds) {
        return tween(w::opacity, w.opacityValue(), 0f, seconds).onSettle(w::remove);
    }
}

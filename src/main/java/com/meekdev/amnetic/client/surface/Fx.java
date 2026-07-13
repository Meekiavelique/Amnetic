package com.meekdev.amnetic.client.surface;

import com.meekdev.amnetic.client.anim.Easing;
import com.meekdev.amnetic.client.anim.EasingFunction;
import com.meekdev.amnetic.client.surface.reactive.Effect;
import com.meekdev.amnetic.client.surface.reactive.Motion;
import com.meekdev.amnetic.client.surface.reactive.Reactive;
import com.meekdev.amnetic.client.surface.widget.Widget;
import java.util.function.Consumer;

// generic motion drivers, nothing here knows what a shake or a bounce is: you compose
// them from springs, tweens and per-frame functions applied to any float setter,
// most commonly the widget transform channels
//
//   Fx.spring(v -> w.scaleChannel(v), 1f, 400, 8).target(1.15f);      // a bounce
//   Fx.perFrame(t -> w.translate((float) Math.sin(t * 60) * amp, 0)); // a shake
//   Fx.tween(w::opacity, 0f, 1f, 0.3f);                               // a fade
public final class Fx {

    private Fx() {}

    // spring driving an arbitrary float setter, retarget it whenever you like
    public static Motion<Float> spring(Consumer<Float> setter, float initial, float stiffness, float damping) {
        Motion<Float> m = Motion.spring(initial, stiffness, damping);
        new Effect(() -> setter.accept(m.value().get()));
        return m;
    }

    // one-shot tween driving an arbitrary float setter
    public static Motion<Float> tween(Consumer<Float> setter, float from, float to, float seconds) {
        return tween(setter, from, to, seconds, Easing.CUBIC_OUT);
    }

    public static Motion<Float> tween(Consumer<Float> setter, float from, float to, float seconds, EasingFunction easing) {
        Motion<Float> m = Motion.tween(from, seconds, easing);
        new Effect(() -> setter.accept(m.value().get()));
        m.target(to);
        return m;
    }

    // a function of elapsed seconds run every frame until disposed, the escape hatch for
    // anything oscillatory or procedural
    public static Effect perFrame(Consumer<Float> body) {
        float start = Reactive.clock().peek();
        return new Effect(() -> body.accept(Reactive.clock().get() - start));
    }

    // a per-frame driver that kills itself after the duration, for finite procedural bursts
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

    // fade sugar built on the generic pieces
    public static Motion<Float> fadeIn(Widget w, float seconds) {
        return tween(w::opacity, 0f, 1f, seconds);
    }

    // fades to zero then removes the widget from its parent
    public static Motion<Float> fadeOut(Widget w, float seconds) {
        return tween(w::opacity, w.opacityValue(), 0f, seconds).onSettle(w::remove);
    }
}

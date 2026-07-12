package com.meekdev.amnetic.client.particle.track;

import com.meekdev.amnetic.client.anim.Easing;
import java.util.ArrayList;
import java.util.List;

public final class Curve {

    private final float[] times;
    private final float[] values;
    private final Easing[] easings;

    private Curve(float[] times, float[] values, Easing[] easings) {
        this.times = times;
        this.values = values;
        this.easings = easings;
    }

    public float at(float t) {
        if (times.length == 1) return values[0];
        if (t <= times[0]) return values[0];
        if (t >= times[times.length - 1]) return values[values.length - 1];

        int i = 0;
        while (i < times.length - 1 && t > times[i + 1]) i++;

        float span = times[i + 1] - times[i];
        float local = span <= 1e-6f ? 0f : (t - times[i]) / span;
        float eased = easings[i].apply(local);

        return values[i] + (values[i + 1] - values[i]) * eased;
    }

    public static Curve linear(float a, float b) {
        return new Curve(new float[]{0f, 1f}, new float[]{a, b}, new Easing[]{Easing.LINEAR});
    }

    public static Curve constant(float v) {
        return new Curve(new float[]{0f}, new float[]{v}, new Easing[]{Easing.LINEAR});
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        private final List<float[]> keys = new ArrayList<>();
        private final List<Easing> eases = new ArrayList<>();

        public Builder key(float time, float value) {
            return key(time, value, Easing.LINEAR);
        }

        public Builder key(float time, float value, Easing easing) {
            keys.add(new float[]{time, value});
            eases.add(easing);
            return this;
        }

        public Curve build() {
            if (keys.isEmpty()) throw new IllegalStateException("Curve needs at least one key");

            keys.sort((x, y) -> Float.compare(x[0], y[0]));

            float[] t = new float[keys.size()];
            float[] v = new float[keys.size()];
            Easing[] e = new Easing[keys.size()];

            for (int i = 0; i < keys.size(); i++) {
                t[i] = keys.get(i)[0];
                v[i] = keys.get(i)[1];
                e[i] = eases.get(i);
            }

            return new Curve(t, v, e);
        }
    }
}
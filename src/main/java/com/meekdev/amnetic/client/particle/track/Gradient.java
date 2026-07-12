package com.meekdev.amnetic.client.particle.track;

import java.util.ArrayList;
import java.util.List;

public final class Gradient {

    private final float[] t;
    private final float[] r, g, b, a;

    private Gradient(float[] t, float[] r, float[] g, float[] b, float[] a) {
        this.t = t; this.r = r; this.g = g; this.b = b; this.a = a;
    }

    public void eval(float time, float[] out) {
        if (t.length == 1) { out[0] = r[0]; out[1] = g[0]; out[2] = b[0]; out[3] = a[0]; return; }
        if (time <= t[0]) { out[0] = r[0]; out[1] = g[0]; out[2] = b[0]; out[3] = a[0]; return; }
        int last = t.length - 1;
        if (time >= t[last]) { out[0] = r[last]; out[1] = g[last]; out[2] = b[last]; out[3] = a[last]; return; }
        int i = 0;
        while (i < last && time > t[i + 1]) i++;
        float span = t[i + 1] - t[i];
        float f = span <= 1e-6f ? 0f : (time - t[i]) / span;
        out[0] = r[i] + (r[i + 1] - r[i]) * f;
        out[1] = g[i] + (g[i + 1] - g[i]) * f;
        out[2] = b[i] + (b[i + 1] - b[i]) * f;
        out[3] = a[i] + (a[i + 1] - a[i]) * f;
    }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private final List<float[]> stops = new ArrayList<>(); // {t, r, g, b, a}

        public Builder stop(float time, float r, float g, float b, float a) {
            stops.add(new float[]{time, r, g, b, a});
            return this;
        }

        public Builder stop(float time, float r, float g, float b) { return stop(time, r, g, b, 1f); }

        public Gradient build() {
            if (stops.isEmpty()) throw new IllegalStateException("Gradient needs at least one stop");
            stops.sort((x, y) -> Float.compare(x[0], y[0]));
            int n = stops.size();
            float[] t = new float[n], r = new float[n], g = new float[n], b = new float[n], a = new float[n];
            for (int i = 0; i < n; i++) {
                float[] s = stops.get(i);
                t[i] = s[0]; r[i] = s[1]; g[i] = s[2]; b[i] = s[3]; a[i] = s[4];
            }
            return new Gradient(t, r, g, b, a);
        }
    }
}

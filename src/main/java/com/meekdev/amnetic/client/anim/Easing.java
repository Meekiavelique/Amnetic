package com.meekdev.amnetic.client.anim;

public enum Easing implements EasingFunction {

    LINEAR {
        @Override public float apply(float t) { return t; }
    },
    /** Quadratic ease-in ({@code t^2}). */
    EASE_IN {
        @Override public float apply(float t) { return t * t; }
    },
    /** Quadratic ease-out ({@code 1 - (1-t)^2}). */
    EASE_OUT {
        @Override public float apply(float t) { float u = 1f - t; return 1f - u * u; }
    },
    /** Smoothstep ({@code 3t^2 - 2t^3}). */
    SMOOTH {
        @Override public float apply(float t) { return t * t * (3f - 2f * t); }
    },

    QUAD_IN_OUT {
        @Override public float apply(float t) {
            return t < 0.5f ? 2f * t * t : 1f - sq(-2f * t + 2f) / 2f;
        }
    },
    CUBIC_IN {
        @Override public float apply(float t) { return t * t * t; }
    },
    CUBIC_OUT {
        @Override public float apply(float t) { float u = 1f - t; return 1f - u * u * u; }
    },
    CUBIC_IN_OUT {
        @Override public float apply(float t) {
            return t < 0.5f ? 4f * t * t * t : 1f - cube(-2f * t + 2f) / 2f;
        }
    },
    SINE_IN {
        @Override public float apply(float t) { return 1f - (float) Math.cos((t * Math.PI) / 2.0); }
    },
    SINE_OUT {
        @Override public float apply(float t) { return (float) Math.sin((t * Math.PI) / 2.0); }
    },
    SINE_IN_OUT {
        @Override public float apply(float t) { return -0.5f * ((float) Math.cos(Math.PI * t) - 1f); }
    },
    EXPO_OUT {
        @Override public float apply(float t) {
            return t >= 1f ? 1f : 1f - (float) Math.pow(2.0, -10.0 * t);
        }
    },
    BACK_OUT {
        @Override public float apply(float t) {
            float c1 = 1.70158f, c3 = c1 + 1f, u = t - 1f;
            return 1f + c3 * u * u * u + c1 * u * u;
        }
    },
    ELASTIC_OUT {
        @Override public float apply(float t) {
            if (t <= 0f) return 0f;
            if (t >= 1f) return 1f;
            float c4 = (float) ((2.0 * Math.PI) / 3.0);
            return (float) (Math.pow(2.0, -10.0 * t) * Math.sin((t * 10.0 - 0.75) * c4) + 1.0);
        }
    },
    BOUNCE_OUT {
        @Override public float apply(float t) {
            float n1 = 7.5625f, d1 = 2.75f;
            if (t < 1f / d1) return n1 * t * t;
            if (t < 2f / d1) { t -= 1.5f / d1; return n1 * t * t + 0.75f; }
            if (t < 2.5f / d1) { t -= 2.25f / d1; return n1 * t * t + 0.9375f; }
            t -= 2.625f / d1;
            return n1 * t * t + 0.984375f;
        }
    };

    private static float sq(float v) { return v * v; }

    private static float cube(float v) { return v * v * v; }
}

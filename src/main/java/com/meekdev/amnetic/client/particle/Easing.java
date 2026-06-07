package com.meekdev.amnetic.client.particle;

public enum Easing {
    LINEAR {
        @Override public float apply(float t) { return t; }
    },
    EASE_OUT {
        @Override public float apply(float t) { float u = 1f - t; return 1f - u * u; }
    },
    EASE_IN {
        @Override public float apply(float t) { return t * t; }
    },
    SMOOTH {
        @Override public float apply(float t) { return t * t * (3f - 2f * t); }
    };

    public abstract float apply(float t);
}

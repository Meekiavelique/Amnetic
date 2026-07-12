package com.meekdev.amnetic.client.particle.track;

@FunctionalInterface
public interface Track {

    float eval(float t, float seed);

    static Track constant(float v) {
        return (t, seed) -> v;
    }

    static Track random(float min, float max) {
        return (t, seed) -> min + (max - min) * seed;
    }

    static Track curve(Curve c) {
        return (t, seed) -> c.at(t);
    }

    static Track randomBetween(Curve a, Curve b) {
        return (t, seed) -> {
            float va = a.at(t);
            return va + (b.at(t) - va) * seed;
        };
    }

    static Track lerp(float start, float end) {
        return (t, seed) -> start + (end - start) * t;
    }
}

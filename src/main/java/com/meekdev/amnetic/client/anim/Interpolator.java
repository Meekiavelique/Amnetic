package com.meekdev.amnetic.client.anim;

@FunctionalInterface
public interface Interpolator<T> {

    T interpolate(T a, T b, float t);
}

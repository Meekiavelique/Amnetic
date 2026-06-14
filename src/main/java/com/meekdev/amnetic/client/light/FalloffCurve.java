package com.meekdev.amnetic.client.light;

public enum FalloffCurve {
    SMOOTH,
    LINEAR,
    INVERSE_SQUARE,
    EXPONENT;

    public int id() { return ordinal(); }
}

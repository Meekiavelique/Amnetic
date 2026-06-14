package com.meekdev.amnetic.client.light;

public enum LightType {
    POINT,
    SPOT,
    DIRECTIONAL,
    AREA_RECT,
    AREA_DISC,
    TUBE;

    public int id() { return ordinal(); }
}

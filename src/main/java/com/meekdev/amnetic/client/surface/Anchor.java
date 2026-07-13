package com.meekdev.amnetic.client.surface;

// free placement against the parent rect, offsets push inward from the anchored corner/edge
public enum Anchor {
    TOP_LEFT(0f, 0f), TOP_CENTER(0.5f, 0f), TOP_RIGHT(1f, 0f),
    CENTER_LEFT(0f, 0.5f), CENTER(0.5f, 0.5f), CENTER_RIGHT(1f, 0.5f),
    BOTTOM_LEFT(0f, 1f), BOTTOM_CENTER(0.5f, 1f), BOTTOM_RIGHT(1f, 1f);

    public final float fx, fy;

    Anchor(float fx, float fy) {
        this.fx = fx;
        this.fy = fy;
    }
}

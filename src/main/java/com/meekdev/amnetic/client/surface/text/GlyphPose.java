package com.meekdev.amnetic.client.surface.text;

public final class GlyphPose {

    public float dx, dy;
    public float scale = 1f;
    public float alpha = 1f;

    public void reset() {
        dx = 0; dy = 0; scale = 1f; alpha = 1f;
    }
}

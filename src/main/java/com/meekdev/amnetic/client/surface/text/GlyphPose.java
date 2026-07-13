package com.meekdev.amnetic.client.surface.text;

// mutable per-glyph pose handed to a GlyphFx, reset before every glyph so an fx only
// writes the channels it cares about
public final class GlyphPose {

    public float dx, dy;      // gui px offset from the resting pen position
    public float scale = 1f;  // around the glyph center
    public float alpha = 1f;  // multiplies the text color alpha

    public void reset() {
        dx = 0; dy = 0; scale = 1f; alpha = 1f;
    }
}

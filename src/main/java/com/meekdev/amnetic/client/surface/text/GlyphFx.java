package com.meekdev.amnetic.client.surface.text;

// the generic animated-text hook: called once per glyph per frame, mutate the pose to
// move/scale/fade that glyph, a typewriter is alpha-by-index, a wave is dy = sin(i + t),
// all user code, nothing shipped as presets
public interface GlyphFx {
    void apply(int glyphIndex, float time, GlyphPose pose);
}

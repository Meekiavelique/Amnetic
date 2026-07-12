package com.meekdev.amnetic.client.surface.draw;

import com.meekdev.amnetic.client.surface.internal.UiBatcher;
import com.meekdev.amnetic.client.surface.text.Fonts;
import com.meekdev.amnetic.client.surface.text.SdfFont;
import net.minecraft.resources.Identifier;

// immediate drawing surface handed to draw callbacks, coordinates are gui-scaled pixels
// like GuiGraphics, phase 2 widgets render through this same api
public final class UiDraw {

    private final UiBatcher batcher;
    private final float width, height;
    private Identifier font;

    public UiDraw(UiBatcher batcher, float width, float height) {
        this.batcher = batcher;
        this.width = width;
        this.height = height;
    }

    public float width() { return width; }
    public float height() { return height; }

    // font used by the text calls until changed
    public UiDraw font(Identifier fontId) {
        this.font = fontId;
        return this;
    }

    public UiDraw rect(float x, float y, float w, float h, int argb) {
        batcher.rect(x, y, w, h, 0f, 0f, 0f, argb);
        return this;
    }

    public UiDraw roundedRect(float x, float y, float w, float h, float radius, int argb) {
        batcher.rect(x, y, w, h, radius, 0f, 0f, argb);
        return this;
    }

    public UiDraw border(float x, float y, float w, float h, float radius, float borderWidth, int argb) {
        batcher.rect(x, y, w, h, radius, borderWidth, 0f, argb);
        return this;
    }

    // soft drop shape, offset it yourself for a drop shadow
    public UiDraw shadow(float x, float y, float w, float h, float radius, float softness, int argb) {
        batcher.rect(x, y, w, h, radius, 0f, softness, argb);
        return this;
    }

    public UiDraw image(int glTextureId, float x, float y, float w, float h, int argb) {
        batcher.image(glTextureId, x, y, w, h, argb);
        return this;
    }

    // baseline-left text at cap height px
    public UiDraw text(String s, float x, float y, float px, int argb) {
        SdfFont f = resolveFont();
        if (f == null) return this;
        emit(f, s, x, y + f.ascentPx() * (px / f.bakePx()), px, argb);
        return this;
    }

    public UiDraw textCentered(String s, float cx, float cy, float px, int argb) {
        SdfFont f = resolveFont();
        if (f == null) return this;
        float scale = px / f.bakePx();
        float x = cx - f.width(s, px) * 0.5f;
        float baseline = cy + f.ascentPx() * scale * 0.5f;
        emit(f, s, x, baseline, px, argb);
        return this;
    }

    public float textWidth(String s, float px) {
        SdfFont f = resolveFont();
        return f == null ? 0f : f.width(s, px);
    }

    private SdfFont resolveFont() {
        return font == null ? null : Fonts.get(font);
    }

    private void emit(SdfFont f, String s, float penX, float baseline, float px, int argb) {
        float scale = px / f.bakePx();
        float inv = 1f / f.atlasSize();
        float r = ((argb >> 16) & 0xFF) / 255f, g = ((argb >> 8) & 0xFF) / 255f;
        float b = (argb & 0xFF) / 255f, a = ((argb >>> 24) & 0xFF) / 255f;
        int prev = -1;
        for (int i = 0; i < s.length(); ) {
            int cp = s.codePointAt(i);
            i += Character.charCount(cp);
            SdfFont.Glyph gl = f.glyph(cp);
            if (gl == null) { prev = cp; continue; }
            if (prev != -1) penX += f.kern(prev, cp) * scale;
            if (gl.aw() > 0 && gl.ah() > 0) {
                float x0 = penX + gl.xoff() * scale, y0 = baseline + gl.yoff() * scale;
                float x1 = x0 + gl.aw() * scale, y1 = y0 + gl.ah() * scale;
                batcher.glyph(f.texture(),
                        x0, y0, x1, y1,
                        gl.ax() * inv, gl.ay() * inv,
                        (gl.ax() + gl.aw()) * inv, (gl.ay() + gl.ah()) * inv,
                        r, g, b, a);
            }
            penX += gl.advance() * scale;
            prev = cp;
        }
    }
}

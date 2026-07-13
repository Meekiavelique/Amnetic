package com.meekdev.amnetic.client.surface.draw;

import com.meekdev.amnetic.client.surface.internal.UiBatcher;
import com.meekdev.amnetic.client.surface.material.SurfaceMaterial;
import com.meekdev.amnetic.client.surface.text.Fonts;
import com.meekdev.amnetic.client.surface.text.SdfFont;
import java.util.ArrayDeque;
import java.util.Deque;
import net.minecraft.resources.Identifier;

// immediate drawing surface handed to draw callbacks and widgets, coordinates are
// gui-scaled pixels like GuiGraphics
public final class UiDraw {

    private final UiBatcher batcher;
    private final float width, height;
    private final int sceneTexture; // 0 when no capture is available this frame
    private Identifier font;
    private final Deque<float[]> clips = new ArrayDeque<>();

    public UiDraw(UiBatcher batcher, float width, float height) {
        this(batcher, width, height, 0);
    }

    public UiDraw(UiBatcher batcher, float width, float height, int sceneTexture) {
        this.batcher = batcher;
        this.width = width;
        this.height = height;
        this.sceneTexture = sceneTexture;
    }

    public float width() { return width; }
    public float height() { return height; }

    // font used by the text calls until changed
    public UiDraw font(Identifier fontId) {
        this.font = fontId;
        return this;
    }

    public Identifier currentFont() {
        return font;
    }

    // nested clips intersect, pop restores the previous one
    public UiDraw pushClip(float x, float y, float w, float h) {
        float x0 = x, y0 = y, x1 = x + w, y1 = y + h;
        float[] prev = clips.peek();
        if (prev != null) {
            x0 = Math.max(x0, prev[0]); y0 = Math.max(y0, prev[1]);
            x1 = Math.min(x1, prev[2]); y1 = Math.min(y1, prev[3]);
        }
        float[] clip = {x0, y0, x1, y1};
        clips.push(clip);
        batcher.setClip(x0, y0, x1, y1);
        return this;
    }

    public UiDraw popClip() {
        clips.poll();
        float[] prev = clips.peek();
        if (prev != null) batcher.setClip(prev[0], prev[1], prev[2], prev[3]);
        else batcher.clearClip();
        return this;
    }

    private final Deque<float[]> transforms = new ArrayDeque<>();

    // translate + uniform scale + rotation around a pivot, applies to everything until pop,
    // the generic hook every motion effect drives (shake, bounce, pop-in, spin, whatever)
    public UiDraw pushTransform(float pivotX, float pivotY, float dx, float dy, float scale, float rotation) {
        transforms.push(batcher.transform());
        batcher.composeTransform(pivotX, pivotY, dx, dy, scale, rotation);
        return this;
    }

    public UiDraw popTransform() {
        float[] prev = transforms.poll();
        if (prev != null) batcher.setTransform(prev);
        return this;
    }

    // flush what's recorded, run arbitrary rendering (offscreen model views etc), then
    // restore the pass state so recording can continue in painter's order
    public UiDraw interrupt(Runnable outsideRendering) {
        batcher.flush();
        outsideRendering.run();
        batcher.restorePassState();
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

    public UiDraw gradient(float x, float y, float w, float h, float radius, int topArgb, int bottomArgb) {
        batcher.rectGradient(x, y, w, h, radius, 0f, 0f, topArgb, bottomArgb);
        return this;
    }

    public UiDraw border(float x, float y, float w, float h, float radius, float borderWidth, int argb) {
        batcher.rect(x, y, w, h, radius, borderWidth, 0f, argb);
        return this;
    }

    // soft shape, offset it yourself for a drop shadow
    public UiDraw shadow(float x, float y, float w, float h, float radius, float softness, int argb) {
        batcher.rect(x, y, w, h, radius, 0f, softness, argb);
        return this;
    }

    public UiDraw image(int glTextureId, float x, float y, float w, float h, int argb) {
        batcher.image(glTextureId, x, y, w, h, argb);
        return this;
    }

    // frosted glass over the scene, falls back to a translucent fill without a capture
    public UiDraw blurBehind(float x, float y, float w, float h, float radius, float blurPx, int tint) {
        batcher.blurBehind(sceneTexture, x, y, w, h, radius, blurPx, tint);
        return this;
    }

    // surface material fill, hover/pressed/focus feed the shader built-ins
    public UiDraw material(SurfaceMaterial mat, float x, float y, float w, float h, float radius,
                           float hover, float pressed, float focus, int tint) {
        batcher.material(mat, x, y, w, h, radius, hover, pressed, focus, tint);
        return this;
    }

    public UiDraw material(SurfaceMaterial mat, float x, float y, float w, float h, float radius) {
        return material(mat, x, y, w, h, radius, 0f, 0f, 0f, 0xFFFFFFFF);
    }

    // baseline-left text at cap height px
    public UiDraw text(String s, float x, float y, float px, int argb) {
        SdfFont f = resolveFont();
        if (f == null) return this;
        emit(f, s, x, y + f.ascentPx() * (px / f.bakePx()), px, argb, 0f, 0f);
        return this;
    }

    // styled text at an explicit baseline: edgeOffset grows/shrinks the glyph edge in gui px
    // (positive = fatter, an outline pass), softness feathers the edge in gui px (glow/blur),
    // reach tops out around px/8 where the baked sdf spread ends
    public UiDraw textStyled(String s, float x, float baselineY, float px, int argb,
                             float edgeOffset, float softness) {
        SdfFont f = resolveFont();
        if (f == null) return this;
        float u = f.sdfUnitsPerGuiPx(px);
        emit(f, s, x, baselineY, px, argb, edgeOffset * u, softness * u);
        return this;
    }

    // emits one styled glyph at the pen position and returns its advance in gui px,
    // kerning is the caller's job (SdfFont.kern), this is the per-glyph fx building block
    public float glyph(int codepoint, float penX, float baselineY, float px, int argb,
                       float edgeOffset, float softness) {
        SdfFont f = resolveFont();
        if (f == null) return 0f;
        SdfFont.Glyph gl = f.glyph(codepoint);
        if (gl == null) return 0f;
        float scale = px / f.bakePx();
        float u = f.sdfUnitsPerGuiPx(px);
        if (gl.aw() > 0 && gl.ah() > 0) {
            float inv = 1f / f.atlasSize();
            float r = ((argb >> 16) & 0xFF) / 255f, g = ((argb >> 8) & 0xFF) / 255f;
            float b = (argb & 0xFF) / 255f, a = ((argb >>> 24) & 0xFF) / 255f;
            float x0 = penX + gl.xoff() * scale, y0 = baselineY + gl.yoff() * scale;
            batcher.glyph(f.texture(),
                    x0, y0, x0 + gl.aw() * scale, y0 + gl.ah() * scale,
                    gl.ax() * inv, gl.ay() * inv,
                    (gl.ax() + gl.aw()) * inv, (gl.ay() + gl.ah()) * inv,
                    r, g, b, a, edgeOffset * u, softness * u);
        }
        return gl.advance() * scale;
    }

    public UiDraw textCentered(String s, float cx, float cy, float px, int argb) {
        SdfFont f = resolveFont();
        if (f == null) return this;
        float scale = px / f.bakePx();
        float x = cx - f.width(s, px) * 0.5f;
        float baseline = cy + f.capPx() * scale * 0.5f; // center the cap block, not the ascent
        emit(f, s, x, baseline, px, argb, 0f, 0f);
        return this;
    }

    // left-aligned, vertically centered on cy, for fields and rows
    public UiDraw textLeftCentered(String s, float x, float cy, float px, int argb) {
        SdfFont f = resolveFont();
        if (f == null) return this;
        float baseline = cy + f.capPx() * (px / f.bakePx()) * 0.5f;
        emit(f, s, x, baseline, px, argb, 0f, 0f);
        return this;
    }

    public float textWidth(String s, float px) {
        SdfFont f = resolveFont();
        return f == null ? 0f : f.width(s, px);
    }

    public float lineHeight(float px) {
        SdfFont f = resolveFont();
        return f == null ? px : f.ascentPx() * (px / f.bakePx()) * 1.35f;
    }

    private SdfFont resolveFont() {
        return font == null ? null : Fonts.get(font);
    }

    // edgeOffset/softness already converted to sdf units here
    private void emit(SdfFont f, String s, float penX, float baseline, float px, int argb,
                      float edgeOffset, float softness) {
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
                        r, g, b, a, edgeOffset, softness);
            }
            penX += gl.advance() * scale;
            prev = cp;
        }
    }
}

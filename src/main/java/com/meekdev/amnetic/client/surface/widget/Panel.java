package com.meekdev.amnetic.client.surface.widget;

import com.meekdev.amnetic.client.surface.Anchor;
import com.meekdev.amnetic.client.surface.draw.UiDraw;
import com.meekdev.amnetic.client.surface.material.SurfaceMaterial;

public class Panel extends Stack {

    int background = 0xE0141820;
    int backgroundBottom;
    float rounding = 10f;
    float borderWidth;
    int borderColor = 0x40FFFFFF;
    float shadowSoftness;
    int shadowColor = 0x80000000;
    boolean clipContent;
    float blurBehind;
    SurfaceMaterial material;

    public Panel background(int argb) { background = argb; return this; }
    public Panel gradient(int topArgb, int bottomArgb) { background = topArgb; backgroundBottom = bottomArgb; return this; }
    public Panel rounding(float r) { rounding = r; return this; }
    public Panel border(float width, int argb) { borderWidth = width; borderColor = argb; return this; }
    public Panel shadow(float softness) { shadowSoftness = softness; return this; }
    public Panel shadow(float softness, int argb) { shadowSoftness = softness; shadowColor = argb; return this; }
    public Panel clipContent(boolean clip) { clipContent = clip; return this; }

    public Panel blurBehind(float blurPx) { blurBehind = blurPx; return this; }

    public Panel material(SurfaceMaterial mat) { material = mat; return this; }

    @Override
    protected void drawSelf(UiDraw d, float alpha) {
        if (shadowSoftness > 0) {
            d.shadow(x + 2, y + 4, w, h, rounding, shadowSoftness, fade(shadowColor, alpha));
        }
        if (material != null) {
            d.material(material, x, y, w, h, rounding,
                    hovered ? 1f : 0f, pressed ? 1f : 0f, focused ? 1f : 0f, fade(0xFFFFFFFF, alpha));
        } else if (blurBehind > 0) {
            d.blurBehind(x, y, w, h, rounding, blurBehind, fade(background, alpha));
        } else if (backgroundBottom != 0) {
            d.gradient(x, y, w, h, rounding, fade(background, alpha), fade(backgroundBottom, alpha));
        } else if ((background >>> 24) != 0) {
            d.roundedRect(x, y, w, h, rounding, fade(background, alpha));
        }
        if (borderWidth > 0) {
            d.border(x, y, w, h, rounding, borderWidth, fade(borderColor, alpha));
        }
        if (clipContent) d.pushClip(x + padding, y + padding, w - padding * 2, h - padding * 2);
    }

    @Override
    public final void drawAfterChildren(UiDraw d) {
        if (clipContent) d.popClip();
    }

    @Override public Panel size(float w, float h) { super.size(w, h); return this; }
    @Override public Panel width(float w) { super.width(w); return this; }
    @Override public Panel height(float h) { super.height(h); return this; }
    @Override public Panel grow(float g) { super.grow(g); return this; }
    @Override public Panel anchor(Anchor a) { super.anchor(a); return this; }
    @Override public Panel offset(float dx, float dy) { super.offset(dx, dy); return this; }
    @Override public Panel padding(float p) { super.padding(p); return this; }
    @Override public Panel visible(boolean v) { super.visible(v); return this; }
    @Override public Panel opacity(float o) { super.opacity(o); return this; }
}

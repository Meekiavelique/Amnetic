package com.meekdev.amnetic.client.surface.widget;

import com.meekdev.amnetic.client.surface.draw.UiDraw;
import com.meekdev.amnetic.client.surface.material.SurfaceMaterial;

// styled free-placement container: rounded fill or gradient or custom material,
// optional border and drop shadow, optional content clip
public class Panel extends Stack {

    int background = 0xE0141820;
    int backgroundBottom; // 0 means no gradient
    float rounding = 10f;
    float borderWidth;
    int borderColor = 0x40FFFFFF;
    float shadowSoftness;
    int shadowColor = 0x80000000;
    boolean clipContent;
    SurfaceMaterial material;

    public Panel background(int argb) { background = argb; return this; }
    public Panel gradient(int topArgb, int bottomArgb) { background = topArgb; backgroundBottom = bottomArgb; return this; }
    public Panel rounding(float r) { rounding = r; return this; }
    public Panel border(float width, int argb) { borderWidth = width; borderColor = argb; return this; }
    public Panel shadow(float softness) { shadowSoftness = softness; return this; }
    public Panel shadow(float softness, int argb) { shadowSoftness = softness; shadowColor = argb; return this; }
    public Panel clipContent(boolean clip) { clipContent = clip; return this; }

    // surface shader material replaces the flat fill
    public Panel material(SurfaceMaterial mat) { material = mat; return this; }

    @Override
    protected void drawSelf(UiDraw d, float alpha) {
        if (shadowSoftness > 0) {
            d.shadow(x + 2, y + 4, w, h, rounding, shadowSoftness, fade(shadowColor, alpha));
        }
        if (material != null) {
            d.material(material, x, y, w, h, rounding,
                    hovered ? 1f : 0f, pressed ? 1f : 0f, focused ? 1f : 0f, fade(0xFFFFFFFF, alpha));
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
}

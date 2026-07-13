package com.meekdev.amnetic.client.surface.widget;

import com.meekdev.amnetic.client.surface.Anchor;
import com.meekdev.amnetic.client.surface.draw.UiDraw;
import com.meekdev.amnetic.client.surface.material.SurfaceMaterial;
import com.meekdev.amnetic.client.surface.reactive.Motion;
import com.meekdev.amnetic.client.surface.reactive.Signal;
import com.meekdev.amnetic.client.surface.text.Fonts;
import com.meekdev.amnetic.client.surface.text.SdfFont;
import com.meekdev.amnetic.client.surface.Surfaces;
import net.minecraft.resources.Identifier;

// clickable rounded button, hover rides a spring so the highlight glides instead of snapping
public class Button extends Widget {

    String label;
    float px = 14;
    float rounding = 8;
    int background = 0xFF232A33;
    int hoverBackground = 0xFF3A4654;
    int textColor = 0xFFFFFFFF;
    float borderWidth = 1f;
    int borderColor = 0x30FFFFFF;
    int borderHover = 0x30FFFFFF;
    int labelShadow; // 0 = off, classic mc text look when set
    Identifier fontId;
    SurfaceMaterial material;
    Runnable onClick;

    private final Signal<Float> hoverTarget = new Signal<>(0f);
    private final Motion<Float> hoverT = Motion.spring(0f, 60f, 9f);

    public Button(String label) {
        this.label = label;
        hoverT.follow(hoverTarget);
    }

    public Button label(String l) { label = l; return this; }
    public Button px(float p) { px = p; return this; }
    public Button rounding(float r) { rounding = r; return this; }
    public Button colors(int normal, int hover) { background = normal; hoverBackground = hover; return this; }
    public Button textColor(int argb) { textColor = argb; return this; }
    public Button textShadow(int argb) { labelShadow = argb; return this; }
    public Button border(float width, int color) { borderWidth = width; borderColor = color; borderHover = color; return this; }
    public Button border(float width, int color, int hoverColor) { borderWidth = width; borderColor = color; borderHover = hoverColor; return this; }
    public Button font(Identifier id) { fontId = id; return this; }
    public Button material(SurfaceMaterial mat) { material = mat; return this; }
    public Button onClick(Runnable r) { onClick = r; return this; }

    @Override
    protected boolean interactive() { return true; }

    @Override
    protected float contentWidth() {
        Identifier id = fontId != null ? fontId : Surfaces.defaultFont();
        SdfFont f = id == null ? null : Fonts.get(id);
        return (f == null ? 60 : f.width(label, px)) + 24;
    }

    @Override
    protected float contentHeight(float forWidth) {
        return px + 14;
    }

    @Override
    protected void drawSelf(UiDraw d, float alpha) {
        hoverTarget.set(hovered ? 1f : 0f);
        float t = hoverT.value().peek();
        if (material != null) {
            d.material(material, x, y, w, h, rounding, t, pressed ? 1f : 0f, 0f, fade(0xFFFFFFFF, alpha));
        } else {
            int bg = lerpColor(background, hoverBackground, t);
            if (pressed) bg = lerpColor(bg, 0xFF000000, 0.25f);
            d.roundedRect(x, y, w, h, rounding, fade(bg, alpha));
            if (borderWidth > 0) d.border(x, y, w, h, rounding, borderWidth, fade(lerpColor(borderColor, borderHover, t), alpha));
        }
        Identifier prev = d.currentFont();
        d.font(fontId != null ? fontId : Surfaces.defaultFont());
        float lift = pressed ? 1f : 0f;
        if (labelShadow != 0) d.textCentered(label, x + w * 0.5f + 1, y + h * 0.5f + lift + 1, px, fade(labelShadow, alpha));
        d.textCentered(label, x + w * 0.5f, y + h * 0.5f + lift, px, fade(textColor, alpha));
        if (prev != null) d.font(prev);
    }

    @Override
    public boolean onMouseDown(float mx, float my, int button) {
        return true;
    }

    @Override
    public void onMouseUp(float mx, float my, int button) {
        if (mx >= x && my >= y && mx <= x + w && my <= y + h && onClick != null) onClick.run();
    }

    @Override
    public void remove() {
        hoverT.dispose();
        super.remove();
    }

    // fluent overrides so chains keep the subtype
    @Override public Button size(float w, float h) { super.size(w, h); return this; }
    @Override public Button width(float w) { super.width(w); return this; }
    @Override public Button height(float h) { super.height(h); return this; }
    @Override public Button grow(float g) { super.grow(g); return this; }
    @Override public Button anchor(Anchor a) { super.anchor(a); return this; }
    @Override public Button offset(float dx, float dy) { super.offset(dx, dy); return this; }
    @Override public Button padding(float p) { super.padding(p); return this; }
    @Override public Button visible(boolean v) { super.visible(v); return this; }
    @Override public Button opacity(float o) { super.opacity(o); return this; }
}

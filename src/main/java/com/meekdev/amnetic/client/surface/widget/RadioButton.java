package com.meekdev.amnetic.client.surface.widget;

import com.meekdev.amnetic.client.surface.Anchor;
import com.meekdev.amnetic.client.surface.Surfaces;
import com.meekdev.amnetic.client.surface.draw.UiDraw;
import com.meekdev.amnetic.client.surface.reactive.Motion;
import com.meekdev.amnetic.client.surface.reactive.Signal;
import com.meekdev.amnetic.client.surface.text.Fonts;
import com.meekdev.amnetic.client.surface.text.SdfFont;
import net.minecraft.resources.Identifier;

public class RadioButton extends Widget {

    String label;
    float circle = 16;
    float px = 13;
    int ringColor = 0xFF2A2F36;
    int activeColor = 0xFF4C8FDD;
    int textColor = 0xFFFFFFFF;

    RadioGroup group;
    int index;
    private boolean localOn;

    private final Signal<Float> target = new Signal<>(0f);
    private final Motion<Float> dot = Motion.spring(0f, 60f, 9f);

    public RadioButton(String label) {
        this.label = label;
        dot.follow(target);
    }

    public RadioButton label(String l) { label = l; return this; }
    public RadioButton px(float p) { px = p; return this; }
    public RadioButton colors(int ring, int active) { ringColor = ring; activeColor = active; return this; }
    public RadioButton textColor(int argb) { textColor = argb; return this; }

    @Override
    protected boolean interactive() { return true; }

    private boolean selected() {
        return group != null ? group.selected.peek() == index : localOn;
    }

    private SdfFont font() {
        Identifier id = Surfaces.defaultFont();
        return id == null ? null : Fonts.get(id);
    }

    @Override
    protected float contentWidth() {
        if (label == null || label.isEmpty()) return circle;
        SdfFont f = font();
        return circle + 6 + (f == null ? 60 : f.width(label, px));
    }

    @Override
    protected float contentHeight(float forWidth) {
        return circle;
    }

    @Override
    protected void drawSelf(UiDraw d, float alpha) {
        target.set(selected() ? 1f : 0f);
        float t = dot.value().peek();
        float cy0 = y + (h - circle) * 0.5f;
        int bg = lerpColor(ringColor, activeColor, Math.min(Math.max(t, 0f), 1f));
        d.roundedRect(x, cy0, circle, circle, circle * 0.5f, fade(bg, alpha));
        d.border(x, cy0, circle, circle, circle * 0.5f, 1f, fade(0x30FFFFFF, alpha));
        float inner = (circle - 8) * t;
        if (inner > 0.5f) {
            float cx = x + circle * 0.5f, cy = cy0 + circle * 0.5f;
            d.roundedRect(cx - inner * 0.5f, cy - inner * 0.5f, inner, inner, inner * 0.5f, fade(0xFFFFFFFF, alpha));
        }
        if (label != null && !label.isEmpty()) {
            Identifier prev = d.currentFont();
            d.font(Surfaces.defaultFont());
            d.textLeftCentered(label, x + circle + 6, y + h * 0.5f, px, fade(textColor, alpha));
            if (prev != null) d.font(prev);
        }
    }

    @Override
    public boolean onMouseDown(float mx, float my, int button) {
        return true;
    }

    @Override
    public void onMouseUp(float mx, float my, int button) {
        if (mx < x || my < y || mx > x + w || my > y + h) return;
        if (group != null) group.select(index);
        else localOn = !localOn;
    }

    @Override
    public void remove() {
        dot.dispose();
        super.remove();
    }

    @Override public RadioButton size(float w, float h) { super.size(w, h); return this; }
    @Override public RadioButton width(float w) { super.width(w); return this; }
    @Override public RadioButton height(float h) { super.height(h); return this; }
    @Override public RadioButton grow(float g) { super.grow(g); return this; }
    @Override public RadioButton anchor(Anchor a) { super.anchor(a); return this; }
    @Override public RadioButton offset(float dx, float dy) { super.offset(dx, dy); return this; }
    @Override public RadioButton padding(float p) { super.padding(p); return this; }
    @Override public RadioButton visible(boolean v) { super.visible(v); return this; }
    @Override public RadioButton opacity(float o) { super.opacity(o); return this; }
}

package com.meekdev.amnetic.client.surface.widget;

import com.meekdev.amnetic.client.surface.Anchor;
import com.meekdev.amnetic.client.surface.Surfaces;
import com.meekdev.amnetic.client.surface.draw.UiDraw;
import com.meekdev.amnetic.client.surface.reactive.Motion;
import com.meekdev.amnetic.client.surface.reactive.Signal;
import net.minecraft.resources.Identifier;

// non-interactive 0..1 bar, the fill chases the signal on a spring so jumps glide
public class ProgressBar extends Widget {

    public final Signal<Float> value;
    String label;
    int track = 0xFF2A2F36;
    int fill = 0xFF4C8FDD;
    int textColor = 0xFFFFFFFF;
    float rounding = 7;
    float px = 10;

    private final Motion<Float> smooth;

    public ProgressBar(float initial) {
        value = new Signal<>(clamp(initial));
        smooth = Motion.spring(value.peek(), 60f, 9f);
        smooth.follow(value);
        prefH = 14;
    }

    public ProgressBar set(float v) { value.set(clamp(v)); return this; }
    public ProgressBar label(String l) { label = l; return this; }
    public ProgressBar px(float p) { px = p; return this; }
    public ProgressBar colors(int track, int fill) { this.track = track; this.fill = fill; return this; }
    public ProgressBar textColor(int argb) { textColor = argb; return this; }
    public ProgressBar rounding(float r) { rounding = r; return this; }

    @Override
    protected float contentWidth() { return 120; }

    @Override
    protected float contentHeight(float forWidth) { return 14; }

    @Override
    protected void drawSelf(UiDraw d, float alpha) {
        d.roundedRect(x, y, w, h, rounding, fade(track, alpha));
        float t = Math.min(Math.max(smooth.value().peek(), 0f), 1f);
        if (t > 0.001f) {
            // never thinner than the pill height so the rounding stays clean
            float fillW = Math.max(w * t, Math.min(h, w));
            d.roundedRect(x, y, fillW, h, rounding, fade(fill, alpha));
        }
        if (label != null && !label.isEmpty()) {
            Identifier prev = d.currentFont();
            d.font(Surfaces.defaultFont());
            d.textCentered(label, x + w * 0.5f, y + h * 0.5f, px, fade(textColor, alpha));
            if (prev != null) d.font(prev);
        }
    }

    @Override
    public void remove() {
        smooth.dispose();
        super.remove();
    }

    private static float clamp(float v) {
        return Math.min(Math.max(v, 0f), 1f);
    }

    // fluent overrides so chains keep the subtype
    @Override public ProgressBar size(float w, float h) { super.size(w, h); return this; }
    @Override public ProgressBar width(float w) { super.width(w); return this; }
    @Override public ProgressBar height(float h) { super.height(h); return this; }
    @Override public ProgressBar grow(float g) { super.grow(g); return this; }
    @Override public ProgressBar anchor(Anchor a) { super.anchor(a); return this; }
    @Override public ProgressBar offset(float dx, float dy) { super.offset(dx, dy); return this; }
    @Override public ProgressBar padding(float p) { super.padding(p); return this; }
    @Override public ProgressBar visible(boolean v) { super.visible(v); return this; }
    @Override public ProgressBar opacity(float o) { super.opacity(o); return this; }
}

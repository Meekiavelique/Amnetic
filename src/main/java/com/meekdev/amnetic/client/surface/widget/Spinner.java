package com.meekdev.amnetic.client.surface.widget;

import com.meekdev.amnetic.client.surface.Anchor;
import com.meekdev.amnetic.client.surface.Fx;
import com.meekdev.amnetic.client.surface.draw.UiDraw;
import com.meekdev.amnetic.client.surface.reactive.Effect;

public class Spinner extends Widget {

    int color = 0xFFFFFFFF;
    int segments = 8;
    float speed = 4f;

    private final Effect driver;

    public Spinner() {
        prefW = 24;
        prefH = 24;
        driver = Fx.perFrame(t -> rotate(t * speed));
    }

    public Spinner color(int argb) { color = argb; return this; }
    public Spinner segments(int n) { segments = Math.max(n, 2); return this; }
    public Spinner speed(float radiansPerSecond) { speed = radiansPerSecond; return this; }

    @Override
    protected void drawSelf(UiDraw d, float alpha) {
        float cx = x + w * 0.5f, cy = y + h * 0.5f;
        float radius = Math.min(w, h) * 0.5f - 2;
        float s = Math.max(2f, radius * 0.35f);
        for (int i = 0; i < segments; i++) {
            double a = i * Math.PI * 2.0 / segments;
            float sx = cx + (float) Math.cos(a) * (radius - s * 0.5f);
            float sy = cy + (float) Math.sin(a) * (radius - s * 0.5f);
            float trail = (i + 1f) / segments;
            d.roundedRect(sx - s * 0.5f, sy - s * 0.5f, s, s, s * 0.3f, fade(color, alpha * trail));
        }
    }

    @Override
    public void remove() {
        driver.dispose();
        super.remove();
    }

    @Override public Spinner size(float w, float h) { super.size(w, h); return this; }
    @Override public Spinner width(float w) { super.width(w); return this; }
    @Override public Spinner height(float h) { super.height(h); return this; }
    @Override public Spinner grow(float g) { super.grow(g); return this; }
    @Override public Spinner anchor(Anchor a) { super.anchor(a); return this; }
    @Override public Spinner offset(float dx, float dy) { super.offset(dx, dy); return this; }
    @Override public Spinner padding(float p) { super.padding(p); return this; }
    @Override public Spinner visible(boolean v) { super.visible(v); return this; }
    @Override public Spinner opacity(float o) { super.opacity(o); return this; }
}

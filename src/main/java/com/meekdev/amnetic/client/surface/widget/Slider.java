package com.meekdev.amnetic.client.surface.widget;

import com.meekdev.amnetic.client.surface.draw.UiDraw;
import com.meekdev.amnetic.client.surface.reactive.Signal;
import java.util.function.Consumer;

// horizontal 0..1 slider, the value is a signal so anything can bind to it
public class Slider extends Widget {

    public final Signal<Float> value;
    int track = 0xFF2A2F36;
    int fill = 0xFF4C8FDD;
    float rounding = 7;
    Consumer<Float> onChange;

    public Slider(float initial) {
        value = new Signal<>(clamp(initial));
        prefH = 14;
    }

    public Slider colors(int track, int fill) { this.track = track; this.fill = fill; return this; }
    public Slider onChange(Consumer<Float> c) { onChange = c; return this; }

    @Override
    protected boolean interactive() { return true; }

    @Override
    protected float contentWidth() { return 120; }

    @Override
    protected float contentHeight(float forWidth) { return 14; }

    @Override
    protected void drawSelf(UiDraw d, float alpha) {
        d.roundedRect(x, y, w, h, rounding, fade(track, alpha));
        float v = value.peek();
        // knob rides the track, fill ends exactly at the knob so they never drift apart
        float knobSize = h - 4;
        float knobX = x + 2 + (w - knobSize - 4) * v;
        float fillW = Math.max(knobX + knobSize + 2 - x, h);
        d.roundedRect(x, y, fillW, h, rounding, fade(fill, alpha));
        d.roundedRect(knobX, y + 2, knobSize, knobSize, knobSize * 0.5f, fade(0xFFFFFFFF, alpha));
    }

    private void setFromMouse(float mx) {
        // invert the knob-center mapping so the knob lands under the cursor
        float v = clamp((mx - x - h * 0.5f) / Math.max(w - h, 1));
        if (!value.peek().equals(v)) {
            value.set(v);
            if (onChange != null) onChange.accept(v);
        }
    }

    @Override
    public boolean onMouseDown(float mx, float my, int button) {
        setFromMouse(mx);
        return true;
    }

    @Override
    public void onMouseDrag(float mx, float my) {
        setFromMouse(mx);
    }

    private static float clamp(float v) {
        return Math.min(Math.max(v, 0f), 1f);
    }
}

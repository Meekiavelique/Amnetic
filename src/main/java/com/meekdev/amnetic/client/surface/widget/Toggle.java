package com.meekdev.amnetic.client.surface.widget;

import com.meekdev.amnetic.client.surface.draw.UiDraw;
import com.meekdev.amnetic.client.surface.reactive.Motion;
import com.meekdev.amnetic.client.surface.reactive.Signal;
import java.util.function.Consumer;

// on/off switch, the knob glides on a spring
public class Toggle extends Widget {

    public final Signal<Boolean> value;
    int offColor = 0xFF2A2F36;
    int onColor = 0xFF4C8FDD;
    Consumer<Boolean> onChange;

    private final Signal<Float> target = new Signal<>(0f);
    private final Motion<Float> knob = Motion.spring(0f, 60f, 9f);

    public Toggle(boolean initial) {
        value = new Signal<>(initial);
        target.set(initial ? 1f : 0f);
        knob.follow(target);
        prefW = 36;
        prefH = 18;
    }

    public Toggle colors(int off, int on) { offColor = off; onColor = on; return this; }
    public Toggle onChange(Consumer<Boolean> c) { onChange = c; return this; }

    @Override
    protected boolean interactive() { return true; }

    @Override
    protected void drawSelf(UiDraw d, float alpha) {
        float t = knob.value().peek();
        int bg = lerpColor(offColor, onColor, t);
        d.roundedRect(x, y, w, h, h * 0.5f, fade(bg, alpha));
        float knobSize = h - 4;
        float knobX = x + 2 + (w - knobSize - 4) * t;
        d.roundedRect(knobX, y + 2, knobSize, knobSize, knobSize * 0.5f, fade(0xFFFFFFFF, alpha));
    }

    @Override
    public boolean onMouseDown(float mx, float my, int button) {
        return true;
    }

    @Override
    public void onMouseUp(float mx, float my, int button) {
        if (mx < x || my < y || mx > x + w || my > y + h) return;
        boolean next = !value.peek();
        value.set(next);
        target.set(next ? 1f : 0f);
        if (onChange != null) onChange.accept(next);
    }

    @Override
    public void remove() {
        knob.dispose();
        super.remove();
    }
}

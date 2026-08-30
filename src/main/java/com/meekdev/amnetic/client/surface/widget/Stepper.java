package com.meekdev.amnetic.client.surface.widget;

import com.meekdev.amnetic.client.surface.Anchor;
import com.meekdev.amnetic.client.surface.Fx;
import com.meekdev.amnetic.client.surface.Surfaces;
import com.meekdev.amnetic.client.surface.draw.UiDraw;
import com.meekdev.amnetic.client.surface.reactive.Effect;
import com.meekdev.amnetic.client.surface.reactive.Motion;
import com.meekdev.amnetic.client.surface.reactive.Signal;
import java.util.Locale;
import java.util.function.Consumer;
import net.minecraft.resources.Identifier;

public class Stepper extends Widget {

    public final Signal<Float> value;
    final float min, max, step;
    Consumer<Float> onChange;
    float px = 13;
    int textColor = 0xFFFFFFFF;

    private static final float REPEAT_DELAY = 0.4f;
    private static final float REPEAT_INTERVAL = 0.06f;

    public Stepper(float initial, float min, float max, float step) {
        this.min = min;
        this.max = max;
        this.step = step;
        value = new Signal<>(clamp(initial));
        add(new StepButton("-", -1));
        add(new StepButton("+", 1));
        prefH = 20;
    }

    public Stepper onChange(Consumer<Float> c) { onChange = c; return this; }
    public Stepper px(float p) { px = p; return this; }
    public Stepper textColor(int argb) { textColor = argb; return this; }

    private float clamp(float v) {
        return Math.min(Math.max(v, min), max);
    }

    private void apply(int dir) {
        float next = clamp(value.peek() + dir * step);
        if (value.peek() != next) {
            value.set(next);
            if (onChange != null) onChange.accept(next);
        }
    }

    private String format(float v) {
        if (v == Math.rint(v)) return String.valueOf((long) v);
        String s = String.format(Locale.ROOT, "%.2f", v);
        while (s.endsWith("0")) s = s.substring(0, s.length() - 1);
        return s;
    }

    @Override
    protected float contentWidth() { return 90; }

    @Override
    protected float contentHeight(float forWidth) { return 20; }

    @Override
    protected void placeChildren() {
        children.get(0).layout(x, y, h, h);
        children.get(1).layout(x + w - h, y, h, h);
    }

    @Override
    protected void drawSelf(UiDraw d, float alpha) {
        Identifier fid = Surfaces.defaultFont();
        if (fid == null) return;
        Identifier prev = d.currentFont();
        d.font(fid);
        d.textCentered(format(value.peek()), x + w * 0.5f, y + h * 0.5f, px, fade(textColor, alpha));
        if (prev != null) d.font(prev);
    }

    private final class StepButton extends Widget {

        private final String glyph;
        private final int dir;
        private final Signal<Float> hoverTarget = new Signal<>(0f);
        private final Motion<Float> hoverT = Motion.spring(0f, 60f, 9f);
        private Effect repeater;

        StepButton(String glyph, int dir) {
            this.glyph = glyph;
            this.dir = dir;
            hoverT.follow(hoverTarget);
        }

        @Override
        protected boolean interactive() { return true; }

        @Override
        protected void drawSelf(UiDraw d, float alpha) {
            hoverTarget.set(hovered ? 1f : 0f);
            float t = hoverT.value().peek();
            int bg = lerpColor(0xFF232A33, 0xFF3A4654, t);
            if (pressed) bg = lerpColor(bg, 0xFF000000, 0.25f);
            d.roundedRect(x, y, w, h, 6, fade(bg, alpha));
            d.border(x, y, w, h, 6, 1f, fade(0x30FFFFFF, alpha));
            Identifier fid = Surfaces.defaultFont();
            if (fid == null) return;
            Identifier prev = d.currentFont();
            d.font(fid);
            d.textCentered(glyph, x + w * 0.5f, y + h * 0.5f, px, fade(textColor, alpha));
            if (prev != null) d.font(prev);
        }

        @Override
        public boolean onMouseDown(float mx, float my, int button) {
            apply(dir);
            stopRepeat();
            float[] lastFire = {0f};
            repeater = Fx.perFrame(t -> {
                if (t >= REPEAT_DELAY && t - lastFire[0] >= REPEAT_INTERVAL) {
                    apply(dir);
                    lastFire[0] = Math.max(t, REPEAT_DELAY);
                }
            });
            return true;
        }

        @Override
        public void onMouseUp(float mx, float my, int button) {
            stopRepeat();
        }

        private void stopRepeat() {
            if (repeater != null) {
                repeater.dispose();
                repeater = null;
            }
        }

        @Override
        public void remove() {
            stopRepeat();
            hoverT.dispose();
            super.remove();
        }
    }

    @Override public Stepper size(float w, float h) { super.size(w, h); return this; }
    @Override public Stepper width(float w) { super.width(w); return this; }
    @Override public Stepper height(float h) { super.height(h); return this; }
    @Override public Stepper grow(float g) { super.grow(g); return this; }
    @Override public Stepper anchor(Anchor a) { super.anchor(a); return this; }
    @Override public Stepper offset(float dx, float dy) { super.offset(dx, dy); return this; }
    @Override public Stepper padding(float p) { super.padding(p); return this; }
    @Override public Stepper visible(boolean v) { super.visible(v); return this; }
    @Override public Stepper opacity(float o) { super.opacity(o); return this; }
}

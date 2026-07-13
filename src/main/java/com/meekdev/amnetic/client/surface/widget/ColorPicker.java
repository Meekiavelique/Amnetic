package com.meekdev.amnetic.client.surface.widget;

import com.meekdev.amnetic.client.surface.Anchor;
import com.meekdev.amnetic.client.surface.draw.UiDraw;
import com.meekdev.amnetic.client.surface.reactive.Effect;
import com.meekdev.amnetic.client.surface.reactive.Signal;
import java.util.Locale;
import java.util.function.Consumer;

// hsv color picker: sv square + hue bar + hex field stacked in a column, the square
// is exact per strip because rgb is bilinear in s and v so vertical gradients from
// lerped hue tops to black reproduce the model, only s is quantized into strips
public class ColorPicker extends Column {

    public final Signal<Integer> color;
    Consumer<Integer> onChange;

    private float hue, sat, val;
    private int alphaByte;
    private final TextField hex;
    private final Effect colorSync;

    public ColorPicker(int initialArgb) {
        gap = 8;
        color = new Signal<>(initialArgb);
        setHsvFrom(initialArgb);

        SvSquare square = new SvSquare();
        square.prefH = 90;
        HueBar hueBar = new HueBar();
        hueBar.prefH = 12;
        hex = new TextField(hexString(initialArgb));
        hex.px(12).onSubmit(this::parseHex);

        add(square);
        add(hueBar);
        add(hex);

        // external writes to the signal re-derive hsv, our own writes round-trip equal
        colorSync = new Effect(() -> {
            int c = color.get();
            if (c != hsvToArgb(hue, sat, val, alphaByte)) {
                setHsvFrom(c);
                syncHex(c);
            }
        });
    }

    public ColorPicker onChange(Consumer<Integer> c) { onChange = c; return this; }

    private void setHsvFrom(int argb) {
        alphaByte = (argb >>> 24) & 0xFF;
        float r = ((argb >> 16) & 0xFF) / 255f;
        float g = ((argb >> 8) & 0xFF) / 255f;
        float b = (argb & 0xFF) / 255f;
        float max = Math.max(r, Math.max(g, b));
        float min = Math.min(r, Math.min(g, b));
        float delta = max - min;
        val = max;
        sat = max <= 0f ? 0f : delta / max;
        if (delta <= 0f) {
            // keep the previous hue so a black or grey pick does not snap the square
            return;
        }
        float h;
        if (max == r) h = ((g - b) / delta) % 6f;
        else if (max == g) h = (b - r) / delta + 2f;
        else h = (r - g) / delta + 4f;
        hue = (h / 6f + 1f) % 1f;
    }

    private void changed() {
        int c = hsvToArgb(hue, sat, val, alphaByte);
        color.set(c);
        syncHex(c);
        if (onChange != null) onChange.accept(c);
    }

    private void syncHex(int c) {
        if (!hex.focused) hex.value.set(hexString(c));
    }

    private void parseHex(String s) {
        String t = s.trim();
        if (t.startsWith("#")) t = t.substring(1);
        if (t.length() == 6) {
            try {
                int rgb = Integer.parseInt(t, 16);
                setHsvFrom((alphaByte << 24) | rgb);
                changed();
                return;
            } catch (NumberFormatException ignored) {
            }
        }
        // bad input, restore the current value
        hex.value.set(hexString(color.peek()));
    }

    private static String hexString(int argb) {
        return String.format(Locale.ROOT, "#%06X", argb & 0xFFFFFF);
    }

    // rgb channel = v * (1 - s * (1 - hueChannel)), the standard hsv cone
    static int hsvToArgb(float h, float s, float v, int a) {
        float[] rgb = hueRgb(h);
        int r = Math.round(v * (1f - s * (1f - rgb[0])) * 255f);
        int g = Math.round(v * (1f - s * (1f - rgb[1])) * 255f);
        int b = Math.round(v * (1f - s * (1f - rgb[2])) * 255f);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static float[] hueRgb(float h) {
        float x = ((h % 1f) + 1f) % 1f * 6f;
        float r = Math.min(Math.max(Math.abs(x - 3f) - 1f, 0f), 1f);
        float g = Math.min(Math.max(2f - Math.abs(x - 2f), 0f), 1f);
        float b = Math.min(Math.max(2f - Math.abs(x - 4f), 0f), 1f);
        return new float[] {r, g, b};
    }

    @Override
    public void remove() {
        colorSync.dispose();
        super.remove();
    }

    // saturation left to right, value top to bottom, built from vertical strip gradients
    private final class SvSquare extends Widget {

        private static final int STRIPS = 16;

        @Override
        protected boolean interactive() { return true; }

        @Override
        protected float contentWidth() { return 140; }

        @Override
        protected float contentHeight(float forWidth) { return 90; }

        @Override
        protected void drawSelf(UiDraw d, float alpha) {
            int hueC = hsvToArgb(hue, 1f, 1f, 255);
            float sw = w / STRIPS;
            for (int i = 0; i < STRIPS; i++) {
                float t = (i + 0.5f) / STRIPS;
                int top = lerpColor(0xFFFFFFFF, hueC, t);
                d.gradient(x + i * sw, y, sw + 0.5f, h, 0, fade(top, alpha), fade(0xFF000000, alpha));
            }
            d.border(x, y, w, h, 2, 1f, fade(0x30FFFFFF, alpha));
            // selection ring
            float sx = x + sat * w, sy = y + (1f - val) * h;
            d.roundedRect(sx - 4, sy - 4, 8, 8, 4, fade(0xFF000000, alpha));
            d.roundedRect(sx - 3, sy - 3, 6, 6, 3, fade(0xFFFFFFFF, alpha));
        }

        private void set(float mx, float my) {
            sat = Math.min(Math.max((mx - x) / Math.max(w, 1), 0f), 1f);
            val = Math.min(Math.max(1f - (my - y) / Math.max(h, 1), 0f), 1f);
            changed();
        }

        @Override
        public boolean onMouseDown(float mx, float my, int button) {
            set(mx, my);
            return true;
        }

        @Override
        public void onMouseDrag(float mx, float my) {
            set(mx, my);
        }
    }

    // rainbow strip with a draggable knob
    private final class HueBar extends Widget {

        private static final int SEGMENTS = 24;

        @Override
        protected boolean interactive() { return true; }

        @Override
        protected float contentWidth() { return 140; }

        @Override
        protected float contentHeight(float forWidth) { return 12; }

        @Override
        protected void drawSelf(UiDraw d, float alpha) {
            float sw = w / SEGMENTS;
            for (int i = 0; i < SEGMENTS; i++) {
                int c = hsvToArgb((i + 0.5f) / SEGMENTS, 1f, 1f, 255);
                d.rect(x + i * sw, y, sw + 0.5f, h, fade(c, alpha));
            }
            d.border(x, y, w, h, 2, 1f, fade(0x30FFFFFF, alpha));
            float kx = x + hue * w;
            d.roundedRect(kx - 2.5f, y - 1, 5, h + 2, 2.5f, fade(0xFF000000, alpha));
            d.roundedRect(kx - 1.5f, y, 3, h, 1.5f, fade(0xFFFFFFFF, alpha));
        }

        private void set(float mx) {
            hue = Math.min(Math.max((mx - x) / Math.max(w, 1), 0f), 1f);
            changed();
        }

        @Override
        public boolean onMouseDown(float mx, float my, int button) {
            set(mx);
            return true;
        }

        @Override
        public void onMouseDrag(float mx, float my) {
            set(mx);
        }
    }

    // fluent overrides so chains keep the subtype
    @Override public ColorPicker size(float w, float h) { super.size(w, h); return this; }
    @Override public ColorPicker width(float w) { super.width(w); return this; }
    @Override public ColorPicker height(float h) { super.height(h); return this; }
    @Override public ColorPicker grow(float g) { super.grow(g); return this; }
    @Override public ColorPicker anchor(Anchor a) { super.anchor(a); return this; }
    @Override public ColorPicker offset(float dx, float dy) { super.offset(dx, dy); return this; }
    @Override public ColorPicker padding(float p) { super.padding(p); return this; }
    @Override public ColorPicker visible(boolean v) { super.visible(v); return this; }
    @Override public ColorPicker opacity(float o) { super.opacity(o); return this; }
}

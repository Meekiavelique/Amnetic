package com.meekdev.amnetic.client.surface.widget;

import com.meekdev.amnetic.client.surface.Anchor;
import com.meekdev.amnetic.client.surface.draw.UiDraw;
import com.meekdev.amnetic.client.surface.reactive.Signal;

public class SplitPane extends Widget {

    public final Signal<Float> ratio;
    final Widget first, second;
    boolean horizontal = true;
    float minFirst = 40, minSecond = 40;
    int dividerColor = 0x35FFFFFF;
    int dividerActiveColor = 0x80FFFFFF;

    private static final float HIT = 8;

    private final Handle handle = new Handle();

    public SplitPane(Widget first, Widget second) {
        this(first, second, 0.5f);
    }

    public SplitPane(Widget first, Widget second, float initialRatio) {
        this.first = first;
        this.second = second;
        ratio = new Signal<>(Math.min(Math.max(initialRatio, 0f), 1f));
        add(first);
        add(second);
        add(handle);
    }

    public SplitPane horizontal(boolean h) { horizontal = h; return this; }
    public SplitPane minSizes(float firstMin, float secondMin) { minFirst = firstMin; minSecond = secondMin; return this; }
    public SplitPane dividerColors(int idle, int active) { dividerColor = idle; dividerActiveColor = active; return this; }

    @Override
    protected float contentWidth() {
        if (horizontal) return first.measureWidth() + HIT + second.measureWidth();
        return Math.max(first.measureWidth(), second.measureWidth());
    }

    @Override
    protected float contentHeight(float forWidth) {
        if (horizontal) return Math.max(first.measureHeight(forWidth), second.measureHeight(forWidth));
        return first.measureHeight(forWidth) + HIT + second.measureHeight(forWidth);
    }

    private float firstSize(float avail) {
        float lo = Math.min(minFirst, avail);
        float hi = Math.max(lo, avail - minSecond);
        return Math.min(Math.max(ratio.peek() * avail, lo), hi);
    }

    @Override
    protected void placeChildren() {
        float cx = x + padding, cy = y + padding;
        float cw = w - padding * 2, ch = h - padding * 2;
        if (horizontal) {
            float avail = Math.max(cw - HIT, 0);
            float a = firstSize(avail);
            first.layout(cx, cy, a, ch);
            handle.layout(cx + a, cy, HIT, ch);
            second.layout(cx + a + HIT, cy, avail - a, ch);
        } else {
            float avail = Math.max(ch - HIT, 0);
            float a = firstSize(avail);
            first.layout(cx, cy, cw, a);
            handle.layout(cx, cy + a, cw, HIT);
            second.layout(cx, cy + a + HIT, cw, avail - a);
        }
    }

    private void dragTo(float mx, float my) {
        float cx = x + padding, cy = y + padding;
        float cw = w - padding * 2, ch = h - padding * 2;
        float t = horizontal
                ? (mx - cx - HIT * 0.5f) / Math.max(cw - HIT, 1)
                : (my - cy - HIT * 0.5f) / Math.max(ch - HIT, 1);
        ratio.set(Math.min(Math.max(t, 0f), 1f));
    }

    private final class Handle extends Widget {

        @Override
        protected boolean interactive() { return true; }

        @Override
        protected void drawSelf(UiDraw d, float alpha) {
            int c = hovered || pressed ? dividerActiveColor : dividerColor;
            if (horizontal) {
                d.roundedRect(x + w * 0.5f - 1, y + 3, 2, h - 6, 1, fade(c, alpha));
            } else {
                d.roundedRect(x + 3, y + h * 0.5f - 1, w - 6, 2, 1, fade(c, alpha));
            }
        }

        @Override
        public boolean onMouseDown(float mx, float my, int button) {
            return true;
        }

        @Override
        public void onMouseDrag(float mx, float my) {
            dragTo(mx, my);
        }
    }

    @Override public SplitPane size(float w, float h) { super.size(w, h); return this; }
    @Override public SplitPane width(float w) { super.width(w); return this; }
    @Override public SplitPane height(float h) { super.height(h); return this; }
    @Override public SplitPane grow(float g) { super.grow(g); return this; }
    @Override public SplitPane anchor(Anchor a) { super.anchor(a); return this; }
    @Override public SplitPane offset(float dx, float dy) { super.offset(dx, dy); return this; }
    @Override public SplitPane padding(float p) { super.padding(p); return this; }
    @Override public SplitPane visible(boolean v) { super.visible(v); return this; }
    @Override public SplitPane opacity(float o) { super.opacity(o); return this; }
}

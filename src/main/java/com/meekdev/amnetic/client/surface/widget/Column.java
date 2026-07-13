package com.meekdev.amnetic.client.surface.widget;

import com.meekdev.amnetic.client.surface.Anchor;
// vertical flex container: gap, per-child grow shares leftover height
public class Column extends Widget {

    float gap = 6;
    float align = 0f; // cross-axis: 0 start, 0.5 center, 1 end

    public Column gap(float g) { gap = g; return this; }
    public Column alignStart() { align = 0f; return this; }
    public Column alignCenter() { align = 0.5f; return this; }
    public Column alignEnd() { align = 1f; return this; }

    @Override
    protected float contentWidth() {
        float max = 0;
        for (Widget c : children) {
            if (c.visible) max = Math.max(max, c.measureWidth());
        }
        return max;
    }

    @Override
    protected float contentHeight(float forWidth) {
        float total = 0;
        int n = 0;
        for (Widget c : children) {
            if (!c.visible) continue;
            total += c.measureHeight(forWidth);
            n++;
        }
        return total + Math.max(0, n - 1) * gap;
    }

    @Override
    protected void placeChildren() {
        float cx = x + padding, cy = y + padding;
        float cw = w - padding * 2, ch = h - padding * 2;

        float fixed = 0, growSum = 0;
        int n = 0;
        for (Widget c : children) {
            if (!c.visible) continue;
            if (c.grow > 0) growSum += c.grow;
            else fixed += c.measureHeight(cw);
            n++;
        }
        float leftover = Math.max(0, ch - fixed - Math.max(0, n - 1) * gap);

        float penY = cy;
        boolean first = true;
        for (Widget c : children) {
            if (!c.visible) continue;
            if (!first) penY += gap;
            float childH = c.grow > 0 ? leftover * (c.grow / growSum) : c.measureHeight(cw);
            float childW = c.prefW >= 0 ? c.prefW : cw;
            float childX = cx + (cw - childW) * align;
            c.layout(childX, penY, childW, childH);
            penY += childH;
            first = false;
        }
    }

    // fluent overrides so chains keep the subtype
    @Override public Column size(float w, float h) { super.size(w, h); return this; }
    @Override public Column width(float w) { super.width(w); return this; }
    @Override public Column height(float h) { super.height(h); return this; }
    @Override public Column grow(float g) { super.grow(g); return this; }
    @Override public Column anchor(Anchor a) { super.anchor(a); return this; }
    @Override public Column offset(float dx, float dy) { super.offset(dx, dy); return this; }
    @Override public Column padding(float p) { super.padding(p); return this; }
    @Override public Column visible(boolean v) { super.visible(v); return this; }
    @Override public Column opacity(float o) { super.opacity(o); return this; }
}

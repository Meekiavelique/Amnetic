package com.meekdev.amnetic.client.surface.widget;

import com.meekdev.amnetic.client.surface.Anchor;
public class Row extends Widget {

    float gap = 6;
    boolean wrap;
    float align = 0f;

    public Row gap(float g) { gap = g; return this; }
    public Row wrap(boolean w) { wrap = w; return this; }
    public Row alignStart() { align = 0f; return this; }
    public Row alignCenter() { align = 0.5f; return this; }
    public Row alignEnd() { align = 1f; return this; }

    @Override
    protected float contentWidth() {
        float total = 0;
        int n = 0;
        for (Widget c : children) {
            if (!c.visible) continue;
            total += c.measureWidth();
            n++;
        }
        return total + Math.max(0, n - 1) * gap;
    }

    @Override
    protected float contentHeight(float forWidth) {
        float lineH = 0, totalH = 0, lineW = 0;
        boolean first = true;
        for (Widget c : children) {
            if (!c.visible) continue;
            float cw = c.measureWidth();
            float ch = c.measureHeight(cw);
            if (wrap && !first && lineW + gap + cw > forWidth) {
                totalH += lineH + gap;
                lineW = 0; lineH = 0; first = true;
            }
            lineW += (first ? 0 : gap) + cw;
            lineH = Math.max(lineH, ch);
            first = false;
        }
        return totalH + lineH;
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
            else fixed += c.measureWidth();
            n++;
        }
        float leftover = Math.max(0, cw - fixed - Math.max(0, n - 1) * gap);

        float penX = cx, penY = cy, lineH = 0;
        boolean first = true;
        for (Widget c : children) {
            if (!c.visible) continue;
            float childW = c.grow > 0 ? leftover * (c.grow / growSum) : c.measureWidth();
            float childH = c.prefH >= 0 ? c.prefH
                    : c.grow > 0 ? ch
                    : Math.min(c.measureHeight(childW), ch);
            if (wrap && !first && penX + gap + childW > cx + cw) {
                penY += lineH + gap;
                penX = cx; lineH = 0; first = true;
            }
            if (!first) penX += gap;
            float childY = penY + (ch - childH) * (wrap ? 0 : align);
            c.layout(penX, wrap ? penY : childY, childW, childH);
            penX += childW;
            lineH = Math.max(lineH, childH);
            first = false;
        }
    }

    @Override public Row size(float w, float h) { super.size(w, h); return this; }
    @Override public Row width(float w) { super.width(w); return this; }
    @Override public Row height(float h) { super.height(h); return this; }
    @Override public Row grow(float g) { super.grow(g); return this; }
    @Override public Row anchor(Anchor a) { super.anchor(a); return this; }
    @Override public Row offset(float dx, float dy) { super.offset(dx, dy); return this; }
    @Override public Row padding(float p) { super.padding(p); return this; }
    @Override public Row visible(boolean v) { super.visible(v); return this; }
    @Override public Row opacity(float o) { super.opacity(o); return this; }
}

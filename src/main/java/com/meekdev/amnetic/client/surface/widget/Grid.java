package com.meekdev.amnetic.client.surface.widget;

import com.meekdev.amnetic.client.surface.Anchor;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Grid extends Widget {

    final int cols;
    float gap = 6;
    private final Map<Widget, Integer> spans = new HashMap<>();

    public Grid(int cols) {
        this.cols = Math.max(1, cols);
    }

    public Grid gap(float g) { gap = g; return this; }

    public Grid span(Widget child, int colSpan) {
        spans.put(child, colSpan);
        return this;
    }

    public Grid add(Widget child, int colSpan) {
        spans.put(child, colSpan);
        add(child);
        return this;
    }

    private int spanOf(Widget c) {
        Integer s = spans.get(c);
        return s == null ? 1 : Math.min(Math.max(s, 1), cols);
    }

    @Override
    protected float contentWidth() {
        float cell = 0;
        for (Widget c : children) {
            if (!c.visible) continue;
            int s = spanOf(c);
            cell = Math.max(cell, (c.measureWidth() - (s - 1) * gap) / s);
        }
        return cell * cols + gap * (cols - 1);
    }

    @Override
    protected float contentHeight(float forWidth) {
        float cell = (forWidth - gap * (cols - 1)) / cols;
        float total = 0, rowH = 0;
        int col = 0;
        for (Widget c : children) {
            if (!c.visible) continue;
            int s = spanOf(c);
            if (col > 0 && col + s > cols) {
                total += rowH + gap;
                col = 0;
                rowH = 0;
            }
            float cw = cell * s + gap * (s - 1);
            rowH = Math.max(rowH, c.measureHeight(cw));
            col += s;
        }
        return total + rowH;
    }

    @Override
    protected void placeChildren() {
        float cx = x + padding, cy = y + padding;
        float cw = w - padding * 2;
        float cell = (cw - gap * (cols - 1)) / cols;

        List<Widget> rowBuf = new ArrayList<>();
        List<float[]> rowRects = new ArrayList<>();
        float penY = cy, rowH = 0;
        int col = 0;
        for (Widget c : children) {
            if (!c.visible) continue;
            int s = spanOf(c);
            if (col > 0 && col + s > cols) {
                penY = flushRow(rowBuf, rowRects, penY, rowH) + gap;
                col = 0;
                rowH = 0;
            }
            float childX = cx + col * (cell + gap);
            float childW = cell * s + gap * (s - 1);
            rowBuf.add(c);
            rowRects.add(new float[] {childX, childW});
            rowH = Math.max(rowH, c.measureHeight(childW));
            col += s;
        }
        flushRow(rowBuf, rowRects, penY, rowH);
    }

    private float flushRow(List<Widget> rowBuf, List<float[]> rowRects, float penY, float rowH) {
        for (int i = 0; i < rowBuf.size(); i++) {
            float[] r = rowRects.get(i);
            rowBuf.get(i).layout(r[0], penY, r[1], rowH);
        }
        rowBuf.clear();
        rowRects.clear();
        return penY + rowH;
    }

    @Override public Grid size(float w, float h) { super.size(w, h); return this; }
    @Override public Grid width(float w) { super.width(w); return this; }
    @Override public Grid height(float h) { super.height(h); return this; }
    @Override public Grid grow(float g) { super.grow(g); return this; }
    @Override public Grid anchor(Anchor a) { super.anchor(a); return this; }
    @Override public Grid offset(float dx, float dy) { super.offset(dx, dy); return this; }
    @Override public Grid padding(float p) { super.padding(p); return this; }
    @Override public Grid visible(boolean v) { super.visible(v); return this; }
    @Override public Grid opacity(float o) { super.opacity(o); return this; }
}

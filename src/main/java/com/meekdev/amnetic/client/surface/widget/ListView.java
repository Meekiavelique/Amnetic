package com.meekdev.amnetic.client.surface.widget;

import com.meekdev.amnetic.client.surface.Anchor;
import com.meekdev.amnetic.client.surface.draw.UiDraw;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

public class ListView<T> extends Widget {

    List<T> items;
    final float rowHeight;
    final BiFunction<T, Integer, Widget> rowFactory;
    float scrollY;
    int barColor = 0x50FFFFFF;

    private final Map<Integer, Widget> mounted = new HashMap<>();

    public ListView(List<T> items, float rowHeight, BiFunction<T, Integer, Widget> rowFactory) {
        this.items = items;
        this.rowHeight = rowHeight;
        this.rowFactory = rowFactory;
    }

    public ListView<T> items(List<T> newItems) {
        items = newItems;
        for (Widget wgt : mounted.values()) wgt.parent = null;
        mounted.clear();
        children.clear();
        return this;
    }

    @Override
    protected boolean interactive() { return true; }

    @Override
    protected float contentWidth() { return 120; }

    @Override
    protected float contentHeight(float forWidth) { return rowHeight * 4; }

    private float totalH() {
        return items.size() * rowHeight;
    }

    @Override
    protected void placeChildren() {
        float maxScroll = Math.max(0, totalH() - h);
        scrollY = Math.min(Math.max(scrollY, 0), maxScroll);

        int first = Math.max(0, (int) (scrollY / rowHeight));
        int last = Math.min(items.size() - 1, (int) ((scrollY + h) / rowHeight));

        children.clear();
        for (int i = first; i <= last; i++) {
            Widget row = mounted.get(i);
            if (row == null) {
                row = rowFactory.apply(items.get(i), i);
                mounted.put(i, row);
            }
            row.parent = this;
            children.add(row);
            row.layout(x, y + i * rowHeight - scrollY, w - 6, rowHeight);
        }
    }

    @Override
    protected void drawSelf(UiDraw d, float alpha) {
        d.pushClip(x, y, w, h);
    }

    @Override
    public void drawAfterChildren(UiDraw d) {
        d.popClip();
        if (totalH() > h) {
            float track = h - 4;
            float barH = Math.max(track * (h / totalH()), 20);
            float barY = y + 2 + (track - barH) * (scrollY / Math.max(totalH() - h, 1));
            d.roundedRect(x + w - 4, barY, 2.5f, barH, 1.25f, barColor);
        }
    }

    @Override
    public boolean onScroll(float amount) {
        if (totalH() <= h) return false;
        scrollY -= amount * 24;
        return true;
    }

    @Override public ListView<T> size(float w, float h) { super.size(w, h); return this; }
    @Override public ListView<T> width(float w) { super.width(w); return this; }
    @Override public ListView<T> height(float h) { super.height(h); return this; }
    @Override public ListView<T> grow(float g) { super.grow(g); return this; }
    @Override public ListView<T> anchor(Anchor a) { super.anchor(a); return this; }
    @Override public ListView<T> offset(float dx, float dy) { super.offset(dx, dy); return this; }
    @Override public ListView<T> padding(float p) { super.padding(p); return this; }
    @Override public ListView<T> visible(boolean v) { super.visible(v); return this; }
    @Override public ListView<T> opacity(float o) { super.opacity(o); return this; }
}

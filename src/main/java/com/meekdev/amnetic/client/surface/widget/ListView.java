package com.meekdev.amnetic.client.surface.widget;

import com.meekdev.amnetic.client.surface.draw.UiDraw;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

// virtualized fixed-row-height list, only visible rows are mounted so ten thousand
// items cost the same as ten
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

        // mount only the visible window, reuse rows built earlier so state survives
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
}

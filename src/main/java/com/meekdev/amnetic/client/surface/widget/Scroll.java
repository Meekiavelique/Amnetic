package com.meekdev.amnetic.client.surface.widget;

import com.meekdev.amnetic.client.surface.draw.UiDraw;

// vertical scroll container: children flow like a column, overflow clips, wheel scrolls
public class Scroll extends Column {

    float scrollY;
    float contentH;
    int barColor = 0x50FFFFFF;

    public Scroll() {
        padding = 0;
    }

    @Override
    protected boolean interactive() { return true; }

    @Override
    protected void placeChildren() {
        float cx = x + padding, cy = y + padding;
        float cw = w - padding * 2, ch = h - padding * 2;
        contentH = contentHeight(cw - 6); // reserve scrollbar lane
        float maxScroll = Math.max(0, contentH - ch);
        scrollY = Math.min(Math.max(scrollY, 0), maxScroll);

        float penY = cy - scrollY;
        boolean first = true;
        for (Widget c : children) {
            if (!c.visible) continue;
            if (!first) penY += gap;
            float childW = c.prefW >= 0 ? c.prefW : cw - 6;
            float childH = c.measureHeight(childW);
            c.layout(cx, penY, childW, childH);
            penY += childH;
            first = false;
        }
    }

    @Override
    protected void drawSelf(UiDraw d, float alpha) {
        d.pushClip(x, y, w, h);
    }

    @Override
    public void drawAfterChildren(UiDraw d) {
        d.popClip();
        if (contentH > h) {
            float track = h - 4;
            float barH = Math.max(track * (h / contentH), 20);
            float barY = y + 2 + (track - barH) * (scrollY / Math.max(contentH - h, 1));
            d.roundedRect(x + w - 4, barY, 2.5f, barH, 1.25f, barColor);
        }
    }

    @Override
    public boolean onScroll(float amount) {
        if (contentH <= h) return false;
        scrollY -= amount * 24;
        return true;
    }
}

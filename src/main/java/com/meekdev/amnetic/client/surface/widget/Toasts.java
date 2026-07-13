package com.meekdev.amnetic.client.surface.widget;

import com.meekdev.amnetic.client.surface.Anchor;
import com.meekdev.amnetic.client.surface.Fx;

// corner toast manager: entries live in an animated column so survivors glide
// into place when one fades out
public final class Toasts {

    private final Column column = new Column().gap(8);
    private final Anchor corner;
    float width = 220;

    public Toasts(Stack overlayLayer, Anchor corner) {
        this.corner = corner;
        column.anchor(corner).offset(12, 12);
        overlayLayer.add(column);
    }

    public Toasts width(float w) { width = w; return this; }

    // direct access for styling or manual cleanup
    public Column column() {
        return column;
    }

    public Toasts push(Widget content, float seconds) {
        content.animateLayout(true);
        column.add(content);
        Fx.fadeIn(content, 0.25f);
        // slide in from the anchored side, vertical for centered columns
        float dx = corner.fx > 0.5f ? 40 : corner.fx < 0.5f ? -40 : 0;
        float dy = dx == 0 ? (corner.fy > 0.5f ? 24 : -24) : 0;
        if (dx != 0) Fx.spring(v -> content.translate(v, 0), dx, 60f, 9f).target(0f);
        else Fx.spring(v -> content.translate(0, v), dy, 60f, 9f).target(0f);
        Fx.timed(seconds, t -> {
            if (t >= seconds) Fx.fadeOut(content, 0.3f);
        });
        return this;
    }

    public Toasts push(String text, float seconds) {
        Panel p = new Panel().background(0xE81A2029).rounding(8).padding(10)
                .border(1f, 0x30FFFFFF).width(width);
        p.add(new Text(text).px(12).wrap(true));
        return push(p, seconds);
    }

    public void dispose() {
        column.remove();
    }
}

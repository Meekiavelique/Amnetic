package com.meekdev.amnetic.client.surface.widget;

import com.meekdev.amnetic.client.surface.Anchor;
import com.meekdev.amnetic.client.surface.Fx;
import com.meekdev.amnetic.client.surface.reactive.Effect;
import com.meekdev.amnetic.client.surface.reactive.Reactive;

// hover tooltip: a clock-driven poller shows the content in the overlay after a
// short dwell, fades it in and drops it on unhover, dispose the returned effect
// to uninstall
public final class Tooltip {

    private static final float DELAY = 0.5f;

    private Tooltip() {}

    // target must be the widget that actually receives hover (interactive ones do)
    public static Effect install(Widget target, Widget content, Stack overlayLayer) {
        float[] hoverStart = {-1f};
        boolean[] shown = {false};
        return new Effect(() -> {
            float now = Reactive.clock().get();
            if (target.hovered) {
                if (hoverStart[0] < 0) hoverStart[0] = now;
                if (!shown[0] && now - hoverStart[0] >= DELAY) {
                    shown[0] = true;
                    position(target, content, overlayLayer);
                    overlayLayer.add(content);
                    Fx.fadeIn(content, 0.15f);
                } else if (shown[0]) {
                    // keep tracking in case the target moves under the pointer
                    position(target, content, overlayLayer);
                }
            } else {
                hoverStart[0] = -1f;
                if (shown[0]) {
                    shown[0] = false;
                    content.remove();
                }
            }
        });
    }

    public static Effect text(Widget target, String text, Stack overlayLayer) {
        Panel p = new Panel().background(0xF0161B23).rounding(6).padding(6).border(1f, 0x30FFFFFF);
        p.add(new Text(text).px(12));
        return install(target, p, overlayLayer);
    }

    // below-right of the target, clamped to the overlay rect, flips above when clipped
    private static void position(Widget target, Widget content, Stack overlay) {
        float cw = content.measureWidth();
        float ch = content.measureHeight(cw);
        float tx = target.x + 8;
        float ty = target.y + target.h + 6;
        float minX = overlay.x + 4;
        float maxX = overlay.x + overlay.w - cw - 4;
        tx = Math.min(Math.max(tx, minX), Math.max(maxX, minX));
        if (ty + ch > overlay.y + overlay.h - 4) ty = target.y - ch - 6;
        ty = Math.max(ty, overlay.y + 4);
        content.anchor(Anchor.TOP_LEFT)
                .offset(tx - overlay.x - overlay.padding, ty - overlay.y - overlay.padding);
    }
}

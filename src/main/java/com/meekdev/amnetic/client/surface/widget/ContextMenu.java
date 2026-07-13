package com.meekdev.amnetic.client.surface.widget;

import com.meekdev.amnetic.client.surface.Anchor;
import com.meekdev.amnetic.client.surface.Surfaces;
import com.meekdev.amnetic.client.surface.draw.UiDraw;
import com.meekdev.amnetic.client.surface.reactive.Motion;
import com.meekdev.amnetic.client.surface.reactive.Signal;
import com.meekdev.amnetic.client.surface.text.Fonts;
import com.meekdev.amnetic.client.surface.text.SdfFont;
import java.util.List;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// floating right-click menu mounted on an overlay stack
//
// widgets own their input, so the menu is opened from the caller's mouse handler:
// InputRouter passes GLFW button ids straight through to onMouseDown and right
// click is GLFW_MOUSE_BUTTON_RIGHT (1), so inside your widget:
//
//   @Override public boolean onMouseDown(float mx, float my, int button) {
//       if (ContextMenu.onMouseDown(overlay, mx, my, button, items)) return true;
//       return super.onMouseDown(mx, my, button);
//   }
//
// or call ContextMenu.open(overlay, x, y, items) directly to open at any point,
// the menu closes itself on selection or on a click anywhere else
public final class ContextMenu {

    private static final Logger LOG = LoggerFactory.getLogger("Amnetic/Surface");
    private static final float ROW_H = 20;
    private static final float TEXT_PX = 13;

    private ContextMenu() {}

    public record Item(String label, Runnable action, boolean enabled) {
        public Item(String label, Runnable action) {
            this(label, action, true);
        }
    }

    // sugar for the handler pattern above, returns true when it consumed the press
    public static boolean onMouseDown(Stack overlayLayer, float mx, float my, int button, List<Item> items) {
        if (button != GLFW.GLFW_MOUSE_BUTTON_RIGHT) return false;
        open(overlayLayer, mx, my, items);
        return true;
    }

    // opens a menu at the given gui-scaled point, clamped to the overlay bounds
    public static void open(Stack overlayLayer, float x, float y, List<Item> items) {
        if (overlayLayer == null) {
            LOG.warn("context menu has no overlay layer, menu skipped");
            return;
        }
        if (items.isEmpty()) return;
        float pad = overlayLayer.padding;
        float ox = overlayLayer.x + pad, oy = overlayLayer.y + pad;
        float ow = overlayLayer.w - pad * 2, oh = overlayLayer.h - pad * 2;

        float widest = 0;
        for (Item item : items) widest = Math.max(widest, labelWidth(item.label()));
        float pw = Math.min(Math.max(widest + 24, 90), ow);
        float ph = Math.min(items.size() * ROW_H + 8, oh);
        float px = Math.min(Math.max(x, ox), ox + ow - pw);
        float py = Math.min(Math.max(y, oy), oy + oh - ph);

        // the catcher closes both, built before the panel so the array indirection
        // lets the close closure see them
        Widget[] parts = new Widget[2];
        Runnable close = () -> {
            for (Widget part : parts) {
                if (part != null) part.remove();
            }
        };

        Dropdown.PopPanel panel = new Dropdown.PopPanel();
        panel.background(0xF0161B22).rounding(8).border(1, 0x30FFFFFF).shadow(14)
                .padding(4).clipContent(true)
                .anchor(Anchor.TOP_LEFT).offset(px - ox, py - oy).size(pw, ph);
        Column col = new Column().gap(0);
        for (Item item : items) col.add(new MenuRow(item, close));
        panel.add(col);

        parts[0] = new Dropdown.Catcher(close);
        parts[1] = panel;
        overlayLayer.add(parts[0]);
        overlayLayer.add(parts[1]);
    }

    private static float labelWidth(String s) {
        Identifier id = Surfaces.defaultFont();
        SdfFont f = id == null ? null : Fonts.get(id);
        return f == null ? 60 : f.width(s, TEXT_PX);
    }

    // one menu entry, disabled rows are greyed and swallow the click without closing
    private static final class MenuRow extends Widget {

        private final Item item;
        private final Runnable close;
        private final Signal<Float> hoverTarget = new Signal<>(0f);
        private final Motion<Float> hoverT = Motion.spring(0f, 60f, 9f);

        MenuRow(Item item, Runnable close) {
            this.item = item;
            this.close = close;
            prefH = ROW_H;
            hoverT.follow(hoverTarget);
        }

        @Override
        protected boolean interactive() { return true; }

        @Override
        protected void drawSelf(UiDraw d, float alpha) {
            hoverTarget.set(hovered && item.enabled() ? 1f : 0f);
            float t = hoverT.value().peek();
            if (t > 0.01f) d.roundedRect(x, y, w, h, 4, fade(0x20FFFFFF, alpha * t));
            Identifier fid = Surfaces.defaultFont();
            if (fid == null) return;
            Identifier prev = d.currentFont();
            d.font(fid);
            d.textLeftCentered(item.label(), x + 8, y + h * 0.5f, TEXT_PX,
                    fade(item.enabled() ? 0xFFFFFFFF : 0x50FFFFFF, alpha));
            if (prev != null) d.font(prev);
        }

        @Override
        public boolean onMouseDown(float mx, float my, int button) {
            return true;
        }

        @Override
        public void onMouseUp(float mx, float my, int button) {
            if (mx < x || my < y || mx > x + w || my > y + h) return;
            if (!item.enabled()) return;
            if (item.action() != null) item.action().run();
            close.run();
        }

        @Override
        public void remove() {
            hoverT.dispose();
            super.remove();
        }
    }
}

package com.meekdev.amnetic.client.surface.widget;

import com.meekdev.amnetic.client.surface.Anchor;
import com.meekdev.amnetic.client.surface.Surfaces;
import com.meekdev.amnetic.client.surface.draw.UiDraw;
import com.meekdev.amnetic.client.surface.reactive.Motion;
import com.meekdev.amnetic.client.surface.reactive.Signal;
import com.meekdev.amnetic.client.surface.text.Fonts;
import com.meekdev.amnetic.client.surface.text.SdfFont;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// closed state shows the current option plus a chevron, clicking opens a popup of
// option rows mounted on an overlay stack so it floats above everything else
public class Dropdown extends Widget {

    private static final Logger LOG = LoggerFactory.getLogger("Amnetic/Surface");

    public final Signal<Integer> selected;
    List<String> options;
    Consumer<Integer> onChange;
    Stack overlay;
    float px = 13;
    float rounding = 7;
    int background = 0xFF232A33;
    int hoverBackground = 0xFF3A4654;
    int popupBackground = 0xF0161B22;
    int textColor = 0xFFFFFFFF;
    int accent = 0xFF4C8FDD;

    private final Signal<Float> hoverTarget = new Signal<>(0f);
    private final Motion<Float> hoverT = Motion.spring(0f, 60f, 9f);
    private final Signal<Float> openTarget = new Signal<>(0f);
    private final Motion<Float> openT = Motion.spring(0f, 60f, 9f);

    private Widget catcher, popup;

    public Dropdown(List<String> options, int initialIndex) {
        this.options = options;
        this.selected = new Signal<>(Math.min(Math.max(initialIndex, 0), Math.max(options.size() - 1, 0)));
        hoverT.follow(hoverTarget);
        openT.follow(openTarget);
        prefH = 24;
    }

    public Dropdown(Stack overlayLayer, List<String> options, int initialIndex) {
        this(options, initialIndex);
        this.overlay = overlayLayer;
    }

    // the popup needs somewhere above the rest of the tree to live
    public Dropdown rounding(float r) { rounding = r; return this; }
    public Dropdown accent(int argb) { accent = argb; return this; }

    public Dropdown attach(Stack overlayLayer) { overlay = overlayLayer; return this; }
    public Dropdown onChange(Consumer<Integer> c) { onChange = c; return this; }
    public Dropdown px(float p) { px = p; return this; }
    public Dropdown colors(int normal, int hover) { background = normal; hoverBackground = hover; return this; }

    public boolean isOpen() { return popup != null; }

    @Override
    protected boolean interactive() { return true; }

    @Override
    protected float contentWidth() {
        float widest = 0;
        for (String opt : options) widest = Math.max(widest, labelWidth(opt));
        return widest + 34;
    }

    @Override
    protected float contentHeight(float forWidth) { return 24; }

    private float labelWidth(String s) {
        Identifier id = Surfaces.defaultFont();
        SdfFont f = id == null ? null : Fonts.get(id);
        return f == null ? 60 : f.width(s, px);
    }

    @Override
    protected void drawSelf(UiDraw d, float alpha) {
        hoverTarget.set(hovered ? 1f : 0f);
        float t = hoverT.value().peek();
        int bg = lerpColor(background, hoverBackground, t);
        if (pressed) bg = lerpColor(bg, 0xFF000000, 0.25f);
        d.roundedRect(x, y, w, h, rounding, fade(bg, alpha));
        d.border(x, y, w, h, rounding, 1f, fade(popup != null ? accent & 0x80FFFFFF | 0x80000000 : 0x30FFFFFF, alpha));

        Identifier fid = Surfaces.defaultFont();
        if (fid != null) {
            Identifier prev = d.currentFont();
            d.font(fid);
            int idx = selected.peek();
            String label = options.isEmpty() ? "" : options.get(Math.min(idx, options.size() - 1));
            d.pushClip(x + 6, y, w - 26, h);
            d.textLeftCentered(label, x + 8, y + h * 0.5f, px, fade(textColor, alpha));
            d.popClip();
            if (prev != null) d.font(prev);
        }
        // chevron flips upward as the popup opens
        drawChevron(d, x + w - 12, y + h * 0.5f, openT.value().peek() * (float) Math.PI, fade(0xB0FFFFFF, alpha));
    }

    // small v built from two rotated bars, extraRotation spins the whole glyph
    static void drawChevron(UiDraw d, float cx, float cy, float extraRotation, int argb) {
        d.pushTransform(cx, cy, 0, 0, 1, extraRotation);
        d.pushTransform(cx - 2f, cy, 0, 0, 1, 0.7854f);
        d.rect(cx - 2f - 2.6f, cy - 0.75f, 5.2f, 1.5f, argb);
        d.popTransform();
        d.pushTransform(cx + 2f, cy, 0, 0, 1, -0.7854f);
        d.rect(cx + 2f - 2.6f, cy - 0.75f, 5.2f, 1.5f, argb);
        d.popTransform();
        d.popTransform();
    }

    @Override
    public boolean onMouseDown(float mx, float my, int button) {
        return true;
    }

    @Override
    public void onMouseUp(float mx, float my, int button) {
        if (mx < x || my < y || mx > x + w || my > y + h) return;
        if (popup != null) close();
        else open();
    }

    private void open() {
        if (overlay == null) {
            LOG.warn("dropdown has no overlay attached, popup skipped");
            return;
        }
        if (options.isEmpty()) return;
        float pad = overlay.padding;
        float ox = overlay.x + pad, oy = overlay.y + pad;
        float ow = overlay.w - pad * 2, oh = overlay.h - pad * 2;

        float rowH = px + 9;
        float widest = 0;
        for (String opt : options) widest = Math.max(widest, labelWidth(opt));
        float pw = Math.min(Math.max(w, widest + 24), ow);
        float ph = Math.min(options.size() * rowH + 8, oh);

        // below the widget, flip above when there is no room, clamp to the surface
        float popX = Math.min(Math.max(x, ox), ox + ow - pw);
        float popY = y + h + 2;
        if (popY + ph > oy + oh) popY = y - ph - 2;
        popY = Math.min(Math.max(popY, oy), oy + oh - ph);

        PopPanel panel = new PopPanel();
        panel.background(popupBackground).rounding(8).border(1, 0x30FFFFFF).shadow(14)
                .padding(4).clipContent(true)
                .anchor(Anchor.TOP_LEFT).offset(popX - ox, popY - oy).size(pw, ph);
        Column col = new Column().gap(0);
        for (int i = 0; i < options.size(); i++) {
            col.add(new OptionRow(i, rowH));
        }
        panel.add(col);

        catcher = new Catcher(this::close);
        popup = panel;
        overlay.add(catcher);
        overlay.add(popup);
        openTarget.set(1f);
    }

    private void close() {
        if (catcher != null) { catcher.remove(); catcher = null; }
        if (popup != null) { popup.remove(); popup = null; }
        openTarget.set(0f);
    }

    private void select(int index) {
        selected.set(index);
        if (onChange != null) onChange.accept(index);
        close();
    }

    @Override
    public void remove() {
        close();
        hoverT.dispose();
        openT.dispose();
        super.remove();
    }

    // one popup entry, hover glides like a button
    private final class OptionRow extends Widget {

        private final int index;
        private final Signal<Float> rowHoverTarget = new Signal<>(0f);
        private final Motion<Float> rowHoverT = Motion.spring(0f, 60f, 9f);

        OptionRow(int index, float rowH) {
            this.index = index;
            prefH = rowH;
            rowHoverT.follow(rowHoverTarget);
        }

        @Override
        protected boolean interactive() { return true; }

        @Override
        protected void drawSelf(UiDraw d, float alpha) {
            rowHoverTarget.set(hovered ? 1f : 0f);
            float t = rowHoverT.value().peek();
            if (t > 0.01f) d.roundedRect(x, y, w, h, 4, fade(0x20FFFFFF, alpha * t));
            Identifier fid = Surfaces.defaultFont();
            if (fid == null) return;
            Identifier prev = d.currentFont();
            d.font(fid);
            boolean isSelected = selected.peek() == index;
            d.textLeftCentered(options.get(index), x + 8, y + h * 0.5f, px,
                    fade(isSelected ? accent : textColor, alpha));
            if (prev != null) d.font(prev);
        }

        @Override
        public boolean onMouseDown(float mx, float my, int button) {
            return true;
        }

        @Override
        public void onMouseUp(float mx, float my, int button) {
            if (mx >= x && my >= y && mx <= x + w && my <= y + h) select(index);
        }

        @Override
        public void remove() {
            rowHoverT.dispose();
            super.remove();
        }
    }

    // invisible full-overlay pane behind the popup, any click on it closes
    static final class Catcher extends Widget {

        private final Runnable onDown;

        Catcher(Runnable onDown) {
            this.onDown = onDown;
        }

        @Override
        protected boolean interactive() { return true; }

        @Override
        public boolean onMouseDown(float mx, float my, int button) {
            onDown.run();
            return true;
        }
    }

    // popup chrome with the house pop-in: a spring on the scale channel
    static final class PopPanel extends Panel {

        private final Motion<Float> pop = Motion.spring(0.85f, 60f, 9f);

        PopPanel() {
            scaleChannel(0.85f);
            pop.target(1f);
        }

        @Override
        protected void drawSelf(UiDraw d, float alpha) {
            scaleChannel(pop.value().peek());
            super.drawSelf(d, alpha);
        }

        @Override
        public void remove() {
            pop.dispose();
            super.remove();
        }
    }

    // fluent overrides so chains keep the subtype
    @Override public Dropdown size(float w, float h) { super.size(w, h); return this; }
    @Override public Dropdown width(float w) { super.width(w); return this; }
    @Override public Dropdown height(float h) { super.height(h); return this; }
    @Override public Dropdown grow(float g) { super.grow(g); return this; }
    @Override public Dropdown anchor(Anchor a) { super.anchor(a); return this; }
    @Override public Dropdown offset(float dx, float dy) { super.offset(dx, dy); return this; }
    @Override public Dropdown padding(float p) { super.padding(p); return this; }
    @Override public Dropdown visible(boolean v) { super.visible(v); return this; }
    @Override public Dropdown opacity(float o) { super.opacity(o); return this; }
}

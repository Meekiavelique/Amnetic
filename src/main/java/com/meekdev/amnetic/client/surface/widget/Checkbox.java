package com.meekdev.amnetic.client.surface.widget;

import com.meekdev.amnetic.client.surface.Anchor;
import com.meekdev.amnetic.client.surface.Surfaces;
import com.meekdev.amnetic.client.surface.draw.UiDraw;
import com.meekdev.amnetic.client.surface.reactive.Motion;
import com.meekdev.amnetic.client.surface.reactive.Signal;
import com.meekdev.amnetic.client.surface.text.Fonts;
import com.meekdev.amnetic.client.surface.text.SdfFont;
import java.util.function.Consumer;
import net.minecraft.resources.Identifier;

// checkable box, the inner mark springs scale 0 to 1 so it pops in
public class Checkbox extends Widget {

    public final Signal<Boolean> value;
    String label;
    float box = 16;
    float px = 13;
    int boxColor = 0xFF2A2F36;
    int checkedColor = 0xFF4C8FDD;
    int textColor = 0xFFFFFFFF;
    Consumer<Boolean> onChange;

    private final Signal<Float> target = new Signal<>(0f);
    private final Motion<Float> check = Motion.spring(0f, 60f, 9f);

    public Checkbox(boolean initial) {
        this(initial, null);
    }

    public Checkbox(boolean initial, String label) {
        value = new Signal<>(initial);
        this.label = label;
        target.set(initial ? 1f : 0f);
        check.follow(target);
    }

    public Checkbox label(String l) { label = l; return this; }
    public Checkbox px(float p) { px = p; return this; }
    public Checkbox colors(int box, int checked) { boxColor = box; checkedColor = checked; return this; }
    public Checkbox textColor(int argb) { textColor = argb; return this; }
    public Checkbox onChange(Consumer<Boolean> c) { onChange = c; return this; }

    @Override
    protected boolean interactive() { return true; }

    private SdfFont font() {
        Identifier id = Surfaces.defaultFont();
        return id == null ? null : Fonts.get(id);
    }

    @Override
    protected float contentWidth() {
        if (label == null || label.isEmpty()) return box;
        SdfFont f = font();
        return box + 6 + (f == null ? 60 : f.width(label, px));
    }

    @Override
    protected float contentHeight(float forWidth) {
        return box;
    }

    @Override
    protected void drawSelf(UiDraw d, float alpha) {
        float t = check.value().peek();
        float by = y + (h - box) * 0.5f;
        int bg = lerpColor(boxColor, checkedColor, Math.min(Math.max(t, 0f), 1f));
        d.roundedRect(x, by, box, box, 4, fade(bg, alpha));
        d.border(x, by, box, box, 4, 1f, fade(0x30FFFFFF, alpha));
        // the mark is a rect scaling out from the box center, overshoot reads as a bounce
        float inner = (box - 8) * t;
        if (inner > 0.5f) {
            float cx = x + box * 0.5f, cy = by + box * 0.5f;
            d.roundedRect(cx - inner * 0.5f, cy - inner * 0.5f, inner, inner, 2, fade(0xFFFFFFFF, alpha));
        }
        if (label != null && !label.isEmpty()) {
            Identifier prev = d.currentFont();
            d.font(Surfaces.defaultFont());
            d.textLeftCentered(label, x + box + 6, y + h * 0.5f, px, fade(textColor, alpha));
            if (prev != null) d.font(prev);
        }
    }

    @Override
    public boolean onMouseDown(float mx, float my, int button) {
        return true;
    }

    @Override
    public void onMouseUp(float mx, float my, int button) {
        if (mx < x || my < y || mx > x + w || my > y + h) return;
        boolean next = !value.peek();
        value.set(next);
        target.set(next ? 1f : 0f);
        if (onChange != null) onChange.accept(next);
    }

    @Override
    public void remove() {
        check.dispose();
        super.remove();
    }

    // fluent overrides so chains keep the subtype
    @Override public Checkbox size(float w, float h) { super.size(w, h); return this; }
    @Override public Checkbox width(float w) { super.width(w); return this; }
    @Override public Checkbox height(float h) { super.height(h); return this; }
    @Override public Checkbox grow(float g) { super.grow(g); return this; }
    @Override public Checkbox anchor(Anchor a) { super.anchor(a); return this; }
    @Override public Checkbox offset(float dx, float dy) { super.offset(dx, dy); return this; }
    @Override public Checkbox padding(float p) { super.padding(p); return this; }
    @Override public Checkbox visible(boolean v) { super.visible(v); return this; }
    @Override public Checkbox opacity(float o) { super.opacity(o); return this; }
}

package com.meekdev.amnetic.client.surface.widget;

import com.meekdev.amnetic.client.surface.Surfaces;
import com.meekdev.amnetic.client.surface.draw.UiDraw;
import com.meekdev.amnetic.client.surface.reactive.Effect;
import com.meekdev.amnetic.client.surface.reactive.Signal;
import com.meekdev.amnetic.client.surface.text.Fonts;
import com.meekdev.amnetic.client.surface.text.SdfFont;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.resources.Identifier;

// sdf text with greedy word wrap, value can be bound to a signal
public class Text extends Widget {

    String value;
    float px = 14;
    int color = 0xFFFFFFFF;
    float align = 0f; // 0 left, 0.5 center, 1 right
    boolean wrap;
    Identifier fontId;
    Effect binding;

    // wrap cache
    private final List<String> lines = new ArrayList<>(1);
    private float linesForWidth = -1;
    private String linesForValue;

    public Text(String value) {
        this.value = value;
    }

    public Text(Signal<String> source) {
        this.value = source.peek();
        binding = new Effect(() -> { value = source.get(); linesForWidth = -1; });
    }

    public Text text(String v) { value = v; linesForWidth = -1; return this; }
    public Text px(float p) { px = p; linesForWidth = -1; return this; }
    public Text color(int argb) { color = argb; return this; }
    public Text center() { align = 0.5f; return this; }
    public Text right() { align = 1f; return this; }
    public Text wrap(boolean w) { wrap = w; linesForWidth = -1; return this; }
    public Text font(Identifier id) { fontId = id; linesForWidth = -1; return this; }

    private SdfFont font() {
        Identifier id = fontId != null ? fontId : Surfaces.defaultFont();
        return id == null ? null : Fonts.get(id);
    }

    private float lineHeight(SdfFont f) {
        return f.ascentPx() * (px / f.bakePx()) * 1.35f;
    }

    @Override
    protected float contentWidth() {
        SdfFont f = font();
        if (f == null || value == null) return 0;
        return f.width(value, px);
    }

    @Override
    protected float contentHeight(float forWidth) {
        SdfFont f = font();
        if (f == null || value == null) return px;
        if (!wrap) return lineHeight(f);
        wrapLines(f, forWidth);
        return Math.max(1, lines.size()) * lineHeight(f);
    }

    private void wrapLines(SdfFont f, float maxW) {
        if (linesForWidth == maxW && value.equals(linesForValue)) return;
        linesForWidth = maxW;
        linesForValue = value;
        lines.clear();
        for (String hard : value.split("\n", -1)) {
            StringBuilder line = new StringBuilder();
            for (String word : hard.split(" ")) {
                String candidate = line.isEmpty() ? word : line + " " + word;
                if (!line.isEmpty() && f.width(candidate, px) > maxW) {
                    lines.add(line.toString());
                    line = new StringBuilder(word);
                } else {
                    line = new StringBuilder(candidate);
                }
            }
            lines.add(line.toString());
        }
    }

    @Override
    protected void drawSelf(UiDraw d, float alpha) {
        SdfFont f = font();
        if (f == null || value == null) return;
        Identifier prev = d.currentFont();
        d.font(fontId != null ? fontId : Surfaces.defaultFont());
        int c = fade(color, alpha);
        if (!wrap) {
            float tx = x + (w - f.width(value, px)) * align;
            d.text(value, tx, y, px, c);
        } else {
            wrapLines(f, w);
            float lh = lineHeight(f);
            float penY = y;
            for (String line : lines) {
                float tx = x + (w - f.width(line, px)) * align;
                d.text(line, tx, penY, px, c);
                penY += lh;
            }
        }
        if (prev != null) d.font(prev);
    }

    @Override
    public void remove() {
        if (binding != null) binding.dispose();
        super.remove();
    }
}

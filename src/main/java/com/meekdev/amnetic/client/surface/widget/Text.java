package com.meekdev.amnetic.client.surface.widget;

import com.meekdev.amnetic.client.surface.Anchor;
import com.meekdev.amnetic.client.surface.Surfaces;
import com.meekdev.amnetic.client.surface.draw.UiDraw;
import com.meekdev.amnetic.client.surface.reactive.Effect;
import com.meekdev.amnetic.client.surface.reactive.Reactive;
import com.meekdev.amnetic.client.surface.reactive.Signal;
import com.meekdev.amnetic.client.surface.text.Fonts;
import com.meekdev.amnetic.client.surface.text.GlyphFx;
import com.meekdev.amnetic.client.surface.text.GlyphPose;
import com.meekdev.amnetic.client.surface.text.SdfFont;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.resources.Identifier;

public class Text extends Widget {

    String value;
    float px = 14;
    int color = 0xFFFFFFFF;
    float align = 0f;
    boolean wrap;
    boolean ellipsis;
    Identifier fontId;
    Effect binding;

    private record Layer(float dx, float dy, float edgeOffset, float softness, int color) {}
    private List<Layer> layers;

    GlyphFx fx;
    private final GlyphPose pose = new GlyphPose();

    private final List<String> lines = new ArrayList<>(1);
    private float linesForWidth = -1;
    private String linesForValue;

    private String ellipsized;
    private float ellipsisForWidth = -1;
    private String ellipsisForValue;

    public Text(String value) {
        this.value = value;
    }

    public Text(Signal<String> source) {
        this.value = source.peek();
        binding = new Effect(() -> { value = source.get(); invalidate(); });
    }

    private void invalidate() {
        linesForWidth = -1;
        ellipsisForWidth = -1;
    }

    public Text text(String v) { value = v; invalidate(); return this; }
    public Text px(float p) { px = p; invalidate(); return this; }
    public Text color(int argb) { color = argb; return this; }
    public Text center() { align = 0.5f; return this; }
    public Text right() { align = 1f; return this; }
    public Text wrap(boolean w) { wrap = w; invalidate(); return this; }
    public Text font(Identifier id) { fontId = id; invalidate(); return this; }

    public Text ellipsis(boolean e) { ellipsis = e; ellipsisForWidth = -1; return this; }

    public Text glyphFx(GlyphFx fx) { this.fx = fx; return this; }

    public Text outline(float width, int color) { return layer(0, 0, width, 0, color); }
    public Text glow(float radius, int color) { return layer(0, 0, 0, radius, color); }
    public Text textShadow(float dx, float dy, int color) { return layer(dx, dy, 0, 0, color); }

    public Text layer(float dx, float dy, float edgeOffset, float softness, int color) {
        if (layers == null) layers = new ArrayList<>(1);
        layers.add(new Layer(dx, dy, edgeOffset, softness, color));
        return this;
    }

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

    private String displayed(SdfFont f) {
        if (!ellipsis || wrap) return value;
        if (ellipsisForWidth == w && value.equals(ellipsisForValue)) return ellipsized;
        ellipsisForWidth = w;
        ellipsisForValue = value;
        if (f.width(value, px) <= w) {
            ellipsized = value;
            return ellipsized;
        }
        float dots = f.width("...", px);
        float scale = px / f.bakePx();
        StringBuilder sb = new StringBuilder();
        float pen = 0;
        int prev = -1;
        for (int i = 0; i < value.length(); ) {
            int cp = value.codePointAt(i);
            int n = Character.charCount(cp);
            SdfFont.Glyph g = f.glyph(cp);
            float adv = (prev != -1 ? f.kern(prev, cp) * scale : 0)
                    + (g == null ? 0 : g.advance() * scale);
            if (pen + adv + dots > w) break;
            pen += adv;
            sb.append(value, i, i + n);
            i += n;
            prev = cp;
        }
        ellipsized = sb + "...";
        return ellipsized;
    }

    @Override
    protected void drawSelf(UiDraw d, float alpha) {
        SdfFont f = font();
        if (f == null || value == null) return;
        Identifier prev = d.currentFont();
        d.font(fontId != null ? fontId : Surfaces.defaultFont());

        List<String> ls;
        if (wrap) {
            wrapLines(f, w);
            ls = lines;
        } else {
            ls = List.of(displayed(f));
        }

        if (layers != null) {
            for (Layer l : layers) {
                drawPass(d, f, ls, l.dx(), l.dy(), l.edgeOffset(), l.softness(), fade(l.color(), alpha));
            }
        }
        drawPass(d, f, ls, 0, 0, 0, 0, fade(color, alpha));

        if (prev != null) d.font(prev);
    }

    private void drawPass(UiDraw d, SdfFont f, List<String> ls,
                          float odx, float ody, float edgeOffset, float softness, int c) {
        float scale = px / f.bakePx();
        float ascent = f.ascentPx() * scale;
        float lh = lineHeight(f);
        float time = fx == null ? 0f : Reactive.clock().peek();
        float penY = y;
        int gi = 0;
        for (String line : ls) {
            float tx = x + (w - f.width(line, px)) * align + odx;
            float baseline = penY + ascent + ody;
            if (fx == null) {
                d.textStyled(line, tx, baseline, px, c, edgeOffset, softness);
            } else {
                gi = emitFxLine(d, f, line, tx, baseline, c, edgeOffset, softness, time, gi);
            }
            penY += lh;
        }
    }

    private int emitFxLine(UiDraw d, SdfFont f, String s, float x, float baselineY,
                           int c, float edgeOffset, float softness, float time, int glyphIndex) {
        float scale = px / f.bakePx();
        float pen = x;
        int prev = -1;
        for (int i = 0; i < s.length(); ) {
            int cp = s.codePointAt(i);
            i += Character.charCount(cp);
            if (prev != -1) pen += f.kern(prev, cp) * scale;
            pose.reset();
            fx.apply(glyphIndex, time, pose);
            float a = Math.min(Math.max(pose.alpha, 0f), 1f);
            int gc = a >= 1f ? c : fade(c, a);
            boolean scaled = pose.scale != 1f;
            float gx = pen + pose.dx, gy = baselineY + pose.dy;
            if (scaled) {
                SdfFont.Glyph g = f.glyph(cp);
                float adv = g == null ? 0 : g.advance() * scale;
                d.pushTransform(gx + adv * 0.5f, gy - f.capPx() * scale * 0.5f, 0, 0, pose.scale, 0);
            }
            float adv = d.glyph(cp, gx, gy, px, gc, edgeOffset, softness);
            if (scaled) d.popTransform();
            pen += adv;
            glyphIndex++;
            prev = cp;
        }
        return glyphIndex;
    }

    @Override
    public void remove() {
        if (binding != null) binding.dispose();
        super.remove();
    }

    @Override public Text size(float w, float h) { super.size(w, h); return this; }
    @Override public Text width(float w) { super.width(w); return this; }
    @Override public Text height(float h) { super.height(h); return this; }
    @Override public Text grow(float g) { super.grow(g); return this; }
    @Override public Text anchor(Anchor a) { super.anchor(a); return this; }
    @Override public Text offset(float dx, float dy) { super.offset(dx, dy); return this; }
    @Override public Text padding(float p) { super.padding(p); return this; }
    @Override public Text visible(boolean v) { super.visible(v); return this; }
    @Override public Text opacity(float o) { super.opacity(o); return this; }
}

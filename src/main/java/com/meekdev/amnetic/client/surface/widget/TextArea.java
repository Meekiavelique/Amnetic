package com.meekdev.amnetic.client.surface.widget;

import com.meekdev.amnetic.client.surface.Anchor;
import com.meekdev.amnetic.client.surface.Surfaces;
import com.meekdev.amnetic.client.surface.draw.UiDraw;
import com.meekdev.amnetic.client.surface.reactive.Reactive;
import com.meekdev.amnetic.client.surface.reactive.Signal;
import com.meekdev.amnetic.client.surface.text.Fonts;
import com.meekdev.amnetic.client.surface.text.SdfFont;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

public class TextArea extends Widget {

    public final Signal<String> value;
    String placeholder = "";
    float px = 13;
    int background = 0xFF11151B;
    int textColor = 0xFFFFFFFF;
    int placeholderColor = 0x60FFFFFF;
    float rounding = 7;

    private int caret;
    private float scrollY;
    private float preferredX = -1;

    private final List<int[]> lineRanges = new ArrayList<>();
    private float rangesForWidth = -1;
    private String rangesForValue;

    public TextArea(String initial) {
        value = new Signal<>(initial);
        caret = initial.length();
        prefH = 80;
    }

    public TextArea placeholder(String p) { placeholder = p; return this; }
    public TextArea px(float p) { px = p; rangesForWidth = -1; return this; }

    @Override
    protected boolean interactive() { return true; }

    @Override
    protected boolean focusable() { return true; }

    @Override
    protected float contentWidth() { return 160; }

    @Override
    protected float contentHeight(float forWidth) { return 80; }

    private SdfFont font() {
        Identifier fid = Surfaces.defaultFont();
        return fid == null ? null : Fonts.get(fid);
    }

    private float lineHeight(SdfFont f) {
        return f.ascentPx() * (px / f.bakePx()) * 1.35f;
    }

    private float textX() { return x + 8; }
    private float textY() { return y + 6; }
    private float viewH() { return h - 12; }

    private float advance(SdfFont f, int prev, int cp, float scale) {
        SdfFont.Glyph g = f.glyph(cp);
        return (prev != -1 ? f.kern(prev, cp) * scale : 0)
                + (g == null ? 0 : g.advance() * scale);
    }

    private void rewrap(SdfFont f, float maxW) {
        String s = value.peek();
        if (rangesForWidth == maxW && s.equals(rangesForValue)) return;
        rangesForWidth = maxW;
        rangesForValue = s;
        lineRanges.clear();
        float scale = px / f.bakePx();
        int lineStart = 0, lastSpace = -1, prev = -1;
        float pen = 0;
        int i = 0;
        while (i < s.length()) {
            int cp = s.codePointAt(i);
            int n = Character.charCount(cp);
            if (cp == '\n') {
                lineRanges.add(new int[]{lineStart, i});
                i += n;
                lineStart = i;
                pen = 0; prev = -1; lastSpace = -1;
                continue;
            }
            float adv = advance(f, prev, cp, scale);
            if (pen + adv > maxW && i > lineStart) {
                if (lastSpace > lineStart) {
                    lineRanges.add(new int[]{lineStart, lastSpace});
                    i = lastSpace + 1;
                } else {
                    lineRanges.add(new int[]{lineStart, i});
                }
                lineStart = i;
                pen = 0; prev = -1; lastSpace = -1;
                continue;
            }
            if (cp == ' ') lastSpace = i;
            pen += adv;
            prev = cp;
            i += n;
        }
        lineRanges.add(new int[]{lineStart, s.length()});
    }

    private int caretLine() {
        for (int li = 0; li < lineRanges.size(); li++) {
            if (caret <= lineRanges.get(li)[1]) return li;
        }
        return lineRanges.size() - 1;
    }

    private int indexInLine(SdfFont f, int li, float targetX) {
        int[] range = lineRanges.get(li);
        String s = value.peek();
        float scale = px / f.bakePx();
        float pen = 0;
        int prev = -1;
        for (int i = range[0]; i < range[1]; ) {
            int cp = s.codePointAt(i);
            float adv = advance(f, prev, cp, scale);
            if (targetX < pen + adv * 0.5f) return i;
            pen += adv;
            i += Character.charCount(cp);
            prev = cp;
        }
        return range[1];
    }

    private float caretX(SdfFont f) {
        int[] range = lineRanges.get(caretLine());
        return f.width(value.peek().substring(range[0], Math.max(range[0], caret)), px);
    }

    private void clampScroll(SdfFont f) {
        float content = lineRanges.size() * lineHeight(f);
        scrollY = Math.min(Math.max(scrollY, 0), Math.max(0, content - viewH()));
    }

    private void ensureCaretVisible(SdfFont f) {
        rewrap(f, w - 16);
        float lh = lineHeight(f);
        float top = caretLine() * lh;
        if (top < scrollY) scrollY = top;
        if (top + lh > scrollY + viewH()) scrollY = top + lh - viewH();
        clampScroll(f);
    }

    @Override
    protected void drawSelf(UiDraw d, float alpha) {
        d.roundedRect(x, y, w, h, rounding, fade(background, alpha));
        d.border(x, y, w, h, rounding, 1f, fade(focused ? 0x804C8FDD : 0x30FFFFFF, alpha));

        SdfFont f = font();
        if (f == null) return;
        d.font(Surfaces.defaultFont());
        String s = value.peek();
        rewrap(f, w - 16);
        clampScroll(f);
        float lh = lineHeight(f);
        float textX = textX(), textY = textY();

        d.pushClip(x + 6, y + 4, w - 12, h - 8);
        if (s.isEmpty() && !focused) {
            d.text(placeholder, textX, textY, px, fade(placeholderColor, alpha));
        } else {
            for (int li = 0; li < lineRanges.size(); li++) {
                float top = textY + li * lh - scrollY;
                if (top + lh < y || top > y + h) continue;
                int[] r = lineRanges.get(li);
                d.text(s.substring(r[0], r[1]), textX, top, px, fade(textColor, alpha));
            }
        }
        if (focused && (Reactive.clock().peek() % 1f) < 0.55f) {
            float top = textY + caretLine() * lh - scrollY;
            d.rect(textX + caretX(f) + 1, top, 1.2f, lh * 0.9f, fade(textColor, alpha));
        }
        d.popClip();
    }

    @Override
    public boolean onMouseDown(float mx, float my, int button) {
        SdfFont f = font();
        if (f == null) return true;
        rewrap(f, w - 16);
        float lh = lineHeight(f);
        int li = (int) ((my - textY() + scrollY) / lh);
        li = Math.min(Math.max(li, 0), lineRanges.size() - 1);
        caret = indexInLine(f, li, mx - textX());
        preferredX = -1;
        return true;
    }

    @Override
    public boolean onScroll(float amount) {
        SdfFont f = font();
        if (f == null) return false;
        rewrap(f, w - 16);
        if (lineRanges.size() * lineHeight(f) <= viewH()) return false;
        scrollY -= amount * lineHeight(f) * 2;
        clampScroll(f);
        return true;
    }

    private void insert(String text) {
        String s = value.peek();
        value.set(s.substring(0, caret) + text + s.substring(caret));
        caret += text.length();
        preferredX = -1;
        SdfFont f = font();
        if (f != null) ensureCaretVisible(f);
    }

    @Override
    public boolean onChar(int codepoint) {
        if (codepoint < 32) return false;
        insert(new String(Character.toChars(codepoint)));
        return true;
    }

    private void moveLine(SdfFont f, int dir) {
        rewrap(f, w - 16);
        if (preferredX < 0) preferredX = caretX(f);
        int li = caretLine() + dir;
        if (li < 0 || li >= lineRanges.size()) return;
        caret = indexInLine(f, li, preferredX);
    }

    @Override
    public boolean onKey(int key, int modifiers) {
        String s = value.peek();
        SdfFont f = font();
        if (f != null) rewrap(f, w - 16);
        switch (key) {
            case GLFW.GLFW_KEY_BACKSPACE -> {
                if (caret > 0) {
                    int n = Character.charCount(s.codePointBefore(caret));
                    value.set(s.substring(0, caret - n) + s.substring(caret));
                    caret -= n;
                }
                preferredX = -1;
            }
            case GLFW.GLFW_KEY_DELETE -> {
                if (caret < s.length()) {
                    int n = Character.charCount(s.codePointAt(caret));
                    value.set(s.substring(0, caret) + s.substring(caret + n));
                }
                preferredX = -1;
            }
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> insert("\n");
            case GLFW.GLFW_KEY_LEFT -> {
                if (caret > 0) caret -= Character.charCount(s.codePointBefore(caret));
                preferredX = -1;
            }
            case GLFW.GLFW_KEY_RIGHT -> {
                if (caret < s.length()) caret += Character.charCount(s.codePointAt(caret));
                preferredX = -1;
            }
            case GLFW.GLFW_KEY_UP -> { if (f != null) moveLine(f, -1); }
            case GLFW.GLFW_KEY_DOWN -> { if (f != null) moveLine(f, 1); }
            case GLFW.GLFW_KEY_HOME -> {
                if (f != null) caret = lineRanges.get(caretLine())[0];
                preferredX = -1;
            }
            case GLFW.GLFW_KEY_END -> {
                if (f != null) caret = lineRanges.get(caretLine())[1];
                preferredX = -1;
            }
            default -> { return false; }
        }
        if (f != null) ensureCaretVisible(f);
        return true;
    }

    @Override public TextArea size(float w, float h) { super.size(w, h); return this; }
    @Override public TextArea width(float w) { super.width(w); return this; }
    @Override public TextArea height(float h) { super.height(h); return this; }
    @Override public TextArea grow(float g) { super.grow(g); return this; }
    @Override public TextArea anchor(Anchor a) { super.anchor(a); return this; }
    @Override public TextArea offset(float dx, float dy) { super.offset(dx, dy); return this; }
    @Override public TextArea padding(float p) { super.padding(p); return this; }
    @Override public TextArea visible(boolean v) { super.visible(v); return this; }
    @Override public TextArea opacity(float o) { super.opacity(o); return this; }
}

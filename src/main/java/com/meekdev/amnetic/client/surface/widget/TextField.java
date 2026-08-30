package com.meekdev.amnetic.client.surface.widget;

import com.meekdev.amnetic.client.surface.Anchor;
import com.meekdev.amnetic.client.surface.Surfaces;
import com.meekdev.amnetic.client.surface.draw.UiDraw;
import com.meekdev.amnetic.client.surface.reactive.Reactive;
import com.meekdev.amnetic.client.surface.reactive.Signal;
import com.meekdev.amnetic.client.surface.text.Fonts;
import com.meekdev.amnetic.client.surface.text.SdfFont;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

public class TextField extends Widget {

    public final Signal<String> value;
    String placeholder = "";
    float px = 13;
    int background = 0xFF11151B;
    int textColor = 0xFFFFFFFF;
    int placeholderColor = 0x60FFFFFF;
    int selectionColor = 0x504C8FDD;
    int accent = 0x804C8FDD;
    float rounding = 7;
    Consumer<String> onSubmit;

    private int caret;
    private int selAnchor = -1;

    public TextField(String initial) {
        value = new Signal<>(initial);
        caret = initial.length();
        prefH = 24;
    }

    public TextField placeholder(String p) { placeholder = p; return this; }
    public TextField colors(int background, int text) { this.background = background; this.textColor = text; return this; }
    public TextField rounding(float r) { rounding = r; return this; }
    public TextField accent(int argb) { accent = argb; selectionColor = (argb & 0xFFFFFF) | 0x50000000; return this; }
    public TextField px(float p) { px = p; return this; }
    public TextField onSubmit(Consumer<String> c) { onSubmit = c; return this; }

    @Override
    protected boolean interactive() { return true; }

    @Override
    protected boolean focusable() { return true; }

    @Override
    protected float contentWidth() { return 140; }

    @Override
    protected float contentHeight(float forWidth) { return 24; }

    private boolean hasSelection() {
        return selAnchor != -1 && selAnchor != caret;
    }

    private int selStart() { return Math.min(selAnchor, caret); }
    private int selEnd() { return Math.max(selAnchor, caret); }

    private SdfFont font() {
        Identifier fid = Surfaces.defaultFont();
        return fid == null ? null : Fonts.get(fid);
    }

    private float textX() { return x + 8; }

    private int indexAt(float mx) {
        SdfFont f = font();
        String s = value.peek();
        if (f == null) return s.length();
        float scale = px / f.bakePx();
        float pen = textX();
        int prev = -1;
        for (int i = 0; i < s.length(); ) {
            int cp = s.codePointAt(i);
            int n = Character.charCount(cp);
            SdfFont.Glyph g = f.glyph(cp);
            float adv = (prev != -1 ? f.kern(prev, cp) * scale : 0)
                    + (g == null ? 0 : g.advance() * scale);
            if (mx < pen + adv * 0.5f) return i;
            pen += adv;
            i += n;
            prev = cp;
        }
        return s.length();
    }

    @Override
    protected void drawSelf(UiDraw d, float alpha) {
        d.roundedRect(x, y, w, h, rounding, fade(background, alpha));
        d.border(x, y, w, h, rounding, 1f, fade(focused ? accent : 0x30FFFFFF, alpha));

        Identifier fid = Surfaces.defaultFont();
        if (fid == null) return;
        d.font(fid);
        String s = value.peek();
        float textX = textX();
        float centerY = y + h * 0.5f;
        d.pushClip(x + 6, y, w - 12, h);
        SdfFont f = Fonts.get(fid);
        if (focused && hasSelection() && f != null) {
            float sx = textX + f.width(s.substring(0, selStart()), px);
            float ex = textX + f.width(s.substring(0, selEnd()), px);
            d.rect(sx, y + 5, ex - sx, h - 10, fade(selectionColor, alpha));
        }
        if (s.isEmpty() && !focused) {
            d.textLeftCentered(placeholder, textX, centerY, px, fade(placeholderColor, alpha));
        } else {
            d.textLeftCentered(s, textX, centerY, px, fade(textColor, alpha));
        }
        if (focused && (Reactive.clock().peek() % 1f) < 0.55f) {
            float caretX = textX + (f == null ? 0 : f.width(s.substring(0, caret), px));
            d.rect(caretX + 1, y + 5, 1.2f, h - 10, fade(textColor, alpha));
        }
        d.popClip();
    }

    private static boolean shiftHeld() {
        var window = Minecraft.getInstance().getWindow();
        return InputConstants.isKeyDown(window, GLFW.GLFW_KEY_LEFT_SHIFT)
                || InputConstants.isKeyDown(window, GLFW.GLFW_KEY_RIGHT_SHIFT);
    }

    @Override
    public boolean onMouseDown(float mx, float my, int button) {
        int idx = indexAt(mx);
        if (shiftHeld()) {
            if (selAnchor == -1) selAnchor = caret;
        } else {
            selAnchor = idx;
        }
        caret = idx;
        return true;
    }

    @Override
    public void onMouseDrag(float mx, float my) {
        caret = indexAt(mx);
    }

    @Override
    public void onMouseUp(float mx, float my, int button) {
        if (selAnchor == caret) selAnchor = -1;
    }

    private String deleteSelection(String s) {
        String out = s.substring(0, selStart()) + s.substring(selEnd());
        caret = selStart();
        selAnchor = -1;
        return out;
    }

    @Override
    public boolean onChar(int codepoint) {
        if (codepoint < 32) return false;
        String s = value.peek();
        if (hasSelection()) s = deleteSelection(s);
        value.set(s.substring(0, caret) + new String(Character.toChars(codepoint)) + s.substring(caret));
        caret += Character.charCount(codepoint);
        selAnchor = -1;
        return true;
    }

    private void insert(String text) {
        String s = value.peek();
        if (hasSelection()) s = deleteSelection(s);
        value.set(s.substring(0, caret) + text + s.substring(caret));
        caret += text.length();
        selAnchor = -1;
    }

    private void moveCaret(int to, boolean shift) {
        if (shift) {
            if (selAnchor == -1) selAnchor = caret;
        } else {
            selAnchor = -1;
        }
        caret = to;
    }

    @Override
    public boolean onKey(int key, int modifiers) {
        String s = value.peek();
        boolean shift = (modifiers & GLFW.GLFW_MOD_SHIFT) != 0;
        boolean ctrl = (modifiers & GLFW.GLFW_MOD_CONTROL) != 0;

        if (ctrl) {
            switch (key) {
                case GLFW.GLFW_KEY_A -> {
                    selAnchor = 0;
                    caret = s.length();
                    return true;
                }
                case GLFW.GLFW_KEY_C -> {
                    if (hasSelection()) {
                        Minecraft.getInstance().keyboardHandler.setClipboard(s.substring(selStart(), selEnd()));
                    }
                    return true;
                }
                case GLFW.GLFW_KEY_X -> {
                    if (hasSelection()) {
                        Minecraft.getInstance().keyboardHandler.setClipboard(s.substring(selStart(), selEnd()));
                        value.set(deleteSelection(s));
                    }
                    return true;
                }
                case GLFW.GLFW_KEY_V -> {
                    String clip = Minecraft.getInstance().keyboardHandler.getClipboard();
                    if (clip != null && !clip.isEmpty()) {
                        insert(clip.replace("\r", "").replace('\n', ' '));
                    }
                    return true;
                }
            }
        }

        switch (key) {
            case GLFW.GLFW_KEY_BACKSPACE -> {
                if (hasSelection()) {
                    value.set(deleteSelection(s));
                } else if (caret > 0) {
                    int n = Character.charCount(s.codePointBefore(caret));
                    value.set(s.substring(0, caret - n) + s.substring(caret));
                    caret -= n;
                }
                return true;
            }
            case GLFW.GLFW_KEY_DELETE -> {
                if (hasSelection()) {
                    value.set(deleteSelection(s));
                } else if (caret < s.length()) {
                    int n = Character.charCount(s.codePointAt(caret));
                    value.set(s.substring(0, caret) + s.substring(caret + n));
                }
                return true;
            }
            case GLFW.GLFW_KEY_LEFT -> {
                int to = caret > 0 ? caret - Character.charCount(s.codePointBefore(caret)) : 0;
                if (!shift && hasSelection()) to = selStart();
                moveCaret(to, shift);
                return true;
            }
            case GLFW.GLFW_KEY_RIGHT -> {
                int to = caret < s.length() ? caret + Character.charCount(s.codePointAt(caret)) : caret;
                if (!shift && hasSelection()) to = selEnd();
                moveCaret(to, shift);
                return true;
            }
            case GLFW.GLFW_KEY_HOME -> { moveCaret(0, shift); return true; }
            case GLFW.GLFW_KEY_END -> { moveCaret(s.length(), shift); return true; }
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
                if (onSubmit != null) onSubmit.accept(s);
                return true;
            }
        }
        return false;
    }

    @Override
    public void onFocusLost() {
        selAnchor = -1;
    }

    @Override public TextField size(float w, float h) { super.size(w, h); return this; }
    @Override public TextField width(float w) { super.width(w); return this; }
    @Override public TextField height(float h) { super.height(h); return this; }
    @Override public TextField grow(float g) { super.grow(g); return this; }
    @Override public TextField anchor(Anchor a) { super.anchor(a); return this; }
    @Override public TextField offset(float dx, float dy) { super.offset(dx, dy); return this; }
    @Override public TextField padding(float p) { super.padding(p); return this; }
    @Override public TextField visible(boolean v) { super.visible(v); return this; }
    @Override public TextField opacity(float o) { super.opacity(o); return this; }
}

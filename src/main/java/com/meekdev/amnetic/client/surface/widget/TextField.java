package com.meekdev.amnetic.client.surface.widget;

import com.meekdev.amnetic.client.surface.Surfaces;
import com.meekdev.amnetic.client.surface.draw.UiDraw;
import com.meekdev.amnetic.client.surface.reactive.Reactive;
import com.meekdev.amnetic.client.surface.reactive.Signal;
import com.meekdev.amnetic.client.surface.text.Fonts;
import com.meekdev.amnetic.client.surface.text.SdfFont;
import java.util.function.Consumer;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

// single-line text input with focus, caret and a blink driven by the reactive clock
public class TextField extends Widget {

    public final Signal<String> value;
    String placeholder = "";
    float px = 13;
    int background = 0xFF11151B;
    int textColor = 0xFFFFFFFF;
    int placeholderColor = 0x60FFFFFF;
    float rounding = 7;
    Consumer<String> onSubmit;

    private int caret;

    public TextField(String initial) {
        value = new Signal<>(initial);
        caret = initial.length();
        prefH = 24;
    }

    public TextField placeholder(String p) { placeholder = p; return this; }
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

    @Override
    protected void drawSelf(UiDraw d, float alpha) {
        d.roundedRect(x, y, w, h, rounding, fade(background, alpha));
        d.border(x, y, w, h, rounding, 1f, fade(focused ? 0x804C8FDD : 0x30FFFFFF, alpha));

        Identifier fid = Surfaces.defaultFont();
        if (fid == null) return;
        d.font(fid);
        String s = value.peek();
        float textX = x + 8;
        float centerY = y + h * 0.5f;
        d.pushClip(x + 6, y, w - 12, h);
        if (s.isEmpty() && !focused) {
            d.textLeftCentered(placeholder, textX, centerY, px, fade(placeholderColor, alpha));
        } else {
            d.textLeftCentered(s, textX, centerY, px, fade(textColor, alpha));
        }
        if (focused && (Reactive.clock().peek() % 1f) < 0.55f) {
            SdfFont f = Fonts.get(fid);
            float caretX = textX + (f == null ? 0 : f.width(s.substring(0, caret), px));
            d.rect(caretX + 1, y + 5, 1.2f, h - 10, fade(textColor, alpha));
        }
        d.popClip();
    }

    @Override
    public boolean onMouseDown(float mx, float my, int button) {
        caret = value.peek().length(); // click puts the caret at the end, per-glyph caret later
        return true;
    }

    @Override
    public boolean onChar(int codepoint) {
        if (codepoint < 32) return false;
        String s = value.peek();
        value.set(s.substring(0, caret) + new String(Character.toChars(codepoint)) + s.substring(caret));
        caret += Character.charCount(codepoint);
        return true;
    }

    @Override
    public boolean onKey(int key, int modifiers) {
        String s = value.peek();
        switch (key) {
            case GLFW.GLFW_KEY_BACKSPACE -> {
                if (caret > 0) {
                    value.set(s.substring(0, caret - 1) + s.substring(caret));
                    caret--;
                }
                return true;
            }
            case GLFW.GLFW_KEY_DELETE -> {
                if (caret < s.length()) value.set(s.substring(0, caret) + s.substring(caret + 1));
                return true;
            }
            case GLFW.GLFW_KEY_LEFT -> { caret = Math.max(0, caret - 1); return true; }
            case GLFW.GLFW_KEY_RIGHT -> { caret = Math.min(s.length(), caret + 1); return true; }
            case GLFW.GLFW_KEY_HOME -> { caret = 0; return true; }
            case GLFW.GLFW_KEY_END -> { caret = s.length(); return true; }
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
                if (onSubmit != null) onSubmit.accept(s);
                return true;
            }
        }
        return false;
    }
}

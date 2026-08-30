package com.meekdev.amnetic.client.surface.widget;

import com.meekdev.amnetic.client.surface.Anchor;
import com.meekdev.amnetic.client.surface.Surfaces;
import com.meekdev.amnetic.client.surface.draw.UiDraw;
import com.meekdev.amnetic.client.surface.reactive.Motion;
import com.meekdev.amnetic.client.surface.reactive.Signal;
import java.util.Locale;
import java.util.function.Consumer;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

public class KeybindField extends Widget {

    public final Signal<Integer> key;
    Consumer<Integer> onChange;
    float px = 13;
    float rounding = 7;
    int background = 0xFF232A33;
    int hoverBackground = 0xFF3A4654;
    int accent = 0xFF4C8FDD;
    int textColor = 0xFFFFFFFF;

    private boolean armed;

    private final Signal<Float> hoverTarget = new Signal<>(0f);
    private final Motion<Float> hoverT = Motion.spring(0f, 60f, 9f);

    public KeybindField(int initialKey) {
        key = new Signal<>(initialKey);
        hoverT.follow(hoverTarget);
        prefH = 24;
    }

    public KeybindField onChange(Consumer<Integer> c) { onChange = c; return this; }
    public KeybindField px(float p) { px = p; return this; }
    public KeybindField accent(int argb) { accent = argb; return this; }

    public boolean isArmed() { return armed; }

    @Override
    protected boolean interactive() { return true; }

    @Override
    protected boolean focusable() { return true; }

    @Override
    protected float contentWidth() { return 110; }

    @Override
    protected float contentHeight(float forWidth) { return 24; }

    private String display() {
        if (armed) return "press a key...";
        return keyName(key.peek());
    }

    public static String keyName(int code) {
        if (code < 0) return "none";
        String name = GLFW.glfwGetKeyName(code, 0);
        if (name != null && !name.isEmpty()) return name.toUpperCase(Locale.ROOT);
        return "KEY " + code;
    }

    @Override
    protected void drawSelf(UiDraw d, float alpha) {
        hoverTarget.set(hovered ? 1f : 0f);
        float t = hoverT.value().peek();
        int bg = lerpColor(background, hoverBackground, t);
        if (pressed) bg = lerpColor(bg, 0xFF000000, 0.25f);
        d.roundedRect(x, y, w, h, rounding, fade(bg, alpha));
        d.border(x, y, w, h, rounding, 1f, fade(armed ? accent : 0x30FFFFFF, alpha));
        Identifier fid = Surfaces.defaultFont();
        if (fid == null) return;
        Identifier prev = d.currentFont();
        d.font(fid);
        d.textCentered(display(), x + w * 0.5f, y + h * 0.5f,
                px, fade(armed ? fade(textColor, 0.7f) : textColor, alpha));
        if (prev != null) d.font(prev);
    }

    @Override
    public boolean onMouseDown(float mx, float my, int button) {
        return true;
    }

    @Override
    public void onMouseUp(float mx, float my, int button) {
        if (mx < x || my < y || mx > x + w || my > y + h) return;
        armed = true;
    }

    @Override
    public boolean onKey(int keyCode, int modifiers) {
        if (!armed) return false;
        armed = false;
        if (keyCode != GLFW.GLFW_KEY_ESCAPE) {
            key.set(keyCode);
            if (onChange != null) onChange.accept(keyCode);
        }
        return true;
    }

    @Override
    public boolean onChar(int codepoint) {
        return armed;
    }

    @Override
    public void onFocusLost() {
        armed = false;
    }

    @Override
    public void remove() {
        hoverT.dispose();
        super.remove();
    }

    @Override public KeybindField size(float w, float h) { super.size(w, h); return this; }
    @Override public KeybindField width(float w) { super.width(w); return this; }
    @Override public KeybindField height(float h) { super.height(h); return this; }
    @Override public KeybindField grow(float g) { super.grow(g); return this; }
    @Override public KeybindField anchor(Anchor a) { super.anchor(a); return this; }
    @Override public KeybindField offset(float dx, float dy) { super.offset(dx, dy); return this; }
    @Override public KeybindField padding(float p) { super.padding(p); return this; }
    @Override public KeybindField visible(boolean v) { super.visible(v); return this; }
    @Override public KeybindField opacity(float o) { super.opacity(o); return this; }
}

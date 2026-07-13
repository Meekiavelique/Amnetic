package com.meekdev.amnetic.client.surface.internal;

import com.meekdev.amnetic.client.surface.widget.Widget;
import java.util.ArrayList;
import java.util.List;

// pointer/keyboard routing for one widget tree: hover enter/leave, press/click,
// drag, focus and tab cycling, all coordinates gui-scaled
public final class InputRouter {

    private final Widget root;
    private Widget hovered, pressed, focused;

    public InputRouter(Widget root) {
        this.root = root;
    }

    public Widget focused() {
        return focused;
    }

    public void mouseMoved(float mx, float my) {
        Widget hit = root.hitTest(mx, my);
        if (hit != hovered) {
            if (hovered != null) hovered.hovered = false;
            hovered = hit;
            if (hovered != null) hovered.hovered = true;
        }
    }

    public boolean mouseDown(float mx, float my, int button) {
        mouseMoved(mx, my);
        Widget hit = root.hitTest(mx, my);
        setFocus(hit != null && hit.isFocusableWidget() ? hit : null);
        if (hit == null) return false;
        if (hit.onMouseDown(mx, my, button)) {
            pressed = hit;
            hit.pressed = true;
            return true;
        }
        return false;
    }

    public boolean mouseUp(float mx, float my, int button) {
        if (pressed == null) return false;
        pressed.pressed = false;
        pressed.onMouseUp(mx, my, button);
        pressed = null;
        return true;
    }

    public boolean mouseDragged(float mx, float my) {
        if (pressed == null) return false;
        pressed.onMouseDrag(mx, my);
        return true;
    }

    public boolean scroll(float mx, float my, float amount) {
        Widget hit = root.hitTest(mx, my);
        while (hit != null) {
            hit.onScroll(amount);
            hit = null; // single-target for now, containers get bubbling with the scroll widget
        }
        return false;
    }

    public boolean charTyped(int codepoint) {
        return focused != null && focused.onChar(codepoint);
    }

    public boolean keyPressed(int key, int modifiers) {
        if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_TAB && focused != null) {
            cycleFocus();
            return true;
        }
        return focused != null && focused.onKey(key, modifiers);
    }

    public void setFocus(Widget target) {
        if (target == focused) return;
        if (focused != null) {
            focused.focused = false;
            focused.onFocusLost();
        }
        focused = target;
        if (focused != null) focused.focused = true;
    }

    private void cycleFocus() {
        List<Widget> focusables = new ArrayList<>();
        collectFocusables(root, focusables);
        if (focusables.isEmpty()) return;
        int idx = focusables.indexOf(focused);
        setFocus(focusables.get((idx + 1) % focusables.size()));
    }

    private static void collectFocusables(Widget w, List<Widget> out) {
        if (!w.isVisible()) return;
        if (w.isFocusableWidget()) out.add(w);
        for (Widget c : w.children()) collectFocusables(c, out);
    }
}

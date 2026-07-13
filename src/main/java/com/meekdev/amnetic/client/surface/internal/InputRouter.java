package com.meekdev.amnetic.client.surface.internal;

import com.meekdev.amnetic.client.surface.widget.Widget;
import java.util.ArrayList;
import java.util.List;
import org.lwjgl.glfw.GLFW;

// pointer/keyboard routing for one widget tree: hover enter/leave, press/click, drag,
// scroll bubbling, drag-and-drop and focus with tab cycling, coordinates gui-scaled
public final class InputRouter {

    private final Widget root;
    private Widget hovered, pressed, focused;

    // drag and drop state
    private Widget dragSource, dragging;
    private float dragX, dragY, pressX, pressY;

    public InputRouter(Widget root) {
        this.root = root;
    }

    public Widget focused() {
        return focused;
    }

    public Widget dragging() {
        return dragging;
    }

    public float dragX() { return dragX; }
    public float dragY() { return dragY; }

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

        // nearest draggable ancestor becomes the drag candidate
        for (Widget wgt = hit; wgt != null; wgt = wgt.parentWidget()) {
            if (wgt.dragPayloadValue() != null) {
                dragSource = wgt;
                break;
            }
        }
        pressX = mx; pressY = my;

        if (hit.onMouseDown(mx, my, button)) {
            pressed = hit;
            hit.pressed = true;
            return true;
        }
        return dragSource != null;
    }

    public boolean mouseUp(float mx, float my, int button) {
        boolean any = pressed != null || dragging != null;
        if (dragging != null) {
            Widget hit = root.hitTest(mx, my);
            for (Widget wgt = hit; wgt != null; wgt = wgt.parentWidget()) {
                if (wgt.dropHandlerValue() != null) {
                    wgt.dropHandlerValue().accept(dragging.dragPayloadValue());
                    break;
                }
            }
            if (pressed != null) pressed.pressed = false;
            pressed = null; // a completed drag is not a click
        } else if (pressed != null) {
            pressed.pressed = false;
            pressed.onMouseUp(mx, my, button);
            pressed = null;
        }
        dragging = null;
        dragSource = null;
        return any;
    }

    public boolean mouseDragged(float mx, float my) {
        dragX = mx; dragY = my;
        if (dragging == null && dragSource != null
                && Math.abs(mx - pressX) + Math.abs(my - pressY) > 5) {
            dragging = dragSource;
        }
        if (dragging != null) return true;
        if (pressed == null) return false;
        pressed.onMouseDrag(mx, my);
        return true;
    }

    // bubbles from the hit widget up so a button inside a scroll still scrolls it
    public boolean scroll(float mx, float my, float amount) {
        for (Widget wgt = root.hitTest(mx, my); wgt != null; wgt = wgt.parentWidget()) {
            if (wgt.onScroll(amount)) return true;
        }
        return false;
    }

    public boolean charTyped(int codepoint) {
        return focused != null && focused.onChar(codepoint);
    }

    public boolean keyPressed(int key, int modifiers) {
        if (key == GLFW.GLFW_KEY_TAB && focused != null) {
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

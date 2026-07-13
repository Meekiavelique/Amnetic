package com.meekdev.amnetic.client.surface.widget;

import com.meekdev.amnetic.client.surface.Anchor;
import com.meekdev.amnetic.client.surface.draw.UiDraw;
import com.meekdev.amnetic.client.surface.reactive.Motion;
import java.util.ArrayList;
import java.util.List;

// retained node of a surface tree, gui-scaled pixel space, layout runs top down each frame
public abstract class Widget {

    Widget parent;
    final List<Widget> children = new ArrayList<>();

    // requested geometry, -1 means derive from content
    float prefW = -1, prefH = -1;
    float grow;
    Anchor anchor;
    float offX, offY;
    float padding;
    boolean visible = true;
    float opacity = 1f;

    // computed rect
    public float x, y, w, h;

    public boolean hovered, pressed, focused;

    // generic transform channels around the widget center, drive them with anything:
    // a spring on scale is a bounce, clock noise on translate is a shake, and so on
    float channelDx, channelDy, channelScale = 1f, channelRotation;

    public Widget translate(float dx, float dy) { channelDx = dx; channelDy = dy; return this; }
    public Widget scaleChannel(float s) { channelScale = s; return this; }
    public Widget rotate(float radians) { channelRotation = radians; return this; }
    public float translateXValue() { return channelDx; }
    public float translateYValue() { return channelDy; }
    public float scaleValue() { return channelScale; }
    public float rotationValue() { return channelRotation; }

    private boolean hasTransform() {
        return channelDx != 0 || channelDy != 0 || channelScale != 1f || channelRotation != 0;
    }

    // layout animation: when enabled the widget springs to new layout positions
    // instead of teleporting, one switch that works inside every container
    boolean animateLayout;
    float layoutStiffness = 60, layoutDamping = 9;
    private Motion<Float> layoutMx, layoutMy;
    private boolean laidOutOnce;

    public Widget animateLayout(boolean animate) { animateLayout = animate; return this; }

    public Widget animateLayout(float stiffness, float damping) {
        animateLayout = true;
        layoutStiffness = stiffness;
        layoutDamping = damping;
        return this;
    }

    public Widget add(Widget child) {
        child.parent = this;
        children.add(child);
        return this;
    }

    public Widget removeChild(Widget child) {
        children.remove(child);
        child.parent = null;
        return this;
    }

    public void remove() {
        if (parent != null) parent.removeChild(this);
    }

    public List<Widget> children() {
        return children;
    }

    public Widget size(float w, float h) { prefW = w; prefH = h; return this; }
    public Widget width(float w) { prefW = w; return this; }
    public Widget height(float h) { prefH = h; return this; }
    public Widget grow(float g) { grow = g; return this; }
    public Widget anchor(Anchor a) { anchor = a; return this; }
    public Widget offset(float dx, float dy) { offX = dx; offY = dy; return this; }
    public Widget padding(float p) { padding = p; return this; }
    public Widget visible(boolean v) { visible = v; return this; }
    public Widget opacity(float o) { opacity = o; return this; }
    public float opacityValue() { return opacity; }

    public boolean isVisible() { return visible; }

    // content-derived size when pref is -1, containers override
    protected float contentWidth() { return 0; }
    protected float contentHeight(float forWidth) { return 0; }

    public float measureWidth() {
        return prefW >= 0 ? prefW : contentWidth() + padding * 2;
    }

    public float measureHeight(float forWidth) {
        return prefH >= 0 ? prefH : contentHeight(forWidth - padding * 2) + padding * 2;
    }

    // place self at the given rect then lay out children, containers override placeChildren
    public final void layout(float x, float y, float w, float h) {
        if (animateLayout && laidOutOnce) {
            if (layoutMx == null) {
                layoutMx = Motion.spring(this.x, layoutStiffness, layoutDamping);
                layoutMy = Motion.spring(this.y, layoutStiffness, layoutDamping);
            }
            layoutMx.target(x);
            layoutMy.target(y);
            this.x = layoutMx.value().peek();
            this.y = layoutMy.value().peek();
        } else {
            this.x = x;
            this.y = y;
        }
        this.w = w; this.h = h;
        laidOutOnce = true;
        placeChildren();
    }

    // default free placement: anchored children inside the content box
    protected void placeChildren() {
        float cx = x + padding, cy = y + padding;
        float cw = w - padding * 2, ch = h - padding * 2;
        for (Widget c : children) {
            if (!c.visible) continue;
            float childW = c.prefW >= 0 ? c.prefW : (c.anchor == null ? cw : c.measureWidth());
            float childH = c.prefH >= 0 ? c.prefH : (c.anchor == null ? ch : c.measureHeight(childW));
            Anchor a = c.anchor == null ? Anchor.TOP_LEFT : c.anchor;
            float px = cx + (cw - childW) * a.fx + c.offX * (a.fx == 1f ? -1f : 1f);
            float py = cy + (ch - childH) * a.fy + c.offY * (a.fy == 1f ? -1f : 1f);
            c.layout(px, py, childW, childH);
        }
    }

    public final void draw(UiDraw d, float parentOpacity) {
        if (!visible) return;
        float alpha = opacity * parentOpacity;
        if (alpha <= 0f) return;
        boolean xf = hasTransform();
        if (xf) d.pushTransform(x + w * 0.5f, y + h * 0.5f, channelDx, channelDy, channelScale, channelRotation);
        drawSelf(d, alpha);
        for (Widget c : children) c.draw(d, alpha);
        drawAfterChildren(d);
        if (xf) d.popTransform();
    }

    protected void drawSelf(UiDraw d, float alpha) {}

    // runs after the subtree, panels pop their clip here
    public void drawAfterChildren(UiDraw d) {}

    // topmost interactive descendant containing the point, null if none
    // draggables and drop targets count as hittable even when otherwise passive
    public Widget hitTest(float mx, float my) {
        if (!visible) return null;
        if (hasTransform()) {
            // inverse of the draw transform so the pointer follows shakes/scales/rotations
            float cx = x + w * 0.5f, cy = y + h * 0.5f;
            float px = mx - cx - channelDx, py = my - cy - channelDy;
            float inv = 1f / Math.max(channelScale, 1e-4f);
            float cos = (float) Math.cos(-channelRotation), sin = (float) Math.sin(-channelRotation);
            mx = cx + (px * cos - py * sin) * inv;
            my = cy + (px * sin + py * cos) * inv;
        }
        if (mx < x || my < y || mx > x + w || my > y + h) return null;
        for (int i = children.size() - 1; i >= 0; i--) {
            Widget hit = children.get(i).hitTest(mx, my);
            if (hit != null) return hit;
        }
        return interactive() || dragPayload != null || dropHandler != null ? this : null;
    }

    protected boolean interactive() { return false; }
    protected boolean focusable() { return false; }

    // engine-side check, not for api users
    public final boolean isFocusableWidget() { return focusable(); }

    // input hooks, coordinates are gui-scaled, return true to consume
    public boolean onMouseDown(float mx, float my, int button) { return interactive(); }
    public void onMouseUp(float mx, float my, int button) {}
    public void onMouseDrag(float mx, float my) {}
    public boolean onScroll(float amount) { return false; }
    public boolean onChar(int codepoint) { return false; }
    public boolean onKey(int key, int modifiers) { return false; }
    public void onFocusLost() {}

    // drag and drop: a draggable carries a payload, a drop target consumes one
    Object dragPayload;
    java.util.function.Consumer<Object> dropHandler;

    public Widget draggable(Object payload) { dragPayload = payload; return this; }
    public Widget dropTarget(java.util.function.Consumer<Object> onDrop) { dropHandler = onDrop; return this; }

    public Object dragPayloadValue() { return dragPayload; }
    public java.util.function.Consumer<Object> dropHandlerValue() { return dropHandler; }
    public Widget parentWidget() { return parent; }

    // multiply an argb's alpha by a factor
    protected static int fade(int argb, float alpha) {
        int a = (int) (((argb >>> 24) & 0xFF) * alpha);
        return (a << 24) | (argb & 0xFFFFFF);
    }

    protected static int lerpColor(int from, int to, float t) {
        t = Math.min(Math.max(t, 0f), 1f);
        int a = (int) (((from >>> 24) & 0xFF) + (((to >>> 24) & 0xFF) - ((from >>> 24) & 0xFF)) * t);
        int r = (int) (((from >> 16) & 0xFF) + (((to >> 16) & 0xFF) - ((from >> 16) & 0xFF)) * t);
        int g = (int) (((from >> 8) & 0xFF) + (((to >> 8) & 0xFF) - ((from >> 8) & 0xFF)) * t);
        int b = (int) ((from & 0xFF) + ((to & 0xFF) - (from & 0xFF)) * t);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }
}

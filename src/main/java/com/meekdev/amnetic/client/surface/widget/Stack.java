package com.meekdev.amnetic.client.surface.widget;

import com.meekdev.amnetic.client.surface.Anchor;
// free-placement container, children position via anchor/offset/size (the Widget default)
public class Stack extends Widget {

    @Override
    protected float contentWidth() {
        float max = 0;
        for (Widget c : children) {
            if (c.visible) max = Math.max(max, c.offX + c.measureWidth());
        }
        return max;
    }

    @Override
    protected float contentHeight(float forWidth) {
        float max = 0;
        for (Widget c : children) {
            if (c.visible) max = Math.max(max, c.offY + c.measureHeight(forWidth));
        }
        return max;
    }

    // fluent overrides so chains keep the subtype
    @Override public Stack size(float w, float h) { super.size(w, h); return this; }
    @Override public Stack width(float w) { super.width(w); return this; }
    @Override public Stack height(float h) { super.height(h); return this; }
    @Override public Stack grow(float g) { super.grow(g); return this; }
    @Override public Stack anchor(Anchor a) { super.anchor(a); return this; }
    @Override public Stack offset(float dx, float dy) { super.offset(dx, dy); return this; }
    @Override public Stack padding(float p) { super.padding(p); return this; }
    @Override public Stack visible(boolean v) { super.visible(v); return this; }
    @Override public Stack opacity(float o) { super.opacity(o); return this; }
}

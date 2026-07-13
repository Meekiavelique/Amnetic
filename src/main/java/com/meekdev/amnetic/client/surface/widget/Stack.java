package com.meekdev.amnetic.client.surface.widget;

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
}

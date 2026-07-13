package com.meekdev.amnetic.client.surface;

import com.meekdev.amnetic.client.surface.draw.UiDraw;
import com.meekdev.amnetic.client.surface.internal.SurfaceRenderer;
import com.meekdev.amnetic.client.surface.widget.Stack;
import java.util.function.Consumer;

// a screen-space overlay canvas drawn over post-processing and under the vanilla gui,
// build a widget tree under root() or draw immediately via onDraw, both compose
public final class HudSurface {

    private final Stack root = new Stack();
    private Consumer<UiDraw> drawCallback;
    private boolean visible = true;
    private boolean removed;

    HudSurface() {}

    public Stack root() {
        return root;
    }

    public HudSurface onDraw(Consumer<UiDraw> callback) {
        this.drawCallback = callback;
        return this;
    }

    public HudSurface setVisible(boolean visible) {
        this.visible = visible;
        return this;
    }

    public boolean isVisible() {
        return visible && !removed;
    }

    public void remove() {
        removed = true;
        SurfaceRenderer.INSTANCE.remove(this);
    }

    // engine-side accessor, not for api users
    public Consumer<UiDraw> internalDrawCallback() {
        return drawCallback;
    }
}

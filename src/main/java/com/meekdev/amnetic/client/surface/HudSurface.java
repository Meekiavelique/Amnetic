package com.meekdev.amnetic.client.surface;

import com.meekdev.amnetic.client.surface.draw.UiDraw;
import com.meekdev.amnetic.client.surface.internal.InputRouter;
import com.meekdev.amnetic.client.surface.internal.SurfaceRenderer;
import com.meekdev.amnetic.client.surface.widget.Stack;
import java.util.function.Consumer;

public final class HudSurface {

    private final Stack outer = new Stack();
    private final Stack root = new Stack();
    private final Stack overlay = new Stack();
    private final InputRouter input = new InputRouter(outer);
    private Consumer<UiDraw> drawCallback;
    private boolean visible = true;
    private boolean interactive;
    private boolean removed;

    HudSurface() {
        outer.add(root);
        outer.add(overlay);
    }

    public Stack overlay() {
        return overlay;
    }

    public Stack internalTree() {
        return outer;
    }

    public Stack root() {
        return root;
    }

    public HudSurface interactive(boolean i) {
        interactive = i;
        return this;
    }

    public boolean isInteractive() {
        return interactive;
    }

    public InputRouter internalInput() {
        return input;
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

    public Consumer<UiDraw> internalDrawCallback() {
        return drawCallback;
    }
}

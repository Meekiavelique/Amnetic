package com.meekdev.amnetic.client.surface;

import com.meekdev.amnetic.client.surface.internal.InputRouter;
import com.meekdev.amnetic.client.surface.internal.SurfaceScreen;
import com.meekdev.amnetic.client.surface.widget.Stack;
import net.minecraft.client.Minecraft;

// a modal ui: opens a vanilla screen for input capture and pause semantics while the
// visuals render through the surface pass, widgets live under root()
public final class ScreenSurface {

    private final Stack outer = new Stack();
    private final Stack root = new Stack();
    private final Stack overlay = new Stack();
    private final InputRouter input = new InputRouter(outer);
    private final String name;
    private boolean pausesGame;
    private int dim = 0x90000000;
    private SurfaceScreen screen;

    ScreenSurface(String name) {
        this.name = name;
        outer.add(root);
        outer.add(overlay); // drawn above and hit-tested first
    }

    // floating layer for popups, tooltips, menus and toasts
    public Stack overlay() {
        return overlay;
    }

    // engine-side: the full tree including the overlay
    public Stack internalTree() {
        return outer;
    }

    public Stack root() {
        return root;
    }

    public ScreenSurface pausesGame(boolean pause) {
        this.pausesGame = pause;
        return this;
    }

    // backdrop dim drawn under the widgets, 0 disables
    public ScreenSurface dim(int argb) {
        this.dim = argb;
        return this;
    }

    public int dimValue() {
        return dim;
    }

    public boolean pausesGameValue() {
        return pausesGame;
    }

    public String name() {
        return name;
    }

    public boolean isOpen() {
        return screen != null && Minecraft.getInstance().screen == screen;
    }

    public ScreenSurface open() {
        if (!isOpen()) {
            screen = new SurfaceScreen(this);
            Minecraft.getInstance().setScreen(screen);
        }
        return this;
    }

    public void close() {
        if (isOpen()) Minecraft.getInstance().setScreen(null);
        screen = null;
    }

    // engine-side accessor, not for api users
    public InputRouter internalInput() {
        return input;
    }

    public void internalClosed() {
        screen = null;
    }
}

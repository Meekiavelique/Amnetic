package com.meekdev.amnetic.client.surface;

import com.meekdev.amnetic.client.surface.internal.InputRouter;
import com.meekdev.amnetic.client.surface.internal.SurfaceScreen;
import com.meekdev.amnetic.client.surface.widget.Stack;
import net.minecraft.client.Minecraft;

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

    public ScreenSurface pausesGame(boolean pause) {
        this.pausesGame = pause;
        return this;
    }

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

    public InputRouter internalInput() {
        return input;
    }

    public void internalClosed() {
        screen = null;
    }
}

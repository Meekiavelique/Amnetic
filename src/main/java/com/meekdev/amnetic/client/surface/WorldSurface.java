package com.meekdev.amnetic.client.surface;

import com.meekdev.amnetic.client.surface.internal.InputRouter;
import com.meekdev.amnetic.client.surface.internal.WorldSurfaceRenderer;
import com.meekdev.amnetic.client.surface.widget.Stack;
import org.joml.Vector3f;

// a widget tree living on a plane in the world: rendered offscreen at a chosen density,
// drawn as a (optionally curved) panel, hover/click picked from the crosshair ray
public final class WorldSurface {

    private final Stack outer = new Stack();
    private final Stack root = new Stack();
    private final Stack overlay = new Stack();
    private final InputRouter input = new InputRouter(outer);
    private final float widthM, heightM;
    private double x, y, z;
    private final Vector3f facing = new Vector3f(0, 0, -1);
    private float curve; // total arc in radians, 0 flat, positive bends edges toward the viewer
    private boolean billboard;
    private int resolution = 256; // canvas px per meter
    private float maxDistance = 8f;
    private boolean alwaysOnTop;
    private boolean visible = true;
    private boolean removed;

    WorldSurface(float widthM, float heightM) {
        this.widthM = widthM;
        this.heightM = heightM;
        outer.add(root);
        outer.add(overlay);
    }

    // floating layer for popups, tooltips, menus and toasts
    public Stack overlay() {
        return overlay;
    }

    // engine-side: the full tree including the overlay
    public Stack internalTree() {
        return outer;
    }

    public Stack root() { return root; }

    public WorldSurface at(double x, double y, double z) {
        this.x = x; this.y = y; this.z = z;
        return this;
    }

    // outward normal of the panel, horizontal component is what matters
    public WorldSurface facing(float fx, float fy, float fz) {
        facing.set(fx, fy, fz).normalize();
        return this;
    }

    public WorldSurface curve(float radians) { curve = radians; return this; }

    // always face the camera instead of the fixed facing
    public WorldSurface billboard(boolean b) { billboard = b; return this; }
    public boolean billboardValue() { return billboard; }
    public WorldSurface resolution(int pxPerMeter) { resolution = Math.max(16, pxPerMeter); return this; }
    public WorldSurface maxDistance(float meters) { maxDistance = meters; return this; }
    public WorldSurface alwaysOnTop(boolean top) { alwaysOnTop = top; return this; }

    public WorldSurface setVisible(boolean v) { visible = v; return this; }
    public boolean isVisible() { return visible && !removed; }

    public void remove() {
        removed = true;
        WorldSurfaceRenderer.INSTANCE.remove(this);
    }

    public float widthM() { return widthM; }
    public float heightM() { return heightM; }
    public double xPos() { return x; }
    public double yPos() { return y; }
    public double zPos() { return z; }
    public Vector3f facingValue() { return facing; }
    public float curveValue() { return curve; }
    public int resolutionValue() { return resolution; }
    public float maxDistanceValue() { return maxDistance; }
    public boolean alwaysOnTopValue() { return alwaysOnTop; }

    public int canvasW() { return Math.round(widthM * resolution); }
    public int canvasH() { return Math.round(heightM * resolution); }

    // engine-side accessor, not for api users
    public InputRouter internalInput() { return input; }
}

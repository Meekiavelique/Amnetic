package com.meekdev.amnetic.client.surface;

import com.meekdev.amnetic.client.surface.internal.InputRouter;
import com.meekdev.amnetic.client.surface.internal.WorldSurfaceRenderer;
import com.meekdev.amnetic.client.surface.widget.Stack;
import org.joml.Vector3f;

public final class WorldSurface {

    private final Stack outer = new Stack();
    private final Stack root = new Stack();
    private final Stack overlay = new Stack();
    private final InputRouter input = new InputRouter(outer);
    private final float widthM, heightM;
    private double x, y, z;
    private final Vector3f facing = new Vector3f(0, 0, -1);
    private boolean direct;
    private Vector3f orientRight;
    private Vector3f orientUp;
    private float curve;
    private boolean billboard;
    private int resolution = 256;
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

    public Stack overlay() {
        return overlay;
    }

    public Stack internalTree() {
        return outer;
    }

    public Stack root() { return root; }

    public WorldSurface at(double x, double y, double z) {
        this.x = x; this.y = y; this.z = z;
        return this;
    }

    public WorldSurface facing(float fx, float fy, float fz) {
        facing.set(fx, fy, fz).normalize();
        return this;
    }

    // lays the surface on any plane: right is where the canvas runs left to right as its reader sees
    // it, up is where it runs bottom to top. overrides facing and billboard
    public WorldSurface orient(float rx, float ry, float rz, float ux, float uy, float uz) {
        orientRight = new Vector3f(rx, ry, rz).normalize();
        orientUp = new Vector3f(ux, uy, uz).normalize();
        return this;
    }

    // draws the widgets straight into the world instead of onto a canvas texture first, so text and
    // edges stay sharp at any distance. flat surfaces only: a curved one still goes through the canvas
    public WorldSurface direct(boolean d) { direct = d; return this; }
    public boolean directValue() { return direct; }

    public Vector3f orientRightValue() { return orientRight; }
    public Vector3f orientUpValue() { return orientUp; }

    public WorldSurface curve(float radians) { curve = radians; return this; }

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

    public InputRouter internalInput() { return input; }
}

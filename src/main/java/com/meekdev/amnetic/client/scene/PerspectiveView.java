package com.meekdev.amnetic.client.scene;

import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;

public final class PerspectiveView {

    private Vec3 eye = Vec3.ZERO;
    private final Matrix4f viewRotation = new Matrix4f();
    private final Matrix4f projection = new Matrix4f();
    private boolean matchMain;
    private boolean hasClip;
    private final Vector3f clipNormal = new Vector3f();
    private float clipD;
    private float distance;

    public PerspectiveView eye(Vec3 pos) { this.eye = pos; return this; }

    public PerspectiveView viewRotation(Matrix4fc matrix) { this.viewRotation.set(matrix); return this; }

    public PerspectiveView projection(Matrix4fc matrix) { this.projection.set(matrix); this.matchMain = false; return this; }

    public PerspectiveView matchMainProjection() { this.matchMain = true; return this; }

    public PerspectiveView clipPlane(Vector3f normal, float d) { return clipPlane(normal.x, normal.y, normal.z, d); }

    public PerspectiveView clipPlane(float nx, float ny, float nz, float d) {
        this.clipNormal.set(nx, ny, nz);
        this.clipD = d;
        this.hasClip = true;
        return this;
    }

    public PerspectiveView distance(float distance) { this.distance = distance; return this; }

    void reset() {
        eye = Vec3.ZERO;
        viewRotation.identity();
        projection.identity();
        matchMain = false;
        hasClip = false;
        clipNormal.zero();
        clipD = 0f;
        distance = 0f;
    }

    public Vec3 eye() { return eye; }
    public Matrix4f viewRotation() { return viewRotation; }
    public Matrix4f projection() { return projection; }
    public boolean matchMainProjection_() { return matchMain; }
    public boolean hasClip() { return hasClip; }
    public Vector3f clipNormal() { return clipNormal; }
    public float clipD() { return clipD; }
    public float distanceValue() { return distance; }
}

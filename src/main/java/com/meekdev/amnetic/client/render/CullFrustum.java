package com.meekdev.amnetic.client.render;

import org.joml.FrustumIntersection;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;

public final class CullFrustum {

    private final FrustumIntersection intersection = new FrustumIntersection();
    private final Matrix4f viewProjection = new Matrix4f();

    private CullFrustum() {
    }

    public static CullFrustum of(Matrix4fc viewProjection) {
        CullFrustum frustum = new CullFrustum();
        return frustum.set(viewProjection);
    }

    public static CullFrustum of(Matrix4fc projection, Matrix4fc view) {
        return of(new Matrix4f(projection).mul(view));
    }

    public static CullFrustum everything() {
        return new CullFrustum();
    }

    public CullFrustum set(Matrix4fc value) {
        viewProjection.set(value);
        intersection.set(viewProjection);
        everything = false;
        return this;
    }

    private boolean everything = true;

    public Matrix4fc viewProjection() {
        return viewProjection;
    }

    public CullFrustum transformedBy(Matrix4fc transform) {
        if (everything) {
            return everything();
        }
        return of(new Matrix4f(viewProjection).mul(transform));
    }

    public boolean testAab(double minX, double minY, double minZ,
                           double maxX, double maxY, double maxZ) {
        if (everything) {
            return true;
        }
        return intersection.testAab((float) minX, (float) minY, (float) minZ,
                (float) maxX, (float) maxY, (float) maxZ);
    }

    public boolean testSphere(Vector3f centre, float radius) {
        return everything || intersection.testSphere(centre.x, centre.y, centre.z, radius);
    }

    public boolean testPoint(double x, double y, double z) {
        return everything || intersection.testPoint((float) x, (float) y, (float) z);
    }
}

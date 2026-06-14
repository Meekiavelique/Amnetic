package com.meekdev.amnetic.client.scene.internal;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector4f;

public final class ReflectionMath {

    public static final float CLIP_SIGN = 1.0f;

    private ReflectionMath() {}

    public static Vec3 reflectPoint(Vec3 p, double px, double py, double pz, float nx, float ny, float nz) {
        double d = (p.x - px) * nx + (p.y - py) * ny + (p.z - pz) * nz;
        return new Vec3(p.x - 2 * d * nx, p.y - 2 * d * ny, p.z - 2 * d * nz);
    }

    public static void reflectedView(Matrix4f dest, Matrix4fc mainView, float nx, float ny, float nz) {
        float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (len > 1e-6f) { nx /= len; ny /= len; nz /= len; }
        Matrix4f reflect = new Matrix4f();
        reflect.m00(1 - 2 * nx * nx); reflect.m11(1 - 2 * ny * ny); reflect.m22(1 - 2 * nz * nz);
        reflect.m01(-2 * nx * ny);    reflect.m10(-2 * nx * ny);
        reflect.m02(-2 * nx * nz);    reflect.m20(-2 * nx * nz);
        reflect.m12(-2 * ny * nz);    reflect.m21(-2 * ny * nz);
        dest.set(mainView).mul(reflect);
    }

    public static void obliqueProjection(Matrix4f out, Matrix4fc base, Matrix4fc viewRotation, Vec3 eye,
                                         float nx, float ny, float nz, float dw) {
        out.set(base);

        Matrix4f viewFull = new Matrix4f(viewRotation)
                .translate(-(float) eye.x, -(float) eye.y, -(float) eye.z);
        Vector4f planeView = viewFull.invert().transpose().transform(new Vector4f(nx, ny, nz, dw));
        planeView.mul(CLIP_SIGN);

        Vector4f q = new Matrix4f(base).invert()
                .transform(new Vector4f(Math.signum(planeView.x), Math.signum(planeView.y), 1f, 1f));
        float denom = planeView.dot(q);
        if (Math.abs(denom) < 1e-9f) return;

        boolean zeroToOne = RenderSystem.getDevice().isZZeroToOne();
        Vector4f c = new Vector4f(planeView).mul((zeroToOne ? 1f : 2f) / denom);
        if (zeroToOne) {
            out.m02(c.x); out.m12(c.y); out.m22(c.z); out.m32(c.w);
        } else {
            out.m02(c.x - out.m03());
            out.m12(c.y - out.m13());
            out.m22(c.z - out.m23());
            out.m32(c.w - out.m33());
        }
    }
}

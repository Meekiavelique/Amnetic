package com.meekdev.amnetic.client.scene;

import com.meekdev.amnetic.client.instanced.BuiltinShader;
import com.meekdev.amnetic.client.instanced.InstancePhase;
import com.meekdev.amnetic.client.instanced.InstancedMesh;
import com.meekdev.amnetic.client.instanced.MeshData;
import com.meekdev.amnetic.client.instanced.RenderState;
import com.meekdev.amnetic.client.scene.internal.ReflectionMath;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

public final class PlanarReflection {

    public record Plane(Vec3 point, Vector3f normal) {}

    @FunctionalInterface
    public interface PlaneSupplier {
        Plane plane(CaptureContext ctx);
    }

    public static final Identifier SCREEN_SHADER = Identifier.fromNamespaceAndPath("amnetic", "scene/screen_reflect");

    private final Identifier reflectionId;

    private PlanarReflection(Identifier reflectionId) {
        this.reflectionId = reflectionId;
    }

    public Identifier reflectionId() { return reflectionId; }

    public static Builder builder() { return new Builder(); }

    public Surface surface() { return new Surface(reflectionId); }

    public static final class Builder {
        private PlaneSupplier planeSupplier;
        private float resolution = 0.5f;
        private boolean obliqueClip = true;
        private double activateWithin = -1;

        private Builder() {}

        public Builder plane(Vec3 point, Vector3f normal) {
            Plane p = new Plane(point, new Vector3f(normal).normalize());
            this.planeSupplier = ctx -> p;
            return this;
        }

        public Builder plane(PlaneSupplier supplier) { this.planeSupplier = supplier; return this; }
        public Builder resolution(float scale) { this.resolution = scale; return this; }
        public Builder obliqueClip(boolean enabled) { this.obliqueClip = enabled; return this; }
        public Builder activateWithin(double distance) { this.activateWithin = distance; return this; }

        public PlanarReflection register(Identifier reflectionId) {
            if (planeSupplier == null) throw new IllegalStateException("plane(...) is required");
            final PlaneSupplier supplier = planeSupplier;
            final boolean clip = obliqueClip;
            final double within = activateWithin;
            final Matrix4f scratch = new Matrix4f();

            PerspectiveCapture.builder()
                    .resolution(resolution)
                    .onCapture((ctx, view) -> {
                        Plane plane = supplier.plane(ctx);
                        if (plane == null) return CaptureResult.SKIP;
                        Vec3 pt = plane.point();
                        Vector3f n = plane.normal();
                        Vec3 eye = ctx.mainEye();
                        double dist = eye.distanceTo(pt);
                        if (within > 0 && dist > within) return CaptureResult.SKIP;

                        view.eye(ReflectionMath.reflectPoint(eye, pt.x, pt.y, pt.z, n.x, n.y, n.z));
                        ReflectionMath.reflectedView(scratch, ctx.mainViewRotation(), n.x, n.y, n.z);
                        view.viewRotation(scratch);
                        view.matchMainProjection();
                        if (clip) {
                            float d = -(n.x * (float) pt.x + n.y * (float) pt.y + n.z * (float) pt.z);
                            view.clipPlane(n.x, n.y, n.z, d);
                        }
                        view.distance((float) dist);
                        return CaptureResult.RENDER;
                    })
                    .register(reflectionId);

            return new PlanarReflection(reflectionId);
        }
    }

    public static final class Surface {
        private final Identifier reflectionId;
        private Vec3 center = Vec3.ZERO;
        private final Vector3f normal = new Vector3f(0f, 0f, 1f);
        private float width = 1f;
        private float height = 1f;

        private Surface(Identifier reflectionId) { this.reflectionId = reflectionId; }

        public Surface quad(Vec3 center, Vector3f normal, float width, float height) {
            this.center = center;
            this.normal.set(normal).normalize();
            this.width = width;
            this.height = height;
            return this;
        }

        public void register(Identifier faceId) {
            final Vec3 c = center;
            final Vector3f n = new Vector3f(normal);
            final float w = width, h = height;
            InstancedMesh.builder(BuiltinShader.TRANSFORM)
                    .geometry(MeshData.quad())
                    .shaders(SCREEN_SHADER, SCREEN_SHADER)
                    .extraSampler("ReflectionSampler", reflectionId, 1)
                    .renderState(RenderState.builder()
                            .depthTest(true).depthWrite(true)
                            .blend(RenderState.BlendMode.NONE)
                            .backfaceCulling(false)
                            .build())
                    .phase(InstancePhase.WORLD_LAST)
                    .onRender((ctx, batch) -> {
                        Vector3f right = new Vector3f(n).cross(0f, 1f, 0f);
                        if (right.lengthSquared() < 1e-6f) right.set(1f, 0f, 0f);
                        right.normalize();
                        Vector3f up = new Vector3f(right).cross(n).normalize();
                        Vec3 cam = ctx.cameraPos();
                        Matrix4f m = new Matrix4f();
                        m.m00(right.x * w); m.m01(right.y * w); m.m02(right.z * w);
                        m.m10(n.x);         m.m11(n.y);         m.m12(n.z);
                        m.m20(up.x * h);    m.m21(up.y * h);    m.m22(up.z * h);
                        m.m30((float) (c.x - cam.x)); m.m31((float) (c.y - cam.y)); m.m32((float) (c.z - cam.z));
                        batch.add(new BuiltinShader.Transform(m));
                    })
                    .register(faceId);
        }
    }
}

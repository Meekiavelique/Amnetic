package com.meekdev.amnetic.client.scene;

import com.meekdev.amnetic.client.instanced.InstanceLayout;
import com.meekdev.amnetic.client.instanced.InstanceRenderContext;
import com.meekdev.amnetic.client.instanced.InstanceWriter;
import com.meekdev.amnetic.client.instanced.InstancedMesh;
import com.meekdev.amnetic.client.instanced.MeshData;
import com.meekdev.amnetic.client.instanced.RenderState;
import com.meekdev.amnetic.client.instanced.InstancePhase;
import com.meekdev.amnetic.client.instanced.internal.InstanceMeshRegistry;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.joml.Vector4fc;

public final class CaptureSurface {

    public static final Identifier SHADER = Identifier.fromNamespaceAndPath("amnetic", "scene/capture_surface");

    public enum Sampling { FIT, SCREEN }

    public static final class Mask {
        static final int NONE = 0, CIRCLE = 1, TEXTURE = 2;
        private static final Mask NONE_MASK = new Mask(NONE, 0f, 0f, null);

        final int mode;
        final float feather;
        final float radius;
        final Identifier texture;

        private Mask(int mode, float feather, float radius, Identifier texture) {
            this.mode = mode;
            this.feather = feather;
            this.radius = radius;
            this.texture = texture;
        }

        public static Mask none() {
            return NONE_MASK;
        }

        public static Mask circle() {
            return circle(0.02f, 1f);
        }

        public static Mask circle(float feather) {
            return circle(feather, 1f);
        }

        public static Mask circle(float feather, float radius) {
            return new Mask(CIRCLE, Math.max(0f, feather), Math.max(0f, radius), null);
        }

        public static Mask texture(Identifier alphaMask) {
            if (alphaMask == null) throw new IllegalArgumentException("mask texture id is required");
            return new Mask(TEXTURE, 0f, 0f, alphaMask);
        }
    }

    public record Placement(Vec3 center, Vector3f normal, float width, float height) {}

    @FunctionalInterface
    public interface PlacementSupplier {
        Placement placement(InstanceRenderContext ctx);
    }

    private static final InstanceLayout LAYOUT = InstanceLayout.builder().mat4(1).vec4(5).build();
    private static final InstanceWriter<SurfaceInstance> WRITER =
            (i, p) -> p.putMat4(i.transform()).putVec4(i.params());

    private record SurfaceInstance(Matrix4fc transform, Vector4fc params) {}

    private final Identifier surfaceId;
    private volatile boolean enabled = true;

    private CaptureSurface(Identifier surfaceId) {
        this.surfaceId = surfaceId;
    }

    public Identifier surfaceId() {
        return surfaceId;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public CaptureSurface setEnabled(boolean enabled) {
        this.enabled = enabled;
        return this;
    }

    public void remove() {
        enabled = false;
        InstanceMeshRegistry.INSTANCE.unregister(surfaceId);
    }

    public static Builder builder(Identifier feedId) {
        return new Builder(feedId);
    }

    public static final class Builder {
        private final Identifier feedId;
        private MeshData geometry = MeshData.quad();
        private Mask mask = Mask.none();
        private Sampling sampling = Sampling.FIT;
        private InstancePhase phase = InstancePhase.WORLD_LAST;
        private PlacementSupplier placement;

        private Builder(Identifier feedId) {
            if (feedId == null) throw new IllegalArgumentException("feed id is required");
            this.feedId = feedId;
        }

        public Builder geometry(MeshData geometry) {
            this.geometry = geometry;
            return this;
        }

        public Builder mask(Mask mask) {
            this.mask = mask == null ? Mask.none() : mask;
            return this;
        }

        public Builder sampling(Sampling sampling) {
            this.sampling = sampling;
            return this;
        }

        public Builder phase(InstancePhase phase) {
            this.phase = phase;
            return this;
        }

        public Builder placement(PlacementSupplier supplier) {
            this.placement = supplier;
            return this;
        }

        public Builder at(Vec3 center, Vector3f normal, float width, float height) {
            if (width <= 0f || height <= 0f) {
                throw new IllegalArgumentException("width and height must be > 0");
            }
            Placement fixed = new Placement(center, new Vector3f(normal).normalize(), width, height);
            this.placement = ctx -> fixed;
            return this;
        }

        public CaptureSurface register(Identifier surfaceId) {
            if (geometry == null) throw new IllegalStateException("geometry must be set");
            if (geometry.hasTextureCoordinates()) {
                throw new IllegalStateException("CaptureSurface needs position-only geometry; "
                        + "surface UVs are derived from the local mesh, so textured meshes are not supported");
            }
            if (placement == null) throw new IllegalStateException("placement(...) or at(...) is required");

            final PlacementSupplier supplier = placement;
            final Vector4f params = new Vector4f(
                    sampling == Sampling.SCREEN ? 1f : 0f,
                    mask.mode,
                    mask.feather,
                    mask.radius);

            CaptureSurface surface = new CaptureSurface(surfaceId);

            var builder = InstancedMesh.builder(LAYOUT, WRITER)
                    .geometry(geometry)
                    .shaders(SHADER, SHADER)
                    .extraSampler("CaptureSampler", feedId, 1)
                    .renderState(RenderState.builder()
                            .depthTest(true).depthWrite(true)
                            .blend(RenderState.BlendMode.ALPHA)
                            .backfaceCulling(false)
                            .build())
                    .phase(phase)
                    .onRender((ctx, batch) -> {
                        if (!surface.enabled) return;
                        Placement pl = supplier.placement(ctx);
                        if (pl == null) return;
                        batch.add(new SurfaceInstance(modelMatrix(pl, ctx.cameraPos()), params));
                    });

            if (mask.mode == Mask.TEXTURE) {
                builder.extraSampler("MaskSampler", mask.texture, 2, true);
            }

            builder.register(surfaceId);
            return surface;
        }
    }

    private static Matrix4f modelMatrix(Placement pl, Vec3 cam) {
        Vector3f n = new Vector3f(pl.normal()).normalize();
        Vector3f right = new Vector3f(n).cross(0f, 1f, 0f);
        if (right.lengthSquared() < 1e-6f) right.set(1f, 0f, 0f);
        right.normalize();
        Vector3f up = new Vector3f(right).cross(n).normalize();
        float w = pl.width(), h = pl.height();
        Vec3 c = pl.center();
        Matrix4f m = new Matrix4f();
        m.m00(right.x * w); m.m01(right.y * w); m.m02(right.z * w);
        m.m10(n.x); m.m11(n.y); m.m12(n.z);
        m.m20(up.x * h); m.m21(up.y * h); m.m22(up.z * h);
        m.m30((float) (c.x - cam.x)); m.m31((float) (c.y - cam.y)); m.m32((float) (c.z - cam.z));
        return m;
    }
}

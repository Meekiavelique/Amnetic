package com.meekdev.amnetic.client.scene;

import com.meekdev.amnetic.client.instanced.InstancePhase;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

public final class CameraFeed {

    public record Shot(Vec3 lens, Matrix4f viewRotation, Vec3 screenCentre, Vector3f screenNormal,
                       float screenWidth, float screenHeight, float distance) {
    }

    @FunctionalInterface
    public interface Aimer {
        Shot aim(CaptureContext context);
    }

    private final PerspectiveCapture capture;
    private final CaptureSurface surface;
    private final Aimer aimer;
    private final float clipOffset;
    private final long refreshNanos;
    private final float maxDistance;

    private Shot shot;
    private long lastRender;

    private CameraFeed(Builder builder, Identifier id) {
        this.aimer = builder.aimer;
        this.clipOffset = builder.clipOffset;
        this.refreshNanos = builder.refreshNanos;
        this.maxDistance = builder.maxDistance;

        this.capture = PerspectiveCapture.builder()
                .resolution(builder.resolution)
                .onCapture((context, view) -> {
                    shot = aimer.aim(context);
                    if (shot == null || shot.distance() > maxDistance) {
                        return CaptureResult.SKIP;
                    }
                    long now = System.nanoTime();
                    if (refreshNanos > 0L && now - lastRender < refreshNanos) {
                        return CaptureResult.SKIP;
                    }
                    lastRender = now;

                    Vector3f look = forward(shot.viewRotation());
                    Vec3 eye = shot.lens().add(look.x * clipOffset, look.y * clipOffset,
                            look.z * clipOffset);
                    view.eye(eye);
                    view.viewRotation(shot.viewRotation());
                    view.matchMainProjection();
                    if (clipOffset > 0.0F) {
                        Vec3 plane = shot.lens();
                        view.clipPlane(look.x, look.y, look.z,
                                -(float) (look.x * plane.x + look.y * plane.y + look.z * plane.z));
                    }
                    view.distance(shot.distance());
                    return CaptureResult.RENDER;
                })
                .register(id);

        this.surface = CaptureSurface.builder(id)
                .phase(builder.phase)
                .placement(context -> {
                    Shot current = shot;
                    if (current == null || current.distance() > maxDistance) {
                        return null;
                    }
                    return new CaptureSurface.Placement(current.screenCentre(),
                            current.screenNormal(), current.screenWidth(), current.screenHeight());
                })
                .register(Identifier.fromNamespaceAndPath(id.getNamespace(), id.getPath() + "_surface"));
    }

    private static Vector3f forward(Matrix4f viewRotation) {
        Matrix4f inverse = new Matrix4f(viewRotation).invert();
        return inverse.transformDirection(new Vector3f(0.0F, 0.0F, -1.0F)).normalize();
    }

    public PerspectiveCapture capture() {
        return capture;
    }

    public CaptureSurface surface() {
        return surface;
    }

    public void remove() {
        surface.remove();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        private Aimer aimer = context -> null;
        private float resolution = 0.5F;
        private float clipOffset = 0.55F;
        private long refreshNanos = 100_000_000L;
        private float maxDistance = 48.0F;
        private InstancePhase phase = InstancePhase.WORLD_TRANSLUCENT;

        public Builder aim(Aimer aimer) {
            this.aimer = aimer;
            return this;
        }

        public Builder resolution(float resolution) {
            this.resolution = resolution;
            return this;
        }

        public Builder clipOffset(float blocks) {
            this.clipOffset = blocks;
            return this;
        }

        public Builder refreshHz(float hz) {
            this.refreshNanos = hz <= 0.0F ? 0L : (long) (1_000_000_000.0 / hz);
            return this;
        }

        public Builder maxDistance(float blocks) {
            this.maxDistance = blocks;
            return this;
        }

        public Builder phase(InstancePhase phase) {
            this.phase = phase;
            return this;
        }

        public CameraFeed register(Identifier id) {
            return new CameraFeed(this, id);
        }
    }
}

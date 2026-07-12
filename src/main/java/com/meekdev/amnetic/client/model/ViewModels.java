package com.meekdev.amnetic.client.model;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;

import com.meekdev.amnetic.client.instanced.internal.MainTargetFramebuffer;
import com.meekdev.amnetic.client.model.internal.OffscreenModelRenderer;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.lwjgl.opengl.GL11;

/**
 * renders arbitrary models in first-person view space, independent of the held item.
 *
 * <p>{@link HandModels} binds a model to an item and tracks the vanilla hand pose. ViewModels is for
 * persistent view-space geometry a game owns directly (first-person weapon, arms, a held tool) that
 * renders every frame regardless of inventory. handles draw at the vanilla first-person FOV (shared
 * via {@link #captureProjection}) with the camera at origin looking down -Z, so a handle's
 * {@link Handle#offset} is camera space (x right, y up, +z forward).
 *
 * <p>to avoid clipping into world geometry each handle either clears depth before drawing
 * ({@link Handle#depthClear}, the default, same as the vanilla hand) or squeezes itself into the
 * front of the depth range ({@link Handle#depthRange}), which keeps self-occlusion without a full clear
 */
public final class ViewModels {

    private static final OffscreenModelRenderer RENDERER = new OffscreenModelRenderer();
    private static final Matrix4f capturedProjection = new Matrix4f();
    private static final List<Handle> handles = new CopyOnWriteArrayList<>();

    private ViewModels() {}

    public static Handle add(Model model) {
        return add(() -> model);
    }

    public static Handle add(Supplier<Model> modelSupplier) {
        Handle handle = new Handle(modelSupplier);
        handles.add(handle);
        return handle;
    }

    // loads a model by resource id and adds it as a view-model in one call
    public static Handle add(Identifier modelId) {
        return add(new LazyModel(modelId));
    }

    public static void remove(Handle handle) {
        handles.remove(handle);
    }

    public static void clear() {
        handles.clear();
    }

    // vanilla first-person hand projection, captured each frame by the GameRenderer mixin
    public static void captureProjection(Matrix4fc projection) {
        capturedProjection.set(projection);
    }

    // draws every visible handle, invoked from the GameRenderer mixin right after the vanilla hand
    public static void render() {
        if (handles.isEmpty()) {
            return;
        }
        int prevFbo = MainTargetFramebuffer.bind();
        if (prevFbo == -1) {
            return;
        }
        boolean depthRangeChanged = false;
        try {
            for (Handle handle : handles) {
                if (!handle.visible) {
                    continue;
                }
                Model model = handle.model();
                if (model == null || !model.isReady()) {
                    continue;
                }
                Matrix4f world = handle.worldMatrix(model);

                if (handle.depthClear) {
                    GL11.glClear(GL11.GL_DEPTH_BUFFER_BIT);
                } else if (handle.depthRanged) {
                    GL11.glDepthRange(handle.depthNear, handle.depthFar);
                    depthRangeChanged = true;
                }
                RENDERER.draw(model.internalGpu(), capturedProjection, world, null,
                        handle.blockLight, handle.skyLight, handle.emissiveStrength);
                if (depthRangeChanged) {
                    GL11.glDepthRange(0.0, 1.0);
                    depthRangeChanged = false;
                }
            }
        } finally {
            if (depthRangeChanged) {
                GL11.glDepthRange(0.0, 1.0);
            }
            MainTargetFramebuffer.restore(prevFbo);
        }
    }

    public static void dispose() {
        handles.clear();
        RENDERER.close();
    }

    private static final class LazyModel implements Supplier<Model> {
        private final Identifier id;
        private Model model;

        private LazyModel(Identifier id) {
            this.id = id;
        }

        @Override
        public Model get() {
            if (model == null) {
                model = Models.load(id);
            }
            return model;
        }
    }

    public static final class Handle {
        private final Supplier<Model> modelSupplier;
        private Model resolved;
        private volatile boolean visible = true;

        private float scale = 0.4f;
        private float fitRadius = -1f;
        private float offsetX = 0f;
        private float offsetY = 0f;
        private float offsetZ = 0f;
        private float pitch = 0f;
        private float yaw = 0f;
        private float roll = 0f;
        private float blockLight = 1f;
        private float skyLight = 0f;
        private float emissiveStrength = 1f;
        private Matrix4fc override;

        private boolean depthClear = true;
        private boolean depthRanged = false;
        private double depthNear = 0.0;
        private double depthFar = 0.1;

        private Handle(Supplier<Model> modelSupplier) {
            this.modelSupplier = modelSupplier;
        }

        private Model model() {
            if (resolved == null) {
                resolved = modelSupplier.get();
            }
            return resolved;
        }

        public Handle visible(boolean visible) {
            this.visible = visible;
            return this;
        }

        public boolean isVisible() {
            return visible;
        }

        public Handle scale(float scale) {
            this.scale = scale;
            this.fitRadius = -1f;
            return this;
        }

        // scales the model so its bounding radius becomes targetRadius (view-space units)
        public Handle fit(float targetRadius) {
            this.fitRadius = targetRadius;
            return this;
        }

        // camera-space offset: x right, y up, +z forward (toward the scene)
        public Handle offset(float x, float y, float z) {
            this.offsetX = x;
            this.offsetY = y;
            this.offsetZ = z;
            return this;
        }

        public Handle rotation(float pitchDeg, float yawDeg, float rollDeg) {
            this.pitch = (float) Math.toRadians(pitchDeg);
            this.yaw = (float) Math.toRadians(yawDeg);
            this.roll = (float) Math.toRadians(rollDeg);
            return this;
        }

        public Handle light(float block, float sky) {
            this.blockLight = block;
            this.skyLight = sky;
            return this;
        }

        public Handle emissive(float strength) {
            this.emissiveStrength = strength;
            return this;
        }

        /**
         * full camera-space transform, bypassing the offset/rotation/scale builder. maps model space
         * to view space (camera at origin, -Z forward). null reverts to the builder fields
         */
        public Handle transform(Matrix4fc cameraSpace) {
            this.override = cameraSpace == null ? null : new Matrix4f(cameraSpace);
            return this;
        }

        // clears depth before drawing so the model never clips the world (default). ignores world
        // depth entirely so it can visually pop through walls
        public Handle depthClear() {
            this.depthClear = true;
            this.depthRanged = false;
            return this;
        }

        /**
         * instead of clearing depth, squeezes the model into the front slice [near, far] of the depth
         * range so it self-occludes correctly and stays in front of the world without a full clear.
         * typical values are a thin slice like (0.0, 0.1)
         */
        public Handle depthRange(double near, double far) {
            this.depthNear = near;
            this.depthFar = far;
            this.depthRanged = true;
            this.depthClear = false;
            return this;
        }

        // draws against the existing world depth (no clear, no range) so world geometry occludes the model
        public Handle overlayOnWorld() {
            this.depthClear = false;
            this.depthRanged = false;
            return this;
        }

        private Matrix4f worldMatrix(Model model) {
            if (override != null) {
                return new Matrix4f(override);
            }
            float effectiveScale = fitRadius > 0f ? fitRadius / model.radius() : scale;
            Matrix4f m = new Matrix4f()
                    .translate(offsetX, offsetY, offsetZ)
                    .rotateXYZ(pitch, yaw, roll)
                    .scale(effectiveScale);
            m.translate(-model.center().x, -model.center().y, -model.center().z);
            return m;
        }
    }
}

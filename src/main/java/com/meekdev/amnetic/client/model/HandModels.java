package com.meekdev.amnetic.client.model;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import com.meekdev.amnetic.client.instanced.internal.MainTargetFramebuffer;
import com.meekdev.amnetic.client.model.internal.OffscreenModelRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.lwjgl.opengl.GL11;

public final class HandModels {

    private static final Map<Item, Binding> BINDINGS = new ConcurrentHashMap<>();
    private static final OffscreenModelRenderer RENDERER = new OffscreenModelRenderer();

    private static final Matrix4f capturedPose = new Matrix4f();
    private static final Matrix4f capturedProjection = new Matrix4f();
    private static Item activeItem;

    private HandModels() {
    }

    public static Binding bind(Item item, Model model) {
        Binding binding = new Binding(() -> model);
        BINDINGS.put(item, binding);
        return binding;
    }

    public static Binding bind(Item item, Supplier<Model> modelSupplier) {
        Binding binding = new Binding(modelSupplier);
        BINDINGS.put(item, binding);
        return binding;
    }

    public static void unbind(Item item) {
        BINDINGS.remove(item);
    }

    public static void beginFrame() {
        activeItem = null;
    }

    public static void captureProjection(Matrix4fc projection) {
        capturedProjection.set(projection);
    }

    public static boolean captureHeld(LivingEntity entity, ItemStack stack, ItemDisplayContext ctx, PoseStack pose) {
        if (entity != Minecraft.getInstance().player || !ctx.firstPerson()) {
            return false;
        }
        Binding binding = BINDINGS.get(stack.getItem());
        if (binding == null) {
            return false;
        }
        capturedPose.set(pose.last().pose());
        activeItem = stack.getItem();
        return true;
    }

    public static void render() {
        Item item = activeItem;
        if (item == null) {
            return;
        }
        Binding binding = BINDINGS.get(item);
        if (binding == null) {
            return;
        }
        Model model = binding.model();
        if (model == null || !model.isReady()) {
            return;
        }

        Matrix4f world = new Matrix4f(capturedPose).mul(binding.baseTransform(model));

        int prevFbo = MainTargetFramebuffer.bind();
        if (prevFbo == -1) {
            return;
        }
        try {
            if (binding.depthClear) {
                GL11.glClear(GL11.GL_DEPTH_BUFFER_BIT);
            }
            RENDERER.draw(model.internalGpu(), capturedProjection, world, null,
                    binding.blockLight, binding.skyLight, binding.emissiveStrength);
        } finally {
            MainTargetFramebuffer.restore(prevFbo);
        }
    }

    public static void dispose() {
        BINDINGS.clear();
        activeItem = null;
        RENDERER.close();
    }

    public static final class Binding {
        private final Supplier<Model> modelSupplier;
        private Model resolved;
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
        private boolean depthClear = true;

        private Binding(Supplier<Model> modelSupplier) {
            this.modelSupplier = modelSupplier;
        }

        private Model model() {
            if (resolved == null) {
                resolved = modelSupplier.get();
            }
            return resolved;
        }

        public Binding scale(float scale) {
            this.scale = scale;
            this.fitRadius = -1f;
            return this;
        }

        public Binding fit(float targetRadius) {
            this.fitRadius = targetRadius;
            return this;
        }

        public Binding offset(float x, float y, float z) {
            this.offsetX = x;
            this.offsetY = y;
            this.offsetZ = z;
            return this;
        }

        public Binding rotation(float pitchDeg, float yawDeg, float rollDeg) {
            this.pitch = (float) Math.toRadians(pitchDeg);
            this.yaw = (float) Math.toRadians(yawDeg);
            this.roll = (float) Math.toRadians(rollDeg);
            return this;
        }

        public Binding light(float block, float sky) {
            this.blockLight = block;
            this.skyLight = sky;
            return this;
        }

        public Binding emissive(float strength) {
            this.emissiveStrength = strength;
            return this;
        }

        public Binding overlayOnWorld() {
            this.depthClear = false;
            return this;
        }

        private Matrix4f baseTransform(Model model) {
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

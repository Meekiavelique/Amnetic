package com.example.instanced;

import com.meekdev.amnetic.client.instanced.BuiltinShader;
import com.meekdev.amnetic.client.instanced.InstancePhase;
import com.meekdev.amnetic.client.instanced.InstancedMesh;
import com.meekdev.amnetic.client.instanced.MeshData;
import com.meekdev.amnetic.client.instanced.RenderState;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.lwjgl.glfw.GLFW;

public final class InstanceCullDemo {

    private static final Identifier CPU_FIELD = Identifier.fromNamespaceAndPath("example", "cull_cpu");
    private static final Identifier GPU_FIELD = Identifier.fromNamespaceAndPath("example", "cull_gpu");

    private static final float CUBE_SCALE = 0.3f;
    private static final float CUBE_RADIUS = 0.6f;
    private static final int[] FIELD_SIZES = {64, 96, 128, 192};

    private static boolean active;
    private static boolean gpuMode = true;
    private static int sizeIndex = 1;

    private static boolean originSet;
    private static int originX, originY, originZ;

    private static boolean kDown, lDown, oDown;

    private InstanceCullDemo() {}

    public static void init() {
        registerCpuField();
        registerGpuField();

        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            boolean k = InputConstants.isKeyDown(mc.getWindow(), GLFW.GLFW_KEY_K);
            boolean l = InputConstants.isKeyDown(mc.getWindow(), GLFW.GLFW_KEY_L);
            boolean o = InputConstants.isKeyDown(mc.getWindow(), GLFW.GLFW_KEY_O);

            if (k && !kDown) toggle(mc);
            if (l && !lDown && active) {
                gpuMode = !gpuMode;
                rebuildStaticField();
                overlay(mc, "Instance cull: " + modeLabel());
            }
            if (o && !oDown && active) {
                sizeIndex = (sizeIndex + 1) % FIELD_SIZES.length;
                rebuildStaticField();
                overlay(mc, "Instance field: " + cubeCount() + " cubes");
            }

            kDown = k;
            lDown = l;
            oDown = o;
        });
    }

    private static void toggle(Minecraft mc) {
        active = !active;
        if (active && mc.player != null) {
            Vec3 p = mc.player.position();
            originX = (int) Math.floor(p.x);
            originY = (int) Math.floor(p.y) - 1;
            originZ = (int) Math.floor(p.z);
            originSet = true;
            overlay(mc, "Instance cull ON: " + cubeCount() + " cubes, " + modeLabel() + " (L switch, O resize)");
        } else {
            overlay(mc, "Instance cull OFF");
        }
        rebuildStaticField();
    }

    private static void rebuildStaticField() {
        InstancedMesh.invalidate(GPU_FIELD);
    }

    private static void registerCpuField() {
        InstancedMesh.builder(BuiltinShader.TRANSFORM_COLOR)
                .geometry(MeshData.unitCube())
                .phase(InstancePhase.WORLD_LAST)
                .renderState(RenderState.DEFAULT)
                .onRender((ctx, batch) -> {
                    if (!active || !originSet || gpuMode) return;

                    int radius = FIELD_SIZES[sizeIndex];
                    float y = originY;
                    for (int dx = -radius; dx <= radius; dx++) {
                        for (int dz = -radius; dz <= radius; dz++) {
                            double wx = originX + dx + 0.5;
                            double wz = originZ + dz + 0.5;

                            if (!batch.visible(wx, y, wz, CUBE_RADIUS)) continue;

                            Matrix4f transform = ctx.worldToModel(wx, y, wz).scale(CUBE_SCALE);
                            batch.add(new BuiltinShader.TransformColor(transform, colorAt(dx, dz)));
                        }
                    }
                })
                .register(CPU_FIELD);
    }

    private static void registerGpuField() {
        InstancedMesh.builder(BuiltinShader.TRANSFORM_COLOR)
                .geometry(MeshData.unitCube())
                .phase(InstancePhase.WORLD_LAST)
                .renderState(RenderState.DEFAULT)
                .staticInstances()
                .worldSpace()
                .gpuCull()
                .onRender((ctx, batch) -> {
                    if (!active || !originSet || !gpuMode) return;

                    int radius = FIELD_SIZES[sizeIndex];
                    float y = originY;
                    for (int dx = -radius; dx <= radius; dx++) {
                        for (int dz = -radius; dz <= radius; dz++) {
                            double wx = originX + dx + 0.5;
                            double wz = originZ + dz + 0.5;

                            Matrix4f transform = new Matrix4f().translation((float) wx, y, (float) wz).scale(CUBE_SCALE);
                            batch.add(new BuiltinShader.TransformColor(transform, colorAt(dx, dz)), wx, y, wz, CUBE_RADIUS);
                        }
                    }
                })
                .register(GPU_FIELD);
    }

    private static Vector4f colorAt(int dx, int dz) {
        float r = Math.floorMod(dx, 16) / 15f;
        float g = Math.floorMod(dz, 16) / 15f;
        return new Vector4f(r, g, 1f - r, 1f);
    }

    private static int cubeCount() {
        int side = FIELD_SIZES[sizeIndex] * 2 + 1;
        return side * side;
    }

    private static String modeLabel() {
        return gpuMode ? "GPU cull (static, compute + indirect)" : "CPU cull (dynamic, batch.visible)";
    }

    private static void overlay(Minecraft mc, String text) {
        if (mc.gui != null) {
            mc.gui.setOverlayMessage(Component.literal(text), false);
        }
    }
}

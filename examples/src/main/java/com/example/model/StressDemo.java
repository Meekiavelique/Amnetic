package com.example.model;

import com.meekdev.amnetic.client.instanced.BuiltinShader;
import com.meekdev.amnetic.client.instanced.InstancePhase;
import com.meekdev.amnetic.client.instanced.InstancedMesh;
import com.meekdev.amnetic.client.instanced.MeshData;
import com.meekdev.amnetic.client.instanced.RenderState;
import com.meekdev.amnetic.client.model.Animator;
import com.meekdev.amnetic.client.model.Model;
import com.meekdev.amnetic.client.model.Models;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.lwjgl.glfw.GLFW;

public final class StressDemo {

    private static final Identifier FOX = Identifier.fromNamespaceAndPath("example", "models3d/fox.glb");
    private static final Identifier FIELD_ID = Identifier.fromNamespaceAndPath("example", "stress_field");

    private static final float FOX_SCALE = 0.02f;
    private static final int FOX_SPACING = 3;
    private static final int CUBE_RADIUS = 40;

    private static boolean active;
    private static boolean animate;
    private static int gridHalf = 6;
    private static boolean f7Down, f8Down, f9Down;

    private static Model fox;
    private static boolean shadowsDisabled;
    private static Matrix4f[] frozenPose;
    private static boolean frozenComputed;
    private static Animator liveAnimator;
    private static Matrix4f[] livePose;

    private static boolean originSet;
    private static int originX, originY, originZ;
    private static final Matrix4f worldScratch = new Matrix4f();

    private StressDemo() {}

    public static void init() {
        registerCubeField();
        Models.onFrame(ctx -> submitCrowd());
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            boolean f7 = InputConstants.isKeyDown(mc.getWindow(), GLFW.GLFW_KEY_F7);
            boolean f8 = InputConstants.isKeyDown(mc.getWindow(), GLFW.GLFW_KEY_F8);
            boolean f9 = InputConstants.isKeyDown(mc.getWindow(), GLFW.GLFW_KEY_F9);
            if (f7 && !f7Down) toggle(mc);
            if (f8 && !f8Down && active) {
                gridHalf = gridHalf >= 20 ? 4 : gridHalf + 4;
                overlay(mc, "Stress crowd: " + (2 * gridHalf) * (2 * gridHalf) + " foxes");
            }
            if (f9 && !f9Down) {
                animate = !animate;
                overlay(mc, animate ? "Crowd ANIMATED (will ghost under TAA until motion vectors)"
                        : "Crowd STATIC (clean batching/culling test)");
            }
            f7Down = f7;
            f8Down = f8;
            f9Down = f9;
        });
    }

    private static void toggle(Minecraft mc) {
        active = !active;
        if (active) {
            if (fox == null) fox = Models.load(FOX);
            if (!shadowsDisabled) { fox.config().castsShadow(false); shadowsDisabled = true; }
            if (mc.player != null) {
                Vec3 p = mc.player.position();
                originX = (int) Math.floor(p.x);
                originY = (int) Math.floor(p.y);
                originZ = (int) Math.floor(p.z);
                originSet = true;
            }
            overlay(mc, "Stress scene ON (" + (2 * gridHalf) * (2 * gridHalf) + " foxes; F8 resize, F9 animate)");
        } else {
            overlay(mc, "Stress scene OFF");
        }
    }

    private static void submitCrowd() {
        if (!active || fox == null || !fox.isReady() || !originSet) return;

        Matrix4f[] pose = animate ? livePose() : frozenPose();

        for (int gx = -gridHalf; gx < gridHalf; gx++) {
            for (int gz = -gridHalf; gz < gridHalf; gz++) {
                float wx = originX + gx * FOX_SPACING + 0.5f;
                float wz = originZ + gz * FOX_SPACING + 0.5f;
                float yaw = Math.floorMod(gx * 37 + gz * 61, 360);
                worldScratch.translation(wx, originY, wz)
                        .rotateY((float) Math.toRadians(yaw))
                        .scale(FOX_SCALE);
                if (pose != null) fox.renderPosed(worldScratch, pose);
                else fox.render(worldScratch);
            }
        }
    }

    private static Matrix4f[] frozenPose() {
        if (frozenComputed) return frozenPose;
        frozenComputed = true;
        if (!fox.isAnimated()) return null;
        Animator a = fox.createAnimator().play(fox.firstClip()).loop(false);
        a.setTime(0.4f);
        Matrix4f[] p = a.pose();
        frozenPose = new Matrix4f[p.length];
        for (int i = 0; i < p.length; i++) frozenPose[i] = new Matrix4f(p[i]);
        return frozenPose;
    }

    private static Matrix4f[] livePose() {
        if (!fox.isAnimated()) return frozenPose();
        if (liveAnimator == null) liveAnimator = fox.createAnimator().play(fox.firstClip()).loop(true);
        float dt = Minecraft.getInstance().getDeltaTracker().getRealtimeDeltaTicks() / 20f;
        liveAnimator.update(dt);
        livePose = liveAnimator.pose();
        return livePose;
    }

    private static void registerCubeField() {
        InstancedMesh.builder(BuiltinShader.TRANSFORM_COLOR)
                .geometry(MeshData.unitCube())
                .phase(InstancePhase.WORLD_LAST)
                .renderState(RenderState.DEFAULT)
                .onRender((ctx, batch) -> {
                    if (!active || !originSet) return;
                    float y = originY - 1f;
                    for (int dx = -CUBE_RADIUS; dx <= CUBE_RADIUS; dx++) {
                        for (int dz = -CUBE_RADIUS; dz <= CUBE_RADIUS; dz++) {
                            double wx = originX + dx + 0.5;
                            double wz = originZ + dz + 0.5;
                            if (!batch.visible(wx, y, wz, 0.6f)) continue;
                            Matrix4f t = ctx.worldToModel(wx, y, wz).scale(0.3f);
                            float r = Math.floorMod(dx, 16) / 15f;
                            float g = Math.floorMod(dz, 16) / 15f;
                            batch.add(new BuiltinShader.TransformColor(t, new Vector4f(r, g, 1f - r, 1f)));
                        }
                    }
                })
                .register(FIELD_ID);
    }

    private static void overlay(Minecraft mc, String text) {
        if (mc.gui != null) {
            mc.gui.setOverlayMessage(Component.literal(text), false);
        }
    }
}

package com.example.geometry;

import com.meekdev.amnetic.client.geometry.EntityMeshTap;
import com.meekdev.amnetic.client.geometry.PosedMesh;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

public final class MeshParticlesDemo {

    private static final int VERTEX_STRIDE = 18;

    private static PosedMesh tap;
    private static boolean f9Down;

    private MeshParticlesDemo() {}

    public static void init() {
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            if (mc.player == null) { stop(); return; }
            boolean down = InputConstants.isKeyDown(mc.getWindow(), GLFW.GLFW_KEY_F9);
            if (down && !f9Down) {
                if (isActive()) stop(); else start(mc.player);
            }
            f9Down = down;
            emit();
        });
    }

    private static void start(Entity entity) {
        tap = EntityMeshTap.tap(entity);
    }

    private static void stop() {
        if (tap != null) { tap.remove(); tap = null; }
    }

    private static boolean isActive() {
        return tap != null && !tap.isRemoved();
    }

    private static void emit() {
        if (!isActive()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        int n = tap.vertexCount();
        if (n == 0) return;

        Camera cam = mc.gameRenderer.getMainCamera();
        Vec3 c = cam.position();
        float[] pos = tap.positions();
        float[] nrm = tap.normals();

        for (int v = 0; v < n; v += VERTEX_STRIDE) {
            int pi = v * 3;
            double wx = c.x + pos[pi];
            double wy = c.y + pos[pi + 1];
            double wz = c.z + pos[pi + 2];
            double speed = 0.02;
            mc.level.addParticle(ParticleTypes.END_ROD, wx, wy, wz,
                    nrm[pi] * speed, nrm[pi + 1] * speed, nrm[pi + 2] * speed);
        }
    }
}

package com.example.geometry;

import com.meekdev.amnetic.client.geometry.EntityMeshTap;
import com.meekdev.amnetic.client.geometry.PosedMesh;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class MeshTapDemo {

    private static final Logger LOG = LoggerFactory.getLogger("Amnetic/MeshTap");
    private static PosedMesh tap;
    private static boolean f8Down;
    private static int ticks;

    private MeshTapDemo() {}

    public static void init() {
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            if (mc.player == null) {
                if (tap != null) { tap.remove(); tap = null; }
                return;
            }
            boolean down = InputConstants.isKeyDown(mc.getWindow(), GLFW.GLFW_KEY_F8);
            if (down && !f8Down) {
                if (tap == null) {
                    tap = EntityMeshTap.tap(mc.player);
                    LOG.info("[MeshTap] enabled on local player");
                } else {
                    tap.remove();
                    tap = null;
                    LOG.info("[MeshTap] disabled");
                }
            }
            f8Down = down;

            if (tap != null && ++ticks % 20 == 0) {
                int n = tap.vertexCount();
                if (n > 0) {
                    float[] p = tap.positions();
                    float[] uv = tap.uvs();
                    LOG.info("[MeshTap] {} verts | v0 pos=({}, {}, {}) uv=({}, {})",
                            n, p[0], p[1], p[2], uv[0], uv[1]);
                } else {
                    LOG.info("[MeshTap] tap active but 0 verts captured (not rendered this frame?)");
                }
            }
        });
    }
}

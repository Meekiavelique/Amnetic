package com.meekdev.amnetic.client.post.internal;

import net.minecraft.client.gl.Framebuffer;
import net.minecraft.util.Identifier;

final class WorldDepthSnapshot {

    static final Identifier TARGET_ID = Identifier.of("amnetic", "world_depth_snapshot");
    private static final DepthSnapshot SNAPSHOT = new DepthSnapshot("amnetic_world_depth_snapshot");

    static void capture(Framebuffer source) {
        SNAPSHOT.capture(source);
    }

    static boolean restoreInto(Framebuffer target) {
        return SNAPSHOT.restoreInto(target);
    }

    static Framebuffer getFramebuffer() {
        return SNAPSHOT.getFramebuffer();
    }

    private WorldDepthSnapshot() {}
}

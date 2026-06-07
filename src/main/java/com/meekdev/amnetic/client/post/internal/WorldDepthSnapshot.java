package com.meekdev.amnetic.client.post.internal;

import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.resources.Identifier;

final class WorldDepthSnapshot {

    static final Identifier TARGET_ID = Identifier.fromNamespaceAndPath("amnetic", "world_depth_snapshot");
    private static final DepthSnapshot SNAPSHOT = new DepthSnapshot("amnetic_world_depth_snapshot");

    static void capture(RenderTarget source) {
        SNAPSHOT.capture(source);
    }

    static boolean restoreInto(RenderTarget target) {
        return SNAPSHOT.restoreInto(target);
    }

    static RenderTarget getFramebuffer() {
        return SNAPSHOT.getFramebuffer();
    }

    private WorldDepthSnapshot() {}
}

package com.meekdev.amnetic.client.post.internal;

import net.minecraft.client.gl.Framebuffer;

final class PostRenderDepthSnapshot {

    private static final DepthSnapshot SNAPSHOT = new DepthSnapshot("amnetic_post_render_depth_snapshot");

    static void capture(Framebuffer source) {
        SNAPSHOT.capture(source);
    }

    static boolean restoreInto(Framebuffer target) {
        return SNAPSHOT.restoreInto(target);
    }

    private PostRenderDepthSnapshot() {}
}

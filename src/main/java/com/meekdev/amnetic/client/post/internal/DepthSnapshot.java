package com.meekdev.amnetic.client.post.internal;

import com.meekdev.amnetic.client.compat.VanillaCompat;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;

final class DepthSnapshot {

    private final String name;
    private TextureTarget snapshot;
    private boolean pendingRestore;

    DepthSnapshot(String name) {
        this.name = name;
    }

    void capture(RenderTarget source) {
        if (VanillaCompat.depthTextureGlId(source) == 0) return;

        if (snapshot == null) {
            snapshot = VanillaCompat.textureTarget(name, source.width, source.height, true);
        } else if (snapshot.width != source.width || snapshot.height != source.height) {
            VanillaCompat.resize(snapshot, source.width, source.height);
        }

        if (VanillaCompat.depthTextureGlId(snapshot) == 0) return;

        snapshot.copyDepthFrom(source);
        pendingRestore = true;
    }

    boolean restoreInto(RenderTarget target) {
        if (!pendingRestore) return false;
        pendingRestore = false;

        if (snapshot == null) return false;
        if (VanillaCompat.depthTextureGlId(snapshot) == 0 || VanillaCompat.depthTextureGlId(target) == 0) return false;
        if (snapshot.width != target.width || snapshot.height != target.height) return false;

        target.copyDepthFrom(snapshot);
        return true;
    }

    RenderTarget getFramebuffer() {
        return snapshot;
    }
}

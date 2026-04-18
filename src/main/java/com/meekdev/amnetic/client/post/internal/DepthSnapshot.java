package com.meekdev.amnetic.client.post.internal;

import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.SimpleFramebuffer;

final class DepthSnapshot {

    private final String name;
    private SimpleFramebuffer snapshot;
    private boolean pendingRestore;

    DepthSnapshot(String name) {
        this.name = name;
    }

    void capture(Framebuffer source) {
        if (!source.useDepthAttachment) return;
        if (source.getDepthAttachment() == null) return;

        if (snapshot == null) {
            snapshot = new SimpleFramebuffer(name, source.textureWidth, source.textureHeight, true);
        } else if (snapshot.textureWidth != source.textureWidth || snapshot.textureHeight != source.textureHeight) {
            snapshot.resize(source.textureWidth, source.textureHeight);
        }

        if (snapshot.getDepthAttachment() == null) return;

        snapshot.copyDepthFrom(source);
        pendingRestore = true;
    }

    boolean restoreInto(Framebuffer target) {
        if (!pendingRestore) return false;
        pendingRestore = false;

        if (snapshot == null) return false;
        if (!target.useDepthAttachment) return false;
        if (snapshot.getDepthAttachment() == null || target.getDepthAttachment() == null) return false;
        if (snapshot.textureWidth != target.textureWidth || snapshot.textureHeight != target.textureHeight) return false;

        target.copyDepthFrom(snapshot);
        return true;
    }

    Framebuffer getFramebuffer() {
        return snapshot;
    }
}

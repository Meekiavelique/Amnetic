package com.meekdev.amnetic.client.entityfx.internal;

import com.meekdev.amnetic.client.framebuffer.Framebuffer;
import com.meekdev.amnetic.client.framebuffer.Framebuffers;
import net.minecraft.resources.Identifier;

public final class SceneColorSnapshot {

    public static final SceneColorSnapshot INSTANCE = new SceneColorSnapshot();
    public static final Identifier ID = Identifier.fromNamespaceAndPath("amnetic", "entityfx_scene_color");

    private Framebuffer capture;

    private SceneColorSnapshot() {}

    public void ensureRegistered() {
        if (capture == null) {
            capture = Framebuffers.captureColor();
        }
        capture.registerColorTexture(ID);
    }

    public void capture() {
        if (capture == null) {
            capture = Framebuffers.captureColor();
        }
        capture.blitColorFromMain();
        capture.registerColorTexture(ID);
    }

    public void dispose() {
        if (capture != null) {
            capture.dispose();
            capture = null;
        }
    }
}

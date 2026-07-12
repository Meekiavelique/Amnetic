package com.meekdev.amnetic.client.pipeline.internal;

import com.mojang.blaze3d.pipeline.RenderTarget;

// returns it from Minecraft.getMainRenderTarget() so the hand / GUI draw into the isolated layer target
public final class LayerRedirect {
    private static RenderTarget active;

    private LayerRedirect() {}

    public static void set(RenderTarget target) {
        active = target;
    }

    public static RenderTarget active() {
        return active;
    }
}

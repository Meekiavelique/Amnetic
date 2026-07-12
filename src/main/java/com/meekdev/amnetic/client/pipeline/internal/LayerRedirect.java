package com.meekdev.amnetic.client.pipeline.internal;

import com.mojang.blaze3d.pipeline.RenderTarget;

// holds the render target vanilla should draw the current layer into. while set, MinecraftMainTargetMixin
// returns it from Minecraft.getMainRenderTarget() so the hand / GUI draw into the isolated layer target
// instead of the screen. cleared the moment the layer is done
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

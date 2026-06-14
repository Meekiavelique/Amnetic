package com.meekdev.amnetic.client.scene.internal;

import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.renderer.texture.AbstractTexture;

public final class CaptureColorTexture extends AbstractTexture {

    private boolean registered;

    public void update(RenderTarget target) {
        this.texture = target.getColorTexture();
        this.textureView = target.getColorTextureView();
    }

    public boolean isRegistered() {
        return registered;
    }

    public void markRegistered() {
        registered = true;
    }

    @Override
    public void close() {
    }
}

package com.meekdev.amnetic.client.scene.internal;

import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.renderer.texture.AbstractTexture;
//? if <1.21.5 {
/*import net.minecraft.server.packs.resources.ResourceManager;
*///?}

public final class CaptureColorTexture extends AbstractTexture {

    private boolean registered;

    public void update(RenderTarget target) {
        //? if >=1.21.5 {
        this.texture = target.getColorTexture();
        this.textureView = target.getColorTextureView();
        //?} else {
        /*this.id = target.getColorTextureId();
        *///?}
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

    //? if <1.21.5 {
    /*@Override
    public void load(ResourceManager resourceManager) {
    }
    *///?}
}

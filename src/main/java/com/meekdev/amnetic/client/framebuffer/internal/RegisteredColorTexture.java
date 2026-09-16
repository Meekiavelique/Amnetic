package com.meekdev.amnetic.client.framebuffer.internal;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.Identifier;
//? if >=1.21.5 {
import com.mojang.blaze3d.systems.RenderSystem;
//?} else {
/*import net.minecraft.server.packs.resources.ResourceManager;
*///?}

public final class RegisteredColorTexture extends AbstractTexture {

    private final Identifier location;
    private boolean registered;

    public RegisteredColorTexture(Identifier id) {
        this.location = id;
    }

    public void update(int glId, int width, int height) {
        //? if >=1.21.5 {
        WrappedGlTexture wrapped = new WrappedGlTexture(glId, width, height);
        this.texture = wrapped;
        this.textureView = RenderSystem.getDevice().createTextureView(wrapped);
        //?} else {
        /*this.id = glId;
        *///?}
        if (!registered) {
            Minecraft.getInstance().getTextureManager().register(location, this);
            registered = true;
        }
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

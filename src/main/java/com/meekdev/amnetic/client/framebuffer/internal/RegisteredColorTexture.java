package com.meekdev.amnetic.client.framebuffer.internal;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.Identifier;

public final class RegisteredColorTexture extends AbstractTexture {

    private final Identifier id;
    private boolean registered;

    public RegisteredColorTexture(Identifier id) {
        this.id = id;
    }

    public void update(int glId, int width, int height) {
        WrappedGlTexture wrapped = new WrappedGlTexture(glId, width, height);
        this.texture = wrapped;
        this.textureView = RenderSystem.getDevice().createTextureView(wrapped);
        if (!registered) {
            Minecraft.getInstance().getTextureManager().register(id, this);
            registered = true;
        }
    }

    @Override
    public void close() {
    }
}

package com.meekdev.amnetic.client.framebuffer.internal;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.Identifier;
//? if >=1.21.5 {
import com.mojang.blaze3d.systems.RenderSystem;
//?} else {
/*import net.minecraft.server.packs.resources.ResourceManager;
*///?}
//? if >=26.2 {
import com.mojang.renderpearl.api.textures.GpuTextureView;
//?}

public final class RegisteredColorTexture extends AbstractTexture {

    private final Identifier location;
    private boolean registered;
    //? if >=26.2 {
    private boolean borrowedView;

    public void update(GpuTextureView view) {
        if (textureView == view && !view.isClosed()) return;
        close();
        borrowedView = true;
        texture = view.texture();
        textureView = view;
        if (!registered) {
            Minecraft.getInstance().getTextureManager().register(location, this);
            registered = true;
        }
    }
    //?}

    public RegisteredColorTexture(Identifier id) {
        this.location = id;
    }

    public void update(int glId, int width, int height) {
        //? if >=1.21.5 {
        if (texture instanceof WrappedGlTexture wrapped && wrapped.glId() == glId
                && wrapped.getWidth(0) == width && wrapped.getHeight(0) == height
                && textureView != null && !textureView.isClosed()) return;
        close();
        //? if >=26.2 {
        borrowedView = false;
        //?}
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
        //? if >=1.21.5 {
        //? if >=26.2 {
        if (textureView != null && !borrowedView) textureView.close();
        textureView = null;
        //?} else {
        /*if (textureView != null) { textureView.close(); textureView = null; }
        *///?}
        texture = null;
        //?}
    }

    //? if <1.21.5 {
    /*@Override
    public void load(ResourceManager resourceManager) {
    }
    *///?}
}

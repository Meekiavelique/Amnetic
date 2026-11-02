package com.meekdev.amnetic.client.framebuffer.internal;

//? if >=1.21.5 {
import com.mojang.renderpearl.backend.opengl.GlTexture;
//? if >=26.2 {
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.backend.opengl.FrameBufferCache;
//?} else {
/*import com.mojang.blaze3d.textures.TextureFormat;
*///?}

public final class WrappedGlTexture extends GlTexture {

    @Override
    public void close() {}

    public WrappedGlTexture(int glId, int width, int height) {
        //? if >=26.2 {
        super(USAGE_TEXTURE_BINDING, "amnetic_framebuffer", GpuFormat.RGBA8_UNORM,
                width, height, 1, 1, glId, new FrameBufferCache());
        //?} else {
        /*super(USAGE_TEXTURE_BINDING, "amnetic_framebuffer", TextureFormat.RGBA8,
                width, height, 1, 1, glId);
        *///?}
    }
}
//?}

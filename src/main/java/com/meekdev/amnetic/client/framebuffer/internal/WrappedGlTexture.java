package com.meekdev.amnetic.client.framebuffer.internal;

//? if >=26.2 {
/*import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.opengl.FrameBufferCache;
import com.mojang.blaze3d.opengl.GlTexture;

public final class WrappedGlTexture extends GlTexture {

    // the device cache is out of reach, these only get sampled so a private one is fine
    private static final FrameBufferCache FBO_CACHE = new FrameBufferCache();

    public WrappedGlTexture(int glId, int width, int height) {
        super(USAGE_TEXTURE_BINDING, "amnetic_framebuffer", GpuFormat.RGBA8_UNORM,
                width, height, 1, 1, glId, FBO_CACHE);
    }
}
*///?} else if >=1.21.5 {
import com.mojang.blaze3d.opengl.GlTexture;
import com.mojang.blaze3d.textures.TextureFormat;

public final class WrappedGlTexture extends GlTexture {

    public WrappedGlTexture(int glId, int width, int height) {
        super(USAGE_TEXTURE_BINDING, "amnetic_framebuffer", TextureFormat.RGBA8,
                width, height, 1, 1, glId);
    }
}
//?}

package com.meekdev.amnetic.client.framebuffer.internal;

import com.mojang.blaze3d.opengl.GlTexture;
import com.mojang.blaze3d.textures.TextureFormat;

public final class WrappedGlTexture extends GlTexture {

    public WrappedGlTexture(int glId, int width, int height) {
        super(USAGE_TEXTURE_BINDING, "amnetic_framebuffer", TextureFormat.RGBA8,
                width, height, 1, 1, glId);
    }
}

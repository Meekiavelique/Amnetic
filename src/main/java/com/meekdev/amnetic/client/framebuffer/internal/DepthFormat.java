package com.meekdev.amnetic.client.framebuffer.internal;

import com.meekdev.amnetic.client.compat.VanillaCompat;
import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL30;

public final class DepthFormat {

    private static int queriedTexture;
    private static int internalFormat = GL14.GL_DEPTH_COMPONENT24;

    private DepthFormat() {}

    public static int internalFormat() {
        RenderTarget main = Minecraft.getInstance().getMainRenderTarget();
        int tex = main != null && main.useDepth ? VanillaCompat.depthTextureGlId(main) : 0;
        if (tex <= 0 || tex == queriedTexture) return internalFormat;
        int prev = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, tex);
        int format = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_INTERNAL_FORMAT);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, prev);
        queriedTexture = tex;
        if (format != 0 && format != GL11.GL_DEPTH_COMPONENT) internalFormat = format;
        return internalFormat;
    }

    public static int pixelFormat(int internal) {
        return hasStencil(internal) ? GL30.GL_DEPTH_STENCIL : GL11.GL_DEPTH_COMPONENT;
    }

    public static int pixelType(int internal) {
        if (internal == GL30.GL_DEPTH24_STENCIL8) return GL30.GL_UNSIGNED_INT_24_8;
        if (internal == GL30.GL_DEPTH32F_STENCIL8) return GL30.GL_FLOAT_32_UNSIGNED_INT_24_8_REV;
        return GL11.GL_FLOAT;
    }

    private static boolean hasStencil(int internal) {
        return internal == GL30.GL_DEPTH24_STENCIL8 || internal == GL30.GL_DEPTH32F_STENCIL8;
    }
}

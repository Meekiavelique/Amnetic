package com.meekdev.amnetic.client.instanced.internal;

import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.opengl.GlTexture;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.textures.GpuTexture;
import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

public final class MainTargetFramebuffer {

    private static int fbo;
    private static int cachedColorId = -1;
    private static int cachedDepthId = -1;
    private static int depthOverride;

    private static final int[] SAVED_VIEWPORT = new int[4];

    private MainTargetFramebuffer() {}

    public static void setDepthOverride(int glId) {
        depthOverride = glId;
    }

    public static int bind() {
        RenderTarget main = Minecraft.getInstance().getMainRenderTarget();
        if (main == null) return -1;
        GpuTexture color = main.getColorTexture();
        if (!(color instanceof GlTexture glColor)) return -1;
        GpuTexture depth = main.useDepth ? main.getDepthTexture() : null;

        int colorId = glColor.glId();
        int mainDepthId = (depth instanceof GlTexture glDepth) ? glDepth.glId() : 0;
        int depthId = depthOverride > 0 ? depthOverride : mainDepthId;

        if (fbo == 0) fbo = GL30.glGenFramebuffers();
        if (colorId != cachedColorId || depthId != cachedDepthId) {
            GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, fbo);
            GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, colorId, 0);
            GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT, GL11.GL_TEXTURE_2D, depthId, 0);
            cachedColorId = colorId;
            cachedDepthId = depthId;
        }

        int prevFbo = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, SAVED_VIEWPORT);

        GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, fbo);
        GlStateManager._viewport(0, 0, main.width, main.height);
        return prevFbo;
    }

    public static void restore(int prevFbo) {
        if (prevFbo < 0) return;
        GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, prevFbo);
        GlStateManager._viewport(SAVED_VIEWPORT[0], SAVED_VIEWPORT[1], SAVED_VIEWPORT[2], SAVED_VIEWPORT[3]);
    }
}

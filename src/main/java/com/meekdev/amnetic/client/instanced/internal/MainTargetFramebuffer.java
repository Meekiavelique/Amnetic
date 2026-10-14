package com.meekdev.amnetic.client.instanced.internal;

import com.meekdev.amnetic.client.compat.VanillaCompat;
import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.pipeline.RenderTarget;
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
        int colorId = VanillaCompat.colorTextureGlId(main);
        if (colorId <= 0) return -1;

        int mainDepthId = VanillaCompat.depthTextureGlId(main);
        int depthId = depthOverride > 0 ? depthOverride : mainDepthId;

        if (fbo == 0) fbo = GL30.glGenFramebuffers();
        // Iris and nested passes can change the outer target within the same frame.
        int previousFbo = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        // re-attach every bind: GL reuses freed texture ids after a resize, so an id-equality cache can keep us
        // pointing at stale storage (model/effects rendered into limbo after going fullscreen). cheap + correct
        GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, fbo);
        GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, colorId, 0);
        GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT, GL11.GL_TEXTURE_2D, depthId, 0);
        cachedColorId = colorId;
        cachedDepthId = depthId;

        // viewport to restore is always the main target's full size, derive it instead of querying GL
        SAVED_VIEWPORT[0] = 0;
        SAVED_VIEWPORT[1] = 0;
        SAVED_VIEWPORT[2] = main.width;
        SAVED_VIEWPORT[3] = main.height;
        GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, fbo);
        GlStateManager._viewport(0, 0, main.width, main.height);
        return previousFbo;
    }

    public static void restore(int prevFbo) {
        if (prevFbo < 0) return;
        GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, prevFbo);
        GlStateManager._viewport(SAVED_VIEWPORT[0], SAVED_VIEWPORT[1], SAVED_VIEWPORT[2], SAVED_VIEWPORT[3]);
    }
}

package com.meekdev.amnetic.client.instanced.internal;

import com.meekdev.amnetic.client.compat.IrisCompat;
import com.meekdev.amnetic.client.compat.VanillaCompat;
import com.meekdev.amnetic.client.render.GlState;
import com.mojang.renderpearl.backend.opengl.GlStateManager;
import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

public final class MainTargetFramebuffer {

    private static int fbo;
    private static int cachedColorId = -1;
    private static int cachedDepthId = -1;
    private static int depthOverride;
    private static int savedReadFbo;

    private static final int[] SAVED_VIEWPORT = new int[4];

    private MainTargetFramebuffer() {}

    public static void setDepthOverride(int glId) {
        depthOverride = glId;
    }

    public static int bind() {
        RenderTarget main = Minecraft.getInstance().gameRenderer.mainRenderTarget();
        if (main == null) return -1;
        int colorId = VanillaCompat.colorTextureGlId(main);
        if (colorId <= 0) return -1;

        int mainDepthId = VanillaCompat.depthTextureGlId(main);
        int depthId = depthOverride > 0 ? depthOverride : mainDepthId;

        int previousDraw = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        savedReadFbo = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, SAVED_VIEWPORT);
        if (fbo == 0) fbo = GL30.glGenFramebuffers();
        // a glGet waits for the driver to drain its queue, ms a frame here, so read GlStateManager's copy
        // iris binds fbos behind GlStateManager's back, so ask GL then
        int previousFbo = IrisCompat.shadersActive()
                ? GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING)
                : GlState.drawFramebuffer();
        // re-attach every bind: GL reuses freed texture ids after a resize, so an id-equality cache can keep us
        // pointing at stale storage (model/effects rendered into limbo after going fullscreen). cheap + correct
        GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, fbo);
        GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, colorId, 0);
        GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT, GL11.GL_TEXTURE_2D, depthId, 0);
        cachedColorId = colorId;
        cachedDepthId = depthId;

        GlStateManager._viewport(0, 0, main.width, main.height);
        return previousDraw;
    }

    public static void restore(int prevFbo) {
        if (prevFbo < 0) return;
        GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, savedReadFbo);
        GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, prevFbo);
        GlStateManager._viewport(SAVED_VIEWPORT[0], SAVED_VIEWPORT[1], SAVED_VIEWPORT[2], SAVED_VIEWPORT[3]);
    }
}

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
    private static int depthOverride;
    private static GpuTexture attachedColor;
    private static GpuTexture attachedDepth;
    private static int attachedColorId = -1;
    private static int attachedDepthId = -1;

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

        // Iris swaps this binding during world, composite and shadow passes. GlStateManager tracks it
        // without the driver stall caused by a raw glGetInteger call.
        int previousFbo = GlStateManager.getFrameBuffer(GL30.GL_DRAW_FRAMEBUFFER);

        if (fbo == 0) fbo = GL30.glGenFramebuffers();
        GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, fbo);
        if (color != attachedColor || depth != attachedDepth
                || colorId != attachedColorId || depthId != attachedDepthId) {
            GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0,
                    GL11.GL_TEXTURE_2D, colorId, 0);
            GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT,
                    GL11.GL_TEXTURE_2D, depthId, 0);
            attachedColor = color;
            attachedDepth = depth;
            attachedColorId = colorId;
            attachedDepthId = depthId;
        }

        // viewport to restore is always the main target's full size, derive it instead of querying GL
        SAVED_VIEWPORT[0] = 0;
        SAVED_VIEWPORT[1] = 0;
        SAVED_VIEWPORT[2] = main.width;
        SAVED_VIEWPORT[3] = main.height;
        GlStateManager._viewport(0, 0, main.width, main.height);
        return previousFbo;
    }

    public static void restore(int prevFbo) {
        if (prevFbo < 0) return;
        GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, prevFbo);
        GlStateManager._viewport(SAVED_VIEWPORT[0], SAVED_VIEWPORT[1], SAVED_VIEWPORT[2], SAVED_VIEWPORT[3]);
    }
}

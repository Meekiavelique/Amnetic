package com.meekdev.amnetic.client.render;

import com.mojang.blaze3d.opengl.GlStateManager;
import org.lwjgl.opengl.ARBTextureBarrier;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL33;
import org.lwjgl.opengl.GL45;
import org.lwjgl.opengl.GLCapabilities;
import org.lwjgl.opengl.NVTextureBarrier;

public final class GlState {

    private static final int MAX_UNIT = 3;

    private static volatile int geometryPolygonMode = GL11.GL_FILL;
    private static final ThreadLocal<java.util.ArrayDeque<Integer>> PREVIOUS_VERTEX_ARRAYS =
            ThreadLocal.withInitial(java.util.ArrayDeque::new);

    private GlState() {}

    public static void geometryPolygonMode(int mode) {
        geometryPolygonMode = mode;
        GL11.glPolygonMode(GL11.GL_FRONT_AND_BACK, mode);
    }

    public static int geometryPolygonMode() {
        return geometryPolygonMode;
    }

    public static void beginFullscreen() {
        PREVIOUS_VERTEX_ARRAYS.get().push(GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING));
        GL11.glPolygonMode(GL11.GL_FRONT_AND_BACK, GL11.GL_FILL);
        GlStateManager._disableBlend(); GL11.glDisable(GL11.GL_BLEND);
        GlStateManager._disableDepthTest(); GL11.glDisable(GL11.GL_DEPTH_TEST);
        GlStateManager._depthMask(false); GL11.glDepthMask(false);
        GlStateManager._disableCull(); GL11.glDisable(GL11.GL_CULL_FACE);
    }

    public static void bindTexture(int unit, int glId) {
        // raw bind forces real GL correct for our pass; the matching GlStateManager call keeps its cache in
        // sync so vanilla's next (cached) bind of the same unit isn't skipped - otherwise vanilla samples our
        // leftover texture and the world renders dark
        GL13.glActiveTexture(GL13.GL_TEXTURE0 + unit);
        GlStateManager._activeTexture(GL13.GL_TEXTURE0 + unit);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, glId);
        GlStateManager._bindTexture(glId);
        GL33.glBindSampler(unit, 0);
    }

    public static void textureBarrier() {
        GLCapabilities caps = GL.getCapabilities();
        if (caps.OpenGL45) {
            GL45.glTextureBarrier();
        } else if (caps.GL_ARB_texture_barrier) {
            ARBTextureBarrier.glTextureBarrier();
        } else if (caps.GL_NV_texture_barrier) {
            NVTextureBarrier.glTextureBarrierNV();
        }
    }

    public static void endFullscreen() {
        for (int u = MAX_UNIT; u >= 0; u--) {
            GL13.glActiveTexture(GL13.GL_TEXTURE0 + u);
            GlStateManager._activeTexture(GL13.GL_TEXTURE0 + u);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
            GlStateManager._bindTexture(0); // keep GlStateManager's cache in sync with the real unbind
            GL33.glBindSampler(u, 0);
        }
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        GlStateManager._activeTexture(GL13.GL_TEXTURE0);
        GlStateManager._glUseProgram(0);
        var previousVertexArrays = PREVIOUS_VERTEX_ARRAYS.get();
        GlStateManager._glBindVertexArray(previousVertexArrays.isEmpty() ? 0 : previousVertexArrays.pop());
        GlStateManager._depthMask(true); GL11.glDepthMask(true);
        GlStateManager._enableDepthTest(); GL11.glEnable(GL11.GL_DEPTH_TEST);
        GlStateManager._enableCull(); GL11.glEnable(GL11.GL_CULL_FACE);
        GlStateManager._blendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL14.glBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GlStateManager._disableBlend(); GL11.glDisable(GL11.GL_BLEND);
        GL11.glPolygonMode(GL11.GL_FRONT_AND_BACK, geometryPolygonMode);
    }
}

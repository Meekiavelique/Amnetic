package com.meekdev.amnetic.client.instanced.internal;

import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.BufferManager;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.GlBackend;
import net.minecraft.client.texture.GlTexture;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

public final class MinecraftFramebufferUtil {

    private MinecraftFramebufferUtil() {}

    public static SavedState bindMainFramebuffer(MinecraftClient client, boolean setViewport) {
        Framebuffer fb = client.getFramebuffer();
        int fbo = getOrCreateGlFbo(fb);
        if (fbo == -1) return null;

        int prevDraw = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int prevRead = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int[] prevViewport = new int[4];
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, prevViewport);

        GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, fbo);
        if (setViewport) {
            GL11.glViewport(0, 0, fb.textureWidth, fb.textureHeight);
        }

        return new SavedState(prevDraw, prevRead, prevViewport);
    }

    public static void restore(SavedState state) {
        if (state == null) return;
        GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, state.drawFbo());
        GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, state.readFbo());
        int[] vp = state.viewport();
        GL11.glViewport(vp[0], vp[1], vp[2], vp[3]);
    }

    private static int getOrCreateGlFbo(Framebuffer fb) {
        if (!(RenderSystem.getDevice() instanceof GlBackend backend)) return -1;
        BufferManager bufferManager = backend.getBufferManager();

        GpuTexture color = fb.getColorAttachment();
        if (!(color instanceof GlTexture glColor)) return -1;
        GpuTexture depth = fb.useDepthAttachment ? fb.getDepthAttachment() : null;
        return glColor.getOrCreateFramebuffer(bufferManager, depth);
    }

    public record SavedState(int drawFbo, int readFbo, int[] viewport) {}
}

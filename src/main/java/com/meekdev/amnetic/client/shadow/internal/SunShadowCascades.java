package com.meekdev.amnetic.client.shadow.internal;

import com.meekdev.amnetic.client.shadow.ShadowSettings;
import com.mojang.blaze3d.opengl.GlStateManager;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL30;

import java.nio.ByteBuffer;

/** depth-only 2D texture array holding the sun's shadow cascades (layer = cascade, tightest first).
 * one FBO is retargeted per layer during the bake, same as PointShadowArray */
public final class SunShadowCascades {

    private static int textureId, fboId, resolution, cascades;

    private SunShadowCascades() {}

    public static int glTextureId() { return textureId; }
    public static int resolution() { return resolution; }
    public static int cascades() { return cascades; }

    public static void ensure(int requestedResolution, int requestedCascades) {
        requestedCascades = Math.max(1, Math.min(ShadowSettings.MAX_CASCADES, requestedCascades));
        if (textureId != 0 && resolution == requestedResolution && cascades == requestedCascades) return;
        delete();
        resolution = requestedResolution;
        cascades = requestedCascades;

        int prevFbo = GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);
        int prevArray = GL11.glGetInteger(GL30.GL_TEXTURE_BINDING_2D_ARRAY);

        textureId = GlStateManager._genTexture();
        GL11.glBindTexture(GL30.GL_TEXTURE_2D_ARRAY, textureId);
        GL12.glTexImage3D(GL30.GL_TEXTURE_2D_ARRAY, 0, GL30.GL_DEPTH_COMPONENT32F,
                resolution, resolution, cascades, 0, GL11.GL_DEPTH_COMPONENT, GL11.GL_FLOAT, (ByteBuffer) null);
        GL11.glTexParameteri(GL30.GL_TEXTURE_2D_ARRAY, GL12.GL_TEXTURE_BASE_LEVEL, 0);
        GL11.glTexParameteri(GL30.GL_TEXTURE_2D_ARRAY, GL12.GL_TEXTURE_MAX_LEVEL, 0);
        GL11.glTexParameteri(GL30.GL_TEXTURE_2D_ARRAY, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL30.GL_TEXTURE_2D_ARRAY, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL30.GL_TEXTURE_2D_ARRAY, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL30.GL_TEXTURE_2D_ARRAY, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL30.GL_TEXTURE_2D_ARRAY, GL14.GL_TEXTURE_COMPARE_MODE, GL11.GL_NONE);

        fboId = GL30.glGenFramebuffers();
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, fboId);
        GL30.glFramebufferTextureLayer(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT, textureId, 0, 0);
        GL11.glDrawBuffer(GL11.GL_NONE);
        GL11.glReadBuffer(GL11.GL_NONE);
        int status = GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER);
        if (status != GL30.GL_FRAMEBUFFER_COMPLETE) {
            throw new IllegalStateException("SunShadowCascades FBO incomplete: 0x" + Integer.toHexString(status));
        }

        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, prevFbo);
        GL11.glBindTexture(GL30.GL_TEXTURE_2D_ARRAY, prevArray);
    }

    public static void bindLayerForRender(int cascade) {
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, fboId);
        GL30.glFramebufferTextureLayer(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT, textureId, 0, cascade);
    }

    public static void delete() {
        if (textureId != 0) { GL11.glDeleteTextures(textureId); textureId = 0; }
        if (fboId != 0) { GL30.glDeleteFramebuffers(fboId); fboId = 0; }
        resolution = 0;
        cascades = 0;
    }
}

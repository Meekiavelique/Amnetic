package com.meekdev.amnetic.client.shadow.internal;

import com.meekdev.amnetic.client.shadow.ShadowSettings;
import com.mojang.blaze3d.opengl.GlStateManager;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL40;

import java.nio.ByteBuffer;

public final class PointShadowArray {

    private static final int GL_TEXTURE_CUBE_MAP_SEAMLESS = 0x884F;

    private static int textureId, fboId, faceSize, slots;

    private PointShadowArray() {}

    public static int glTextureId() { return textureId; }
    public static int faceSize() { return faceSize; }

    public static void ensure(int requestedFaceSize, int requestedSlots) {
        requestedSlots = Math.max(1, Math.min(ShadowSettings.MAX_POINT, requestedSlots));
        if (textureId != 0 && faceSize == requestedFaceSize && slots == requestedSlots) return;
        delete();
        faceSize = requestedFaceSize;
        slots = requestedSlots;
        int layerCount = 6 * slots;

        int prevFbo = GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);
        int prevCubeArray = GL11.glGetInteger(GL40.GL_TEXTURE_BINDING_CUBE_MAP_ARRAY);

        textureId = GlStateManager._genTexture();
        GL11.glBindTexture(GL40.GL_TEXTURE_CUBE_MAP_ARRAY, textureId);
        GL12.glTexImage3D(GL40.GL_TEXTURE_CUBE_MAP_ARRAY, 0, GL30.GL_DEPTH_COMPONENT32F,
                faceSize, faceSize, layerCount, 0, GL11.GL_DEPTH_COMPONENT, GL11.GL_FLOAT, (ByteBuffer) null);
        GL11.glTexParameteri(GL40.GL_TEXTURE_CUBE_MAP_ARRAY, GL12.GL_TEXTURE_BASE_LEVEL, 0);
        GL11.glTexParameteri(GL40.GL_TEXTURE_CUBE_MAP_ARRAY, GL12.GL_TEXTURE_MAX_LEVEL, 0);
        GL11.glTexParameteri(GL40.GL_TEXTURE_CUBE_MAP_ARRAY, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL40.GL_TEXTURE_CUBE_MAP_ARRAY, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL40.GL_TEXTURE_CUBE_MAP_ARRAY, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL40.GL_TEXTURE_CUBE_MAP_ARRAY, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL40.GL_TEXTURE_CUBE_MAP_ARRAY, GL12.GL_TEXTURE_WRAP_R, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL40.GL_TEXTURE_CUBE_MAP_ARRAY, GL14.GL_TEXTURE_COMPARE_MODE, GL11.GL_NONE);
        GL11.glEnable(GL_TEXTURE_CUBE_MAP_SEAMLESS);

        fboId = GL30.glGenFramebuffers();
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, fboId);
        GL30.glFramebufferTextureLayer(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT, textureId, 0, 0);
        GL11.glDrawBuffer(GL11.GL_NONE);
        GL11.glReadBuffer(GL11.GL_NONE);
        int status = GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER);
        if (status != GL30.GL_FRAMEBUFFER_COMPLETE) {
            throw new IllegalStateException("PointShadowArray FBO incomplete: 0x" + Integer.toHexString(status));
        }

        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, prevFbo);
        GL11.glBindTexture(GL40.GL_TEXTURE_CUBE_MAP_ARRAY, prevCubeArray);
    }

    public static void bindFaceForRender(int slot, int face) {
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, fboId);
        GL30.glFramebufferTextureLayer(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT, textureId, 0, slot * 6 + face);
    }

    public static void delete() {
        if (textureId != 0) { GL11.glDeleteTextures(textureId); textureId = 0; }
        if (fboId != 0) { GL30.glDeleteFramebuffers(fboId); fboId = 0; }
        faceSize = 0;
        slots = 0;
    }
}

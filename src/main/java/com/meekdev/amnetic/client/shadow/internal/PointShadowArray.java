package com.meekdev.amnetic.client.shadow.internal;

import com.meekdev.amnetic.client.shadow.ShadowSettings;
import com.mojang.renderpearl.backend.opengl.GlStateManager;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL40;

import java.nio.ByteBuffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class PointShadowArray {
    private static final int GL_TEXTURE_CUBE_MAP_SEAMLESS = 0x884F;
    private static final Logger LOG = LoggerFactory.getLogger("Amnetic/Shadows");
    private static final long BUDGET = 512L << 20;
    private static final int MIN_FACE_SIZE = 128;
    private static final int FACES = 6;
    private static final int DEPTH_BYTES = 4;
    private static final int MAX_DRAINED_ERRORS = 16;
    private static int textureId;
    private static int fboId;
    private static int faceSize;
    private static int slots;
    private static int requestedFace;

    private PointShadowArray() {}

    public static int glTextureId() {
        return textureId;
    }

    public static int faceSize() {
        return faceSize;
    }

    public static void ensure(int requestedFaceSize, int requestedSlots) {
        requestedSlots = Math.max(1, Math.min(ShadowSettings.MAX_POINT, requestedSlots));
        if (textureId != 0 && requestedFace == requestedFaceSize && slots == requestedSlots) return;
        delete();
        int size = requestedFaceSize;
        while (size > MIN_FACE_SIZE && (long) size * size * FACES * requestedSlots * DEPTH_BYTES > BUDGET) size /= 2;
        if (size != requestedFaceSize) LOG.info("{} point shadows at {}px don't fit, using {}px faces", requestedSlots, requestedFaceSize, size);
        requestedFace = requestedFaceSize;
        faceSize = size;
        slots = requestedSlots;
        int layerCount = FACES * slots;

        int prevFbo = GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);
        int prevCubeArray = GL11.glGetInteger(GL40.GL_TEXTURE_BINDING_CUBE_MAP_ARRAY);

        textureId = GlStateManager._genTexture();
        GL11.glBindTexture(GL40.GL_TEXTURE_CUBE_MAP_ARRAY, textureId);
        drainErrors();
        allocateFaces(layerCount);
        while (GL11.glGetError() == GL11.GL_OUT_OF_MEMORY && faceSize > MIN_FACE_SIZE) {
            faceSize /= 2;
            LOG.warn("out of video memory for point shadows, using {}px faces", faceSize);
            allocateFaces(layerCount);
        }
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

    private static void drainErrors() {
        for (int i = 0; i < MAX_DRAINED_ERRORS; i++) {
            if (GL11.glGetError() == GL11.GL_NO_ERROR) return;
        }
    }

    private static void allocateFaces(int layerCount) {
        GL12.glTexImage3D(GL40.GL_TEXTURE_CUBE_MAP_ARRAY, 0, GL30.GL_DEPTH_COMPONENT32F,
                faceSize, faceSize, layerCount, 0, GL11.GL_DEPTH_COMPONENT, GL11.GL_FLOAT, (ByteBuffer) null);
    }

    public static void bindFaceForRender(int slot, int face) {
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, fboId);
        GL30.glFramebufferTextureLayer(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT, textureId, 0, slot * FACES + face);
    }

    public static void delete() {
        if (textureId != 0) {
            GL11.glDeleteTextures(textureId);
            textureId = 0;
        }
        if (fboId != 0) {
            GL30.glDeleteFramebuffers(fboId);
            fboId = 0;
        }
        faceSize = 0;
        slots = 0;
        requestedFace = 0;
    }
}

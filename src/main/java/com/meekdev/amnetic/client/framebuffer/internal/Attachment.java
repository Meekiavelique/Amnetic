package com.meekdev.amnetic.client.framebuffer.internal;

import com.meekdev.amnetic.client.framebuffer.ColorFormat;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryUtil;

public final class Attachment {

    public enum Kind { COLOR_TEXTURE, DEPTH_TEXTURE, DEPTH_RENDERBUFFER }

    private final Kind kind;
    private final ColorFormat colorFormat; // null unless COLOR_TEXTURE
    private int glId;

    private Attachment(Kind kind, ColorFormat colorFormat) {
        this.kind = kind;
        this.colorFormat = colorFormat;
    }

    public static Attachment colorTexture(ColorFormat format) {
        return new Attachment(Kind.COLOR_TEXTURE, format);
    }

    public static Attachment depthTexture() {
        return new Attachment(Kind.DEPTH_TEXTURE, null);
    }

    public static Attachment depthRenderbuffer() {
        return new Attachment(Kind.DEPTH_RENDERBUFFER, null);
    }

    public Kind kind() {
        return kind;
    }

    public int glId() {
        return glId;
    }

    public boolean isColor() {
        return kind == Kind.COLOR_TEXTURE;
    }

    public void allocate(int width, int height) {
        free();
        switch (kind) {
            case COLOR_TEXTURE -> {
                glId = GL11.glGenTextures();
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, glId);
                setTextureParams();
                GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, colorFormat.glInternalFormat(),
                        width, height, 0, colorFormat.glFormat(), colorFormat.glType(), MemoryUtil.NULL);
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
            }
            case DEPTH_TEXTURE -> {
                glId = GL11.glGenTextures();
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, glId);
                setTextureParams();
                GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL14.GL_DEPTH_COMPONENT24,
                        width, height, 0, GL11.GL_DEPTH_COMPONENT, GL11.GL_FLOAT, MemoryUtil.NULL);
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
            }
            case DEPTH_RENDERBUFFER -> {
                glId = GL30.glGenRenderbuffers();
                GL30.glBindRenderbuffer(GL30.GL_RENDERBUFFER, glId);
                GL30.glRenderbufferStorage(GL30.GL_RENDERBUFFER, GL14.GL_DEPTH_COMPONENT24, width, height);
                GL30.glBindRenderbuffer(GL30.GL_RENDERBUFFER, 0);
            }
        }
    }
    // colorIndex is ignored for depth
    public void attachTo(int colorIndex) {
        switch (kind) {
            case COLOR_TEXTURE -> GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER,
                    GL30.GL_COLOR_ATTACHMENT0 + colorIndex, GL11.GL_TEXTURE_2D, glId, 0);
            case DEPTH_TEXTURE -> GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER,
                    GL30.GL_DEPTH_ATTACHMENT, GL11.GL_TEXTURE_2D, glId, 0);
            case DEPTH_RENDERBUFFER -> GL30.glFramebufferRenderbuffer(GL30.GL_FRAMEBUFFER,
                    GL30.GL_DEPTH_ATTACHMENT, GL30.GL_RENDERBUFFER, glId);
        }
    }

    public void free() {
        if (glId == 0) return;
        if (kind == Kind.DEPTH_RENDERBUFFER) {
            GL30.glDeleteRenderbuffers(glId);
        } else {
            GL11.glDeleteTextures(glId);
        }
        glId = 0;
    }

    private static void setTextureParams() {
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL30.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL30.GL_CLAMP_TO_EDGE);
    }
}

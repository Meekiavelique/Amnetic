package com.meekdev.amnetic.client.entityfx.internal;

import com.meekdev.amnetic.client.framebuffer.internal.RegisteredColorTexture;
import java.nio.ByteBuffer;
import net.minecraft.resources.Identifier;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

public final class EffectUniforms {

    public static final int WIDTH = 4;
    public static final int CHANNELS = WIDTH * 4;

    private final Identifier id;
    private final float[] channels = new float[CHANNELS];
    private final ByteBuffer pixels = BufferUtils.createByteBuffer(WIDTH * 4);

    private RegisteredColorTexture registered;
    private int glId;
    private boolean dirty = true;

    public EffectUniforms(Identifier id) {
        this.id = id;
    }

    public Identifier id() {
        return id;
    }

    public void set(int channel, float value01) {
        if (channel < 0 || channel >= CHANNELS) return;
        float v = value01 < 0f ? 0f : (value01 > 1f ? 1f : value01);
        if (channels[channel] != v) {
            channels[channel] = v;
            dirty = true;
        }
    }

    public float get(int channel) {
        return (channel < 0 || channel >= CHANNELS) ? 0f : channels[channel];
    }

    public void upload() {
        ensureTexture();
        if (!dirty) return;
        pixels.clear();
        for (float c : channels) {
            pixels.put((byte) Math.round(c * 255f));
        }
        pixels.flip();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, glId);
        GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, WIDTH, 1,
                GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixels);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
        dirty = false;
    }

    private void ensureTexture() {
        if (glId != 0) return;
        glId = GL11.glGenTextures();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, glId);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, WIDTH, 1, 0,
                GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
        registered = new RegisteredColorTexture(id);
        registered.update(glId, WIDTH, 1);
    }

    public void dispose() {
        if (glId != 0) {
            GL11.glDeleteTextures(glId);
            glId = 0;
        }
        registered = null;
    }
}

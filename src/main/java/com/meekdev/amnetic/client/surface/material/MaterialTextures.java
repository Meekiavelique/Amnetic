package com.meekdev.amnetic.client.surface.material;

import com.meekdev.amnetic.client.render.GlState;
import java.nio.ByteBuffer;
import java.util.Random;
import org.lwjgl.opengl.GL11;
import org.lwjgl.system.MemoryUtil;

public final class MaterialTextures {

    private static int noise;

    private MaterialTextures() {}

    public static int noise() {
        if (noise != 0) return noise;
        int size = 256;
        ByteBuffer data = MemoryUtil.memAlloc(size * size * 4);
        Random rng = new Random(0x5EEDF00DL);
        for (int i = 0; i < size * size * 4; i++) data.put((byte) rng.nextInt(256));
        data.flip();

        noise = GL11.glGenTextures();
        GlState.bindTexture(0, noise);
        GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 1);
        GL11.glPixelStorei(GL11.GL_UNPACK_ROW_LENGTH, 0);
        GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_ROWS, 0);
        GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_PIXELS, 0);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, size, size, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, data);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_REPEAT);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL11.GL_REPEAT);
        MemoryUtil.memFree(data);
        return noise;
    }
}

package com.meekdev.amnetic.client.compute;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

public final class ComputeTexture implements AutoCloseable {

    private final int id;
    private final int width;
    private final int height;

    private ComputeTexture(int id, int width, int height) {
        this.id = id;
        this.width = width;
        this.height = height;
    }

    public static ComputeTexture load(Identifier pngId) {
        BufferedImage img = readImage(pngId);
        int w = img.getWidth();
        int h = img.getHeight();

        ByteBuffer pixels = BufferUtils.createByteBuffer(w * h * 4);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int argb = img.getRGB(x, y);
                pixels.put((byte) (argb >> 16)); // R
                pixels.put((byte) (argb >> 8));  // G
                pixels.put((byte) argb);         // B
                pixels.put((byte) (argb >> 24)); // A
            }
        }
        pixels.flip();

        int tex = GL11.glGenTextures();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, tex);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_REPEAT);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL11.GL_REPEAT);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR_MIPMAP_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, w, h, 0,
                GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixels);
        GL30.glGenerateMipmap(GL11.GL_TEXTURE_2D);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
        return new ComputeTexture(tex, w, h);
    }

    public int id() {
        return id;
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    @Override
    public void close() {
        if (id != 0) {
            GL11.glDeleteTextures(id);
        }
    }

    private static BufferedImage readImage(Identifier id) {
        Resource resource = Minecraft.getInstance().getResourceManager().getResource(id)
                .orElseThrow(() -> new RuntimeException("Compute texture not found: " + id));
        try (InputStream in = resource.open()) {
            BufferedImage img = ImageIO.read(in);
            if (img == null) {
                throw new RuntimeException("Unsupported image format for compute texture: " + id);
            }
            return img;
        } catch (IOException e) {
            throw new RuntimeException("Failed to read compute texture " + id, e);
        }
    }
}

package com.meekdev.amnetic.client.model.internal;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Optional;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

public final class ModelTexture implements AutoCloseable {

    private int id;

    private ModelTexture(int id) { this.id = id; }

    public int id() { return id; }

    public static ModelTexture fromBytes(byte[] bytes) {
        try (InputStream is = new ByteArrayInputStream(bytes)) {
            BufferedImage img = ImageIO.read(is);
            return img == null ? null : upload(img);
        } catch (Exception e) {
            return null;
        }
    }

    public static ModelTexture fromIdentifier(Identifier id) {
        try {
            Optional<Resource> res = Minecraft.getInstance().getResourceManager().getResource(id);
            if (res.isEmpty()) return null;
            try (InputStream is = res.get().open()) {
                BufferedImage img = ImageIO.read(is);
                return img == null ? null : upload(img);
            }
        } catch (Exception e) {
            return null;
        }
    }

    private static ModelTexture upload(BufferedImage img) {
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
        return new ModelTexture(tex);
    }

    @Override
    public void close() {
        if (id != 0) {
            GL11.glDeleteTextures(id);
            id = 0;
        }
    }
}

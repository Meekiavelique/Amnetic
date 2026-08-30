package com.meekdev.amnetic.client.model.internal;

import com.meekdev.amnetic.client.model.TextureFilter;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import javax.imageio.ImageIO;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.EXTTextureFilterAnisotropic;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL21;
import org.lwjgl.opengl.GL30;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ModelTexture implements AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger("Amnetic/ModelTexture");

    private static int fallback;

    private static final Map<CacheKey, ModelTexture> IDENTIFIER_CACHE = new ConcurrentHashMap<>();

    private volatile int id; // 0 until the GL upload runs
    private volatile boolean closed;
    private boolean shared; // cached identifier texture: outlives the models that use it

    private ModelTexture() {}

    private record CacheKey(Identifier id, boolean srgb) {}

    public int id() {
        int local = id;
        return local != 0 ? local : fallbackTexture();
    }

    public boolean ready() {
        return id != 0;
    }

    public static ModelTexture fromBytes(byte[] bytes, boolean srgb) {
        return fromBytes(bytes, srgb, TextureFilter.LINEAR);
    }

    public static ModelTexture fromBytes(byte[] bytes, boolean srgb, TextureFilter filter) {
        ModelTexture tex = new ModelTexture();
        GlUploadQueue.decode(() -> {
            try (InputStream is = new ByteArrayInputStream(bytes)) {
                BufferedImage img = ImageIO.read(is);
                if (img == null) {
                    LOG.warn("Amnetic: embedded model texture could not be decoded");
                    return;
                }
                Decoded d = decode(img);
                GlUploadQueue.submit(() -> tex.finishUpload(d, srgb, filter));
            } catch (Exception e) {
                LOG.warn("Amnetic: failed to decode embedded model texture", e);
            }
        });
        return tex;
    }

    public static ModelTexture external(int glId) {
        ModelTexture tex = new ModelTexture();
        tex.id = glId;
        tex.shared = true;
        return tex;
    }

    public static ModelTexture fromIdentifier(Identifier resId, boolean srgb) {
        CacheKey key = new CacheKey(resId, srgb);
        ModelTexture cached = IDENTIFIER_CACHE.get(key);
        if (cached != null) return cached;
        byte[] bytes;
        try {
            Optional<Resource> res = Minecraft.getInstance().getResourceManager().getResource(resId);
            if (res.isEmpty()) {
                LOG.warn("Amnetic: model texture not found: {}", resId);
                return null;
            }
            try (InputStream is = res.get().open()) {
                bytes = is.readAllBytes();
            }
        } catch (Exception e) {
            LOG.warn("Amnetic: failed to read model texture: {}", resId, e);
            return null;
        }
        ModelTexture tex = fromBytes(bytes, srgb, TextureFilter.NEAREST);
        tex.shared = true;
        IDENTIFIER_CACHE.put(key, tex);
        return tex;
    }

    private static Decoded decode(BufferedImage img) {
        int w = img.getWidth();
        int h = img.getHeight();
        int[] argb = img.getRGB(0, 0, w, h, null, 0, w);
        ByteBuffer pixels = BufferUtils.createByteBuffer(w * h * 4);
        for (int c : argb) {
            pixels.put((byte) (c >> 16));
            pixels.put((byte) (c >> 8));
            pixels.put((byte) c);
            pixels.put((byte) (c >> 24));
        }
        pixels.flip();
        return new Decoded(w, h, pixels);
    }

    private void finishUpload(Decoded d, boolean srgb, TextureFilter filter) {
        if (closed) {
            return;
        }
        int tex = GL11.glGenTextures();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, tex);
        GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 4);
        GL11.glPixelStorei(GL11.GL_UNPACK_ROW_LENGTH, 0);
        GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_ROWS, 0);
        GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_PIXELS, 0);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_REPEAT);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL11.GL_REPEAT);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER,
                filter == TextureFilter.NEAREST ? GL11.GL_NEAREST_MIPMAP_LINEAR : GL11.GL_LINEAR_MIPMAP_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER,
                filter == TextureFilter.NEAREST ? GL11.GL_NEAREST : GL11.GL_LINEAR);
        int internalFormat = srgb ? GL21.GL_SRGB8_ALPHA8 : GL11.GL_RGBA8;
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, internalFormat, d.w, d.h, 0,
                GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, d.pixels);
        GL30.glGenerateMipmap(GL11.GL_TEXTURE_2D);
        if (filter != TextureFilter.NEAREST && GL.getCapabilities().GL_EXT_texture_filter_anisotropic) {
            float maxAniso = GL11.glGetFloat(EXTTextureFilterAnisotropic.GL_MAX_TEXTURE_MAX_ANISOTROPY_EXT);
            GL11.glTexParameterf(GL11.GL_TEXTURE_2D,
                    EXTTextureFilterAnisotropic.GL_TEXTURE_MAX_ANISOTROPY_EXT, Math.min(16f, maxAniso));
        }
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
        if (closed) {
            GL11.glDeleteTextures(tex);
        } else {
            id = tex;
        }
    }

    private static int fallbackTexture() {
        if (fallback == 0) {
            fallback = GL11.glGenTextures();
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, fallback);
            ByteBuffer one = BufferUtils.createByteBuffer(4);
            one.put((byte) 255).put((byte) 255).put((byte) 255).put((byte) 255).flip();
            GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 4);
            GL11.glPixelStorei(GL11.GL_UNPACK_ROW_LENGTH, 0);
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, 1, 1, 0,
                    GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, one);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
        }
        return fallback;
    }

    @Override
    public void close() {
        if (shared) return; // shared identifier textures live for the session, an individual model can't free them
        closed = true;
        int handle = id;
        id = 0;
        if (handle != 0) {
            GlReaper.submit(() -> GL11.glDeleteTextures(handle));
        }
    }

    private record Decoded(int w, int h, ByteBuffer pixels) {}
}

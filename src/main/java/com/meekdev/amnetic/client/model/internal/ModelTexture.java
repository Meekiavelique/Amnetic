package com.meekdev.amnetic.client.model.internal;

import com.meekdev.amnetic.client.content.ImageProbe;
import com.meekdev.amnetic.client.content.RejectedContentException;
import com.meekdev.amnetic.client.model.TextureFilter;
import com.meekdev.amnetic.client.model.internal.ammesh.TextureCache;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.EXTTextureCompressionS3TC;
import org.lwjgl.opengl.EXTTextureFilterAnisotropic;
import org.lwjgl.opengl.EXTTextureSRGB;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL21;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ModelTexture implements AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger("Amnetic/ModelTexture");

    private static int fallback;

    private static final Map<CacheKey, ModelTexture> IDENTIFIER_CACHE = new ConcurrentHashMap<>();

    private static final Map<String, ModelTexture> STORED = new ConcurrentHashMap<>();
    private static final int SMALLEST_COMPRESSED = 128;
    private static Boolean dxt;

    private volatile int id;
    private volatile boolean closed;
    private boolean shared;
    private String storedAs;
    private int users;

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
        String name = "bytes:" + TextureCache.hash(bytes).substring(0, 32) + (srgb ? "s" : "l") + filter;
        synchronized (STORED) {
            ModelTexture known = STORED.get(name);
            if (known != null) {
                known.users++;
                return known;
            }
            ModelTexture tex = new ModelTexture();
            tex.storedAs = name;
            tex.users = 1;
            STORED.put(name, tex);
            GlUploadQueue.decode(() -> {
                try {
                    Decoded d = decode(ImageProbe.decode(bytes, ImageProbe.TEXTURES));
                    GlUploadQueue.submit(() -> tex.finishUpload(d, srgb, filter));
                } catch (RejectedContentException refused) {
                    LOG.warn("Amnetic: embedded model texture is refused: {}", refused.getMessage());
                } catch (Exception e) {
                    LOG.warn("Amnetic: failed to decode embedded model texture", e);
                }
            });
            return tex;
        }
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

    public static ModelTexture fromStore(Path dir, String key, boolean srgb, TextureFilter filter) {
        String name = dir + "/" + key;
        synchronized (STORED) {
            ModelTexture known = STORED.get(name);
            if (known != null) {
                known.users++;
                return known;
            }
            ModelTexture tex = new ModelTexture();
            tex.storedAs = name;
            tex.users = 1;
            STORED.put(name, tex);
            boolean squeeze = srgb && filter == TextureFilter.LINEAR && dxtSupported();
            GlUploadQueue.decode(() -> {
                TextureCache.Image image;
                try {
                    image = squeeze ? TextureCache.read(dir, key, true) : null;
                    if (image == null) image = TextureCache.read(dir, key, false);
                } catch (RejectedContentException refused) {
                    LOG.warn("Amnetic: processed texture {} in {} is refused: {}", key, dir, refused.getMessage());
                    return;
                }
                if (image == null) {
                    LOG.warn("Amnetic: processed texture {} is missing from {}", key, dir);
                    return;
                }
                TextureCache.Image ready = image;
                boolean compress = squeeze && ready.format() == TextureCache.RGBA
                        && Math.min(ready.width(), ready.height()) >= SMALLEST_COMPRESSED;
                GlUploadQueue.submit(() -> tex.finishStored(ready, srgb, filter, compress ? new Squeeze(dir, key) : null));
            });
            return tex;
        }
    }

    private record Squeeze(Path dir, String key) {}

    private static boolean dxtSupported() {
        if (dxt == null) {
            var caps = GL.getCapabilities();
            dxt = caps.GL_EXT_texture_compression_s3tc && caps.GL_EXT_texture_sRGB;
        }
        return dxt;
    }

    private void finishStored(TextureCache.Image image, boolean srgb, TextureFilter filter, Squeeze squeeze) {
        if (closed) return;
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
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_BASE_LEVEL, 0);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_MAX_LEVEL, image.levels().length - 1);
        int kind = image.opaque() ? TextureCache.DXT1 : TextureCache.DXT5;
        int squeezed = squeeze == null ? 0 : compressedFormat(kind, srgb);
        for (int n = 0; n < image.levels().length; n++) {
            int w = Math.max(1, image.width() >> n);
            int h = Math.max(1, image.height() >> n);
            if (image.format() == TextureCache.RGBA) {
                int internal = squeezed != 0 ? squeezed : srgb ? GL21.GL_SRGB8_ALPHA8 : GL11.GL_RGBA8;
                GL11.glTexImage2D(GL11.GL_TEXTURE_2D, n, internal, w, h, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, image.levels()[n]);
            } else {
                GL13.glCompressedTexImage2D(GL11.GL_TEXTURE_2D, n, compressedFormat(image.format(), srgb), w, h, 0, image.levels()[n]);
            }
        }
        if (filter != TextureFilter.NEAREST && GL.getCapabilities().GL_EXT_texture_filter_anisotropic) {
            float maxAniso = GL11.glGetFloat(EXTTextureFilterAnisotropic.GL_MAX_TEXTURE_MAX_ANISOTROPY_EXT);
            GL11.glTexParameterf(GL11.GL_TEXTURE_2D, EXTTextureFilterAnisotropic.GL_TEXTURE_MAX_ANISOTROPY_EXT, Math.min(16f, maxAniso));
        }
        if (squeezed != 0) keepSqueezed(image, squeeze, kind);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
        if (closed) GL11.glDeleteTextures(tex);
        else id = tex;
    }

    private static void keepSqueezed(TextureCache.Image image, Squeeze squeeze, int format) {
        if (GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL13.GL_TEXTURE_COMPRESSED) == 0) return;
        byte[][] levels = new byte[image.levels().length][];
        for (int n = 0; n < levels.length; n++) {
            int size = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, n, GL13.GL_TEXTURE_COMPRESSED_IMAGE_SIZE);
            ByteBuffer out = MemoryUtil.memAlloc(size);
            try {
                GL13.glGetCompressedTexImage(GL11.GL_TEXTURE_2D, n, out);
                levels[n] = new byte[size];
                out.get(0, levels[n]);
            } finally {
                MemoryUtil.memFree(out);
            }
        }
        GlUploadQueue.decode(() -> {
            try {
                TextureCache.storeCompressed(squeeze.dir(), squeeze.key(), format, image.width(), image.height(), image.opaque(), levels);
            } catch (IOException e) {
                LOG.warn("Amnetic: could not keep the compressed {}", squeeze.key(), e);
            }
        });
    }

    private static int compressedFormat(int format, boolean srgb) {
        if (format == TextureCache.DXT1) {
            return srgb ? EXTTextureSRGB.GL_COMPRESSED_SRGB_S3TC_DXT1_EXT : EXTTextureCompressionS3TC.GL_COMPRESSED_RGB_S3TC_DXT1_EXT;
        }
        return srgb ? EXTTextureSRGB.GL_COMPRESSED_SRGB_ALPHA_S3TC_DXT5_EXT : EXTTextureCompressionS3TC.GL_COMPRESSED_RGBA_S3TC_DXT5_EXT;
    }

    private static Decoded decode(BufferedImage img) {
        int w = img.getWidth();
        int h = img.getHeight();
        ByteBuffer pixels = MemoryUtil.memAlloc(w * h * 4);
        int[] row = new int[w];
        for (int y = 0; y < h; y++) {
            img.getRGB(0, y, w, 1, row, 0, w);
            for (int c : row) {
                pixels.put((byte) (c >> 16));
                pixels.put((byte) (c >> 8));
                pixels.put((byte) c);
                pixels.put((byte) (c >> 24));
            }
        }
        pixels.flip();
        return new Decoded(w, h, pixels);
    }

    private void finishUpload(Decoded d, boolean srgb, TextureFilter filter) {
        if (closed) {
            MemoryUtil.memFree(d.pixels);
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
        MemoryUtil.memFree(d.pixels);
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
        if (shared) return;
        if (storedAs != null) {
            synchronized (STORED) {
                if (--users > 0) return;
                STORED.remove(storedAs);
            }
        }
        closed = true;
        int handle = id;
        id = 0;
        if (handle != 0) {
            GlReaper.submit(() -> GL11.glDeleteTextures(handle));
        }
    }

    private record Decoded(int w, int h, ByteBuffer pixels) {}
}

package com.meekdev.amnetic.client.model.internal;

import com.meekdev.amnetic.client.model.TextureFilter;
import com.meekdev.amnetic.client.model.internal.ammesh.TextureCache;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import javax.imageio.ImageIO;

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

/**
 * model texture uploaded asynchronously: image decodes on a worker thread and the GL upload is
 * queued via GlUploadQueue so it never stalls the frame it first appears. until the real texture
 * lands, id() returns a shared 1x1 fallback so the model draws immediately instead of hitching
 */
public final class ModelTexture implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger("Amnetic/ModelTexture");

    private static int fallback; // shared 1x1 white, lazily created on the render thread

    // identifier textures are shared across every model that references them. without sharing, each
    // terrain chunk re-decodes + re-uploads its own copy and shows the 1x1 white fallback until that
    // private upload lands (the "white chunk" flash). session-lived: shared entries are never freed
    // by an individual model's disposal
    private static final Map<CacheKey, ModelTexture> IDENTIFIER_CACHE = new ConcurrentHashMap<>();

    // processed textures by folder and key, shared by every model naming them and freed with the last one
    private static final Map<String, ModelTexture> STORED = new ConcurrentHashMap<>();
    private static final int SMALLEST_COMPRESSED = 128;
    private static Boolean dxt;

    private volatile int id; // 0 until the GL upload runs
    private volatile boolean closed;
    private boolean shared; // cached identifier texture: outlives the models that use it
    private String storedAs;
    private int users;

    private ModelTexture() {}

    private record CacheKey(Identifier id, boolean srgb) {}

    // real GL texture id if uploaded, otherwise the shared fallback so drawing never blocks on a decode
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

    // images with the same bytes share one texture, freed with the last model using it
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
    }

    // wraps a caller-owned GL texture id (e.g. a dynamically updated video/emissive texture)
    // always ready, never freed by model disposal, bypasses the async decode path
    public static ModelTexture external(int glId) {
        ModelTexture tex = new ModelTexture();
        tex.id = glId;
        tex.shared = true; // close() is a no-op, the caller owns the GL object
        return tex;
    }

    public static ModelTexture fromIdentifier(Identifier resId, boolean srgb) {
        CacheKey key = new CacheKey(resId, srgb);
        ModelTexture cached = IDENTIFIER_CACHE.get(key);
        if (cached != null) return cached; // already decoding/uploaded elsewhere, share it (no re-upload, no white)
        // read the resource bytes on the caller thread (cheap), then decode off-thread like the embedded path
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
        // came out of the resource pack, so it is authored pixel art rather than a PBR map
        ModelTexture tex = fromBytes(bytes, srgb, TextureFilter.NEAREST);
        tex.shared = true;
        IDENTIFIER_CACHE.put(key, tex);
        return tex;
    }

    // a texture the converter already decoded with all its mips, render thread only
    // a smooth colour map the driver can squeeze is uploaded compressed and the squeezed mips kept for next time
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
                TextureCache.Image image = squeeze ? TextureCache.read(dir, key, true) : null;
                if (image == null) image = TextureCache.read(dir, key, false);
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

    // render thread: every level as it is on disk, or compressed by the driver and read back to keep
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

    // reads what the driver made of each level and writes it beside the plain mips, off this thread
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

    // CPU only: decode into a tightly packed RGBA buffer, safe off the render thread
    private static Decoded decode(BufferedImage img) {
        int w = img.getWidth();
        int h = img.getHeight();
        // freed right after the upload, a gc-only direct buffer per image grows the heap while a level loads
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

    // render thread: create the GL texture from the decoded pixels
    private void finishUpload(Decoded d, boolean srgb, TextureFilter filter) {
        if (closed) {
            MemoryUtil.memFree(d.pixels);
            return;
        }
        int tex = GL11.glGenTextures();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, tex);
        // reset pixel-unpack state, Blaze3D leaves ROW_LENGTH/skip set from its last upload which shears rows
        GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 4);
        GL11.glPixelStorei(GL11.GL_UNPACK_ROW_LENGTH, 0);
        GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_ROWS, 0);
        GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_PIXELS, 0);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_REPEAT);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL11.GL_REPEAT);
        // nearest magnification keeps pixels crisp up close, mipmapped minification still avoids
        // shimmer at distance
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
            GL11.glDeleteTextures(tex); // closed while uploading, drop it
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

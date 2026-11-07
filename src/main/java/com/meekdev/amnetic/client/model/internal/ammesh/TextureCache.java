package com.meekdev.amnetic.client.model.internal.ammesh;

import com.meekdev.amnetic.client.content.Bounded;
import com.meekdev.amnetic.client.content.ImageProbe;
import com.meekdev.amnetic.client.content.RejectedContentException;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;

// images decoded once into their whole mip chain on disk, named by what they hold, so drawing never decodes
// no gl in here, a server can fill it too
public final class TextureCache {

    private static final byte[] MAGIC = {'A', 'M', 'T', 'X'};
    // bump AmmeshFormat.CONVERTER too when this changes, models name their textures by key
    private static final int VERSION = 1;
    private static final int HEADER = 4 + 4 * 6;

    public static final int RGBA = 0;
    public static final int DXT1 = 1;
    public static final int DXT5 = 2;

    private static final int RGBA_TEXEL = 4;
    private static final int DXT_BLOCK = 4;
    private static final int DXT1_BLOCK_BYTES = 8;
    private static final int DXT5_BLOCK_BYTES = 16;

    private static final float[] TO_LINEAR = new float[256];

    static {
        for (int i = 0; i < 256; i++) {
            float c = i / 255f;
            TO_LINEAR[i] = c <= 0.04045f ? c / 12.92f : (float) Math.pow((c + 0.055f) / 1.055f, 2.4f);
        }
    }

    // levels are tightly packed, rgba8 or dxt blocks, level n is width >> n by height >> n
    public record Image(int format, int width, int height, boolean opaque, ByteBuffer[] levels) {}

    private TextureCache() {}

    // srgb images get their mips averaged in linear light, so a colour map and a normal map of the same bytes differ
    public static String key(byte[] image, boolean srgb) {
        return hash(image).substring(0, 32) + (srgb ? "s" : "l");
    }

    public static String hash(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public static Path file(Path dir, String key, boolean compressed) {
        return dir.resolve(key + (compressed ? ".dxt.amtex" : ".amtex"));
    }

    public static boolean has(Path dir, String key) {
        return Files.isRegularFile(file(dir, key, false));
    }

    // decodes the image and writes its mips under key, refused when it is no image a model can use or too big
    public static void store(Path dir, String key, byte[] image, boolean srgb) throws IOException {
        if (has(dir, key)) return;
        BufferedImage decoded = ImageProbe.decode(image, ImageProbe.TEXTURES);
        int w = decoded.getWidth();
        int h = decoded.getHeight();
        boolean opaque = true;
        byte[] level = new byte[w * h * 4];
        int[] row = new int[w];
        for (int y = 0; y < h; y++) {
            decoded.getRGB(0, y, w, 1, row, 0, w);
            for (int x = 0; x < w; x++) {
                int c = row[x];
                int i = (y * w + x) * 4;
                level[i] = (byte) (c >> 16);
                level[i + 1] = (byte) (c >> 8);
                level[i + 2] = (byte) c;
                level[i + 3] = (byte) (c >>> 24);
                if (c >>> 24 != 255) opaque = false;
            }
        }
        int count = levels(w, h);
        byte[][] levels = new byte[count][];
        levels[0] = level;
        for (int n = 1; n < count; n++) levels[n] = half(levels[n - 1], Math.max(1, w >> (n - 1)), Math.max(1, h >> (n - 1)), srgb);
        write(file(dir, key, false), RGBA, w, h, opaque, levels);
    }

    // the same mips as the driver compressed them, kept beside the plain ones
    public static void storeCompressed(Path dir, String key, int format, int width, int height, boolean opaque, byte[][] levels) throws IOException {
        write(file(dir, key, true), format, width, height, opaque, levels);
    }

    // mapped, so uploading reads straight from the file, null when missing or from another version
    // a file that claims more than it holds is refused before any of it reaches the driver
    public static Image read(Path dir, String key, boolean compressed) {
        Path file = file(dir, key, compressed);
        if (!Files.isRegularFile(file)) return null;
        try (FileChannel channel = FileChannel.open(file, StandardOpenOption.READ)) {
            MappedByteBuffer map = channel.map(FileChannel.MapMode.READ_ONLY, 0, channel.size());
            map.order(ByteOrder.BIG_ENDIAN);
            if (map.remaining() < HEADER) return null;
            byte[] magic = new byte[MAGIC.length];
            map.get(magic);
            if (!Arrays.equals(magic, MAGIC) || map.getInt() != VERSION) return null;
            int format = map.getInt();
            int w = map.getInt();
            int h = map.getInt();
            boolean opaque = map.getInt() != 0;
            int count = map.getInt();
            Bounded.require(format == RGBA || format == DXT1 || format == DXT5, "texture " + key + " has unknown format " + format);
            ImageProbe.checkSize(w, h);
            Bounded.require(count > 0 && count <= levels(w, h), "texture " + key + " claims " + count + " mip levels");
            ByteBuffer[] levels = new ByteBuffer[count];
            for (int n = 0; n < count; n++) {
                int size = Bounded.count(map, 1, "texture " + key + " level " + n);
                int expected = levelBytes(format, Math.max(1, w >> n), Math.max(1, h >> n));
                Bounded.require(size == expected, "texture " + key + " level " + n + " holds " + size + " bytes, not " + expected);
                levels[n] = map.slice(map.position(), size);
                map.position(map.position() + size);
            }
            return new Image(format, w, h, opaque, levels);
        } catch (IOException e) {
            return null;
        } catch (BufferUnderflowException e) {
            throw new RejectedContentException("texture " + key + " ends early");
        }
    }

    // what one level of an image takes, rgba8 or dxt blocks of four by four texels
    static int levelBytes(int format, int w, int h) {
        if (format == RGBA) return w * h * RGBA_TEXEL;
        int blocks = ((w + DXT_BLOCK - 1) / DXT_BLOCK) * ((h + DXT_BLOCK - 1) / DXT_BLOCK);
        return blocks * (format == DXT1 ? DXT1_BLOCK_BYTES : DXT5_BLOCK_BYTES);
    }

    public static int levels(int w, int h) {
        return 32 - Integer.numberOfLeadingZeros(Math.max(w, h));
    }

    // box filter down one level, an odd edge repeats its last texel like the driver does
    static byte[] half(byte[] from, int w, int h, boolean srgb) {
        int nw = Math.max(1, w >> 1);
        int nh = Math.max(1, h >> 1);
        byte[] out = new byte[nw * nh * 4];
        for (int y = 0; y < nh; y++) {
            int y0 = Math.min(h - 1, y * 2);
            int y1 = Math.min(h - 1, y * 2 + 1);
            for (int x = 0; x < nw; x++) {
                int x0 = Math.min(w - 1, x * 2);
                int x1 = Math.min(w - 1, x * 2 + 1);
                int a = (y0 * w + x0) * 4;
                int b = (y0 * w + x1) * 4;
                int c = (y1 * w + x0) * 4;
                int d = (y1 * w + x1) * 4;
                int to = (y * nw + x) * 4;
                for (int ch = 0; ch < 4; ch++) {
                    if (srgb && ch < 3) {
                        float sum = TO_LINEAR[from[a + ch] & 255] + TO_LINEAR[from[b + ch] & 255]
                                + TO_LINEAR[from[c + ch] & 255] + TO_LINEAR[from[d + ch] & 255];
                        out[to + ch] = (byte) toSrgb(sum / 4f);
                    } else {
                        int sum = (from[a + ch] & 255) + (from[b + ch] & 255) + (from[c + ch] & 255) + (from[d + ch] & 255);
                        out[to + ch] = (byte) ((sum + 2) >> 2);
                    }
                }
            }
        }
        return out;
    }

    private static int toSrgb(float linear) {
        float c = linear <= 0.0031308f ? linear * 12.92f : 1.055f * (float) Math.pow(linear, 1 / 2.4f) - 0.055f;
        return Math.max(0, Math.min(255, Math.round(c * 255f)));
    }

    private static void write(Path file, int format, int w, int h, boolean opaque, byte[][] levels) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream(HEADER + levels[0].length * 2);
        try (DataOutputStream out = new DataOutputStream(buf)) {
            out.write(MAGIC);
            out.writeInt(VERSION);
            out.writeInt(format);
            out.writeInt(w);
            out.writeInt(h);
            out.writeInt(opaque ? 1 : 0);
            out.writeInt(levels.length);
            for (byte[] level : levels) {
                out.writeInt(level.length);
                out.write(level);
            }
        }
        atomicWrite(file, buf.toByteArray());
    }

    // written beside and moved in, a reader never sees half a file and two writers of one key both win
    static void atomicWrite(Path file, byte[] bytes) throws IOException {
        Files.createDirectories(file.getParent());
        Path temporary = Files.createTempFile(file.getParent(), ".part-", ".tmp");
        try {
            Files.write(temporary, bytes);
            try {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}

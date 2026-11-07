package com.meekdev.amnetic.client.model.internal.ammesh;

import com.meekdev.amnetic.client.content.Hostile;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

public final class TextureCacheRegression {

    private TextureCacheRegression() {}

    public static void verify() throws IOException {
        Path dir = Files.createTempDirectory("amnetic-amtex-test-");
        try {
            amtex(dir, "huge", TextureCache.RGBA, 65536, 65536, 1, 4);
            Hostile.refused(".amtex claiming 65536 by 65536 in 4 bytes", "larger than", () -> TextureCache.read(dir, "huge", false));
            amtex(dir, "short", TextureCache.RGBA, 4, 4, 1, 8);
            Hostile.refused(".amtex level shorter than its size", "holds 8 bytes, not 64", () -> TextureCache.read(dir, "short", false));
            amtex(dir, "levels", TextureCache.DXT1, 4, 4, 31, 8);
            Hostile.refused(".amtex claiming 31 mip levels of a 4 by 4", "mip levels", () -> TextureCache.read(dir, "levels", false));
            amtex(dir, "format", 7, 4, 4, 1, 64);
            Hostile.refused(".amtex of an unknown format", "unknown format", () -> TextureCache.read(dir, "format", false));
            amtex(dir, "past", TextureCache.RGBA, 4, 4, 1, Integer.MAX_VALUE);
            Hostile.refused(".amtex level running past the file", "bytes are left", () -> TextureCache.read(dir, "past", false));
            amtex(dir, "cut", TextureCache.RGBA, 2, 1, 2, 8);
            Hostile.refused(".amtex missing a level it claims", "ends early", () -> TextureCache.read(dir, "cut", false));
            amtex(dir, "good", TextureCache.RGBA, 2, 1, 1, 8);
            Hostile.require(TextureCache.read(dir, "good", false).width() == 2, "a real .amtex reads");
            System.out.println("PASS: a real .amtex reads");
        } finally {
            try (var paths = Files.walk(dir)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
            }
        }
    }

    // the header writes what the test claims, the one level holds as many bytes as the claimed size says up to 64
    private static void amtex(Path dir, String key, int format, int width, int height, int levels, int size) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        out.write(new byte[] {'A', 'M', 'T', 'X'});
        out.writeInt(1);
        out.writeInt(format);
        out.writeInt(width);
        out.writeInt(height);
        out.writeInt(1);
        out.writeInt(levels);
        out.writeInt(size);
        out.write(new byte[Math.min(size, 64)]);
        Files.write(TextureCache.file(dir, key, false), bytes.toByteArray());
    }
}

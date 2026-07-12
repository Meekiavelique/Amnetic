package com.meekdev.amnetic.client.render;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ImportedTextures {

    private static final Map<String, Identifier> CACHE = new HashMap<>();

    private ImportedTextures() {}

    public static boolean isImported(Identifier id) {
        return id != null && "amnetic".equals(id.getNamespace())
                && (id.getPath().startsWith("imported/") || id.getPath().startsWith("file/"));
    }

    public static Path dir() {
        Path p = Minecraft.getInstance().gameDirectory.toPath().resolve("amnetic").resolve("textures");
        try { Files.createDirectories(p); } catch (IOException ignored) {}
        return p;
    }

    public static List<String> list() {
        try (var s = Files.list(dir())) {
            List<String> out = new ArrayList<>();
            s.filter(f -> f.getFileName().toString().toLowerCase().endsWith(".png"))
                    .forEach(f -> out.add(f.getFileName().toString()));
            out.sort(String::compareTo);
            return out;
        } catch (IOException e) {
            return List.of();
        }
    }

    public static Identifier idFor(String fileName) {
        return load(fileName, dir().resolve(fileName));
    }

    public static Identifier idForPath(String absolutePath) {
        Path p = Path.of(absolutePath);
        String key = p.toAbsolutePath().toString();
        String name = sanitize(p.getFileName().toString()) + "_" + Integer.toHexString(key.hashCode());
        return load(key, p, "file/" + name);
    }

    private static Identifier load(String cacheKey, Path file) {
        return load(cacheKey, file, "imported/" + sanitize(file.getFileName().toString()));
    }

    private static Identifier load(String cacheKey, Path file, String idPath) {
        Identifier cached = CACHE.get(cacheKey);
        if (cached != null) return cached;
        try (InputStream in = Files.newInputStream(file)) {
            NativeImage img = NativeImage.read(in);
            DynamicTexture tex = new DynamicTexture(() -> "amnetic_imported/" + idPath, img);
            tex.upload();
            Identifier id = Identifier.fromNamespaceAndPath("amnetic", idPath);
            Minecraft.getInstance().getTextureManager().register(id, tex);
            CACHE.put(cacheKey, id);
            return id;
        } catch (Exception e) {
            return null;
        }
    }

    private static String sanitize(String s) {
        return s.toLowerCase().replaceAll("[^a-z0-9_./-]", "_");
    }
}

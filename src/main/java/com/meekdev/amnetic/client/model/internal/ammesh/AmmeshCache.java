package com.meekdev.amnetic.client.model.internal.ammesh;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.CRC32;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class AmmeshCache {

    public static final AmmeshCache INSTANCE = new AmmeshCache();
    private static final Logger LOG = LoggerFactory.getLogger("Amnetic/Ammesh");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type MANIFEST_TYPE = new TypeToken<HashMap<String, ManifestEntry>>() {}.getType();

    private final Path cacheDir = FabricLoader.getInstance().getGameDir().resolve("amnetic").resolve("ammesh_cache");
    private final Path manifestPath = cacheDir.resolve("manifest.json");
    private final Map<String, ManifestEntry> manifest = new ConcurrentHashMap<>();
    private volatile boolean manifestLoaded;

    private AmmeshCache() {}

    private static final class ManifestEntry {
        long crc32;
        long size;
        int formatVersion;
        String cachedFile;
    }

    private synchronized void ensureManifestLoaded() {
        if (manifestLoaded) return;
        manifestLoaded = true;
        if (!Files.exists(manifestPath)) return;
        try (Reader reader = Files.newBufferedReader(manifestPath, StandardCharsets.UTF_8)) {
            Map<String, ManifestEntry> loaded = GSON.fromJson(reader, MANIFEST_TYPE);
            if (loaded != null) manifest.putAll(loaded);
        } catch (IOException | RuntimeException e) {
            LOG.warn("Amnetic: failed to read ammesh cache manifest, starting fresh", e);
        }
    }

    private synchronized void saveManifest() {
        try {
            Files.createDirectories(cacheDir);
            try (Writer writer = Files.newBufferedWriter(manifestPath, StandardCharsets.UTF_8)) {
                GSON.toJson(new HashMap<>(manifest), MANIFEST_TYPE, writer);
            }
        } catch (IOException e) {
            LOG.warn("Amnetic: failed to write ammesh cache manifest", e);
        }
    }

    private Path cachedFilePath(Identifier source) {
        String safe = (source.getNamespace() + "_" + source.getPath()).replaceAll("[^a-zA-Z0-9_.-]", "_");
        return cacheDir.resolve(safe + ".ammesh");
    }

    private static long crc32(byte[] bytes) {
        CRC32 crc = new CRC32();
        crc.update(bytes);
        return crc.getValue();
    }

    public Optional<byte[]> getCached(Identifier source, byte[] sourceBytes) {
        ensureManifestLoaded();
        ManifestEntry entry = manifest.get(source.toString());
        if (entry == null || entry.formatVersion != AmmeshFormat.VERSION) return Optional.empty();
        if (entry.crc32 != crc32(sourceBytes) || entry.size != sourceBytes.length) return Optional.empty();
        Path path = cacheDir.resolve(entry.cachedFile);
        if (!Files.exists(path)) return Optional.empty();
        try {
            return Optional.of(Files.readAllBytes(path));
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    public Optional<byte[]> getCachedByIdentifierOnly(Identifier source) {
        ensureManifestLoaded();
        ManifestEntry entry = manifest.get(source.toString());
        if (entry == null || entry.formatVersion != AmmeshFormat.VERSION) return Optional.empty();
        Path path = cacheDir.resolve(entry.cachedFile);
        if (!Files.exists(path)) return Optional.empty();
        try {
            return Optional.of(Files.readAllBytes(path));
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    public byte[] convertAndStore(Identifier source, byte[] sourceBytes) {
        byte[] ammesh = AmmeshConverter.convert(sourceBytes, source);
        Path path = cachedFilePath(source);
        try {
            Files.createDirectories(cacheDir);
            Files.write(path, ammesh);
        } catch (IOException e) {
            LOG.error("Amnetic: failed to write ammesh cache file for {}", source, e);
            return ammesh;
        }
        ManifestEntry entry = new ManifestEntry();
        entry.crc32 = crc32(sourceBytes);
        entry.size = sourceBytes.length;
        entry.formatVersion = AmmeshFormat.VERSION;
        entry.cachedFile = path.getFileName().toString();
        manifest.put(source.toString(), entry);
        saveManifest();
        return ammesh;
    }

    public static byte[] readResourceBytes(Identifier source) throws IOException {
        Optional<Resource> res = Minecraft.getInstance().getResourceManager().getResource(source);
        if (res.isEmpty()) throw new IOException("Model resource not found: " + source);
        try (InputStream is = res.get().open()) {
            return is.readAllBytes();
        }
    }
}

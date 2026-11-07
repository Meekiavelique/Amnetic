package com.meekdev.amnetic.client.model.internal.ammesh;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.meekdev.amnetic.client.model.internal.ModelIR;
import com.meekdev.amnetic.client.model.internal.ModelLod;
import com.meekdev.amnetic.client.model.internal.parse.GltfParser;
import com.meekdev.amnetic.platform.Platform;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.zip.CRC32;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class AmmeshCache {
    public static final AmmeshCache INSTANCE = new AmmeshCache(null);
    private static final Map<Path, AmmeshCache> FOLDERS = new ConcurrentHashMap<>();
    private static final Logger LOG = LoggerFactory.getLogger("Amnetic/Ammesh");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type MANIFEST_TYPE = new TypeToken<HashMap<String, ManifestEntry>>() {}.getType();

    private final Path folder;
    private Path cacheDir;
    private Path manifestPath;
    private final Map<String, ManifestEntry> manifest = new ConcurrentHashMap<>();
    private volatile boolean manifestLoaded;

    private AmmeshCache(Path folder) {
        this.folder = folder;
    }

    public static AmmeshCache at(Path folder) {
        return FOLDERS.computeIfAbsent(folder.toAbsolutePath().normalize(), AmmeshCache::new);
    }

    private synchronized Path dir() {
        if (cacheDir == null) {
            cacheDir = folder != null ? folder : Platform.gameDirectory().resolve("amnetic").resolve("ammesh_cache");
            manifestPath = cacheDir.resolve("manifest.json");
        }
        return cacheDir;
    }

    public Path models() {
        return dir().resolve("models");
    }

    public Path textures() {
        return dir().resolve("textures");
    }

    public Path file(String key) {
        return models().resolve(key + ".ammesh");
    }

    public boolean has(String key) {
        return Files.isRegularFile(file(key));
    }

    public ModelIR read(String key) {
        Path path = file(key);
        if (!Files.isRegularFile(path)) return null;
        try {
            ModelIR ir = AmmeshReader.read(ByteBuffer.wrap(Files.readAllBytes(path)));
            ir.setTextures(textures());
            return ir;
        } catch (IOException e) {
            return null;
        }
    }

    public ModelIR convert(byte[] gltf, Identifier source, Function<String, byte[]> files, Supplier<String> key) {
        ModelIR ir = GltfParser.parse(gltf, source, files);
        ModelLod.build(ir);
        Path textures = textures();
        byte[] ammesh = AmmeshWriter.write(ir, (image, srgb) -> {
            String id = TextureCache.key(image, srgb);
            try {
                TextureCache.store(textures, id, image, srgb);
                return id;
            } catch (IOException | RuntimeException e) {
                LOG.warn("Amnetic: could not process an image of {}", source, e);
                return null;
            }
        });
        try {
            TextureCache.atomicWrite(file(key.get()), ammesh);
        } catch (IOException e) {
            LOG.warn("Amnetic: could not keep the processed {}", source, e);
        }
        ModelIR read = AmmeshReader.read(ammesh);
        read.setTextures(textures);
        return read;
    }

    private static final class ManifestEntry {
        long crc32;
        long size;
        int formatVersion;
        String cachedFile;
    }

    private synchronized void ensureManifestLoaded() {
        if (manifestLoaded) return;
        dir();
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
            Files.createDirectories(dir());
            try (Writer writer = Files.newBufferedWriter(manifestPath, StandardCharsets.UTF_8)) {
                GSON.toJson(new HashMap<>(manifest), MANIFEST_TYPE, writer);
            }
        } catch (IOException e) {
            LOG.warn("Amnetic: failed to write ammesh cache manifest", e);
        }
    }

    private Path cachedFilePath(Identifier source) {
        String safe = (source.getNamespace() + "_" + source.getPath()).replaceAll("[^a-zA-Z0-9_.-]", "_");
        return dir().resolve(safe + ".ammesh");
    }

    private static long crc32(byte[] bytes) {
        CRC32 crc = new CRC32();
        crc.update(bytes);
        return crc.getValue();
    }

    public Optional<byte[]> getCached(Identifier source, byte[] sourceBytes) {
        ensureManifestLoaded();
        ManifestEntry entry = manifest.get(source.toString());
        if (entry == null || entry.formatVersion != AmmeshFormat.CONVERTER) return Optional.empty();
        if (entry.crc32 != crc32(sourceBytes) || entry.size != sourceBytes.length) return Optional.empty();
        Path path = dir().resolve(entry.cachedFile);
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
        if (entry == null || entry.formatVersion != AmmeshFormat.CONVERTER) return Optional.empty();
        Path path = dir().resolve(entry.cachedFile);
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
            Files.createDirectories(dir());
            Files.write(path, ammesh);
        } catch (IOException e) {
            LOG.error("Amnetic: failed to write ammesh cache file for {}", source, e);
            return ammesh;
        }
        ManifestEntry entry = new ManifestEntry();
        entry.crc32 = crc32(sourceBytes);
        entry.size = sourceBytes.length;
        entry.formatVersion = AmmeshFormat.CONVERTER;
        entry.cachedFile = path.getFileName().toString();
        manifest.put(source.toString(), entry);
        saveManifest();
        return ammesh;
    }
}

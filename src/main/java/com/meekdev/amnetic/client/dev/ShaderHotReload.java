package com.meekdev.amnetic.client.dev;

import com.meekdev.amnetic.client.render.ShaderProgram;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ShaderHotReload {

    private static final Logger LOG = LoggerFactory.getLogger("Amnetic/ShaderHotReload");
    private static final Set<String> EXTENSIONS = Set.of("vsh", "fsh", "glsl", "comp", "geom", "tesc", "tese");

    private static final List<Runnable> RELOAD_CALLBACKS = new ArrayList<>();
    private static final ConcurrentLinkedQueue<Path> DIRTY = new ConcurrentLinkedQueue<>();
    private static final Map<Path, Path> ROOTS = new HashMap<>();
    private static final Map<Path, Long> PENDING = new HashMap<>();
    private static WatchService watcher;
    private static boolean tickHooked;
    private static long tick;
    private static volatile long generation;

    public static long generation() {
        return generation;
    }

    private ShaderHotReload() {}

    public static synchronized void watchMod(String modId) {
        if (!FabricLoader.getInstance().isDevelopmentEnvironment()) return;
        ModContainer mod = FabricLoader.getInstance().getModContainer(modId).orElse(null);
        if (mod == null) return;
        watchContainer(modId, mod);
        hookTick();
    }

    public static synchronized void watchAllDevMods() {
        if (!FabricLoader.getInstance().isDevelopmentEnvironment()) return;
        for (ModContainer mod : FabricLoader.getInstance().getAllMods()) {
            watchContainer(mod.getMetadata().getId(), mod);
        }
        hookTick();
    }

    private static void watchContainer(String modId, ModContainer mod) {
        try {
            for (Path root : mod.getRootPaths()) {
                if (!"file".equals(root.toUri().getScheme()) || !Files.isDirectory(root)) continue;
                Path src = sourceRootFor(root);
                if (src == null || ROOTS.containsKey(src)) continue;
                ROOTS.put(src, root);
                registerTreeWatch(src);
                LOG.info("watching {} shaders: {}", modId, src);
            }
        } catch (Exception e) {
            LOG.warn("shader hot reload unavailable for {}: {}", modId, e.toString());
        }
    }

    public static synchronized void onReload(Runnable callback) {
        RELOAD_CALLBACKS.add(callback);
    }

    private static Path sourceRootFor(Path buildResources) {
        Path p = buildResources.toAbsolutePath().normalize();
        if (!p.endsWith(Path.of("build", "resources", "main"))) return null;
        Path project = p.getParent().getParent().getParent();
        Path src = project.resolve(Path.of("src", "main", "resources"));
        return Files.isDirectory(src) ? src : null;
    }

    private static void registerTreeWatch(Path srcRoot) throws IOException {
        if (watcher == null) {
            watcher = FileSystems.getDefault().newWatchService();
            Thread t = new Thread(ShaderHotReload::watchLoop, "Amnetic-ShaderWatch");
            t.setDaemon(true);
            t.start();
        }
        registerDirs(srcRoot);
    }

    private static void registerDirs(Path root) throws IOException {
        try (var dirs = Files.walk(root)) {
            for (Path dir : (Iterable<Path>) dirs.filter(Files::isDirectory)::iterator) {
                dir.register(watcher, StandardWatchEventKinds.ENTRY_MODIFY, StandardWatchEventKinds.ENTRY_CREATE);
            }
        }
    }

    private static void watchLoop() {
        while (true) {
            try {
                WatchKey key = watcher.take();
                Path dir = (Path) key.watchable();
                for (WatchEvent<?> ev : key.pollEvents()) {
                    if (!(ev.context() instanceof Path rel)) continue;
                    Path file = dir.resolve(rel);
                    if (Files.isDirectory(file)) {
                        registerDirs(file);
                        continue;
                    }
                    String name = file.getFileName().toString();
                    int dot = name.lastIndexOf('.');
                    if (dot >= 0 && EXTENSIONS.contains(name.substring(dot + 1)) && Files.isRegularFile(file)) {
                        DIRTY.add(file);
                    }
                }
                if (!key.reset()) LOG.warn("watched directory gone: {}", dir);
            } catch (InterruptedException e) {
                return;
            } catch (Exception e) {
                LOG.warn("shader watch error: {}", e.toString());
            }
        }
    }

    private static void hookTick() {
        if (tickHooked) return;
        tickHooked = true;
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            tick++;
            Path p;
            while ((p = DIRTY.poll()) != null) PENDING.put(p, tick);
            if (PENDING.isEmpty()) return;

            int synced = 0;
            var it = PENDING.entrySet().iterator();
            while (it.hasNext()) {
                var e = it.next();
                if (e.getValue() >= tick) continue;
                it.remove();
                if (sync(e.getKey()) != null) synced++;
            }
            if (synced == 0) return;

            generation++;
            ShaderProgram.invalidateAll();
            for (Runnable cb : RELOAD_CALLBACKS) {
                try {
                    cb.run();
                } catch (Exception e) {
                    LOG.warn("shader reload callback failed: {}", e.toString());
                }
            }
            LOG.info("hot-reloaded {} shader file(s)", synced);
        });
    }

    private static Path sync(Path srcFile) {
        for (Map.Entry<Path, Path> e : ROOTS.entrySet()) {
            Path srcRoot = e.getKey();
            if (!srcFile.toAbsolutePath().normalize().startsWith(srcRoot.toAbsolutePath().normalize())) continue;
            try {
                Path rel = srcRoot.relativize(srcFile);
                Path dst = e.getValue().resolve(rel.toString());
                Files.createDirectories(dst.getParent());
                Files.copy(srcFile, dst, StandardCopyOption.REPLACE_EXISTING);
                return dst;
            } catch (IOException io) {
                LOG.warn("failed to sync {}: {}", srcFile, io.toString());
            }
        }
        return null;
    }
}

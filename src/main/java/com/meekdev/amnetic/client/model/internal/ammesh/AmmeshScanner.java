package com.meekdev.amnetic.client.model.internal.ammesh;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// background scan that keeps the .ammesh cache warm so Models.load stays a cheap binary read instead of a glTF parse
public final class AmmeshScanner {

    private static final Logger LOG = LoggerFactory.getLogger("Amnetic/Ammesh");

    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(daemonThreadFactory());

    private static final Set<Identifier> IN_FLIGHT = ConcurrentHashMap.newKeySet();

    private AmmeshScanner() {}

    private static ThreadFactory daemonThreadFactory() {
        AtomicInteger n = new AtomicInteger();
        return r -> {
            Thread t = new Thread(r, "Amnetic-Ammesh-Converter-" + n.incrementAndGet());
            t.setDaemon(true);
            return t;
        };
    }

    public static boolean isConverting(Identifier source) {
        return IN_FLIGHT.contains(source);
    }

    public static void scanAsync() {
        EXECUTOR.submit(AmmeshScanner::scan);
    }

    public static void convertAsync(Identifier source, Runnable onComplete) {
        EXECUTOR.submit(() -> {
            boolean owner = IN_FLIGHT.add(source);
            try {
                if (owner) convertOne(source);
            } finally {
                if (owner) IN_FLIGHT.remove(source);
                if (onComplete != null) {
                    try {
                        onComplete.run();
                    } catch (Throwable t) {
                        LOG.error("Amnetic: failed to load converted model {}", source, t);
                    }
                }
            }
        });
    }

    private static void scan() {
        try {
            Map<Identifier, Resource> models = Minecraft.getInstance().getResourceManager()
                    .listResources("models", id -> id.getPath().endsWith(".gltf") || id.getPath().endsWith(".glb"));
            for (Identifier id : models.keySet()) {
                try {
                    convertOne(id);
                } catch (Throwable e) {
                    LOG.error("Amnetic: failed to convert model {} to .ammesh", id, e);
                }
            }
        } catch (Throwable e) {
            LOG.error("Amnetic: model resource scan failed", e);
        }
    }

    private static void convertOne(Identifier source) {
        try {
            byte[] sourceBytes = AmmeshCache.readResourceBytes(source);
            if (AmmeshCache.INSTANCE.getCached(source, sourceBytes).isPresent()) return;
            AmmeshCache.INSTANCE.convertAndStore(source, sourceBytes);
        } catch (Throwable e) {
            LOG.error("Amnetic: failed to convert model {} to .ammesh", source, e);
        }
    }
}

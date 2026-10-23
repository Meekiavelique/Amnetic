package com.meekdev.amnetic.client.emissive;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class EmissiveSources {

    private static final Logger LOG = LoggerFactory.getLogger("Amnetic/Emissive");

    private static final Map<ResourceLocation, Consumer<EmissiveContext>> SOURCES = new ConcurrentHashMap<>();

    private EmissiveSources() {}

    public static void register(ResourceLocation id, Consumer<EmissiveContext> draw) {
        SOURCES.put(id, draw);
    }

    public static void unregister(ResourceLocation id) {
        SOURCES.remove(id);
    }

    public static boolean isEmpty() {
        return SOURCES.isEmpty();
    }

    public static void emitAll(EmissiveContext ctx) {
        if (SOURCES.isEmpty()) return;
        for (Map.Entry<ResourceLocation, Consumer<EmissiveContext>> e : SOURCES.entrySet()) {
            try {
                e.getValue().accept(ctx);
            } catch (Exception ex) {
                LOG.error("emissive source {} failed; skipping it this frame", e.getKey(), ex);
            }
        }
    }
}

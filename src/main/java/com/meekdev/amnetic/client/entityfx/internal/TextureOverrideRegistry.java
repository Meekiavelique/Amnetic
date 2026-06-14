package com.meekdev.amnetic.client.entityfx.internal;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;

public final class TextureOverrideRegistry {

    public static final TextureOverrideRegistry INSTANCE = new TextureOverrideRegistry();

    public record Entry(Identifier texture, boolean hideLayers) {}

    private final Map<Entity, Entry> overrides = new ConcurrentHashMap<>();

    private TextureOverrideRegistry() {}

    public void put(Entity entity, Identifier texture, boolean hideLayers) {
        overrides.put(entity, new Entry(texture, hideLayers));
    }

    public void remove(Entity entity) {
        overrides.remove(entity);
    }

    public Entry get(Entity entity) {
        return overrides.isEmpty() ? null : overrides.get(entity);
    }

    public boolean isEmpty() {
        return overrides.isEmpty();
    }

    public void tick() {
        if (overrides.isEmpty()) return;
        Iterator<Map.Entry<Entity, Entry>> it = overrides.entrySet().iterator();
        while (it.hasNext()) {
            if (it.next().getKey().isRemoved()) it.remove();
        }
    }

    public void clear() {
        overrides.clear();
    }
}

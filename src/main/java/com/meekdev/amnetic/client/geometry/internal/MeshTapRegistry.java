package com.meekdev.amnetic.client.geometry.internal;

import com.meekdev.amnetic.client.geometry.PosedMesh;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.world.entity.Entity;

public final class MeshTapRegistry {

    public static final MeshTapRegistry INSTANCE = new MeshTapRegistry();

    private final Map<Entity, PosedMesh> taps = new ConcurrentHashMap<>();

    private MeshTapRegistry() {}

    public void put(Entity entity, PosedMesh tap) {
        taps.put(entity, tap);
    }

    public void remove(Entity entity) {
        taps.remove(entity);
    }

    public PosedMesh get(Entity entity) {
        return taps.isEmpty() ? null : taps.get(entity);
    }

    public boolean isEmpty() {
        return taps.isEmpty();
    }

    public void tick() {
        if (taps.isEmpty()) return;
        Iterator<Map.Entry<Entity, PosedMesh>> it = taps.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Entity, PosedMesh> e = it.next();
            if (e.getKey().isRemoved() || e.getValue().isRemoved()) {
                it.remove();
            }
        }
    }

    public void clear() {
        taps.clear();
    }
}

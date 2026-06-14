package com.meekdev.amnetic.client.entityfx.internal;

import com.meekdev.amnetic.client.entityfx.EntityEffect;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.world.entity.Entity;


public final class EntityEffectRegistry {

    public static final EntityEffectRegistry INSTANCE = new EntityEffectRegistry();

    private final Map<Entity, EntityEffect> effects = new ConcurrentHashMap<>();

    private EntityEffectRegistry() {}

    public void put(Entity entity, EntityEffect effect) {
        effects.put(entity, effect);
    }

    public void remove(Entity entity) {
        effects.remove(entity);
    }

    public EntityEffect get(Entity entity) {
        return effects.isEmpty() ? null : effects.get(entity);
    }

    public boolean isEmpty() {
        return effects.isEmpty();
    }

    public void uploadAll() {
        if (effects.isEmpty()) return;
        for (EntityEffect effect : effects.values()) {
            if (!effect.isRemoved()) effect.uploadUniforms();
        }
    }

    public void tick() {
        if (effects.isEmpty()) return;
        Iterator<Map.Entry<Entity, EntityEffect>> it = effects.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Entity, EntityEffect> e = it.next();
            Entity entity = e.getKey();
            EntityEffect effect = e.getValue();
            if (entity.isRemoved() || effect.isRemoved()) {
                effect.disposeInternal();
                it.remove();
                continue;
            }
            if (effect.tickFade()) {
                effect.disposeInternal();
                it.remove();
            }
        }
    }

    public void clear() {
        for (EntityEffect effect : effects.values()) {
            effect.disposeInternal();
        }
        effects.clear();
    }
}

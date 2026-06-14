package com.meekdev.amnetic.client.entityfx;

import com.meekdev.amnetic.client.entityfx.internal.TextureOverrideRegistry;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;

public final class EntityTextureOverride {

    private EntityTextureOverride() {}

    public static void set(Entity entity, Identifier texture) {
        set(entity, texture, false);
    }

    public static void set(Entity entity, Identifier texture, boolean hideLayers) {
        TextureOverrideRegistry.INSTANCE.put(entity, texture, hideLayers);
    }

    public static void clear(Entity entity) {
        TextureOverrideRegistry.INSTANCE.remove(entity);
    }

    public static void clearAll() {
        TextureOverrideRegistry.INSTANCE.clear();
    }
}

package com.meekdev.amnetic.client.surface.text;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;

public final class Fonts {
    private static final Map<List<ResourceLocation>, SdfFont> CACHE = new HashMap<>();
    private static final Map<List<ResourceLocation>, Boolean> FAILED = new HashMap<>();
    private static final Map<ResourceLocation, byte[]> REGISTERED = new HashMap<>();

    private Fonts() {}

    public static void register(ResourceLocation fontId, byte[] ttf) {
        REGISTERED.put(fontId, ttf.clone());
        CACHE.keySet().removeIf(key -> key.contains(fontId));
        FAILED.keySet().removeIf(key -> key.contains(fontId));
    }

    static byte[] registered(ResourceLocation fontId) {
        return REGISTERED.get(fontId);
    }

    public static SdfFont get(ResourceLocation fontId) {
        return chain(fontId);
    }

    public static SdfFont chain(ResourceLocation primary, ResourceLocation... fallbacks) {
        List<ResourceLocation> key = key(primary, fallbacks);
        SdfFont font = CACHE.get(key);
        if (font != null) return font;
        if (FAILED.containsKey(key)) return null;
        font = SdfFont.load(primary, fallbacks);
        if (font == null) {
            FAILED.put(key, Boolean.TRUE);
            return null;
        }
        CACHE.put(key, font);
        return font;
    }

    private static List<ResourceLocation> key(ResourceLocation primary, ResourceLocation... fallbacks) {
        if (fallbacks.length == 0) return List.of(primary);
        ResourceLocation[] all = new ResourceLocation[fallbacks.length + 1];
        all[0] = primary;
        System.arraycopy(fallbacks, 0, all, 1, fallbacks.length);
        return List.of(all);
    }
}

package com.meekdev.amnetic.client.surface.text;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.Identifier;

public final class Fonts {

    private static final Map<List<Identifier>, SdfFont> CACHE = new HashMap<>();
    private static final Map<List<Identifier>, Boolean> FAILED = new HashMap<>();

    private Fonts() {}

    public static SdfFont get(Identifier fontId) {
        return chain(fontId);
    }

    public static SdfFont chain(Identifier primary, Identifier... fallbacks) {
        List<Identifier> key = key(primary, fallbacks);
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

    private static List<Identifier> key(Identifier primary, Identifier... fallbacks) {
        if (fallbacks.length == 0) return List.of(primary);
        Identifier[] all = new Identifier[fallbacks.length + 1];
        all[0] = primary;
        System.arraycopy(fallbacks, 0, all, 1, fallbacks.length);
        return List.of(all);
    }
}

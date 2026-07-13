package com.meekdev.amnetic.client.surface.text;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.Identifier;

// font cache keyed by resource id, e.g. Fonts.get(Identifier.fromNamespaceAndPath("mymod", "fonts/title.ttf")),
// or by a whole fallback chain via chain(primary, fallbacks...)
public final class Fonts {

    // key is the full chain so get(id) and chain(id, fb) coexist without clashing
    private static final Map<List<Identifier>, SdfFont> CACHE = new HashMap<>();
    private static final Map<List<Identifier>, Boolean> FAILED = new HashMap<>();

    private Fonts() {}

    // lazy bake on the render thread, null if the font failed to load (logged once)
    public static SdfFont get(Identifier fontId) {
        return chain(fontId);
    }

    // primary plus fallbacks tried in order per codepoint, all baked into one shared atlas
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

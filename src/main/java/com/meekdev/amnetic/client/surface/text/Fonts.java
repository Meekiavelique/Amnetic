package com.meekdev.amnetic.client.surface.text;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.resources.Identifier;

// font cache keyed by resource id, e.g. Fonts.get(Identifier.fromNamespaceAndPath("mymod", "fonts/title.ttf"))
public final class Fonts {

    private static final Map<Identifier, SdfFont> CACHE = new HashMap<>();
    private static final Map<Identifier, Boolean> FAILED = new HashMap<>();

    private Fonts() {}

    // lazy bake on the render thread, null if the font failed to load (logged once)
    public static SdfFont get(Identifier fontId) {
        SdfFont font = CACHE.get(fontId);
        if (font != null) return font;
        if (FAILED.containsKey(fontId)) return null;
        font = SdfFont.load(fontId);
        if (font == null) {
            FAILED.put(fontId, Boolean.TRUE);
            return null;
        }
        CACHE.put(fontId, font);
        return font;
    }
}

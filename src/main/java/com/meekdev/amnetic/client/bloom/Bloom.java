package com.meekdev.amnetic.client.bloom;

import com.meekdev.amnetic.client.bloom.internal.BloomRenderer;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import org.slf4j.LoggerFactory;

public final class Bloom {

    private static final BloomSettings SETTINGS = new BloomSettings();
    private static final BloomRenderer RENDERER = new BloomRenderer();

    private Bloom() {}

    public static BloomSettings settings() {
        return SETTINGS;
    }

    public static void enable() {
        SETTINGS.enabled(true).all(true);
    }

    public static void disable() {
        SETTINGS.enabled(false);
    }

    public static void render(LevelRenderContext ctx) {
        try {
            RENDERER.render(ctx, SETTINGS);
        } catch (Exception e) {
            // never let a bloom failure take down world rendering
            LoggerFactory.getLogger("Amnetic/Bloom").error("bloom render failed", e);
            SETTINGS.enabled(false);
        }
    }
}

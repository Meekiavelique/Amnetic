package com.meekdev.amnetic.client.bloom;

import com.meekdev.amnetic.client.bloom.internal.BloomRenderer;
import com.meekdev.amnetic.client.render.LevelCamera;
import org.slf4j.LoggerFactory;

public final class Bloom {

    private static final BloomSettings SETTINGS = new BloomSettings();
    private static final BloomRenderer RENDERER = new BloomRenderer();

    private Bloom() {}

    public static BloomSettings settings() {
        return SETTINGS;
    }

    public static void enable() {
        SETTINGS.enabled(true);
    }

    public static void disable() {
        SETTINGS.enabled(false);
    }

    public static void render(LevelCamera camera) {
        try {
            RENDERER.render(camera, SETTINGS);
        } catch (Exception e) {
            // never let a bloom failure take down world rendering
            LoggerFactory.getLogger("Amnetic/Bloom").error("bloom render failed", e);
            SETTINGS.enabled(false);
        }
    }
}

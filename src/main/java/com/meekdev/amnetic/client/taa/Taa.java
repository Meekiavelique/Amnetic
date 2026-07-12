package com.meekdev.amnetic.client.taa;

import com.meekdev.amnetic.client.taa.internal.CasPass;
import com.meekdev.amnetic.client.taa.internal.TaaPass;
import net.fabricmc.loader.api.FabricLoader;

public final class Taa {

    private static final TaaSettings SETTINGS = new TaaSettings();

    // under Iris every Amnetic screen pass skips itself, the projection jitter has to skip
    // too or the world shimmers with no resolve pass to integrate it
    private static final boolean IRIS = FabricLoader.getInstance().isModLoaded("iris");

    private Taa() {}

    public static TaaSettings settings() {
        return SETTINGS;
    }

    public static boolean jitterActive() {
        return SETTINGS.isEnabled() && !IRIS;
    }

    public static void enable() {
        SETTINGS.enabled(true);
    }

    public static void disable() {
        SETTINGS.enabled(false);
    }

    public static void render() {
        TaaPass.INSTANCE.render(SETTINGS);
    }

    public static void renderSharpen() {
        CasPass.INSTANCE.render(SETTINGS);
    }

    public static void dispose() {
        TaaPass.INSTANCE.dispose();
        CasPass.INSTANCE.dispose();
    }
}

package com.meekdev.amnetic.client.taa;

import com.meekdev.amnetic.client.taa.internal.CasPass;
import com.meekdev.amnetic.client.taa.internal.TaaPass;
import com.meekdev.amnetic.client.compat.IrisCompat;

public final class Taa {

    private static final TaaSettings SETTINGS = new TaaSettings();

    private Taa() {}

    public static TaaSettings settings() {
        return SETTINGS;
    }

    public static boolean jitterActive() {
        return SETTINGS.isEnabled() && !IrisCompat.shadersActive();
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

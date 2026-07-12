package com.meekdev.amnetic.client.ssr;

import com.meekdev.amnetic.client.ssr.internal.SsrPass;

public final class Ssr {

    private static final SsrSettings SETTINGS = new SsrSettings();

    private Ssr() {}

    public static SsrSettings settings() {
        return SETTINGS;
    }

    public static void enable() {
        SETTINGS.enabled(true);
    }

    public static void disable() {
        SETTINGS.enabled(false);
    }

    public static void render() {
        SsrPass.INSTANCE.render(SETTINGS);
    }

    public static void dispose() {
        SsrPass.INSTANCE.dispose();
    }
}

package com.meekdev.amnetic.client.ssgi;

import com.meekdev.amnetic.client.ssgi.internal.SsgiPass;

public final class Ssgi {

    private static final SsgiSettings SETTINGS = new SsgiSettings();

    private Ssgi() {}

    public static SsgiSettings settings() { return SETTINGS; }
    public static boolean isEnabled() { return SETTINGS.isEnabled(); }
    public static void enable() { SETTINGS.enabled(true); }
    public static void disable() { SETTINGS.enabled(false); }

    public static void render() { SsgiPass.INSTANCE.render(SETTINGS); }
    public static void dispose() { SsgiPass.INSTANCE.dispose(); }
}

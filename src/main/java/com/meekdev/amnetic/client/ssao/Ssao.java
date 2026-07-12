package com.meekdev.amnetic.client.ssao;

import com.meekdev.amnetic.client.ssao.internal.SsaoPass;

public final class Ssao {

    private static final SsaoSettings SETTINGS = new SsaoSettings();

    private Ssao() {}

    public static SsaoSettings settings() { return SETTINGS; }
    public static boolean isEnabled() { return SETTINGS.isEnabled(); }
    public static void enable() { SETTINGS.enabled(true); }
    public static void disable() { SETTINGS.enabled(false); }

    public static void render() { SsaoPass.INSTANCE.render(SETTINGS); }
    public static void dispose() { SsaoPass.INSTANCE.dispose(); }
}

package com.meekdev.amnetic.client.shadow;

import com.meekdev.amnetic.client.shadow.internal.ShadowMapPass;

public final class Shadows {

    private static boolean enabled;

    private Shadows() {}

    public static boolean isEnabled() { return enabled; }
    public static void setEnabled(boolean v) { enabled = v; }
    public static void enable() { enabled = true; }
    public static void disable() { enabled = false; }

    public static void dispose() {
        ShadowMapPass.INSTANCE.dispose();
    }
}

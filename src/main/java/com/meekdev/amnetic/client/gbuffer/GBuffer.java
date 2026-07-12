package com.meekdev.amnetic.client.gbuffer;

import com.meekdev.amnetic.client.gbuffer.internal.GBufferTargets;

public final class GBuffer {

    private static boolean enabled = true;

    private GBuffer() {}

    public static boolean isEnabled() { return enabled; }
    public static void setEnabled(boolean v) { enabled = v; }
    public static void enable() { enabled = true; }
    public static void disable() { enabled = false; }

    public static void beginFrame() {
        if (enabled) GBufferTargets.INSTANCE.beginFrame();
    }

    public static boolean isPopulated() { return GBufferTargets.INSTANCE.isPopulated(); }

    public static int normalTextureGlId() { return GBufferTargets.INSTANCE.normalGlId(); }

    public static int materialTextureGlId() { return GBufferTargets.INSTANCE.materialGlId(); }

    public static int emissiveTextureGlId() { return GBufferTargets.INSTANCE.emissiveGlId(); }

    public static int depthTextureGlId() { return GBufferTargets.INSTANCE.depthGlId(); }

    public static void dispose() {
        GBufferTargets.INSTANCE.dispose();
    }
}

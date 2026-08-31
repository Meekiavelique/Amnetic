package com.meekdev.amnetic.client.emissive;

import com.meekdev.amnetic.client.emissive.internal.BlockEmissiveSource;
import net.minecraft.resources.Identifier;

public final class BlockEmissive {

    private static final Identifier ID =
            Identifier.fromNamespaceAndPath("amnetic", "block_emissive");

    private static boolean enabled;

    private BlockEmissive() {}

    public static void enable() {
        if (enabled) return;
        enabled = true;
        EmissiveSources.register(ID, BlockEmissiveSource.INSTANCE::draw);
    }

    public static void disable() {
        if (!enabled) return;
        enabled = false;
        EmissiveSources.unregister(ID);
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static void radius(float blocks) {
        BlockEmissiveSource.INSTANCE.radius(blocks);
    }

    public static float radius() {
        return BlockEmissiveSource.INSTANCE.radius();
    }

    public static void intensity(float v) {
        BlockEmissiveSource.INSTANCE.intensity(v);
    }

    public static float intensity() {
        return BlockEmissiveSource.INSTANCE.intensity();
    }

    public static void invalidate() {
        BlockEmissiveSource.INSTANCE.invalidate();
    }
}

package com.meekdev.amnetic.client.gbuffer;

import com.meekdev.amnetic.client.gbuffer.internal.GBufferTargets;
import java.util.function.Predicate;
import net.minecraft.world.entity.Entity;

public final class GBuffer {
    private static boolean enabled = true;
    private static Predicate<Entity> drawn = entity -> false;

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

    public static int albedoTextureGlId() { return GBufferTargets.INSTANCE.albedoGlId(); }

    public static int velocityTextureGlId() { return GBufferTargets.INSTANCE.velocityGlId(); }

    public static int shadeTextureGlId() { return GBufferTargets.INSTANCE.shadeGlId(); }

    public static long frame() { return GBufferTargets.INSTANCE.frame(); }

    public static void drawnEntities(Predicate<Entity> which) { drawn = which == null ? entity -> false : which; }

    public static boolean drawsOwnMotion(Entity entity) { return drawn.test(entity); }

    public static int depthTextureGlId() { return GBufferTargets.INSTANCE.depthGlId(); }

    public static void dispose() {
        GBufferTargets.INSTANCE.dispose();
    }
}

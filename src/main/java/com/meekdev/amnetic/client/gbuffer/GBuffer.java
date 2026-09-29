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

    /** The material colour a forward pass published, so deferred lights shade it instead of the lit frame. */
    public static int albedoTextureGlId() { return GBufferTargets.INSTANCE.albedoGlId(); }

    /** world-space motion since the last frame, xyz in blocks, zero where only the camera moved */
    public static int velocityTextureGlId() { return GBufferTargets.INSTANCE.velocityGlId(); }

    /** r: the share of a pixel's colour that came from sky light, g: the share that came from the sun, b: 1 where a shader wrote it */
    public static int shadeTextureGlId() { return GBufferTargets.INSTANCE.shadeGlId(); }

    /** counts up once per rendered frame */
    public static long frame() { return GBufferTargets.INSTANCE.frame(); }

    /** entities a mod draws itself and writes the motion of; the rest get their box stamped */
    public static void drawnEntities(Predicate<Entity> which) { drawn = which == null ? entity -> false : which; }

    public static boolean drawsOwnMotion(Entity entity) { return drawn.test(entity); }

    public static int depthTextureGlId() { return GBufferTargets.INSTANCE.depthGlId(); }

    public static void dispose() {
        GBufferTargets.INSTANCE.dispose();
    }
}

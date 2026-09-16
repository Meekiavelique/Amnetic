package com.meekdev.amnetic.client.compat;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.irisshaders.iris.api.v0.IrisApi;
import net.irisshaders.iris.api.v0.IrisProgram;
import net.irisshaders.iris.api.v0.IrisShadowProgram;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Optional Iris integration. Iris is compile-only and is never required at runtime. */
public final class IrisCompat {

    private static final Logger LOGGER = LoggerFactory.getLogger("Amnetic/IrisCompat");
    private static final boolean AVAILABLE = detectIris();

    private IrisCompat() {}

    public static boolean isAvailable() {
        return AVAILABLE;
    }

    public static boolean isShaderPackInUse() {
        if (!AVAILABLE) return false;
        try {
            return IrisApi.getInstance().isShaderPackInUse();
        } catch (LinkageError | RuntimeException e) {
            return false;
        }
    }

    public static boolean isRenderingShadowPass() {
        if (!AVAILABLE) return false;
        try {
            return IrisApi.getInstance().isRenderingShadowPass();
        } catch (LinkageError | RuntimeException e) {
            return false;
        }
    }

    public static void registerWorldPipeline(RenderPipeline pipeline, boolean translucent) {
        if (!AVAILABLE) return;
        try {
            IrisApi api = IrisApi.getInstance();
            api.assignPipeline(pipeline, translucent ? IrisProgram.ENTITIES_TRANSLUCENT : IrisProgram.ENTITIES);
            api.assignPipelineShadow(pipeline,
                    translucent ? IrisShadowProgram.SHADOW_TRANSLUCENT : IrisShadowProgram.SHADOW_ENTITIES);
        } catch (LinkageError | RuntimeException e) {
            LOGGER.warn("Could not register {} with Iris; using Amnetic's shader unchanged", pipeline.getLocation(), e);
        }
    }

    private static boolean detectIris() {
        try {
            Class.forName("net.irisshaders.iris.api.v0.IrisApi", false, IrisCompat.class.getClassLoader());
            LOGGER.info("Iris detected; shader-pack compatibility is active");
            return true;
        } catch (ClassNotFoundException | LinkageError ignored) {
            return false;
        }
    }
}

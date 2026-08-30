package com.meekdev.amnetic.client.dev;

import com.meekdev.amnetic.client.post.internal.PostEffectRegistry;
import com.meekdev.amnetic.mixin.accessor.ShaderLoaderAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderManager;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.InactiveProfiler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class PostShaderReload {

    private static final Logger LOG = LoggerFactory.getLogger("Amnetic/PostShaderReload");
    private static boolean installed;

    private PostShaderReload() {}

    public static synchronized void install() {
        if (installed) return;
        installed = true;
        ShaderHotReload.onReload(PostShaderReload::recompile);
    }

    private static void recompile() {
        Minecraft mc = Minecraft.getInstance();
        ShaderManager sm = mc.getShaderManager();
        ResourceManager rm = mc.getResourceManager();
        ShaderLoaderAccessor acc = (ShaderLoaderAccessor) sm;
        try {
            ShaderManager.Configs configs = acc.amnetic$prepare(rm, InactiveProfiler.INSTANCE);
            acc.amnetic$apply(configs, rm, InactiveProfiler.INSTANCE);
            PostEffectRegistry.INSTANCE.invalidatePipelineCaches();
        } catch (Exception e) {
            LOG.warn("post shader recompile failed: {}", e.toString());
        }
    }
}

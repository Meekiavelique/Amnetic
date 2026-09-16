package com.meekdev.amnetic.client.dev;

import com.meekdev.amnetic.client.post.internal.PostEffectRegistry;

public final class PostShaderReload {

    private static boolean installed;

    private PostShaderReload() {}

    public static synchronized void install() {
        if (installed) return;
        installed = true;
        ShaderHotReload.onReload(PostEffectRegistry.INSTANCE::invalidatePipelineCaches);
    }
}

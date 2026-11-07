package com.meekdev.amnetic.client.light;

import com.meekdev.amnetic.client.light.internal.DeferredLightingPass;
import org.joml.Matrix4fc;

public final class ViewLighting {
    private ViewLighting() {}

    public static boolean needed() {
        return DeferredLightingPass.INSTANCE.viewNeeded();
    }

    public static void shade(Matrix4fc invViewProj, int blockLight, int skyLight) {
        DeferredLightingPass.INSTANCE.renderView(invViewProj, blockLight, skyLight);
    }
}

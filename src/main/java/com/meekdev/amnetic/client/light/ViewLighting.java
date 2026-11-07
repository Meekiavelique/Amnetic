package com.meekdev.amnetic.client.light;

import com.meekdev.amnetic.client.light.internal.DeferredLightingPass;
import org.joml.Matrix4fc;

// lights a first person view model with the world's lights after it was drawn at full light
public final class ViewLighting {

    private ViewLighting() {}

    // false when no light or scene ambient would change it, draw the view model with its own light then
    public static boolean needed() {
        return DeferredLightingPass.INSTANCE.viewNeeded();
    }

    // invViewProj takes the view model's clip space to the world around the camera,
    // block and sky are the lightmap levels it would have been drawn with
    public static void shade(Matrix4fc invViewProj, int blockLight, int skyLight) {
        DeferredLightingPass.INSTANCE.renderView(invViewProj, blockLight, skyLight);
    }
}

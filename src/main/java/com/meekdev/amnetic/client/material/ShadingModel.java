package com.meekdev.amnetic.client.material;

import com.meekdev.amnetic.client.material.internal.ShadingModelRegistry;
import com.meekdev.amnetic.client.model.ModelMaterial;

/**
 * a shading model assignable to a {@link ModelMaterial}. the built-in
 * {@link #DEFAULT_PBR} uses the normal Cook-Torrance path in the deferred lighting shader. custom models
 * register a GLSL snippet via {@link #custom(String)} which gets baked into the deferred uber-shader as
 * a dispatch case keyed on a unique material ID assigned at registration time
 */
public final class ShadingModel {

    public static final ShadingModel DEFAULT_PBR = new ShadingModel(0);

    private final int id;

    private ShadingModel(int id) {
        this.id = id;
    }

    /**
     * registers a custom shading snippet: the body of a function receiving a {@code GBufferSample s}
     * (fields: albedo, normal, fragPos, roughness, metallic, radiance) that must {@code return} a vec3
     * color. registering a new snippet triggers a one-time relink of the deferred lighting shader on
     * the next frame it runs
     */
    public static ShadingModel custom(String glslSnippetBody) {
        int id = ShadingModelRegistry.INSTANCE.register(glslSnippetBody);
        return new ShadingModel(id);
    }

    public int id() {
        return id;
    }
}

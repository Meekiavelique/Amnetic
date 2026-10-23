package com.meekdev.amnetic.client.material;

import com.meekdev.amnetic.client.material.internal.ShadingModelRegistry;
import com.meekdev.amnetic.client.model.ModelMaterial;
import com.meekdev.amnetic.client.render.ShaderProgram;
import java.util.function.Supplier;
import net.minecraft.resources.ResourceLocation;

/**
 * a shading model assignable to a {@link ModelMaterial}, built from a lighting base and optional
 * GLSL snippets. {@link #pbr()} is the Cook-Torrance path in the deferred lighting shader and
 * {@link #flat()} shades like a vanilla block; {@link #fragment(ResourceLocation)} replaces the shading
 * and {@link #vertex(ResourceLocation)} displaces the geometry. each registration is baked into the
 * deferred uber-shader as a dispatch case keyed on a material ID assigned at registration time
 */
public final class ShadingModel {

    private enum Base { PBR, FLAT }

    private static final ShadingModel PBR =
            new ShadingModel(0, Base.PBR, false);
    private static final ShadingModel FLAT =
            new ShadingModel(ShadingModelRegistry.FLAT_ID, Base.FLAT, false);

    private final int id;
    private final Base base;

    private final boolean owned;
    private final SurfaceLayout layout;

    private ShadingModel(int id, Base base, boolean owned) {
        this(id, base, owned, SurfaceLayout.EMPTY);
    }

    private ShadingModel(int id, Base base, boolean owned, SurfaceLayout layout) {
        this.id = id;
        this.base = base;
        this.owned = owned;
        this.layout = layout;
    }

    public static ShadingModel pbr() {
        return PBR;
    }

    public static ShadingModel flat() {
        return FLAT;
    }

    /**
     * replaces the shading with a GLSL snippet: the body of a function receiving a
     * {@code GBufferSample s} (fields: albedo, normal, fragPos, roughness, metallic, radiance,
     * lightmap) that must {@code return} a vec3 colour. on a {@link #flat()} base {@code s.albedo}
     * holds the flat result. registering triggers a one-time relink of the deferred lighting shader
     * on the next frame it runs
     */
    public ShadingModel fragment(ResourceLocation snippet) {
        return register(() -> ShaderProgram.readSource(snippet));
    }

    public ShadingModel fragment(String glslBody) {
        return register(() -> glslBody);
    }

    /**
     * displaces the geometry with a vertex-stage snippet: the body of a function receiving a
     * {@code VertexSample v} (fields: localPos, worldPos, origin, uv, time) that must
     * {@code return} a world-space offset added to the vertex. the normal and tangent are rebuilt
     * from the displaced surface so lighting follows the deformation, which costs three evaluations
     * of the snippet per vertex
     */
    public ShadingModel vertex(ResourceLocation vertexSnippet) {
        ShadingModel target = owned ? this : register(this::passthrough);
        ShadingModelRegistry.INSTANCE.attachVertex(
                target.id, () -> ShaderProgram.readSource(vertexSnippet));
        return target;
    }

    /**
     * shades the surface as the model is drawn, before lighting, with a GLSL snippet: the body of a
     * function given {@code inout SurfaceSample s} (uv, albedo, alpha, emission, roughness, metallic,
     * normal, worldPos, time) that changes what it needs. the snippet may declare its own
     * {@code uniform}s, which each draw fills through {@link SurfaceInputs}, see {@link #layout()}
     */
    public ShadingModel surface(String source) {
        SurfaceLayout parsed = SurfaceLayout.parse(source);
        ShadingModel target = owned ? this : register(this::passthrough);
        ShadingModelRegistry.INSTANCE.attachSurface(target.id, parsed.body());
        return new ShadingModel(target.id, base, true, parsed);
    }

    public SurfaceLayout layout() {
        return layout;
    }

    public ShadingModel vertex(String vertexBody) {
        ShadingModel target = owned ? this : register(this::passthrough);
        ShadingModelRegistry.INSTANCE.attachVertex(target.id, () -> vertexBody);
        return target;
    }

    private ShadingModel register(Supplier<String> body) {
        int newId = base == Base.FLAT
                ? ShadingModelRegistry.INSTANCE.registerFlatShaded(body)
                : ShadingModelRegistry.INSTANCE.register(body);
        return new ShadingModel(newId, base, true);
    }

    private String passthrough() {
        return base == Base.FLAT ? "return s.albedo;" : "return s.radiance;";
    }

    /** gives this model's id back once no material uses it any more; the built-in pbr and flat ones stay */
    public void release() {
        if (owned) ShadingModelRegistry.INSTANCE.release(id);
    }

    public int id() {
        return id;
    }
}

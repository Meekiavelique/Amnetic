package com.meekdev.amnetic.client.material;

import com.meekdev.amnetic.client.material.internal.ShadingModelRegistry;
import com.meekdev.amnetic.client.model.ModelMaterial;
import com.meekdev.amnetic.client.render.ShaderProgram;
import java.util.function.Supplier;
import net.minecraft.resources.Identifier;

/**
 * a shading model assignable to a {@link ModelMaterial}, built from a lighting base and optional
 * GLSL snippets. {@link #pbr()} is the Cook-Torrance path in the deferred lighting shader and
 * {@link #flat()} shades like a vanilla block; {@link #fragment(Identifier)} replaces the shading
 * and {@link #vertex(Identifier)} displaces the geometry. each registration is baked into the
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

    private ShadingModel(int id, Base base, boolean owned) {
        this.id = id;
        this.base = base;
        this.owned = owned;
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
    public ShadingModel fragment(Identifier snippet) {
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
    public ShadingModel vertex(Identifier vertexSnippet) {
        ShadingModel target = owned ? this : register(this::passthrough);
        ShadingModelRegistry.INSTANCE.attachVertex(
                target.id, () -> ShaderProgram.readSource(vertexSnippet));
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

    public int id() {
        return id;
    }
}

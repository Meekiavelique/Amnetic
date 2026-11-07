package com.meekdev.amnetic.client.material;

import com.meekdev.amnetic.client.material.internal.ShadingModelRegistry;
import com.meekdev.amnetic.client.model.ModelMaterial;
import com.meekdev.amnetic.client.render.ShaderProgram;
import java.util.function.Supplier;
import net.minecraft.resources.Identifier;

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

    public ShadingModel fragment(Identifier snippet) {
        return register(() -> ShaderProgram.readSource(snippet));
    }

    public ShadingModel fragment(String glslBody) {
        return register(() -> glslBody);
    }

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

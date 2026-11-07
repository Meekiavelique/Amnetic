package com.meekdev.amnetic.client.material;

import com.meekdev.amnetic.client.material.internal.ShadingModelRegistry;
import com.meekdev.amnetic.client.model.ModelMaterial;
import com.meekdev.amnetic.client.render.ShaderProgram;
import java.util.function.Supplier;
import net.minecraft.resources.ResourceLocation;

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

    public ShadingModel fragment(ResourceLocation snippet) {
        return register(() -> ShaderProgram.readSource(snippet));
    }

    public ShadingModel fragment(String glslBody) {
        return register(() -> glslBody);
    }

    public ShadingModel vertex(ResourceLocation vertexSnippet) {
        ShadingModel target = owned ? this : register(this::passthrough);
        ShadingModelRegistry.INSTANCE.attachVertex(
                target.id, () -> ShaderProgram.readSource(vertexSnippet));
        return target;
    }

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

    public void release() {
        if (owned) ShadingModelRegistry.INSTANCE.release(id);
    }

    public int id() {
        return id;
    }
}

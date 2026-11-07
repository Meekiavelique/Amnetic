package com.meekdev.amnetic.client.instanced;

import com.meekdev.amnetic.client.instanced.internal.InstanceMeshRegistry;
import com.meekdev.amnetic.client.model.TextureFilter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;
import net.minecraft.resources.ResourceLocation;

public final class InstancedMesh<T> {
    public record CustomUniform(String name, Supplier<float[]> value) {}

    final MeshData geometry;
    final InstanceLayout vertexLayout;
    final InstanceLayout layout;
    final InstanceWriter<T> writer;
    final BuiltinShader<?> builtinShader;
    final ResourceLocation customShaderId;
    final ResourceLocation vertexShaderId;
    final ResourceLocation fragmentShaderId;
    final ResourceLocation textureId;
    final TextureFilter textureFilter;
    final List<ExtraSampler> extraSamplers;
    final InstancePhase phase;
    final RenderState renderState;
    final boolean emissive;
    final float emissiveStrength;
    final boolean writeGBuffer;
    final boolean castsShadow;
    final List<CustomUniform> customUniforms;
    final boolean shadowDepthOnly;
    final boolean perInstanceShadow;
    final boolean staticInstances;
    final boolean gpuCull;
    final boolean worldSpace;
    final boolean manual;
    final boolean flatShaded;
    final BiConsumer<InstanceRenderContext, InstanceBatch<T>> onRender;

    private InstancedMesh(Builder<T> b) {
        this.geometry = Objects.requireNonNull(b.geometry, "geometry must be set");
        this.vertexLayout = b.vertexLayout;
        this.layout = b.layout;
        this.writer = b.writer;
        this.builtinShader = b.builtinShader;
        this.customShaderId = b.customShaderId;
        this.vertexShaderId = b.vertexShaderId;
        this.fragmentShaderId = b.fragmentShaderId;
        this.textureId = b.textureId;
        this.textureFilter = b.textureFilter;
        this.extraSamplers = List.copyOf(b.extraSamplers);
        this.phase = b.phase;
        this.renderState = b.renderState;
        this.flatShaded = b.flatShaded;
        this.emissive = b.emissive;
        this.emissiveStrength = b.emissiveStrength;
        this.writeGBuffer = b.writeGBuffer;
        this.castsShadow = b.castsShadow;
        this.customUniforms = List.copyOf(b.customUniforms);
        this.shadowDepthOnly = b.shadowDepthOnly;
        this.perInstanceShadow = b.perInstanceShadow;
        this.staticInstances = b.staticInstances;
        this.gpuCull = b.gpuCull;
        this.worldSpace = b.worldSpace;
        this.manual = b.manual;
        this.onRender = Objects.requireNonNull(b.onRender, "onRender must be set");
    }

    public static void invalidate(ResourceLocation id) {
        InstanceMeshRegistry.INSTANCE.invalidate(id);
    }

    public static <T> Builder<T> builder(BuiltinShader<T> shader) {
        Builder<T> b = new Builder<>(shader.layout(), shader.writer());
        b.builtinShader = shader;
        return b;
    }

    public static <T> Builder<T> builder(InstanceLayout layout, InstanceWriter<T> writer) {
        return new Builder<>(layout, writer);
    }

    public MeshData geometry() { return geometry; }
    public InstanceLayout vertexLayout() { return vertexLayout; }
    public InstanceLayout layout() { return layout; }
    public InstanceWriter<T> writer() { return writer; }
    public InstancePhase phase() { return phase; }
    public RenderState renderState() { return renderState; }
    public boolean isEmissive() { return emissive; }
    public float emissiveStrength() { return emissiveStrength; }
    public boolean writeGBuffer() { return writeGBuffer; }
    public boolean castsShadow() { return castsShadow; }
    public List<CustomUniform> customUniforms() { return customUniforms; }
    public boolean shadowDepthOnly() { return shadowDepthOnly; }
    public boolean perInstanceShadow() { return perInstanceShadow; }
    public boolean staticInstances() { return staticInstances; }
    public boolean gpuCull() { return gpuCull; }
    public boolean worldSpace() { return worldSpace; }
    public boolean manual() { return manual; }
    public boolean flatShaded() { return flatShaded; }

    public BiConsumer<InstanceRenderContext, InstanceBatch<T>> onRender() { return onRender; }

    public boolean isBuiltin() { return builtinShader != null; }
    public BuiltinShader<?> builtinShader() { return builtinShader; }
    public ResourceLocation customShaderId() { return customShaderId; }
    public ResourceLocation vertexShaderId() { return vertexShaderId; }
    public ResourceLocation fragmentShaderId() { return fragmentShaderId; }
    public ResourceLocation textureId() { return textureId; }
    public TextureFilter textureFilter() { return textureFilter; }
    public List<ExtraSampler> extraSamplers() { return extraSamplers; }

    public static final class Builder<T> {
        private final InstanceLayout layout;
        private final InstanceWriter<T> writer;
        private BuiltinShader<?> builtinShader;
        private ResourceLocation customShaderId;
        private ResourceLocation vertexShaderId;
        private ResourceLocation fragmentShaderId;
        private ResourceLocation textureId;
        private TextureFilter textureFilter = TextureFilter.NEAREST;
        private final List<ExtraSampler> extraSamplers = new ArrayList<>();
        private MeshData geometry;
        private InstanceLayout vertexLayout;
        private InstancePhase phase = InstancePhase.WORLD_LAST;
        private RenderState renderState = RenderState.DEFAULT;
        private boolean emissive = false;
        private float emissiveStrength = 1.0f;
        private boolean writeGBuffer = false;
        private boolean castsShadow = false;
        private final List<CustomUniform> customUniforms = new ArrayList<>();
        private boolean shadowDepthOnly = false;
        private boolean perInstanceShadow = false;
        private boolean staticInstances = false;
        private boolean gpuCull = false;
        private boolean worldSpace = false;
        private boolean manual = false;
        private boolean flatShaded = false;
        private BiConsumer<InstanceRenderContext, InstanceBatch<T>> onRender;

        private Builder(InstanceLayout layout, InstanceWriter<T> writer) {
            this.layout = layout;
            this.writer = writer;
        }

        public Builder<T> vertexLayout(InstanceLayout layout) {
            this.vertexLayout = layout;
            return this;
        }

        public Builder<T> geometry(MeshData geometry) {
            this.geometry = geometry;
            return this;
        }

        public Builder<T> shader(ResourceLocation id) {
            this.customShaderId = id;
            this.builtinShader = null;
            return this;
        }

        public Builder<T> shaders(ResourceLocation vertexShaderId, ResourceLocation fragmentShaderId) {
            this.vertexShaderId = vertexShaderId;
            this.fragmentShaderId = fragmentShaderId;
            this.builtinShader = null;
            return this;
        }

        public Builder<T> textureFilter(TextureFilter filter) {
            this.textureFilter = filter;
            return this;
        }

        public Builder<T> extraSampler(String uniformName, ResourceLocation textureId, int unit) {
            this.extraSamplers.add(new ExtraSampler(uniformName, textureId, unit));
            return this;
        }

        public Builder<T> extraSampler(String uniformName, ResourceLocation textureId, int unit, boolean fileBacked) {
            this.extraSamplers.add(new ExtraSampler(uniformName, textureId, unit, fileBacked));
            return this;
        }

        public Builder<T> extraSampler(String uniformName, IntSupplier glTexture, int unit) {
            this.extraSamplers.add(new ExtraSampler(uniformName, glTexture, unit));
            return this;
        }

        public Builder<T> extraCubeSampler(String uniformName, IntSupplier glTexture, int unit) {
            this.extraSamplers.add(new ExtraSampler(uniformName, glTexture, unit, true));
            return this;
        }

        public Builder<T> texture(ResourceLocation id) {
            this.textureId = id;
            return this;
        }

        public Builder<T> phase(InstancePhase phase) {
            this.phase = phase;
            return this;
        }

        public Builder<T> renderState(RenderState state) {
            this.renderState = state;
            return this;
        }

        public Builder<T> writeGBuffer(boolean v) {
            this.writeGBuffer = v;
            return this;
        }

        public Builder<T> castsShadow() {
            this.castsShadow = true;
            return this;
        }

        public Builder<T> uniform(String name, Supplier<float[]> value) {
            this.customUniforms.add(new CustomUniform(name, value));
            return this;
        }

        public Builder<T> shadowDepthOnly() {
            this.castsShadow = true;
            this.shadowDepthOnly = true;
            return this;
        }

        public Builder<T> castsShadowPerInstance() {
            this.perInstanceShadow = true;
            return shadowDepthOnly();
        }

        public Builder<T> emissive() {
            return emissive(1.0f);
        }

        public Builder<T> emissive(float strength) {
            this.emissive = true;
            this.emissiveStrength = strength;
            return this;
        }

        public Builder<T> onRender(BiConsumer<InstanceRenderContext, InstanceBatch<T>> callback) {
            this.onRender = callback;
            return this;
        }

        public Builder<T> staticInstances() {
            this.staticInstances = true;
            return this;
        }

        public Builder<T> gpuCull() {
            this.gpuCull = true;
            return this;
        }

        public Builder<T> worldSpace() {
            this.worldSpace = true;
            return this;
        }

        public Builder<T> manual() {
            this.manual = true;
            return this;
        }

        public Builder<T> flatShaded() {
            this.flatShaded = true;
            return this;
        }

        public InstancedMesh<T> build() {
            boolean hasExplicit = vertexShaderId != null && fragmentShaderId != null;
            if (builtinShader == null && customShaderId == null && !hasExplicit) {
                throw new IllegalStateException("Specify a shader");
            }
            if (flatShaded && (builtinShader == null || !builtinShader.hasFlatVariant())) {
                throw new IllegalStateException("flatShaded() needs a builtin shader that has one, "
                        + "and " + (builtinShader == null ? "a custom shader" : builtinShader.shaderId())
                        + " does not");
            }
            return new InstancedMesh<>(this);
        }

        public void register(ResourceLocation id) {
            InstanceMeshRegistry.INSTANCE.register(id, build());
        }
    }
}

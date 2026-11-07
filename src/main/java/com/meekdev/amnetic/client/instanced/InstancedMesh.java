package com.meekdev.amnetic.client.instanced;

import com.meekdev.amnetic.client.instanced.internal.InstanceMeshRegistry;
import com.meekdev.amnetic.client.model.TextureFilter;
import net.minecraft.resources.Identifier;
import org.lwjgl.opengl.GL12;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;
//? if >=26.2 {
import com.mojang.renderpearl.api.textures.GpuTextureView;
//?}

public final class InstancedMesh<T> {

    public record CustomUniform(String name, Supplier<float[]> value) {}

    final MeshData geometry;
    final InstanceLayout vertexLayout;
    final InstanceLayout layout;
    final InstanceWriter<T> writer;
    final BuiltinShader<?> builtinShader;
    final Identifier customShaderId;
    final Identifier vertexShaderId;
    final Identifier fragmentShaderId;
    final Identifier textureId;
    final TextureFilter textureFilter;
    final List<ExtraSampler> extraSamplers;
    final InstancePhase phase;
    final RenderState renderState;
    final boolean emissive;
    final float emissiveStrength;
    final boolean glowsInGBuffer;
    final boolean writeGBuffer;
    final boolean castsShadow;
    final List<CustomUniform> customUniforms;
    final boolean shadowDepthOnly;
    final boolean perInstanceShadow;
    final boolean staticInstances;
    final boolean depthPrepass;
    final boolean gpuCull;
    final boolean worldSpace;
    final boolean manual;
    final boolean flatShaded;
    final BiConsumer<InstanceRenderContext, InstanceBatch<T>> onRender;
    final InstanceSource source;

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
        this.glowsInGBuffer = b.glowsInGBuffer;
        this.writeGBuffer = b.writeGBuffer;
        this.castsShadow = b.castsShadow;
        this.customUniforms = List.copyOf(b.customUniforms);
        this.shadowDepthOnly = b.shadowDepthOnly;
        this.perInstanceShadow = b.perInstanceShadow;
        this.staticInstances = b.staticInstances;
        this.depthPrepass = b.depthPrepass;
        this.gpuCull = b.gpuCull;
        this.worldSpace = b.worldSpace;
        this.manual = b.manual;
        this.onRender = Objects.requireNonNull(b.onRender, "onRender must be set");
        this.source = b.source;
    }

    /**
     * forces a static-instance mesh to re-run its onRender emitter next frame (e.g. to refresh baked
     * per-instance lighting when the time of day changes). no-op for dynamic meshes
     */
    public static void invalidate(Identifier id) {
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

    public static Builder<Void> builder(InstanceLayout layout, InstanceSource source) {
        Builder<Void> b = new Builder<>(layout, (instance, packer) -> {});
        b.source = source;
        b.onRender = (ctx, batch) -> {};
        return b;
    }

    public MeshData geometry() { return geometry; }
    public InstanceLayout vertexLayout() { return vertexLayout; }
    public InstanceLayout layout() { return layout; }
    public InstanceWriter<T> writer() { return writer; }
    public InstancePhase phase() { return phase; }
    public RenderState renderState() { return renderState; }
    public boolean isEmissive() { return emissive; }
    public float emissiveStrength() { return emissiveStrength; }
    public boolean glowsInGBuffer() { return glowsInGBuffer; }
    public boolean writeGBuffer() { return writeGBuffer; }
    public boolean castsShadow() { return castsShadow; }
    public List<CustomUniform> customUniforms() { return customUniforms; }
    public boolean shadowDepthOnly() { return shadowDepthOnly; }
    public boolean perInstanceShadow() { return perInstanceShadow; }
    public boolean staticInstances() { return staticInstances; }
    public boolean depthPrepass() { return depthPrepass && staticInstances && writeGBuffer; }
    public boolean gpuCull() { return gpuCull; }
    public boolean worldSpace() { return worldSpace; }
    public boolean manual() { return manual; }
    public boolean flatShaded() { return flatShaded; }

    public BiConsumer<InstanceRenderContext, InstanceBatch<T>> onRender() { return onRender; }
    public InstanceSource source() { return source; }

    public boolean isBuiltin() { return builtinShader != null; }
    public BuiltinShader<?> builtinShader() { return builtinShader; }
    public Identifier customShaderId() { return customShaderId; }
    public Identifier vertexShaderId() { return vertexShaderId; }
    public Identifier fragmentShaderId() { return fragmentShaderId; }
    public Identifier textureId() { return textureId; }
    public TextureFilter textureFilter() { return textureFilter; }
    public List<ExtraSampler> extraSamplers() { return extraSamplers; }

    public static final class Builder<T> {
        private final InstanceLayout layout;
        private final InstanceWriter<T> writer;
        private BuiltinShader<?> builtinShader;
        private Identifier customShaderId;
        private Identifier vertexShaderId;
        private Identifier fragmentShaderId;
        private Identifier textureId;
        private TextureFilter textureFilter = TextureFilter.NEAREST;
        private final List<ExtraSampler> extraSamplers = new ArrayList<>();
        private MeshData geometry;
        private InstanceLayout vertexLayout;
        private InstancePhase phase = InstancePhase.WORLD_LAST;
        private RenderState renderState = RenderState.DEFAULT;
        private boolean emissive = false;
        private float emissiveStrength = 1.0f;
        private boolean glowsInGBuffer;
        private boolean writeGBuffer = false;
        private boolean castsShadow = false;
        private final List<CustomUniform> customUniforms = new ArrayList<>();
        private boolean shadowDepthOnly = false;
        private boolean perInstanceShadow = false;
        private boolean staticInstances = false;
        private boolean depthPrepass = false;
        private boolean gpuCull = false;
        private boolean worldSpace = false;
        private boolean manual = false;
        private boolean flatShaded = false;
        private BiConsumer<InstanceRenderContext, InstanceBatch<T>> onRender;
        private InstanceSource source;

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

        public Builder<T> shader(Identifier id) {
            this.customShaderId = id;
            this.builtinShader = null;
            return this;
        }

        public Builder<T> shaders(Identifier vertexShaderId, Identifier fragmentShaderId) {
            this.vertexShaderId = vertexShaderId;
            this.fragmentShaderId = fragmentShaderId;
            this.builtinShader = null;
            return this;
        }

        /**
         * how the mesh texture is filtered. nearest by default, which is what a game drawn from
         * texels wants; ask for linear only for something authored smooth
         */
        public Builder<T> textureFilter(TextureFilter filter) {
            this.textureFilter = filter;
            return this;
        }

        public Builder<T> extraSampler(String uniformName, Identifier textureId, int unit) {
            this.extraSamplers.add(new ExtraSampler(uniformName, textureId, unit));
            return this;
        }
        //? if >=26.2 {
        public Builder<T> extraSampler(String uniformName,
                Supplier<GpuTextureView> texture, int unit) {
            this.extraSamplers.add(new ExtraSampler(uniformName, texture, unit));
            return this;
        }
        //?}

        public Builder<T> extraSampler(String uniformName, Identifier textureId, int unit, boolean fileBacked) {
            this.extraSamplers.add(new ExtraSampler(uniformName, textureId, unit, fileBacked));
            return this;
        }

        // a texture the caller resolves itself, for one the texture manager does not hold
        public Builder<T> extraSampler(String uniformName, IntSupplier glTexture, int unit) {
            this.extraSamplers.add(new ExtraSampler(uniformName, glTexture, unit));
            return this;
        }

        // a cube map the caller resolves itself, such as the sky probe from Ibl.cube()
        public Builder<T> extraCubeSampler(String uniformName, IntSupplier glTexture, int unit) {
            this.extraSamplers.add(new ExtraSampler(uniformName, glTexture, unit, true));
            return this;
        }

        public Builder<T> extraVolumeSampler(String uniformName, IntSupplier glTexture, int unit) {
            this.extraSamplers.add(new ExtraSampler(uniformName, glTexture, unit, GL12.GL_TEXTURE_3D));
            return this;
        }

        public Builder<T> texture(Identifier id) {
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

        /** Uploads one to four floats under this name before every draw of this mesh. */
        public Builder<T> uniform(String name, Supplier<float[]> value) {
            this.customUniforms.add(new CustomUniform(name, value));
            return this;
        }

        /**
         * Bakes the shadow with a depth-only program instead of this mesh's own shader, which skips
         * its textures and lighting. The instance transform has to be a mat4 at locations 1 to 4,
         * and a shader that carves its silhouette with discard must not use this.
         */
        public Builder<T> shadowDepthOnly() {
            this.castsShadow = true;
            this.shadowDepthOnly = true;
            return this;
        }

        /**
         * Lets each instance decide whether it casts, through a float at location 7: zero does not
         * cast. Implies {@link #shadowDepthOnly()}.
         */
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

        public Builder<T> glowsInGBuffer() {
            this.glowsInGBuffer = true;
            return this;
        }

        public Builder<T> onRender(BiConsumer<InstanceRenderContext, InstanceBatch<T>> callback) {
            this.onRender = callback;
            return this;
        }

        /**
         * emit instances once and reuse the uploaded buffer every frame (no per-frame CPU re-emit/re-upload),
         * for large static fields like grass. call {@link InstancedMesh#invalidate(Identifier)} to re-emit
         */
        public Builder<T> staticInstances() {
            this.staticInstances = true;
            return this;
        }

        // depth goes in first so the shader runs once per pixel, it gets DepthPass 1 then and returns after its discards
        public Builder<T> depthPrepass() {
            this.depthPrepass = true;
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

        /**
         * shades every face by the axis it points down, the way a vanilla block is shaded and the
         * way {@link com.meekdev.amnetic.client.material.ShadingModel#flat()} shades a model.
         * instanced geometry carries no normals, so the face normal comes from the derivative of
         * the fragment position and there is no lightmap term
         */
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

        public void register(Identifier id) {
            InstanceMeshRegistry.INSTANCE.register(id, build());
        }
    }
}

package com.meekdev.amnetic.client.instanced;

import com.meekdev.amnetic.client.instanced.internal.InstanceMeshRegistry;
import net.minecraft.util.Identifier;

import java.util.Objects;
import java.util.function.BiConsumer;

public final class InstancedMesh<T> {

    final MeshData geometry;
    final InstanceLayout layout;
    final InstanceWriter<T> writer;
    final BuiltinShader<?> builtinShader;
    final Identifier customShaderId;
    final InstancePhase phase;
    final RenderState renderState;
    final BiConsumer<InstanceRenderContext, InstanceBatch<T>> onRender;

    private InstancedMesh(Builder<T> b) {
        this.geometry = Objects.requireNonNull(b.geometry, "geometry must be set");
        this.layout = b.layout;
        this.writer = b.writer;
        this.builtinShader = b.builtinShader;
        this.customShaderId = b.customShaderId;
        this.phase = b.phase;
        this.renderState = b.renderState;
        this.onRender = Objects.requireNonNull(b.onRender, "onRender must be set");
    }

    public static <T> Builder<T> builder(BuiltinShader<T> shader) {
        Builder<T> b = new Builder<>(shader.layout(), shader.writer());
        b.builtinShader = shader;
        return b;
    }

    public static <T> Builder<T> builder(InstanceLayout layout, InstanceWriter<T> writer) {
        return new Builder<>(layout, writer);
    }

    public MeshData geometry()           { return geometry; }
    public InstanceLayout layout()       { return layout; }
    public InstanceWriter<T> writer()    { return writer; }
    public InstancePhase phase()         { return phase; }
    public RenderState renderState()     { return renderState; }

    public BiConsumer<InstanceRenderContext, InstanceBatch<T>> onRender() { return onRender; }

    public boolean isBuiltin()              { return builtinShader != null; }
    public BuiltinShader<?> builtinShader() { return builtinShader; }
    public Identifier customShaderId()      { return customShaderId; }

    public static final class Builder<T> {
        private final InstanceLayout layout;
        private final InstanceWriter<T> writer;
        private BuiltinShader<?> builtinShader;
        private Identifier customShaderId;
        private MeshData geometry;
        private InstancePhase phase = InstancePhase.WORLD_LAST;
        private RenderState renderState = RenderState.DEFAULT;
        private BiConsumer<InstanceRenderContext, InstanceBatch<T>> onRender;

        private Builder(InstanceLayout layout, InstanceWriter<T> writer) {
            this.layout = layout;
            this.writer = writer;
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

        public Builder<T> phase(InstancePhase phase) {
            this.phase = phase;
            return this;
        }

        public Builder<T> renderState(RenderState state) {
            this.renderState = state;
            return this;
        }

        public Builder<T> onRender(BiConsumer<InstanceRenderContext, InstanceBatch<T>> callback) {
            this.onRender = callback;
            return this;
        }

        public InstancedMesh<T> build() {
            if (builtinShader == null && customShaderId == null) {
                throw new IllegalStateException("Specify a shader");
            }
            return new InstancedMesh<>(this);
        }

        public void register(Identifier id) {
            InstanceMeshRegistry.INSTANCE.register(id, build());
        }
    }
}

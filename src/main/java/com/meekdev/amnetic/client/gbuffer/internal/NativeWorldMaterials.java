package com.meekdev.amnetic.client.gbuffer.internal;

import com.meekdev.amnetic.client.render.ShaderPatch;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.vertex.VertexFormat;
import com.mojang.renderpearl.api.pipeline.*;
import net.minecraft.resources.Identifier;
import java.util.*;

public final class NativeWorldMaterials {
    private static final int TARGET_COUNT = 5;
    private static final int MATERIAL_TARGET = 2;
    private static final int ALBEDO_TARGET = 4;
    private static final int WRITE_ALL = 15;
    private static final GpuFormat[] GBUFFER_FORMATS = {
            GpuFormat.RGBA16_FLOAT, GpuFormat.RGBA8_UNORM, GpuFormat.RGBA16_FLOAT, GpuFormat.RGBA8_UNORM
    };
    private static final Identifier SODIUM_BLOCK_VERTEX = patch("sodium_block_vertex");
    private static final Identifier SODIUM_BLOCK_FRAGMENT = patch("sodium_block_fragment");
    private static final Identifier TERRAIN_VERTEX = patch("terrain_vertex");
    private static final Identifier TERRAIN_FRAGMENT = patch("terrain_fragment");
    private static final Identifier ENTITY_FRAGMENT = patch("entity_fragment");
    private static final Identifier OTHER_FRAGMENT = patch("other_fragment");

    public static boolean world;
    private static final Set<CompiledRenderPipeline> SODIUM = Collections.newSetFromMap(new IdentityHashMap<>());
    private static boolean sodiumBound;
    private static final Map<CompiledRenderPipeline, CompiledRenderPipeline> VARIANTS =
            Collections.synchronizedMap(new IdentityHashMap<>());

    private NativeWorldMaterials() {}

    private static Identifier patch(String name) {
        return Identifier.fromNamespaceAndPath("amnetic", "shaders/gbuffer/native/world/" + name + ".glsl");
    }

    public static boolean sodiumReady() {
        return sodiumBound;
    }

    public static boolean eligible(RenderPipeline p) {
        String namespace = p.getLocation().getNamespace();
        return !namespace.equals("amnetic") && p.getColorTargetStates().size() == 1
                && (namespace.equals("minecraft") || namespace.equals("sodium"));
    }

    public static void put(CompiledRenderPipeline original, CompiledRenderPipeline variant, boolean sodium) {
        VARIANTS.put(original, variant);
        if (sodium) SODIUM.add(original);
    }

    public static CompiledRenderPipeline variant(CompiledRenderPipeline original) {
        if (SODIUM.contains(original)) sodiumBound = true;
        return VARIANTS.getOrDefault(original, original);
    }

    public static void release(CompiledRenderPipeline original) {
        SODIUM.remove(original);
        if (SODIUM.isEmpty()) sodiumBound = false;
        var variant = VARIANTS.remove(original);
        if (variant != null) variant.close();
    }

    public static boolean captures(RenderPipeline p) {
        return p.getDepthStencilState() != null && p.getDepthStencilState().writeDepth()
                && p.getColorTargetStates().getFirst().blendFunction().isEmpty();
    }

    public static RenderPipeline definition(RenderPipeline p) {
        var colors = new ColorTargetState[ColorTargetState.MAX_COLOR_TARGETS];
        colors[0] = p.getColorTargetStates().getFirst();
        for (int i = 1; i < TARGET_COUNT; i++) {
            boolean written = captures(p) && (i == MATERIAL_TARGET || i == ALBEDO_TARGET);
            colors[i] = new ColorTargetState(Optional.empty(), GBUFFER_FORMATS[i - 1], written ? WRITE_ALL : 0);
        }
        var snippet = new RenderPipeline.Snippet(p.getShaders(), Optional.of(p.getShaderDefines()),
                Optional.of(p.getBindGroupLayouts()), colors, TARGET_COUNT, Optional.ofNullable(p.getDepthStencilState()),
                Optional.of(p.getPolygonMode()), Optional.of(p.isCull()),
                p.getVertexFormatBindings().toArray(VertexFormat[]::new), Optional.of(p.getPrimitiveTopology()),
                p.pushConstantSize());
        var location = Identifier.fromNamespaceAndPath("amnetic",
                "world_material/" + p.getLocation().getNamespace() + "/" + p.getLocation().getPath());
        return RenderPipeline.builder(snippet).withShaderDefine("AMNETIC_NATIVE_MATERIAL").withLocation(location).build();
    }

    public static ShaderSource source(ShaderSource original, boolean capture) {
        return new ShaderSource() {
            public String getShader(Identifier id, ShaderType type) {
                String source = original.getShader(id, type);
                if (!capture || source == null) return source;
                Identifier patch = patchFor(id, type);
                return patch == null ? source : ShaderPatch.apply(source, patch, Set.of());
            }

            public CachedIncludeSource getInclude(Identifier id) {
                return original.getInclude(id);
            }

            public void close() {}
        };
    }

    private static Identifier patchFor(Identifier id, ShaderType type) {
        boolean vertex = type == ShaderType.VERTEX;
        boolean fragment = type == ShaderType.FRAGMENT;
        if (id.getNamespace().equals("sodium") && id.getPath().equals("blocks/block_layer_opaque")) {
            if (vertex) return SODIUM_BLOCK_VERTEX;
            return fragment ? SODIUM_BLOCK_FRAGMENT : null;
        }
        boolean terrain = id.getPath().equals("core/terrain");
        if (vertex) return terrain ? TERRAIN_VERTEX : null;
        if (!fragment) return null;
        if (terrain) return TERRAIN_FRAGMENT;
        return id.getPath().equals("core/entity") ? ENTITY_FRAGMENT : OTHER_FRAGMENT;
    }
}

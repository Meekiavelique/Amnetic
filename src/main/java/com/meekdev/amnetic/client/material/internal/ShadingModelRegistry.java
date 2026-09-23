package com.meekdev.amnetic.client.material.internal;

import com.meekdev.amnetic.client.material.SurfaceInputs;
import com.meekdev.amnetic.client.render.ShaderProgram;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.resources.Identifier;

public final class ShadingModelRegistry {
    public static final Identifier LADDER_INCLUDE =
            Identifier.fromNamespaceAndPath("amnetic", "shaders/material/custom_ladder.glsl");

    public static final Identifier VERTEX_LADDER_INCLUDE =
            Identifier.fromNamespaceAndPath("amnetic", "shaders/material/custom_vertex_ladder.glsl");

    public static final Identifier SURFACE_LADDER_INCLUDE =
            Identifier.fromNamespaceAndPath("amnetic", "shaders/material/custom_surface_ladder.glsl");

    public static final ShadingModelRegistry INSTANCE = new ShadingModelRegistry();

    public static final int FLAT_ID = 255;

    public static final int FLAT_SHADED_BASE = 128;

    private final Map<Integer, Supplier<String>> snippets = new LinkedHashMap<>();
    private final Map<Integer, Supplier<String>> vertexSnippets = new LinkedHashMap<>();
    private final Map<Integer, Supplier<String>> surfaceSnippets = new LinkedHashMap<>();
    private boolean surfaceDirty;
    private final ArrayDeque<Integer> freeIds = new ArrayDeque<>();
    private final ArrayDeque<Integer> freeFlatIds = new ArrayDeque<>();
    private int nextId = 1;
    private int nextFlatId = FLAT_SHADED_BASE;
    private boolean dirty;
    private boolean vertexDirty;

    private ShadingModelRegistry() {
        ShaderProgram.registerVirtualSource(LADDER_INCLUDE, this::generateLadder);
        ShaderProgram.registerVirtualSource(VERTEX_LADDER_INCLUDE, this::generateVertexLadder);
        ShaderProgram.registerVirtualSource(SURFACE_LADDER_INCLUDE, this::generateSurfaceLadder);
        snippets.put(FLAT_ID, () -> "return s.albedo;");
    }

    public static void touch() {
    }

    public synchronized int register(Supplier<String> snippetGlsl) {
        Integer reused = freeIds.poll();
        if (reused != null) {
            snippets.put(reused, snippetGlsl);
            dirty = true;
            return reused;
        }
        if (nextId >= FLAT_SHADED_BASE) {
            throw new IllegalStateException(
                    "out of shading model ids (max " + (FLAT_SHADED_BASE - 1) + ")");
        }
        int id = nextId++;
        snippets.put(id, snippetGlsl);
        dirty = true;
        return id;
    }

    public synchronized int registerFlatShaded(Supplier<String> snippetGlsl) {
        Integer reused = freeFlatIds.poll();
        if (reused != null) {
            snippets.put(reused, snippetGlsl);
            dirty = true;
            return reused;
        }
        if (nextFlatId >= FLAT_ID) {
            throw new IllegalStateException("out of flat-shaded shading model ids (max "
                    + (FLAT_ID - 1) + ")");
        }
        int id = nextFlatId++;
        snippets.put(id, snippetGlsl);
        dirty = true;
        return id;
    }

    public synchronized void attachVertex(int materialId, Supplier<String> snippetGlsl) {
        vertexSnippets.put(materialId, snippetGlsl);
        vertexDirty = true;
    }

    public synchronized void release(int materialId) {
        if (materialId <= 0 || materialId == FLAT_ID || !snippets.containsKey(materialId)) return;
        snippets.remove(materialId);
        if (vertexSnippets.remove(materialId) != null) vertexDirty = true;
        if (surfaceSnippets.remove(materialId) != null) surfaceDirty = true;
        dirty = true;
        if (materialId >= FLAT_SHADED_BASE) freeFlatIds.add(materialId);
        else freeIds.add(materialId);
    }

    public synchronized void attachSurface(int materialId, String body) {
        surfaceSnippets.put(materialId, () -> body);
        surfaceDirty = true;
    }

    public synchronized boolean consumeSurfaceDirty() {
        if (!surfaceDirty) return false;
        surfaceDirty = false;
        return true;
    }

    public synchronized boolean consumeDirty() {
        if (!dirty) return false;
        dirty = false;
        return true;
    }

    public synchronized boolean consumeVertexDirty() {
        if (!vertexDirty) return false;
        vertexDirty = false;
        return true;
    }

    public synchronized boolean hasCustomModels() {
        return snippets.size() > 1;
    }

    public synchronized boolean hasVertexSnippets() {
        return !vertexSnippets.isEmpty();
    }

    private synchronized String generateLadder() {
        StringBuilder sb = new StringBuilder();
        sb.append("struct GBufferSample {\n")
          .append("    vec3 albedo;\n")
          .append("    vec3 normal;\n")
          .append("    vec3 fragPos;\n")
          .append("    float roughness;\n")
          .append("    float metallic;\n")
          .append("    vec3 radiance;\n")
          .append("    vec3 lightmap;\n")
          .append("    float time;\n")
          .append("};\n\n")
          .append("vec3 shadeCustomMaterial(int materialId, GBufferSample s, out bool handled) {\n")
          .append("    handled = true;\n");
        appendBranches(sb, snippets);
        sb.append("    handled = false;\n")
          .append("    return vec3(0.0);\n")
          .append("}\n");
        return sb.toString();
    }

    private static void appendBranches(StringBuilder sb, Map<Integer, Supplier<String>> source) {
        Map<String, List<Integer>> byBody = new LinkedHashMap<>();
        for (Map.Entry<Integer, Supplier<String>> e : source.entrySet()) {
            byBody.computeIfAbsent(e.getValue().get(), ignored -> new ArrayList<>()).add(e.getKey());
        }
        for (Map.Entry<String, List<Integer>> e : byBody.entrySet()) {
            sb.append("    if (");
            List<Integer> ids = e.getValue();
            for (int i = 0; i < ids.size(); i++) {
                if (i > 0) sb.append(" || ");
                sb.append("materialId == ").append(ids.get(i));
            }
            sb.append(") {\n        ").append(e.getKey()).append("\n    }\n");
        }
    }

    private synchronized String generateSurfaceLadder() {
        StringBuilder sb = new StringBuilder();
        sb.append("struct SurfaceSample {\n")
          .append("    vec2 uv;\n")
          .append("    vec3 albedo;\n")
          .append("    float alpha;\n")
          .append("    vec3 emission;\n")
          .append("    float roughness;\n")
          .append("    float metallic;\n")
          .append("    vec3 normal;\n")
          .append("    vec3 worldPos;\n")
          .append("    float time;\n")
          .append("};\n\n")
          .append("uniform vec4 SurfaceValues[").append(SurfaceInputs.VEC4_SLOTS).append("];\n");
        for (int slot = 0; slot < SurfaceInputs.TEXTURE_SLOTS; slot++) {
            sb.append("uniform sampler2D SurfaceTexture").append(slot).append(";\n");
        }
        sb.append("\nvoid surfaceCustomMaterial(int materialId, inout SurfaceSample s) {\n");
        for (Map.Entry<Integer, Supplier<String>> e : surfaceSnippets.entrySet()) {
            sb.append("    if (materialId == ").append(e.getKey()).append(") {\n")
              .append(e.getValue().get()).append("\n        return;\n    }\n");
        }
        sb.append("}\n");
        return sb.toString();
    }

    private synchronized String generateVertexLadder() {
        StringBuilder sb = new StringBuilder();
        sb.append("struct VertexSample {\n")
          .append("    vec3 localPos;\n")
          .append("    vec3 worldPos;\n")
          .append("    vec3 origin;\n")
          .append("    vec2 uv;\n")
          .append("    float time;\n")
          .append("};\n\n")
          .append("vec3 displaceCustomMaterial(int materialId, VertexSample v, out bool handled) {\n")
          .append("    handled = true;\n");
        appendBranches(sb, vertexSnippets);
        sb.append("    handled = false;\n")
          .append("    return vec3(0.0);\n")
          .append("}\n");
        return sb.toString();
    }
}

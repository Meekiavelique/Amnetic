package com.meekdev.amnetic.client.material.internal;

import com.meekdev.amnetic.client.render.ShaderProgram;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.resources.Identifier;

/**
 * assigns small integer material IDs to custom GLSL shading snippets and generates the
 * "if (materialId == N) { ... }" ladder spliced into the deferred lighting uber-shader via a virtual
 * include ({@link #LADDER_INCLUDE}). snippets are the body of a function that returns a vec3 given a
 * GBufferSample (see the generated struct), e.g.:
 *
 * <pre>{@code
 * return floor(dot(s.normal, vec3(0.4, 0.8, 0.2)) * 3.0 + 3.5) / 3.0 * s.albedo;
 * }</pre>
 *
 * registering marks the registry dirty, the deferred pass checks {@link #consumeDirty()} once per
 * frame and only relinks when something actually changed
 */
public final class ShadingModelRegistry {

    public static final Identifier LADDER_INCLUDE =
            Identifier.fromNamespaceAndPath("amnetic", "shaders/material/custom_ladder.glsl");

    public static final Identifier VERTEX_LADDER_INCLUDE =
            Identifier.fromNamespaceAndPath("amnetic", "shaders/material/custom_vertex_ladder.glsl");

    public static final ShadingModelRegistry INSTANCE = new ShadingModelRegistry();

    public static final int FLAT_ID = 255;

    public static final int FLAT_SHADED_BASE = 128;

    // materialId 0 is reserved for the built-in default PBR path (no custom snippet)
    private final Map<Integer, Supplier<String>> snippets = new LinkedHashMap<>();
    private final Map<Integer, Supplier<String>> vertexSnippets = new LinkedHashMap<>();
    private int nextId = 1;
    private int nextFlatId = FLAT_SHADED_BASE;
    private boolean dirty;
    private boolean vertexDirty;

    private ShadingModelRegistry() {
        ShaderProgram.registerVirtualSource(LADDER_INCLUDE, this::generateLadder);
        ShaderProgram.registerVirtualSource(VERTEX_LADDER_INCLUDE, this::generateVertexLadder);
        // the model shader has already shaded these fragments the vanilla way, so the deferred
        // pass hands the colour straight back rather than lighting it a second time
        snippets.put(FLAT_ID, () -> "return s.albedo;");
    }

    public static void touch() {
    }

    public synchronized int register(Supplier<String> snippetGlsl) {
        // the gbuffer stores materialId in an 8-bit channel, ids past 255 would alias, and
        // everything from FLAT_SHADED_BASE up is reserved for the vanilla-shaded band
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

    // true (and clears the flag) exactly once per newly-registered snippet set
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
          .append("    vec3 radiance;\n") // standard PBR result already computed, for tinting/replacing
          .append("    vec3 lightmap;\n") // vanilla lightmap colour sampled at this fragment
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

    private synchronized String generateVertexLadder() {
        StringBuilder sb = new StringBuilder();
        sb.append("struct VertexSample {\n")
          .append("    vec3 localPos;\n")  // model space, after skinning
          .append("    vec3 worldPos;\n")  // world space, camera relative when WorldSpace is set
          .append("    vec3 origin;\n")    // the instance's own translation
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

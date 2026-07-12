package com.meekdev.amnetic.client.material.internal;

import com.meekdev.amnetic.client.render.ShaderProgram;
import java.util.LinkedHashMap;
import java.util.Map;
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

    public static final ShadingModelRegistry INSTANCE = new ShadingModelRegistry();

    // materialId 0 is reserved for the built-in default PBR path (no custom snippet)
    private final Map<Integer, String> snippets = new LinkedHashMap<>();
    private int nextId = 1;
    private boolean dirty;

    private ShadingModelRegistry() {
        ShaderProgram.registerVirtualSource(LADDER_INCLUDE, this::generateLadder);
    }

    public synchronized int register(String snippetGlsl) {
        // the gbuffer stores materialId in an 8-bit channel, ids past 255 would alias
        if (nextId > 255) throw new IllegalStateException("out of shading model ids (max 255)");
        int id = nextId++;
        snippets.put(id, snippetGlsl);
        dirty = true;
        return id;
    }

    // true (and clears the flag) exactly once per newly-registered snippet set
    public synchronized boolean consumeDirty() {
        if (!dirty) return false;
        dirty = false;
        return true;
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
          .append("};\n\n")
          .append("vec3 shadeCustomMaterial(int materialId, GBufferSample s, out bool handled) {\n")
          .append("    handled = true;\n");
        for (Map.Entry<Integer, String> e : snippets.entrySet()) {
            sb.append("    if (materialId == ").append(e.getKey()).append(") {\n")
              .append("        ").append(e.getValue()).append('\n')
              .append("    }\n");
        }
        sb.append("    handled = false;\n")
          .append("    return vec3(0.0);\n")
          .append("}\n");
        return sb.toString();
    }
}

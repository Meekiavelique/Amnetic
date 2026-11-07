package com.meekdev.amnetic.client.surface.material;

import com.meekdev.amnetic.client.render.GlState;
import com.meekdev.amnetic.client.render.ShaderProgram;
import com.meekdev.amnetic.client.surface.reactive.Effect;
import com.meekdev.amnetic.client.surface.reactive.Signal;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class SurfaceMaterial {

    private static final Logger LOG = LoggerFactory.getLogger("Amnetic/Surface");
    private static final Identifier HEAD = shader("material_head.glsl");
    private static final Identifier MAIN = shader("material_main.glsl");
    private static final Identifier MISSING = shader("material_missing.fsh");

    public enum Blend { MIX, ADD, PREMUL }

    public record Uniform(String name, String glslType, String hint, float[] hintRange, float[] defaultValue) {}

    private final Identifier sourceId;
    private final ShaderProgram program;
    private final Map<String, float[]> values = new LinkedHashMap<>();
    private final Map<String, Integer> textures = new LinkedHashMap<>();
    private final List<Uniform> uniforms = new ArrayList<>();
    private final List<Effect> bindings = new ArrayList<>();
    private Blend blend = Blend.MIX;
    private boolean parsed;
    private boolean broken;

    private SurfaceMaterial(Identifier sourceId) {
        this.sourceId = sourceId;
        Identifier virtual = Identifier.fromNamespaceAndPath("amnetic",
                "shaders/surface/generated/" + sourceId.getNamespace() + "/" + sourceId.getPath() + ".fsh");
        ShaderProgram.registerVirtualSource(virtual, this::generate);
        this.program = new ShaderProgram(
                Identifier.fromNamespaceAndPath("amnetic", "shaders/surface/ui.vsh"), virtual);
    }

    private static Identifier shader(String file) {
        return Identifier.fromNamespaceAndPath("amnetic", "shaders/surface/" + file);
    }

    public static SurfaceMaterial load(Identifier sourceId) {
        return new SurfaceMaterial(sourceId);
    }

    public Blend blend() { return blend; }
    public boolean broken() { return broken; }
    public List<Uniform> uniforms() {
        parseIfNeeded();
        return uniforms;
    }

    public SurfaceMaterial set(String name, float... v) {
        values.put(name, v.clone());
        return this;
    }

    public SurfaceMaterial setColor(String name, int argb) {
        return set(name,
                ((argb >> 16) & 0xFF) / 255f, ((argb >> 8) & 0xFF) / 255f,
                (argb & 0xFF) / 255f, ((argb >>> 24) & 0xFF) / 255f);
    }

    public SurfaceMaterial bind(String name, Signal<Float> signal) {
        bindings.add(new Effect(() -> set(name, signal.get())));
        return this;
    }

    public SurfaceMaterial setTexture(String name, int glTextureId) {
        textures.put(name, glTextureId);
        return this;
    }

    public void dispose() {
        for (Effect e : bindings) e.dispose();
        bindings.clear();
        program.close();
    }

    public boolean beginDraw(org.joml.Matrix4f ortho, float screenW, float screenH,
                             float time, float hover, float pressed, float focus) {
        parseIfNeeded();
        if (broken) return false;
        try {
            program.begin();
        } catch (Exception e) {
            broken = true;
            LOG.warn("surface material {} failed to compile: {}", sourceId, e.getMessage());
            return false;
        }
        program.setMatrix4("Ortho", ortho);
        program.setSampler("Tex", 0);
        program.setFloat("Time", time);
        program.setFloat("Hover", hover);
        program.setFloat("Pressed", pressed);
        program.setFloat("Focus", focus);
        program.setVec2("ScreenSize", screenW, screenH);
        for (Map.Entry<String, float[]> e : values.entrySet()) {
            float[] v = e.getValue();
            switch (v.length) {
                case 1 -> program.setFloat(e.getKey(), v[0]);
                case 2 -> program.setVec2(e.getKey(), v[0], v[1]);
                case 3 -> program.setVec3(e.getKey(), v[0], v[1], v[2]);
                case 4 -> program.setVec4(e.getKey(), v[0], v[1], v[2], v[3]);
                default -> {}
            }
        }
        int unit = 1;
        for (Uniform u : uniforms) {
            if (!"sampler2D".equals(u.glslType())) continue;
            program.setSampler(u.name(), unit);
            Integer id = textures.get(u.name());
            GlState.bindTexture(unit, id == null ? 0 : id);
            unit++;
        }
        if (unit > 1) GlState.bindTexture(0, 0);
        return true;
    }

    private void parseIfNeeded() {
        if (parsed) return;
        parsed = true;
        try {
            parse(readSource());
        } catch (Exception e) {
            broken = true;
            LOG.warn("surface material {} failed to parse: {}", sourceId, e.getMessage());
        }
    }

    private String readSource() throws Exception {
        try (InputStream in = Minecraft.getInstance().getResourceManager().getResource(sourceId)
                .orElseThrow(() -> new RuntimeException("material source not found: " + sourceId)).open()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private void parse(String src) {
        uniforms.clear();
        boolean typed = false;
        for (String rawLine : src.split("\n")) {
            String line = rawLine.trim();
            if (line.startsWith("shader_type")) {
                if (!line.replace(";", "").endsWith("surface")) throw new RuntimeException("expected shader_type surface");
                typed = true;
            } else if (line.startsWith("render_mode")) {
                String mode = line.substring("render_mode".length()).replace(";", "").trim();
                blend = switch (mode) {
                    case "blend_add" -> Blend.ADD;
                    case "blend_premul_alpha" -> Blend.PREMUL;
                    default -> Blend.MIX;
                };
            } else if (line.startsWith("uniform ")) {
                Uniform u = parseUniform(line);
                uniforms.add(u);
                if (u.defaultValue() != null && !values.containsKey(u.name())) {
                    values.put(u.name(), u.defaultValue().clone());
                }
            }
        }
        if (!typed) throw new RuntimeException("missing shader_type surface;");
    }

    private static Uniform parseUniform(String line) {
        String body = line.substring("uniform ".length()).replace(";", "").trim();
        String hint = null;
        float[] range = null;
        float[] def = null;

        int eq = body.indexOf('=');
        if (eq >= 0) {
            def = parseFloats(body.substring(eq + 1).trim());
            body = body.substring(0, eq).trim();
        }
        int colon = body.indexOf(':');
        if (colon >= 0) {
            String h = body.substring(colon + 1).trim();
            body = body.substring(0, colon).trim();
            int paren = h.indexOf('(');
            if (paren >= 0) {
                hint = h.substring(0, paren).trim();
                range = parseFloats(h.substring(paren + 1, h.lastIndexOf(')')));
            } else {
                hint = h;
            }
        }
        String[] parts = body.split("\\s+");
        if (parts.length != 2) throw new RuntimeException("bad uniform: " + line);
        return new Uniform(parts[1], parts[0], hint, range, def);
    }

    private static float[] parseFloats(String s) {
        s = s.replaceAll("[a-zA-Z_][a-zA-Z_0-9]*\\(", "").replace(")", "").replace("f", "");
        String[] parts = s.split(",");
        float[] out = new float[parts.length];
        for (int i = 0; i < parts.length; i++) out[i] = Float.parseFloat(parts[i].trim());
        return out;
    }

    private String generate() {
        String src;
        try {
            src = readSource();
        } catch (Exception e) {
            return ShaderProgram.readRaw(MISSING);
        }

        StringBuilder user = new StringBuilder();
        for (String line : src.split("\n")) {
            String t = line.trim();
            if (t.startsWith("shader_type") || t.startsWith("render_mode")) continue;
            if (t.startsWith("uniform ")) {
                Uniform u = parseUniform(t);
                user.append("uniform ").append(u.glslType()).append(' ').append(u.name()).append(";\n");
                continue;
            }
            user.append(line).append('\n');
        }

        return ShaderProgram.readRaw(HEAD) + user + ShaderProgram.readRaw(MAIN);
    }
}

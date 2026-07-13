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

// surface shader dialect: author writes `shader_type surface;` + `void fragment()`
// against UV/TIME/COLOR/HOVER/PRESSED/FOCUS/RECT_SIZE/SCREEN_UV built-ins, uniforms may
// carry hints (`uniform float Speed : hint_range(0,5) = 1.0;`), the engine owns the
// rounded-rect coverage mask and clip so the author only controls color
public final class SurfaceMaterial {

    private static final Logger LOG = LoggerFactory.getLogger("Amnetic/Surface");

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

    public static SurfaceMaterial load(Identifier sourceId) {
        return new SurfaceMaterial(sourceId);
    }

    public Blend blend() { return blend; }
    public boolean broken() { return broken; }
    public List<Uniform> uniforms() { parseIfNeeded(); return uniforms; }

    public SurfaceMaterial set(String name, float... v) {
        values.put(name, v.clone());
        return this;
    }

    public SurfaceMaterial setColor(String name, int argb) {
        return set(name,
                ((argb >> 16) & 0xFF) / 255f, ((argb >> 8) & 0xFF) / 255f,
                (argb & 0xFF) / 255f, ((argb >>> 24) & 0xFF) / 255f);
    }

    // reactive uniform, re-uploads only when the signal changes
    public SurfaceMaterial bind(String name, Signal<Float> signal) {
        bindings.add(new Effect(() -> set(name, signal.get())));
        return this;
    }

    // backing texture for a `uniform sampler2D <name>;`, raw gl id
    public SurfaceMaterial setTexture(String name, int glTextureId) {
        textures.put(name, glTextureId);
        return this;
    }

    public void dispose() {
        for (Effect e : bindings) e.dispose();
        bindings.clear();
        program.close();
    }

    // engine side: bind program and push built-ins + user uniforms, returns false when broken
    public boolean beginDraw(org.joml.Matrix4f ortho, float screenW, float screenH,
                             float time, float hover, float pressed, float focus) {
        parseIfNeeded();
        if (broken) return false;
        try {
            program.begin();
        } catch (Exception e) {
            broken = true; // bad user shader, log once and fall back to flat
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
        // user samplers live on units 1+, unit 0 stays the engine's Tex
        int unit = 1;
        for (Uniform u : uniforms) {
            if (!"sampler2D".equals(u.glslType())) continue;
            program.setSampler(u.name(), unit);
            Integer id = textures.get(u.name());
            GlState.bindTexture(unit, id == null ? 0 : id);
            unit++;
        }
        if (unit > 1) GlState.bindTexture(0, 0); // leave unit 0 active for the batcher
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

    // records uniforms/blend and applies defaults, body extraction happens in generate()
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

    // `uniform float Name : hint_range(0, 5) = 3.0;`
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

    // full fragment source: preamble + user code with dialect lines stripped + engine main
    private String generate() {
        String src;
        try {
            src = readSource();
        } catch (Exception e) {
            return "#version 330 core\nout vec4 FragColor;\nvoid main(){FragColor=vec4(1.0,0.0,1.0,1.0);}\n";
        }

        StringBuilder user = new StringBuilder();
        for (String line : src.split("\n")) {
            String t = line.trim();
            if (t.startsWith("shader_type") || t.startsWith("render_mode")) continue;
            if (t.startsWith("uniform ")) {
                // strip hints and defaults, glsl doesn't know them
                Uniform u = parseUniform(t);
                user.append("uniform ").append(u.glslType()).append(' ').append(u.name()).append(";\n");
                continue;
            }
            user.append(line).append('\n');
        }

        return """
                #version 330 core
                in vec2 vUv;
                in vec4 vColor;
                in vec2 vPos;
                flat in vec4 vParams;
                flat in vec2 vExtra;
                flat in vec4 vClip;
                uniform sampler2D Tex;
                uniform float Time;
                uniform float Hover;
                uniform float Pressed;
                uniform float Focus;
                uniform vec2 ScreenSize;
                out vec4 FragColor;

                #define PI 3.14159265359
                vec2 UV;
                float TIME;
                float HOVER;
                float PRESSED;
                float FOCUS;
                vec2 RECT_SIZE;
                vec2 SCREEN_UV;
                vec4 COLOR;
                vec4 TEXTURE_at(vec2 uv) { return texture(Tex, uv); }

                float amnetic_roundedBox(vec2 p, vec2 halfSize, float radius) {
                    vec2 q = abs(p) - halfSize + radius;
                    return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - radius;
                }
                """
                + user
                + """

                void main() {
                    if (vClip.z > 0.0 && (vPos.x < vClip.x || vPos.y < vClip.y || vPos.x > vClip.z || vPos.y > vClip.w)) discard;
                    UV = vUv / max(vParams.zw * 2.0, vec2(1e-4)) + 0.5;
                    TIME = Time;
                    HOVER = Hover;
                    PRESSED = Pressed;
                    FOCUS = Focus;
                    RECT_SIZE = vParams.zw * 2.0;
                    SCREEN_UV = gl_FragCoord.xy / ScreenSize;
                    COLOR = vColor;
                    fragment();
                    float d = amnetic_roundedBox(vUv, vParams.zw, vParams.y);
                    float aa = max(fwidth(d), 1e-4);
                    float cov = 1.0 - smoothstep(-aa, aa, d);
                    if (cov <= 0.0) discard;
                    FragColor = vec4(COLOR.rgb, COLOR.a * cov);
                }
                """;
    }
}

package com.meekdev.amnetic.client.material;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class SurfaceLayout {
    public enum Kind { FLOAT, INT, BOOL, VEC2, VEC3, VEC4, TEXTURE }

    public record Slot(Kind kind, int index) {}

    public static final SurfaceLayout EMPTY = new SurfaceLayout(Map.of(), "");

    private static final Pattern DECLARATION =
            Pattern.compile("^\\s*uniform\\s+(float|int|bool|vec2|vec3|vec4|sampler2D)\\s+(\\w+)\\s*;\\s*$", Pattern.MULTILINE);

    private final Map<String, Slot> slots;
    private final String body;

    private SurfaceLayout(Map<String, Slot> slots, String body) {
        this.slots = slots;
        this.body = body;
    }

    public static SurfaceLayout parse(String source) {
        Map<String, Slot> slots = new LinkedHashMap<>();
        StringBuilder defines = new StringBuilder();
        StringBuilder undefines = new StringBuilder();
        int values = 0;
        int textures = 0;
        Matcher matcher = DECLARATION.matcher(source);
        while (matcher.find()) {
            String type = matcher.group(1);
            String name = matcher.group(2);
            Slot slot;
            String read;
            if (type.equals("sampler2D")) {
                if (textures == SurfaceInputs.TEXTURE_SLOTS) {
                    throw new IllegalArgumentException("a surface shader takes at most " + SurfaceInputs.TEXTURE_SLOTS + " textures");
                }
                slot = new Slot(Kind.TEXTURE, textures);
                read = "SurfaceTexture" + textures++;
            } else {
                if (values == SurfaceInputs.VEC4_SLOTS) {
                    throw new IllegalArgumentException("a surface shader takes at most " + SurfaceInputs.VEC4_SLOTS + " values");
                }
                String at = "SurfaceValues[" + values + "]";
                slot = switch (type) {
                    case "float" -> new Slot(Kind.FLOAT, values);
                    case "int" -> new Slot(Kind.INT, values);
                    case "bool" -> new Slot(Kind.BOOL, values);
                    case "vec2" -> new Slot(Kind.VEC2, values);
                    case "vec3" -> new Slot(Kind.VEC3, values);
                    default -> new Slot(Kind.VEC4, values);
                };
                read = switch (type) {
                    case "float" -> at + ".x";
                    case "int" -> "int(" + at + ".x)";
                    case "bool" -> "(" + at + ".x > 0.5)";
                    case "vec2" -> at + ".xy";
                    case "vec3" -> at + ".xyz";
                    default -> at;
                };
                values++;
            }
            slots.put(name, slot);
            defines.append("#define ").append(name).append(' ').append(read).append('\n');
            undefines.append("#undef ").append(name).append('\n');
        }
        String code = DECLARATION.matcher(source).replaceAll("");
        return new SurfaceLayout(Map.copyOf(slots), defines + code + "\n" + undefines);
    }

    public Map<String, Slot> slots() {
        return slots;
    }

    public Slot slot(String name) {
        return slots.get(name);
    }

    String body() {
        return body;
    }
}

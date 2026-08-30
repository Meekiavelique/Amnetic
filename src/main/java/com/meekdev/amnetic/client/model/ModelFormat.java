package com.meekdev.amnetic.client.model;

import java.nio.charset.StandardCharsets;
import net.minecraft.resources.Identifier;

public enum ModelFormat {
    GLTF,
    OBJ,
    AMMESH,
    BBMODEL;

    public static ModelFormat fromPath(String path) {
        String p = path.toLowerCase();
        if (p.endsWith(".obj")) return OBJ;
        if (p.endsWith(".gltf") || p.endsWith(".glb")) return GLTF;
        if (p.endsWith(".ammesh")) return AMMESH;
        if (p.endsWith(".bbmodel")) return BBMODEL;
        return null;
    }

    public static ModelFormat fromIdentifier(Identifier id) {
        return fromPath(id.getPath());
    }

    public static ModelFormat fromMagic(byte[] bytes) {
        if (bytes.length >= 4 && bytes[0] == 'A' && bytes[1] == 'M' && bytes[2] == 'S' && bytes[3] == 'H') {
            return AMMESH;
        }
        if (bytes.length >= 4 && bytes[0] == 'g' && bytes[1] == 'l' && bytes[2] == 'T' && bytes[3] == 'F') {
            return GLTF;
        }
        if (looksLikeBbmodel(bytes)) return BBMODEL;
        for (int i = 0; i < Math.min(bytes.length, 64); i++) {
            char c = (char) (bytes[i] & 0xFF);
            if (c == '{') return GLTF;
            if (c == 'v' || c == '#' || c == 'o' || c == 'm' || c == 'g') return OBJ;
            if (!Character.isWhitespace(c)) break;
        }
        return null;
    }

    private static boolean looksLikeBbmodel(byte[] bytes) {
        int limit = Math.min(bytes.length, 4096);
        String head = new String(bytes, 0, limit, StandardCharsets.UTF_8);
        return head.contains("\"model_format\"") || head.contains("\"elements\"") && head.contains("\"outliner\"");
    }
}

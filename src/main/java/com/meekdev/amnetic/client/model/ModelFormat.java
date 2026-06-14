package com.meekdev.amnetic.client.model;

import net.minecraft.resources.Identifier;

public enum ModelFormat {
    GLTF,
    OBJ;

    public static ModelFormat fromPath(String path) {
        String p = path.toLowerCase();
        if (p.endsWith(".obj")) return OBJ;
        if (p.endsWith(".gltf") || p.endsWith(".glb")) return GLTF;
        return null;
    }

    public static ModelFormat fromIdentifier(Identifier id) {
        return fromPath(id.getPath());
    }

    public static ModelFormat fromMagic(byte[] bytes) {
        if (bytes.length >= 4 && bytes[0] == 'g' && bytes[1] == 'l' && bytes[2] == 'T' && bytes[3] == 'F') {
            return GLTF;
        }
        for (int i = 0; i < Math.min(bytes.length, 64); i++) {
            char c = (char) (bytes[i] & 0xFF);
            if (c == '{') return GLTF;
            if (c == 'v' || c == '#' || c == 'o' || c == 'm' || c == 'g') return OBJ;
            if (!Character.isWhitespace(c)) break;
        }
        return null;
    }
}

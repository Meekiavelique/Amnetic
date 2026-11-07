package com.meekdev.amnetic.client.model.internal.parse;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.javagl.jgltf.model.MaterialModel;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.function.Function;

// the bits of a gltf jgltf leaves out: texture transforms, unlit materials, images by uri
public final class GltfExtras {

    private final JsonArray materials;
    private final List<MaterialModel> models;
    private final Function<String, byte[]> files;

    GltfExtras(JsonObject root, List<MaterialModel> models, Function<String, byte[]> files) {
        this.materials = root != null && root.get("materials") instanceof JsonArray list ? list : new JsonArray();
        this.models = models;
        this.files = files;
    }

    // KHR_texture_transform as u' = a*u + c*v + e, v' = b*u + d*v + f, on the uv set it names
    public record UvTransform(float a, float b, float c, float d, float e, float f, int texCoord) {

        public static UvTransform of(float offsetU, float offsetV, float rotation, float scaleU, float scaleV, int texCoord) {
            float cos = (float) Math.cos(rotation);
            float sin = (float) Math.sin(rotation);
            return new UvTransform(cos * scaleU, -sin * scaleU, sin * scaleV, cos * scaleV, offsetU, offsetV, texCoord);
        }

        public void apply(float[] uvs) {
            for (int i = 0; i + 1 < uvs.length; i += 2) {
                float u = uvs[i];
                float v = uvs[i + 1];
                uvs[i] = a * u + c * v + e;
                uvs[i + 1] = b * u + d * v + f;
            }
        }
    }

    // the base colour map's transform, else the emissive map's, baked into the uvs since both share them
    UvTransform uvTransform(MaterialModel model) {
        JsonObject material = material(model);
        if (material == null) return null;
        UvTransform base = material.get("pbrMetallicRoughness") instanceof JsonObject pbr
                ? transform(pbr.get("baseColorTexture")) : null;
        return base != null ? base : transform(material.get("emissiveTexture"));
    }

    boolean unlit(MaterialModel model) {
        JsonObject material = material(model);
        return material != null && material.get("extensions") instanceof JsonObject ext && ext.has("KHR_materials_unlit");
    }

    byte[] image(String uri) {
        return image(uri, files);
    }

    public static UvTransform transform(JsonElement textureInfo) {
        if (!(textureInfo instanceof JsonObject info)) return null;
        if (!(info.get("extensions") instanceof JsonObject ext)) return null;
        if (!(ext.get("KHR_texture_transform") instanceof JsonObject t)) return null;
        float[] offset = pair(t.get("offset"), 0f);
        float[] scale = pair(t.get("scale"), 1f);
        float rotation = t.has("rotation") ? t.get("rotation").getAsFloat() : 0f;
        int texCoord = t.has("texCoord") ? t.get("texCoord").getAsInt()
                : info.has("texCoord") ? info.get("texCoord").getAsInt() : 0;
        return UvTransform.of(offset[0], offset[1], rotation, scale[0], scale[1], texCoord);
    }

    // COLOR_0 as rgba per vertex, null when missing or plain white so nothing is spent on it
    public static float[] rgba(float[] colors, int vertexCount) {
        if (colors == null || vertexCount <= 0) return null;
        int width = colors.length / vertexCount;
        if (width != 3 && width != 4) return null;
        float[] out = new float[vertexCount * 4];
        boolean white = true;
        for (int v = 0; v < vertexCount; v++) {
            for (int k = 0; k < 4; k++) {
                float value = k < width ? colors[v * width + k] : 1f;
                out[v * 4 + k] = value;
                if (value < 0.999f) white = false;
            }
        }
        return white ? null : out;
    }

    // a data uri decoded, else the file it names read through files, uris are percent encoded
    public static byte[] image(String uri, Function<String, byte[]> files) {
        if (uri == null || uri.isEmpty()) return null;
        if (uri.startsWith("data:")) {
            int comma = uri.indexOf(',');
            if (comma < 0 || !uri.substring(0, comma).endsWith(";base64")) return null;
            try {
                return Base64.getDecoder().decode(uri.substring(comma + 1));
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
        if (files == null) return null;
        try {
            byte[] bytes = files.apply(percentDecoded(uri));
            return bytes == null || bytes.length == 0 ? null : bytes;
        } catch (RuntimeException e) {
            return null;
        }
    }

    public static String percentDecoded(String uri) {
        if (uri.indexOf('%') < 0) return uri;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (int i = 0; i < uri.length(); i++) {
            char ch = uri.charAt(i);
            if (ch == '%' && i + 2 < uri.length()) {
                int hi = Character.digit(uri.charAt(i + 1), 16);
                int lo = Character.digit(uri.charAt(i + 2), 16);
                if (hi >= 0 && lo >= 0) {
                    out.write(hi * 16 + lo);
                    i += 2;
                    continue;
                }
            }
            out.writeBytes(String.valueOf(ch).getBytes(StandardCharsets.UTF_8));
        }
        return out.toString(StandardCharsets.UTF_8);
    }

    // the json of a .glb or a plain .gltf, null when it cannot be read
    static JsonObject json(byte[] bytes) {
        GltfPreflight.JsonChunk chunk = GltfPreflight.json(bytes);
        if (chunk == null) return null;
        try {
            String text = new String(bytes, chunk.from(), chunk.length(), StandardCharsets.UTF_8);
            return JsonParser.parseString(text) instanceof JsonObject root ? root : null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    private JsonObject material(MaterialModel model) {
        if (model == null || models == null) return null;
        int index = models.indexOf(model);
        return index >= 0 && index < materials.size() && materials.get(index) instanceof JsonObject m ? m : null;
    }

    private static float[] pair(JsonElement value, float fallback) {
        if (value instanceof JsonArray list && list.size() >= 2) return new float[] {list.get(0).getAsFloat(), list.get(1).getAsFloat()};
        return new float[] {fallback, fallback};
    }
}

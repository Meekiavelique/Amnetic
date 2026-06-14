package com.meekdev.amnetic.client.model.internal.parse;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.meekdev.amnetic.client.model.ModelLoadException;
import com.meekdev.amnetic.client.model.internal.ModelIR;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import org.joml.Matrix4f;

public final class GltfParser {

    private GltfParser() {}

    private static final int BYTE = 5120, UBYTE = 5121, SHORT = 5122, USHORT = 5123, UINT = 5125, FLOAT = 5126;

    public static ModelIR parse(byte[] bytes, Identifier source) {
        JsonObject json;
        byte[] glbBin = null;
        if (isGlb(bytes)) {
            Glb glb = readGlb(bytes);
            json = JsonParser.parseString(new String(glb.json, StandardCharsets.UTF_8)).getAsJsonObject();
            glbBin = glb.bin;
        } else {
            json = JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject();
        }

        ModelIR ir = new ModelIR();
        if (source != null) ir.setName(source.toString());

        // buffers
        JsonArray buffersJson = json.getAsJsonArray("buffers");
        byte[][] buffers = new byte[buffersJson == null ? 0 : buffersJson.size()][];
        for (int i = 0; i < buffers.length; i++) {
            buffers[i] = resolveBuffer(buffersJson.get(i).getAsJsonObject(), glbBin, source);
        }

        JsonArray bufferViews = json.getAsJsonArray("bufferViews");
        JsonArray accessors = json.getAsJsonArray("accessors");
        JsonArray materialsJson = json.getAsJsonArray("materials");
        JsonArray meshes = json.getAsJsonArray("meshes");
        JsonArray nodes = json.getAsJsonArray("nodes");

        // materials
        if (materialsJson != null) {
            for (JsonElement el : materialsJson) {
                ir.addMaterial(parseMaterial(el.getAsJsonObject(), json, buffers, bufferViews, source));
            }
        }

        if (meshes == null) throw new ModelLoadException("glTF has no meshes: " + ir.name());

        if (nodes != null && nodes.size() > 0) {
            int[] roots = sceneRoots(json, nodes.size());
            for (int root : roots) {
                walkNode(ir, nodes, meshes, accessors, bufferViews, buffers, root, new org.joml.Matrix4f());
            }
        } else {
            for (JsonElement m : meshes) {
                addMeshParts(ir, m.getAsJsonObject(), accessors, bufferViews, buffers, new org.joml.Matrix4f());
            }
        }

        if (ir.isEmpty()) throw new ModelLoadException("glTF produced no drawable geometry: " + ir.name());
        return ir;
    }

    private static int[] sceneRoots(JsonObject json, int nodeCount) {
        int sceneIdx = json.has("scene") ? json.get("scene").getAsInt() : 0;
        JsonArray scenes = json.getAsJsonArray("scenes");
        if (scenes != null && sceneIdx < scenes.size()) {
            JsonArray sn = scenes.get(sceneIdx).getAsJsonObject().getAsJsonArray("nodes");
            if (sn != null) {
                int[] r = new int[sn.size()];
                for (int i = 0; i < r.length; i++) r[i] = sn.get(i).getAsInt();
                return r;
            }
        }
        int[] all = new int[nodeCount];
        for (int i = 0; i < nodeCount; i++) all[i] = i;
        return all;
    }

    private static void walkNode(ModelIR ir, JsonArray nodes, JsonArray meshes, JsonArray accessors,
                                 JsonArray bufferViews, byte[][] buffers, int nodeIdx, org.joml.Matrix4f parent) {
        JsonObject node = nodes.get(nodeIdx).getAsJsonObject();
        org.joml.Matrix4f local = nodeTransform(node);
        org.joml.Matrix4f world = new org.joml.Matrix4f(parent).mul(local);

        if (node.has("mesh")) {
            JsonObject mesh = meshes.get(node.get("mesh").getAsInt()).getAsJsonObject();
            addMeshParts(ir, mesh, accessors, bufferViews, buffers, world);
        }
        if (node.has("children")) {
            for (JsonElement c : node.getAsJsonArray("children")) {
                walkNode(ir, nodes, meshes, accessors, bufferViews, buffers, c.getAsInt(), world);
            }
        }
    }

    private static Matrix4f nodeTransform(JsonObject node) {
        if (node.has("matrix")) {
            JsonArray m = node.getAsJsonArray("matrix");
            float[] f = new float[16];
            for (int i = 0; i < 16; i++) f[i] = m.get(i).getAsFloat();
            return new org.joml.Matrix4f().set(f);   // glTF matrices are column-major, as joml expects
        }
        float tx = 0, ty = 0, tz = 0, qx = 0, qy = 0, qz = 0, qw = 1, sx = 1, sy = 1, sz = 1;
        if (node.has("translation")) { JsonArray t = node.getAsJsonArray("translation"); tx = t.get(0).getAsFloat(); ty = t.get(1).getAsFloat(); tz = t.get(2).getAsFloat(); }
        if (node.has("rotation")) { JsonArray r = node.getAsJsonArray("rotation"); qx = r.get(0).getAsFloat(); qy = r.get(1).getAsFloat(); qz = r.get(2).getAsFloat(); qw = r.get(3).getAsFloat(); }
        if (node.has("scale")) { JsonArray s = node.getAsJsonArray("scale"); sx = s.get(0).getAsFloat(); sy = s.get(1).getAsFloat(); sz = s.get(2).getAsFloat(); }
        return new org.joml.Matrix4f().translationRotateScale(tx, ty, tz, qx, qy, qz, qw, sx, sy, sz);
    }

    private static void addMeshParts(ModelIR ir, JsonObject mesh, JsonArray accessors, JsonArray bufferViews,
                                     byte[][] buffers, org.joml.Matrix4f transform) {
        JsonArray prims = mesh.getAsJsonArray("primitives");
        if (prims == null) return;
        for (JsonElement pe : prims) {
            try {
                JsonObject prim = pe.getAsJsonObject();
                JsonObject attrs = prim.getAsJsonObject("attributes");
                if (attrs == null || !attrs.has("POSITION")) continue;

                float[] pos = readAccessorFloats(accessors, bufferViews, buffers, attrs.get("POSITION").getAsInt(), 3);
                int vcount = pos.length / 3;
                float[] nrm = attrs.has("NORMAL")
                        ? readAccessorFloats(accessors, bufferViews, buffers, attrs.get("NORMAL").getAsInt(), 3) : null;
                float[] uv = attrs.has("TEXCOORD_0")
                        ? readAccessorFloats(accessors, bufferViews, buffers, attrs.get("TEXCOORD_0").getAsInt(), 2) : null;

                int[] indices = prim.has("indices")
                        ? readAccessorInts(accessors, bufferViews, buffers, prim.get("indices").getAsInt()) : null;

                float[] interleaved = interleave(pos, nrm, uv, vcount, indices);
                int mat = prim.has("material") ? prim.get("material").getAsInt() : -1;
                ir.addPart(new ModelIR.Part(interleaved, indices, mat, transform, "gltf"));
            } catch (Exception e) {
                // one bad primitive is skipped, not fatal
            }
        }
    }

    private static float[] interleave(float[] pos, float[] nrm, float[] uv, int vcount, int[] indices) {
        float[] out = new float[vcount * ModelIR.VERTEX_STRIDE_FLOATS];
        for (int i = 0; i < vcount; i++) {
            int o = i * ModelIR.VERTEX_STRIDE_FLOATS;
            out[o]     = pos[i * 3];
            out[o + 1] = pos[i * 3 + 1];
            out[o + 2] = pos[i * 3 + 2];
            if (nrm != null) { out[o + 3] = nrm[i * 3]; out[o + 4] = nrm[i * 3 + 1]; out[o + 5] = nrm[i * 3 + 2]; }
            if (uv != null)  { out[o + 6] = uv[i * 2];  out[o + 7] = uv[i * 2 + 1]; }
        }
        if (nrm == null) computeFlatNormals(out, indices, vcount);
        return out;
    }

    private static void computeFlatNormals(float[] interleaved, int[] indices, int vcount) {
        int s = ModelIR.VERTEX_STRIDE_FLOATS;
        int triCount = indices != null ? indices.length / 3 : vcount / 3;
        for (int t = 0; t < triCount; t++) {
            int ia = indices != null ? indices[t * 3] : t * 3;
            int ib = indices != null ? indices[t * 3 + 1] : t * 3 + 1;
            int ic = indices != null ? indices[t * 3 + 2] : t * 3 + 2;
            int oa = ia * s, ob = ib * s, oc = ic * s;
            float ux = interleaved[ob] - interleaved[oa], uy = interleaved[ob + 1] - interleaved[oa + 1], uz = interleaved[ob + 2] - interleaved[oa + 2];
            float vx = interleaved[oc] - interleaved[oa], vy = interleaved[oc + 1] - interleaved[oa + 1], vz = interleaved[oc + 2] - interleaved[oa + 2];
            float nx = uy * vz - uz * vy, ny = uz * vx - ux * vz, nz = ux * vy - uy * vx;
            float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
            if (len < 1e-8f) { ny = 1f; nx = nz = 0f; len = 1f; }
            nx /= len; ny /= len; nz /= len;
            for (int oi : new int[]{oa, ob, oc}) { interleaved[oi + 3] += nx; interleaved[oi + 4] += ny; interleaved[oi + 5] += nz; }
        }
        for (int i = 0; i < vcount; i++) {
            int o = i * s + 3;
            float len = (float) Math.sqrt(interleaved[o] * interleaved[o] + interleaved[o + 1] * interleaved[o + 1] + interleaved[o + 2] * interleaved[o + 2]);
            if (len > 1e-8f) { interleaved[o] /= len; interleaved[o + 1] /= len; interleaved[o + 2] /= len; }
            else interleaved[o + 1] = 1f;
        }
    }

    private static ModelIR.Material parseMaterial(JsonObject mj, JsonObject root, byte[][] buffers,
                                                  JsonArray bufferViews, Identifier source) {
        ModelIR.Material m = new ModelIR.Material();
        if (mj.has("name")) m.name = mj.get("name").getAsString();
        JsonObject pbr = mj.getAsJsonObject("pbrMetallicRoughness");
        if (pbr != null) {
            if (pbr.has("baseColorFactor")) {
                JsonArray c = pbr.getAsJsonArray("baseColorFactor");
                m.baseR = c.get(0).getAsFloat(); m.baseG = c.get(1).getAsFloat();
                m.baseB = c.get(2).getAsFloat(); m.baseA = c.get(3).getAsFloat();
            }
            if (pbr.has("metallicFactor")) m.metallic = pbr.get("metallicFactor").getAsFloat();
            if (pbr.has("roughnessFactor")) m.roughness = pbr.get("roughnessFactor").getAsFloat();
            if (pbr.has("baseColorTexture")) {
                int texIdx = pbr.getAsJsonObject("baseColorTexture").get("index").getAsInt();
                resolveBaseColorImage(m, root, texIdx, buffers, bufferViews, source);
            }
        }
        if (mj.has("emissiveFactor")) {
            JsonArray e = mj.getAsJsonArray("emissiveFactor");
            m.emR = e.get(0).getAsFloat(); m.emG = e.get(1).getAsFloat(); m.emB = e.get(2).getAsFloat();
        }
        return m;
    }

    private static void resolveBaseColorImage(ModelIR.Material m, JsonObject root, int texIdx,
                                              byte[][] buffers, JsonArray bufferViews, Identifier source) {
        try {
            JsonArray textures = root.getAsJsonArray("textures");
            JsonArray images = root.getAsJsonArray("images");
            if (textures == null || images == null) return;
            JsonObject tex = textures.get(texIdx).getAsJsonObject();
            if (!tex.has("source")) return;
            JsonObject img = images.get(tex.get("source").getAsInt()).getAsJsonObject();
            if (img.has("bufferView")) {
                JsonObject bv = bufferViews.get(img.get("bufferView").getAsInt()).getAsJsonObject();
                int buf = bv.get("buffer").getAsInt();
                int off = bv.has("byteOffset") ? bv.get("byteOffset").getAsInt() : 0;
                int len = bv.get("byteLength").getAsInt();
                byte[] slice = new byte[len];
                System.arraycopy(buffers[buf], off, slice, 0, len);
                m.baseColorImageBytes = slice;
            } else if (img.has("uri")) {
                String uri = img.get("uri").getAsString();
                if (uri.startsWith("data:")) {
                    m.baseColorImageBytes = Base64.getDecoder().decode(uri.substring(uri.indexOf(',') + 1));
                } else if (source != null) {
                    m.baseColorTexture = ObjParser.sibling(source, uri);
                }
            }
        } catch (Exception ignored) {
            // texture is optional; fall back to the base-color factor
        }
    }

    private static byte[] resolveBuffer(JsonObject buf, byte[] glbBin, Identifier source) {
        if (buf.has("uri")) {
            String uri = buf.get("uri").getAsString();
            if (uri.startsWith("data:")) {
                return Base64.getDecoder().decode(uri.substring(uri.indexOf(',') + 1));
            }
            if (source != null) {
                Identifier id = ObjParser.sibling(source, uri);
                Optional<Resource> res = Minecraft.getInstance().getResourceManager().getResource(id);
                if (res.isPresent()) {
                    try (InputStream is = res.get().open()) {
                        return is.readAllBytes();
                    } catch (Exception e) {
                        throw new ModelLoadException("Failed to read external glTF buffer: " + id, e);
                    }
                }
            }
            throw new ModelLoadException("Unresolvable glTF buffer uri: " + uri);
        }
        if (glbBin != null) return glbBin;   // the .glb BIN chunk
        throw new ModelLoadException("glTF buffer has no uri and no binary chunk");
    }

    private static float[] readAccessorFloats(JsonArray accessors, JsonArray bufferViews, byte[][] buffers,
                                              int accessorIdx, int components) {
        JsonObject acc = accessors.get(accessorIdx).getAsJsonObject();
        int count = acc.get("count").getAsInt();
        int compType = acc.get("componentType").getAsInt();
        int accByteOffset = acc.has("byteOffset") ? acc.get("byteOffset").getAsInt() : 0;
        boolean normalized = acc.has("normalized") && acc.get("normalized").getAsBoolean();

        JsonObject bv = bufferViews.get(acc.get("bufferView").getAsInt()).getAsJsonObject();
        int buf = bv.get("buffer").getAsInt();
        int bvOffset = bv.has("byteOffset") ? bv.get("byteOffset").getAsInt() : 0;
        int stride = bv.has("byteStride") ? bv.get("byteStride").getAsInt() : components * componentSize(compType);

        ByteBuffer data = ByteBuffer.wrap(buffers[buf]).order(ByteOrder.LITTLE_ENDIAN);
        float[] out = new float[count * components];
        int base = bvOffset + accByteOffset;
        for (int i = 0; i < count; i++) {
            int rowBase = base + i * stride;
            for (int c = 0; c < components; c++) {
                int p = rowBase + c * componentSize(compType);
                out[i * components + c] = readComponentAsFloat(data, p, compType, normalized);
            }
        }
        return out;
    }

    private static int[] readAccessorInts(JsonArray accessors, JsonArray bufferViews, byte[][] buffers, int accessorIdx) {
        JsonObject acc = accessors.get(accessorIdx).getAsJsonObject();
        int count = acc.get("count").getAsInt();
        int compType = acc.get("componentType").getAsInt();
        int accByteOffset = acc.has("byteOffset") ? acc.get("byteOffset").getAsInt() : 0;
        JsonObject bv = bufferViews.get(acc.get("bufferView").getAsInt()).getAsJsonObject();
        int buf = bv.get("buffer").getAsInt();
        int bvOffset = bv.has("byteOffset") ? bv.get("byteOffset").getAsInt() : 0;
        int csize = componentSize(compType);
        int stride = bv.has("byteStride") ? bv.get("byteStride").getAsInt() : csize;

        ByteBuffer data = ByteBuffer.wrap(buffers[buf]).order(ByteOrder.LITTLE_ENDIAN);
        int[] out = new int[count];
        int base = bvOffset + accByteOffset;
        for (int i = 0; i < count; i++) {
            int p = base + i * stride;
            out[i] = switch (compType) {
                case UBYTE, BYTE -> data.get(p) & 0xFF;
                case USHORT, SHORT -> data.getShort(p) & 0xFFFF;
                case UINT -> data.getInt(p);
                default -> throw new ModelLoadException("Unsupported index component type: " + compType);
            };
        }
        return out;
    }

    private static float readComponentAsFloat(ByteBuffer data, int p, int compType, boolean normalized) {
        return switch (compType) {
            case FLOAT -> data.getFloat(p);
            case UBYTE -> normalized ? (data.get(p) & 0xFF) / 255f : (data.get(p) & 0xFF);
            case BYTE -> normalized ? Math.max((data.get(p)) / 127f, -1f) : data.get(p);
            case USHORT -> normalized ? (data.getShort(p) & 0xFFFF) / 65535f : (data.getShort(p) & 0xFFFF);
            case SHORT -> normalized ? Math.max((data.getShort(p)) / 32767f, -1f) : data.getShort(p);
            default -> throw new ModelLoadException("Unsupported component type: " + compType);
        };
    }

    private static int componentSize(int compType) {
        return switch (compType) {
            case BYTE, UBYTE -> 1;
            case SHORT, USHORT -> 2;
            case UINT, FLOAT -> 4;
            default -> throw new ModelLoadException("Unknown component type: " + compType);
        };
    }

    private static boolean isGlb(byte[] b) {
        return b.length >= 4 && b[0] == 'g' && b[1] == 'l' && b[2] == 'T' && b[3] == 'F';
    }

    private record Glb(byte[] json, byte[] bin) {}

    private static Glb readGlb(byte[] bytes) {
        ByteBuffer bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        bb.getInt();                 // magic
        int version = bb.getInt();
        if (version != 2) throw new ModelLoadException("Unsupported glb version: " + version);
        bb.getInt();                 // total length
        byte[] json = null, bin = null;
        while (bb.remaining() >= 8) {
            int chunkLen = bb.getInt();
            int chunkType = bb.getInt();
            if (chunkLen < 0 || chunkLen > bb.remaining()) break;
            byte[] chunk = new byte[chunkLen];
            bb.get(chunk);
            if (chunkType == 0x4E4F534A) json = chunk;          // "JSON"
            else if (chunkType == 0x004E4942) bin = chunk;      // "BIN\0"
        }
        if (json == null) throw new ModelLoadException("glb has no JSON chunk");
        return new Glb(json, bin);
    }
}

package com.meekdev.amnetic.client.model.internal.parse;

import com.meekdev.amnetic.client.model.ModelLoadException;
import com.meekdev.amnetic.client.model.internal.ModelIR;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;

public final class ObjParser {

    private ObjParser() {}

    public static ModelIR parse(byte[] bytes, Identifier source) {
        ModelIR ir = new ModelIR();
        if (source != null) ir.setName(source.toString());

        List<float[]> positions = new ArrayList<>();
        List<float[]> normals = new ArrayList<>();
        List<float[]> uvs = new ArrayList<>();

        Map<String, Integer> materialIndex = new HashMap<>();
        Map<Integer, List<float[]>> partVerts = new HashMap<>();
        int currentMaterial = -1;

        String text = new String(bytes, StandardCharsets.UTF_8);
        String[] lines = text.split("\\R");
        for (String raw : lines) {
            String line = raw.trim();
            if (line.isEmpty() || line.charAt(0) == '#') continue;
            int sp = line.indexOf(' ');
            String key = sp < 0 ? line : line.substring(0, sp);
            String rest = sp < 0 ? "" : line.substring(sp + 1).trim();

            switch (key) {
                case "v" -> positions.add(parseFloats(rest, 3));
                case "vn" -> normals.add(parseFloats(rest, 3));
                case "vt" -> {
                    float[] f = parseFloats(rest, 2);
                    f[1] = 1f - f[1];
                    uvs.add(f);
                }
                case "mtllib" -> {
                    if (source != null) parseMtl(ir, materialIndex, source, rest);
                }
                case "usemtl" -> {
                    Integer idx = materialIndex.get(rest);
                    if (idx == null) {
                        ModelIR.Material m = new ModelIR.Material();
                        m.name = rest;
                        idx = ir.addMaterial(m);
                        materialIndex.put(rest, idx);
                    }
                    currentMaterial = idx;
                }
                case "f" -> triangulateFace(rest, positions, normals, uvs,
                        partVerts.computeIfAbsent(currentMaterial, k -> new ArrayList<>()));
                default -> { /* o, g, s */ }
            }
        }

        if (partVerts.isEmpty()) throw new ModelLoadException("OBJ has no faces: " + ir.name());

        int defaultMaterial = -1;
        for (Map.Entry<Integer, List<float[]>> e : partVerts.entrySet()) {
            List<float[]> verts = e.getValue();
            if (verts.isEmpty()) continue;
            int matIdx = e.getKey();
            if (matIdx < 0) {
                if (defaultMaterial < 0) {
                    ModelIR.Material def = new ModelIR.Material();
                    def.name = "default";
                    defaultMaterial = ir.addMaterial(def);
                }
                matIdx = defaultMaterial;
            }
            float[] flat = new float[verts.size() * ModelIR.VERTEX_STRIDE_FLOATS];
            for (int i = 0; i < verts.size(); i++) {
                System.arraycopy(verts.get(i), 0, flat, i * ModelIR.VERTEX_STRIDE_FLOATS, ModelIR.VERTEX_STRIDE_FLOATS);
            }
            ir.addPart(new ModelIR.Part(flat, null, matIdx, null, "obj"));
        }
        return ir;
    }

    private static void triangulateFace(String rest, List<float[]> positions, List<float[]> normals,
                                        List<float[]> uvs, List<float[]> out) {
        String[] tokens = rest.split("\\s+");
        if (tokens.length < 3) return;
        float[][] face = new float[tokens.length][];
        for (int i = 0; i < tokens.length; i++) {
            face[i] = resolveVertex(tokens[i], positions, normals, uvs);
        }
        boolean needNormal = false;
        for (float[] v : face) if (v[3] == 0f && v[4] == 0f && v[5] == 0f) { needNormal = true; break; }
        for (int i = 1; i + 1 < tokens.length; i++) {
            float[] a = face[0].clone(), b = face[i].clone(), c = face[i + 1].clone();
            if (needNormal) {
                float[] n = triangleNormal(a, b, c);
                System.arraycopy(n, 0, a, 3, 3);
                System.arraycopy(n, 0, b, 3, 3);
                System.arraycopy(n, 0, c, 3, 3);
            }
            out.add(a); out.add(b); out.add(c);
        }
    }

    private static float[] resolveVertex(String token, List<float[]> positions, List<float[]> normals,
                                         List<float[]> uvs) {
        String[] p = token.split("/", -1);
        float[] v = new float[ModelIR.VERTEX_STRIDE_FLOATS];
        float[] pos = positions.get(resolveIndex(p[0], positions.size()));
        v[0] = pos[0]; v[1] = pos[1]; v[2] = pos[2];
        if (p.length >= 2 && !p[1].isEmpty()) {
            float[] uv = uvs.get(resolveIndex(p[1], uvs.size()));
            v[6] = uv[0]; v[7] = uv[1];
        }
        if (p.length >= 3 && !p[2].isEmpty()) {
            float[] n = normals.get(resolveIndex(p[2], normals.size()));
            v[3] = n[0]; v[4] = n[1]; v[5] = n[2];
        }
        return v;
    }

    private static int resolveIndex(String s, int size) {
        int i = Integer.parseInt(s.trim());
        if (i < 0) return size + i;     // relative
        return i - 1;                   // 1-based
    }

    private static float[] triangleNormal(float[] a, float[] b, float[] c) {
        float ux = b[0] - a[0], uy = b[1] - a[1], uz = b[2] - a[2];
        float vx = c[0] - a[0], vy = c[1] - a[1], vz = c[2] - a[2];
        float nx = uy * vz - uz * vy;
        float ny = uz * vx - ux * vz;
        float nz = ux * vy - uy * vx;
        float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (len < 1e-8f) return new float[]{0f, 1f, 0f};
        return new float[]{nx / len, ny / len, nz / len};
    }

    private static float[] parseFloats(String s, int n) {
        String[] t = s.split("\\s+");
        float[] f = new float[n];
        for (int i = 0; i < n && i < t.length; i++) f[i] = Float.parseFloat(t[i]);
        return f;
    }

    private static void parseMtl(ModelIR ir, Map<String, Integer> materialIndex,
                                 Identifier objSource, String mtlName) {
        Identifier mtlId = sibling(objSource, mtlName);
        Optional<Resource> res = Minecraft.getInstance().getResourceManager().getResource(mtlId);
        if (res.isEmpty()) return;
        String text;
        try (InputStream is = res.get().open()) {
            text = new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return;
        }

        ModelIR.Material current = null;
        for (String raw : text.split("\\R")) {
            String line = raw.trim();
            if (line.isEmpty() || line.charAt(0) == '#') continue;
            int sp = line.indexOf(' ');
            String key = sp < 0 ? line : line.substring(0, sp);
            String rest = sp < 0 ? "" : line.substring(sp + 1).trim();
            switch (key) {
                case "newmtl" -> {
                    current = new ModelIR.Material();
                    current.name = rest;
                    int idx = ir.addMaterial(current);
                    materialIndex.put(rest, idx);
                }
                case "Kd" -> { if (current != null) { float[] c = parseFloats(rest, 3); current.baseR = c[0]; current.baseG = c[1]; current.baseB = c[2]; } }
                case "Ke" -> { if (current != null) { float[] c = parseFloats(rest, 3); current.emR = c[0]; current.emG = c[1]; current.emB = c[2]; } }
                case "d" -> { if (current != null) current.baseA = parseFloats(rest, 1)[0]; }
                case "Tr" -> { if (current != null) current.baseA = 1f - parseFloats(rest, 1)[0]; }
                case "Pm" -> { if (current != null) current.metallic = parseFloats(rest, 1)[0]; }
                case "Pr" -> { if (current != null) current.roughness = parseFloats(rest, 1)[0]; }
                case "map_Kd" -> { if (current != null) current.baseColorTexture = sibling(objSource, lastToken(rest)); }
                default -> { /* ignored */ }
            }
        }
    }

    private static String lastToken(String s) {
        String[] t = s.split("\\s+");
        return t[t.length - 1];
    }

    static Identifier sibling(Identifier source, String relative) {
        String path = source.getPath();
        int slash = path.lastIndexOf('/');
        String dir = slash < 0 ? "" : path.substring(0, slash + 1);
        String rel = relative.replace('\\', '/');
        while (rel.startsWith("./")) rel = rel.substring(2);
        return Identifier.fromNamespaceAndPath(source.getNamespace(), dir + rel);
    }
}

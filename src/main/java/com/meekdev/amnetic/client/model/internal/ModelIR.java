package com.meekdev.amnetic.client.model.internal;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.resources.Identifier;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;

public final class ModelIR {

    public static final int VERTEX_STRIDE_FLOATS = 8;

    private final List<Part> parts = new ArrayList<>();
    private final List<Material> materials = new ArrayList<>();
    private String name = "model";

    public List<Part> parts() { return parts; }
    public List<Material> materials() { return materials; }
    public String name() { return name; }
    public void setName(String name) { this.name = name; }

    public int addMaterial(Material m) {
        materials.add(m);
        return materials.size() - 1;
    }

    public void addPart(Part p) { parts.add(p); }

    public boolean isEmpty() { return parts.isEmpty(); }

    public static final class Part {
        public final float[] vertices;     // interleaved px,py,pz, nx,ny,nz, u,v
        public final int[] indices;        // may be null (non-indexed)
        public final int materialIndex;    // index into materials(), or -1 for default
        public final Matrix4f transform;   // node local→model transform, baked from the hierarchy
        public final String name;

        public Part(float[] vertices, int[] indices, int materialIndex, Matrix4fc transform, String name) {
            this.vertices = vertices;
            this.indices = indices;
            this.materialIndex = materialIndex;
            this.transform = transform == null ? new Matrix4f() : new Matrix4f(transform);
            this.name = name == null ? "part" : name;
        }

        public int vertexCount() { return vertices.length / VERTEX_STRIDE_FLOATS; }
        public boolean hasIndices() { return indices != null && indices.length > 0; }
    }

    public static final class Material {
        public String name = "material";
        public float baseR = 1f, baseG = 1f, baseB = 1f, baseA = 1f;
        public float metallic = 0f;
        public float roughness = 1f;
        public float emR = 0f, emG = 0f, emB = 0f;
        public byte[] baseColorImageBytes;
        public Identifier baseColorTexture;
    }
}

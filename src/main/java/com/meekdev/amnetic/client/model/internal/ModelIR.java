package com.meekdev.amnetic.client.model.internal;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.resources.Identifier;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public final class ModelIR {

    public static final int VERTEX_STRIDE_FLOATS = 8;

    private final List<Part> parts = new ArrayList<>();
    private final List<Material> materials = new ArrayList<>();
    private final List<Node> nodes = new ArrayList<>();
    private final List<Animation> animations = new ArrayList<>();
    private String name = "model";

    public List<Part> parts() {
        return parts;
    }

    public List<Material> materials() {
        return materials;
    }

    public List<Node> nodes() {
        return nodes;
    }

    public List<Animation> animations() {
        return animations;
    }

    public String name() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int addMaterial(Material m) {
        materials.add(m);
        return materials.size() - 1;
    }

    public void addPart(Part p) {
        parts.add(p);
    }

    public int addNode(Node n) {
        nodes.add(n);
        return nodes.size() - 1;
    }

    public Animation animation(String clip) {
        for (Animation a : animations) {
            if (a.name.equals(clip)) {
                return a;
            }
        }
        return null;
    }

    public boolean isEmpty() {
        return parts.isEmpty();
    }

    public boolean hasAnimations() {
        return !animations.isEmpty() && !nodes.isEmpty();
    }

    public static final class Part {
        public final float[] vertices;
        public final float[] tangents;
        public final int[] indices;
        public final int materialIndex;
        public final int nodeIndex;
        public final Matrix4f transform;
        public final String name;

        public float[] jointIndices;
        public float[] jointWeights;
        public int[] jointNodes;
        public Matrix4f[] inverseBind;

        // pregenerated LOD index lists over vertices (lods[0] = full detail, higher = coarser)
        // null until ModelLod finishes on a background thread, volatile for that cross-thread publish
        // skinned parts are not simplified
        public volatile int[][] lods;

        public Part(float[] vertices, float[] tangents, int[] indices, int materialIndex,
                    int nodeIndex, Matrix4fc transform, String name) {
            this.vertices = vertices;
            this.tangents = tangents;
            this.indices = indices;
            this.materialIndex = materialIndex;
            this.nodeIndex = nodeIndex;
            this.transform = transform == null ? new Matrix4f() : new Matrix4f(transform);
            this.name = name == null ? "part" : name;
        }

        public int vertexCount() {
            return vertices.length / VERTEX_STRIDE_FLOATS;
        }

        public boolean hasIndices() {
            return indices != null && indices.length > 0;
        }

        public boolean hasTangents() {
            return tangents != null && tangents.length > 0;
        }

        public boolean hasSkin() {
            return jointNodes != null && jointNodes.length > 0
                    && jointIndices != null && jointWeights != null;
        }

        public int jointCount() {
            return jointNodes == null ? 0 : jointNodes.length;
        }
    }

    public static final class Material {
        public String name = "material";
        public float baseR = 1f;
        public float baseG = 1f;
        public float baseB = 1f;
        public float baseA = 1f;
        public float metallic = 0f;
        public float roughness = 1f;
        public float transmission = 0f;
        public int emissiveGlId = 0; // caller-owned live GL texture overriding the emissive map (0 = none)
        public float emR = 0f;
        public float emG = 0f;
        public float emB = 0f;
        public float alphaCutoff = 0f;
        public int shadingModelId = 0; // 0 = built-in default PBR, else ShadingModelRegistry id
        public boolean blend = false;
        public boolean doubleSided = false;

        public byte[] baseColorImageBytes;
        public Identifier baseColorTexture;
        public byte[] normalImageBytes;
        public Identifier normalTexture;
        public byte[] ormImageBytes;
        public Identifier ormTexture;
        public byte[] emissiveImageBytes;
        public Identifier emissiveTexture;
    }

    public static final class Node {
        public String name = "node";
        public final Matrix4f local = new Matrix4f();
        public int[] children = EMPTY;
        public int parent = -1;

        public final Vector3f t = new Vector3f(0f, 0f, 0f);
        public final Quaternionf r = new Quaternionf();
        public final Vector3f s = new Vector3f(1f, 1f, 1f);

        public void rebuildLocal() {
            local.translationRotateScale(t, r, s);
        }
    }

    public static final class Animation {
        public String name = "animation";
        public float duration = 0f;
        public final List<Channel> channels = new ArrayList<>();
    }

    public enum Path {
        TRANSLATION,
        ROTATION,
        SCALE
    }

    public enum Interp {
        STEP,
        LINEAR
    }

    public static final class Channel {
        public int node;
        public Path path;
        public Interp interp = Interp.LINEAR;
        public float[] times;
        public float[] values;
    }

    private static final int[] EMPTY = new int[0];
}

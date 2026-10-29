package com.meekdev.amnetic.client.model.internal;

import org.joml.Matrix4f;
import org.joml.Vector3f;

public final class Skinning {
    private Skinning() {
    }

    public static int[] order(ModelIR ir) {
        int count = ir.nodes().size();
        int[] out = new int[count];
        boolean[] placed = new boolean[count];
        int at = 0;
        for (int i = 0; i < count; i++) {
            at = place(ir, i, placed, out, at, 0);
        }
        return out;
    }

    private static int place(ModelIR ir, int node, boolean[] placed, int[] out, int at, int depth) {
        if (placed[node]) {
            return at;
        }
        int parent = ir.nodes().get(node).parent;
        if (parent >= 0 && parent < placed.length && parent != node && depth < placed.length) {
            at = place(ir, parent, placed, out, at, depth + 1);
        }
        if (placed[node]) {
            return at;
        }
        placed[node] = true;
        out[at] = node;
        return at + 1;
    }

    public static Matrix4f[] restPose(ModelIR ir) {
        int count = ir.nodes().size();
        Matrix4f[] world = new Matrix4f[count];
        for (int node : order(ir)) {
            ModelIR.Node n = ir.nodes().get(node);
            Matrix4f local = new Matrix4f().translationRotateScale(n.t, n.r, n.s);
            int parent = n.parent;
            world[node] = parent >= 0 && parent < count && world[parent] != null
                    ? new Matrix4f(world[parent]).mul(local) : local;
        }
        return world;
    }

    public static Matrix4f joint(ModelIR.Part part, Matrix4f[] pose, int j, Matrix4f out) {
        int node = part.jointNodes[j];
        if (pose == null || node < 0 || node >= pose.length || pose[node] == null) {
            return out.identity();
        }
        return pose[node].mul(part.inverseBind[j], out);
    }

    public static Matrix4f[] palette(ModelIR.Part part, Matrix4f[] pose) {
        Matrix4f[] out = new Matrix4f[part.jointCount()];
        for (int j = 0; j < out.length; j++) {
            out[j] = joint(part, pose, j, new Matrix4f());
        }
        return out;
    }

    public static Vector3f position(ModelIR.Part part, Matrix4f[] palette, int v, Vector3f out) {
        int o = v * ModelIR.VERTEX_STRIDE_FLOATS;
        float x = part.vertices[o];
        float y = part.vertices[o + 1];
        float z = part.vertices[o + 2];
        float sx = 0f;
        float sy = 0f;
        float sz = 0f;
        for (int k = 0; k < 4; k++) {
            float w = part.jointWeights[v * 4 + k];
            int j = (int) part.jointIndices[v * 4 + k];
            if (w == 0f || j < 0 || j >= palette.length) {
                continue;
            }
            Matrix4f m = palette[j];
            sx += w * (m.m00() * x + m.m10() * y + m.m20() * z + m.m30());
            sy += w * (m.m01() * x + m.m11() * y + m.m21() * z + m.m31());
            sz += w * (m.m02() * x + m.m12() * y + m.m22() * z + m.m32());
        }
        return out.set(sx, sy, sz);
    }

    public static void bounds(ModelIR.Part part, Matrix4f[] pose, Vector3f min, Vector3f max) {
        Matrix4f[] palette = palette(part, pose);
        Vector3f v = new Vector3f();
        int count = part.vertexCount();
        for (int i = 0; i < count; i++) {
            position(part, palette, i, v);
            min.min(v);
            max.max(v);
        }
    }
}

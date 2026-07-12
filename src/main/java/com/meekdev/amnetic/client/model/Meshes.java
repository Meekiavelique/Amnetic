package com.meekdev.amnetic.client.model;

import com.meekdev.amnetic.client.model.internal.ModelIR;

/**
 * builds {@link Model}s from raw vertex arrays for procedural geometry that never comes from a
 * glTF/.ammesh source. interleaves separate position/normal/uv arrays into the packed vertex layout
 * (pos3, normal3, uv2 = {@value ModelIR#VERTEX_STRIDE_FLOATS} floats per vertex) and fills in
 * defaults so callers only supply what they have
 */
public final class Meshes {

    private Meshes() {}

    // positions only: smooth normals computed, UVs zero, sequential triangle-list indices
    public static Model build(float[] positions) {
        return build(positions, null, null, null, new ModelIR.Material());
    }

    /**
     * positions are xyz per vertex. normals, uvs and indices may each be null: null normals get
     * smooth normals computed from the triangles, null uvs become (0,0), null indices become a
     * sequential list (0,1,2,...)
     */
    public static Model build(float[] positions, float[] normals, float[] uvs, int[] indices) {
        return build(positions, normals, uvs, indices, new ModelIR.Material());
    }

    // same as build(positions, normals, uvs, indices) but with a caller-supplied material
    public static Model build(float[] positions, float[] normals, float[] uvs, int[] indices,
                              ModelIR.Material material) {
        if (positions == null || positions.length == 0 || positions.length % 3 != 0) {
            throw new IllegalArgumentException("positions must be non-empty and a multiple of 3");
        }
        int vertexCount = positions.length / 3;

        if (indices == null) {
            indices = sequentialIndices(vertexCount);
        }
        if (normals == null) {
            normals = computeSmoothNormals(positions, indices);
        } else if (normals.length != vertexCount * 3) {
            throw new IllegalArgumentException("normals length must be positions.length (3 per vertex)");
        }
        if (uvs != null && uvs.length != vertexCount * 2) {
            throw new IllegalArgumentException("uvs length must be 2 per vertex");
        }

        float[] interleaved = new float[vertexCount * ModelIR.VERTEX_STRIDE_FLOATS];
        for (int v = 0; v < vertexCount; v++) {
            int o = v * ModelIR.VERTEX_STRIDE_FLOATS;
            interleaved[o] = positions[v * 3];
            interleaved[o + 1] = positions[v * 3 + 1];
            interleaved[o + 2] = positions[v * 3 + 2];
            interleaved[o + 3] = normals[v * 3];
            interleaved[o + 4] = normals[v * 3 + 1];
            interleaved[o + 5] = normals[v * 3 + 2];
            interleaved[o + 6] = uvs == null ? 0f : uvs[v * 2];
            interleaved[o + 7] = uvs == null ? 0f : uvs[v * 2 + 1];
        }

        ModelIR ir = new ModelIR();
        int mat = ir.addMaterial(material == null ? new ModelIR.Material() : material);
        // nodeIndex -1 means no skeleton, GpuModel falls back to the part transform (identity here)
        ir.addPart(new ModelIR.Part(interleaved, null, indices, mat, -1, null, "procedural"));
        return Models.fromIR(ir);
    }

    private static int[] sequentialIndices(int vertexCount) {
        int[] idx = new int[vertexCount];
        for (int i = 0; i < vertexCount; i++) {
            idx[i] = i;
        }
        return idx;
    }

    private static float[] computeSmoothNormals(float[] positions, int[] indices) {
        float[] normals = new float[positions.length];
        for (int t = 0; t + 2 < indices.length; t += 3) {
            int ia = indices[t] * 3;
            int ib = indices[t + 1] * 3;
            int ic = indices[t + 2] * 3;

            float ax = positions[ia], ay = positions[ia + 1], az = positions[ia + 2];
            float bx = positions[ib], by = positions[ib + 1], bz = positions[ib + 2];
            float cx = positions[ic], cy = positions[ic + 1], cz = positions[ic + 2];

            float e1x = bx - ax, e1y = by - ay, e1z = bz - az;
            float e2x = cx - ax, e2y = cy - ay, e2z = cz - az;

            // face normal e1 x e2 left unnormalized so larger triangles weight more
            float nx = e1y * e2z - e1z * e2y;
            float ny = e1z * e2x - e1x * e2z;
            float nz = e1x * e2y - e1y * e2x;

            normals[ia] += nx; normals[ia + 1] += ny; normals[ia + 2] += nz;
            normals[ib] += nx; normals[ib + 1] += ny; normals[ib + 2] += nz;
            normals[ic] += nx; normals[ic + 1] += ny; normals[ic + 2] += nz;
        }
        for (int v = 0; v < normals.length; v += 3) {
            float nx = normals[v], ny = normals[v + 1], nz = normals[v + 2];
            float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
            if (len > 1e-8f) {
                normals[v] = nx / len;
                normals[v + 1] = ny / len;
                normals[v + 2] = nz / len;
            } else {
                normals[v] = 0f; normals[v + 1] = 1f; normals[v + 2] = 0f;
            }
        }
        return normals;
    }
}

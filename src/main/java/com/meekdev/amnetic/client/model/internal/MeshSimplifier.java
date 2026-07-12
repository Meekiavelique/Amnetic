package com.meekdev.amnetic.client.model.internal;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Set;

/**
 * quadric error metric mesh simplifier (Garland and Heckbert). iterative edge contraction with
 * per-vertex quadrics, using subset placement (contract to whichever endpoint has lower error) so
 * surviving vertices keep their original position and attributes. output is just a reduced index
 * list over the same vertex array.
 * geometry-only quadrics. meant to run once at model load off the render thread, not per frame.
 * robust rather than optimal, skips contractions that would fold a face over
 */
public final class MeshSimplifier {

    private static final int STRIDE = ModelIR.VERTEX_STRIDE_FLOATS; // pos3, normal3, uv2

    private MeshSimplifier() {}

    // new index array with about targetTriangles triangles over the same verts
    // clone of indices if already at/under target or too small
    public static int[] simplify(float[] verts, int[] indices, int targetTriangles) {
        int triCount = indices.length / 3;
        if (targetTriangles >= triCount || triCount < 8) {
            return indices.clone();
        }
        int vertexCount = verts.length / STRIDE;

        // homogeneous 4x4 quadric per vertex, stored as 10 unique coefficients (upper triangle)
        double[][] q = new double[vertexCount][10];

        int[] ta = new int[triCount], tb = new int[triCount], tc = new int[triCount];
        boolean[] triAlive = new boolean[triCount];
        @SuppressWarnings("unchecked")
        List<Integer>[] inc = new List[vertexCount];
        for (int i = 0; i < vertexCount; i++) {
            inc[i] = new ArrayList<>(6);
        }

        for (int t = 0; t < triCount; t++) {
            int a = indices[t * 3], b = indices[t * 3 + 1], c = indices[t * 3 + 2];
            ta[t] = a; tb[t] = b; tc[t] = c; triAlive[t] = true;
            double[] fq = faceQuadric(verts, a, b, c);
            add(q[a], fq); add(q[b], fq); add(q[c], fq);
            inc[a].add(t); inc[b].add(t); inc[c].add(t);
        }

        boolean[] vAlive = new boolean[vertexCount];
        Arrays.fill(vAlive, true);
        long[] version = new long[vertexCount];

        PriorityQueue<Edge> heap = new PriorityQueue<>();
        Set<Long> seen = new HashSet<>();
        for (int t = 0; t < triCount; t++) {
            pushEdgeIfNew(heap, seen, verts, q, version, ta[t], tb[t]);
            pushEdgeIfNew(heap, seen, verts, q, version, tb[t], tc[t]);
            pushEdgeIfNew(heap, seen, verts, q, version, tc[t], ta[t]);
        }

        int alive = triCount;
        while (alive > targetTriangles && !heap.isEmpty()) {
            Edge e = heap.poll();
            int a = e.a, b = e.b;
            if (!vAlive[a] || !vAlive[b] || version[a] != e.va || version[b] != e.vb) {
                continue; // stale
            }

            // subset placement: keep the endpoint whose position has lower error under the summed quadric
            double[] qs = new double[10];
            add(qs, q[a]); add(qs, q[b]);
            int keep, rem;
            if (eval(qs, verts, a) <= eval(qs, verts, b)) { keep = a; rem = b; } else { keep = b; rem = a; }

            if (wouldFlip(verts, inc, ta, tb, tc, triAlive, rem, keep)) {
                continue; // contracting would fold a triangle
            }

            System.arraycopy(qs, 0, q[keep], 0, 10);
            for (int t : inc[rem]) {
                if (!triAlive[t]) {
                    continue;
                }
                if (ta[t] == rem) ta[t] = keep;
                if (tb[t] == rem) tb[t] = keep;
                if (tc[t] == rem) tc[t] = keep;
                if (ta[t] == tb[t] || tb[t] == tc[t] || tc[t] == ta[t]) {
                    triAlive[t] = false; // degenerate
                    alive--;
                } else if (!inc[keep].contains(t)) {
                    inc[keep].add(t);
                }
            }
            inc[rem].clear();
            vAlive[rem] = false;
            version[keep]++;

            seen.clear(); // seen only guards initial dedup, after contraction we push freely (version-guarded)
            for (int t : inc[keep]) {
                if (!triAlive[t]) {
                    continue;
                }
                for (int other : new int[]{ta[t], tb[t], tc[t]}) {
                    if (other != keep && vAlive[other]) {
                        heap.add(makeEdge(verts, q, version, keep, other));
                    }
                }
            }
        }

        int[] out = new int[alive * 3];
        int w = 0;
        for (int t = 0; t < triCount; t++) {
            if (triAlive[t]) {
                out[w++] = ta[t]; out[w++] = tb[t]; out[w++] = tc[t];
            }
        }
        return out;
    }

    // true if contracting rem->keep would flip the normal of any triangle around rem (fold-over guard)
    private static boolean wouldFlip(float[] verts, List<Integer>[] inc, int[] ta, int[] tb, int[] tc,
                                     boolean[] triAlive, int rem, int keep) {
        double kx = verts[keep * STRIDE], ky = verts[keep * STRIDE + 1], kz = verts[keep * STRIDE + 2];
        for (int t : inc[rem]) {
            if (!triAlive[t]) {
                continue;
            }
            int a = ta[t], b = tb[t], c = tc[t];
            if ((a == keep || b == keep || c == keep)) {
                continue; // this triangle collapses away
            }
            // normal before
            double[] nb = normal(verts, a, b, c);
            // normal after moving rem -> keep position
            double ax = a == rem ? kx : verts[a * STRIDE];
            double ay = a == rem ? ky : verts[a * STRIDE + 1];
            double az = a == rem ? kz : verts[a * STRIDE + 2];
            double bx = b == rem ? kx : verts[b * STRIDE];
            double by = b == rem ? ky : verts[b * STRIDE + 1];
            double bz = b == rem ? kz : verts[b * STRIDE + 2];
            double cx = c == rem ? kx : verts[c * STRIDE];
            double cy = c == rem ? ky : verts[c * STRIDE + 1];
            double cz = c == rem ? kz : verts[c * STRIDE + 2];
            double e1x = bx - ax, e1y = by - ay, e1z = bz - az;
            double e2x = cx - ax, e2y = cy - ay, e2z = cz - az;
            double nx = e1y * e2z - e1z * e2y;
            double ny = e1z * e2x - e1x * e2z;
            double nz = e1x * e2y - e1y * e2x;
            if (nb[0] * nx + nb[1] * ny + nb[2] * nz < 0) {
                return true;
            }
        }
        return false;
    }

    private static double[] normal(float[] v, int a, int b, int c) {
        double ax = v[a * STRIDE], ay = v[a * STRIDE + 1], az = v[a * STRIDE + 2];
        double e1x = v[b * STRIDE] - ax, e1y = v[b * STRIDE + 1] - ay, e1z = v[b * STRIDE + 2] - az;
        double e2x = v[c * STRIDE] - ax, e2y = v[c * STRIDE + 1] - ay, e2z = v[c * STRIDE + 2] - az;
        return new double[]{ e1y * e2z - e1z * e2y, e1z * e2x - e1x * e2z, e1x * e2y - e1y * e2x };
    }

    private static void pushEdgeIfNew(PriorityQueue<Edge> heap, Set<Long> seen, float[] verts, double[][] q,
                                      long[] version, int a, int b) {
        long key = a < b ? ((long) a << 32) | (b & 0xffffffffL) : ((long) b << 32) | (a & 0xffffffffL);
        if (seen.add(key)) {
            heap.add(makeEdge(verts, q, version, a, b));
        }
    }

    private static Edge makeEdge(float[] verts, double[][] q, long[] version, int a, int b) {
        double[] qs = new double[10];
        add(qs, q[a]); add(qs, q[b]);
        double cost = Math.min(eval(qs, verts, a), eval(qs, verts, b));
        Edge e = new Edge();
        e.a = a; e.b = b; e.va = version[a]; e.vb = version[b]; e.cost = cost;
        return e;
    }

    // quadric of the plane through triangle (a,b,c): outer product of the normalized plane [nx,ny,nz,d]
    private static double[] faceQuadric(float[] v, int a, int b, int c) {
        double ax = v[a * STRIDE], ay = v[a * STRIDE + 1], az = v[a * STRIDE + 2];
        double e1x = v[b * STRIDE] - ax, e1y = v[b * STRIDE + 1] - ay, e1z = v[b * STRIDE + 2] - az;
        double e2x = v[c * STRIDE] - ax, e2y = v[c * STRIDE + 1] - ay, e2z = v[c * STRIDE + 2] - az;
        double nx = e1y * e2z - e1z * e2y;
        double ny = e1z * e2x - e1x * e2z;
        double nz = e1x * e2y - e1y * e2x;
        double len = Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (len < 1e-12) {
            return new double[10];
        }
        nx /= len; ny /= len; nz /= len;
        double d = -(nx * ax + ny * ay + nz * az);
        return new double[]{
                nx * nx, nx * ny, nx * nz, nx * d,
                ny * ny, ny * nz, ny * d,
                nz * nz, nz * d,
                d * d
        };
    }

    private static void add(double[] into, double[] other) {
        for (int i = 0; i < 10; i++) {
            into[i] += other[i];
        }
    }

    // v^T Q v for the homogeneous quadric (v = [x,y,z,1]) of vertex vi
    private static double eval(double[] q, float[] verts, int vi) {
        double x = verts[vi * STRIDE], y = verts[vi * STRIDE + 1], z = verts[vi * STRIDE + 2];
        return q[0] * x * x + 2 * q[1] * x * y + 2 * q[2] * x * z + 2 * q[3] * x
                + q[4] * y * y + 2 * q[5] * y * z + 2 * q[6] * y
                + q[7] * z * z + 2 * q[8] * z
                + q[9];
    }

    private static final class Edge implements Comparable<Edge> {
        int a, b;
        long va, vb;
        double cost;

        @Override
        public int compareTo(Edge o) {
            return Double.compare(cost, o.cost);
        }
    }
}

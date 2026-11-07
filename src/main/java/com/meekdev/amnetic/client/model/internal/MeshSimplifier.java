package com.meekdev.amnetic.client.model.internal;

import java.util.Arrays;
import java.util.PriorityQueue;

public final class MeshSimplifier {
    private static final int STRIDE = ModelIR.VERTEX_STRIDE_FLOATS;
    private static final byte FREE = 0;
    private static final byte SEAM = 1;
    private static final byte LOCKED = 2;
    private static final double SEAM_WEIGHT = 10;
    private static final int MIN_TRIANGLES = 8;

    public record Level(int[] indices, float error) {}

    private MeshSimplifier() {}

    public static int[] simplify(float[] verts, int[] indices, int targetTriangles) {
        return levels(verts, indices, new int[] {targetTriangles}, new float[] {Float.MAX_VALUE})[0].indices();
    }

    public static Level[] levels(float[] verts, int[] indices, int[] targets, float[] maxErrors) {
        Level[] out = new Level[targets.length];
        if (indices.length / 3 < MIN_TRIANGLES) {
            Arrays.fill(out, new Level(indices, 0f));
            return out;
        }
        new Run(verts, indices).levels(targets, maxErrors, out);
        return out;
    }

    private static final class Run {
        final float[] verts;
        final int triangles;
        final int[] corner;
        final int[] position;
        final int[] variant;
        final int[] standIn;
        final int positions;
        final boolean[] alive;
        final double[][] quadric;
        final byte[] kind;
        final int[] version;
        final boolean[] gone;
        final int[][] around;
        final int[] aroundCount;
        int living;
        double worst;

        Run(float[] verts, int[] indices) {
            this.verts = verts;
            this.triangles = indices.length / 3;
            this.corner = indices.clone();
            this.position = weld(verts);
            this.variant = twins(verts);
            int most = 0;
            for (int p : position) most = Math.max(most, p + 1);
            this.positions = most;
            this.standIn = new int[positions];
            for (int v = position.length - 1; v >= 0; v--) standIn[position[v]] = v;
            this.alive = new boolean[triangles];
            this.quadric = new double[positions][10];
            this.kind = new byte[positions];
            this.version = new int[positions];
            this.gone = new boolean[positions];
            this.around = new int[positions][];
            this.aroundCount = new int[positions];
            build();
        }

        private static int[] weld(float[] verts) {
            int count = verts.length / STRIDE;
            Integer[] order = new Integer[count];
            for (int i = 0; i < count; i++) order[i] = i;
            Arrays.sort(order, (a, b) -> {
                for (int axis = 0; axis < 3; axis++) {
                    int c = Float.compare(verts[a * STRIDE + axis], verts[b * STRIDE + axis]);
                    if (c != 0) return c;
                }
                return 0;
            });
            int[] out = new int[count];
            int id = -1;
            for (int i = 0; i < count; i++) {
                int v = order[i];
                if (i == 0 || !samePlace(verts, v, order[i - 1])) id++;
                out[v] = id;
            }
            return out;
        }

        private static int[] twins(float[] verts) {
            int count = verts.length / STRIDE;
            Integer[] order = new Integer[count];
            for (int i = 0; i < count; i++) order[i] = i;
            int[] fields = {0, 1, 2, 6, 7};
            java.util.Comparator<Integer> by = (a, b) -> {
                for (int field : fields) {
                    int c = Float.compare(verts[a * STRIDE + field], verts[b * STRIDE + field]);
                    if (c != 0) return c;
                }
                return 0;
            };
            Arrays.sort(order, by);
            int[] out = new int[count];
            int first = -1;
            for (int i = 0; i < count; i++) {
                int v = order[i];
                if (i == 0 || by.compare(v, order[i - 1]) != 0) first = v;
                out[v] = first;
            }
            return out;
        }

        private static boolean samePlace(float[] verts, int a, int b) {
            return verts[a * STRIDE] == verts[b * STRIDE] && verts[a * STRIDE + 1] == verts[b * STRIDE + 1]
                    && verts[a * STRIDE + 2] == verts[b * STRIDE + 2];
        }

        private void build() {
            int[] counts = new int[positions];
            for (int t = 0; t < triangles; t++) {
                int a = position[corner[t * 3]];
                int b = position[corner[t * 3 + 1]];
                int c = position[corner[t * 3 + 2]];
                alive[t] = a != b && b != c && c != a;
                if (!alive[t]) continue;
                living++;
                counts[a]++;
                counts[b]++;
                counts[c]++;
            }
            for (int p = 0; p < positions; p++) around[p] = new int[Math.max(4, counts[p])];
            int[] first = new int[positions];
            int[] second = new int[positions];
            Arrays.fill(first, -1);
            Arrays.fill(second, -1);
            Edges edges = new Edges(living * 3);
            for (int t = 0; t < triangles; t++) {
                if (!alive[t]) continue;
                double[] plane = plane(t);
                for (int k = 0; k < 3; k++) {
                    int v = variant[corner[t * 3 + k]];
                    int p = position[v];
                    add(around, aroundCount, p, t);
                    addTo(quadric[p], plane, 1);
                    if (first[p] == -1) first[p] = v;
                    else if (first[p] != v && second[p] == -1) second[p] = v;
                    else if (first[p] != v && second[p] != v) kind[p] = LOCKED;
                    int next = variant[corner[t * 3 + (k + 1) % 3]];
                    edges.add(t, p, position[next], v, next);
                }
            }
            for (int p = 0; p < positions; p++) {
                if (kind[p] != LOCKED) kind[p] = second[p] == -1 ? FREE : SEAM;
            }
            edges.mark(this);
        }

        void levels(int[] targets, float[] maxErrors, Level[] out) {
            PriorityQueue<Collapse> queue = new PriorityQueue<>();
            for (int t = 0; t < triangles; t++) {
                if (!alive[t]) continue;
                for (int k = 0; k < 3; k++) {
                    int a = position[corner[t * 3 + k]];
                    int b = position[corner[t * 3 + (k + 1) % 3]];
                    if (a < b) offer(queue, a, b);
                }
            }
            for (int level = 0; level < targets.length; level++) {
                double most = (double) maxErrors[level] * maxErrors[level];
                while (living > targets[level] && !queue.isEmpty()) {
                    Collapse next = queue.peek();
                    if (gone[next.from] || gone[next.to] || version[next.from] != next.fromVersion || version[next.to] != next.toVersion) {
                        queue.poll();
                        continue;
                    }
                    if (next.cost > most) break;
                    queue.poll();
                    if (!collapse(next.from, next.to)) continue;
                    worst = Math.max(worst, next.cost);
                    for (int i = 0; i < aroundCount[next.to]; i++) {
                        int t = around[next.to][i];
                        if (!alive[t]) continue;
                        for (int k = 0; k < 3; k++) {
                            int other = position[corner[t * 3 + k]];
                            if (other != next.to) offer(queue, next.to, other);
                        }
                    }
                }
                out[level] = new Level(snapshot(), (float) Math.sqrt(worst));
            }
        }

        private int[] snapshot() {
            int[] out = new int[living * 3];
            int at = 0;
            for (int t = 0; t < triangles; t++) {
                if (!alive[t]) continue;
                out[at++] = corner[t * 3];
                out[at++] = corner[t * 3 + 1];
                out[at++] = corner[t * 3 + 2];
            }
            return out;
        }

        private void offer(PriorityQueue<Collapse> queue, int a, int b) {
            double[] sum = new double[10];
            addTo(sum, quadric[a], 1);
            addTo(sum, quadric[b], 1);
            double intoB = kind[a] == LOCKED ? Double.MAX_VALUE : error(sum, b);
            double intoA = kind[b] == LOCKED ? Double.MAX_VALUE : error(sum, a);
            if (intoB == Double.MAX_VALUE && intoA == Double.MAX_VALUE) return;
            boolean toB = intoB <= intoA;
            int from = toB ? a : b;
            int to = toB ? b : a;
            queue.add(new Collapse(from, to, Math.max(0, toB ? intoB : intoA), version[from], version[to]));
        }

        private boolean collapse(int from, int to) {
            int[] partner = partners(from, to);
            if (partner == null || flips(from, to) || !linked(from, to)) return false;
            for (int i = 0; i < aroundCount[from]; i++) {
                int t = around[from][i];
                if (!alive[t]) continue;
                if (holds(t, to)) {
                    alive[t] = false;
                    living--;
                    continue;
                }
                for (int k = 0; k < 3; k++) {
                    int c = corner[t * 3 + k];
                    if (position[c] == from) corner[t * 3 + k] = partner[0] == variant[c] ? partner[1] : partner[3];
                }
                add(around, aroundCount, to, t);
            }
            addTo(quadric[to], quadric[from], 1);
            gone[from] = true;
            aroundCount[from] = 0;
            version[to]++;
            return true;
        }

        private boolean holds(int t, int p) {
            return position[corner[t * 3]] == p || position[corner[t * 3 + 1]] == p || position[corner[t * 3 + 2]] == p;
        }

        private int[] partners(int from, int to) {
            int[] pair = {-1, -1, -1, -1};
            for (int i = 0; i < aroundCount[from]; i++) {
                int t = around[from][i];
                if (!alive[t]) continue;
                int mine = -1;
                int theirs = -1;
                for (int k = 0; k < 3; k++) {
                    int c = corner[t * 3 + k];
                    if (position[c] == from) mine = variant[c];
                    if (position[c] == to) theirs = variant[c];
                }
                int slot = pair[0] == -1 || pair[0] == mine ? 0 : pair[2] == -1 || pair[2] == mine ? 2 : -1;
                if (slot < 0) return null;
                pair[slot] = mine;
                if (theirs == -1) continue;
                if (pair[slot + 1] != -1 && pair[slot + 1] != theirs) return null;
                pair[slot + 1] = theirs;
            }
            if (pair[0] == -1 || pair[1] == -1) return null;
            if (pair[2] != -1 && (pair[3] == -1 || pair[3] == pair[1])) return null;
            return pair;
        }

        private boolean flips(int from, int to) {
            for (int i = 0; i < aroundCount[from]; i++) {
                int t = around[from][i];
                if (!alive[t] || holds(t, to)) continue;
                double[] before = normal(t, -1, -1);
                double[] after = normal(t, from, to);
                double dot = before[0] * after[0] + before[1] * after[1] + before[2] * after[2];
                double lengths = Math.sqrt((before[0] * before[0] + before[1] * before[1] + before[2] * before[2])
                        * (after[0] * after[0] + after[1] * after[1] + after[2] * after[2]));
                if (dot <= 0.2 * lengths) return true;
            }
            return false;
        }

        private boolean linked(int from, int to) {
            int[] near = new int[8];
            int count = 0;
            int shared = 0;
            for (int i = 0; i < aroundCount[from]; i++) {
                int t = around[from][i];
                if (!alive[t]) continue;
                if (holds(t, to)) shared++;
                for (int k = 0; k < 3; k++) {
                    int p = position[corner[t * 3 + k]];
                    if (p == from || p == to) continue;
                    boolean known = false;
                    for (int n = 0; n < count && !known; n++) known = near[n] == p;
                    if (known) continue;
                    if (count == near.length) near = Arrays.copyOf(near, count * 2);
                    near[count++] = p;
                }
            }
            int common = 0;
            for (int n = 0; n < count; n++) {
                if (touches(to, near[n])) common++;
            }
            return common <= shared;
        }

        private boolean touches(int p, int other) {
            for (int i = 0; i < aroundCount[p]; i++) {
                int t = around[p][i];
                if (alive[t] && holds(t, other)) return true;
            }
            return false;
        }

        private double[] normal(int t, int moved, int onto) {
            int a = spot(corner[t * 3], moved, onto);
            int b = spot(corner[t * 3 + 1], moved, onto);
            int c = spot(corner[t * 3 + 2], moved, onto);
            double ax = verts[a * STRIDE];
            double ay = verts[a * STRIDE + 1];
            double az = verts[a * STRIDE + 2];
            double e1x = verts[b * STRIDE] - ax;
            double e1y = verts[b * STRIDE + 1] - ay;
            double e1z = verts[b * STRIDE + 2] - az;
            double e2x = verts[c * STRIDE] - ax;
            double e2y = verts[c * STRIDE + 1] - ay;
            double e2z = verts[c * STRIDE + 2] - az;
            return new double[] {e1y * e2z - e1z * e2y, e1z * e2x - e1x * e2z, e1x * e2y - e1y * e2x};
        }

        private int spot(int v, int moved, int onto) {
            return position[v] == moved ? standIn[onto] : v;
        }

        private double[] plane(int t) {
            double[] n = normal(t, -1, -1);
            int a = corner[t * 3];
            return quadricOf(n[0], n[1], n[2], verts[a * STRIDE], verts[a * STRIDE + 1], verts[a * STRIDE + 2]);
        }

        void seam(int t, int v, int next) {
            double[] n = normal(t, -1, -1);
            double ex = verts[next * STRIDE] - verts[v * STRIDE];
            double ey = verts[next * STRIDE + 1] - verts[v * STRIDE + 1];
            double ez = verts[next * STRIDE + 2] - verts[v * STRIDE + 2];
            double px = ey * n[2] - ez * n[1];
            double py = ez * n[0] - ex * n[2];
            double pz = ex * n[1] - ey * n[0];
            double[] q = quadricOf(px, py, pz, verts[v * STRIDE], verts[v * STRIDE + 1], verts[v * STRIDE + 2]);
            addTo(quadric[position[v]], q, SEAM_WEIGHT);
            addTo(quadric[position[next]], q, SEAM_WEIGHT);
        }

        private static double[] quadricOf(double nx, double ny, double nz, double x, double y, double z) {
            double length = Math.sqrt(nx * nx + ny * ny + nz * nz);
            if (length < 1e-20) return new double[10];
            nx /= length;
            ny /= length;
            nz /= length;
            double d = -(nx * x + ny * y + nz * z);
            return new double[] {nx * nx, nx * ny, nx * nz, nx * d, ny * ny, ny * nz, ny * d, nz * nz, nz * d, d * d};
        }

        private double error(double[] q, int p) {
            int v = standIn[p];
            double x = verts[v * STRIDE];
            double y = verts[v * STRIDE + 1];
            double z = verts[v * STRIDE + 2];
            return q[0] * x * x + 2 * q[1] * x * y + 2 * q[2] * x * z + 2 * q[3] * x
                    + q[4] * y * y + 2 * q[5] * y * z + 2 * q[6] * y
                    + q[7] * z * z + 2 * q[8] * z + q[9];
        }
    }

    private static final class Edges {
        final long[] keys;
        final int[] count;
        final int[] from;
        final int[] to;
        final boolean[] split;
        final int mask;
        final int[] added;
        int addedCount;

        Edges(int expected) {
            int size = Integer.highestOneBit(Math.max(16, expected)) << 2;
            keys = new long[size];
            Arrays.fill(keys, -1);
            count = new int[size];
            from = new int[size];
            to = new int[size];
            split = new boolean[size];
            mask = size - 1;
            added = new int[expected * 4];
        }

        void add(int t, int a, int b, int v, int next) {
            long key = a < b ? ((long) a << 32) | b : ((long) b << 32) | a;
            int slot = (int) (mix(key) & mask);
            while (keys[slot] != -1 && keys[slot] != key) slot = (slot + 1) & mask;
            if (keys[slot] == -1) {
                keys[slot] = key;
                from[slot] = v;
                to[slot] = next;
            } else if (from[slot] != next || to[slot] != v) {
                split[slot] = true;
            }
            count[slot]++;
            added[addedCount * 4] = slot;
            added[addedCount * 4 + 1] = t;
            added[addedCount * 4 + 2] = v;
            added[addedCount * 4 + 3] = next;
            addedCount++;
        }

        void mark(Run run) {
            for (int n = 0; n < addedCount; n++) {
                int slot = added[n * 4];
                int v = added[n * 4 + 2];
                int next = added[n * 4 + 3];
                if (count[slot] != 2) {
                    run.kind[run.position[v]] = LOCKED;
                    run.kind[run.position[next]] = LOCKED;
                } else if (split[slot]) {
                    run.seam(added[n * 4 + 1], v, next);
                }
            }
        }

        private static long mix(long key) {
            key ^= key >>> 33;
            key *= 0xff51afd7ed558ccdL;
            key ^= key >>> 33;
            return key;
        }
    }

    private record Collapse(int from, int to, double cost, int fromVersion, int toVersion) implements Comparable<Collapse> {
        @Override
        public int compareTo(Collapse other) {
            return Double.compare(cost, other.cost);
        }
    }

    private static void add(int[][] lists, int[] counts, int p, int t) {
        if (counts[p] == lists[p].length) lists[p] = Arrays.copyOf(lists[p], lists[p].length * 2);
        lists[p][counts[p]++] = t;
    }

    private static void addTo(double[] into, double[] q, double weight) {
        for (int i = 0; i < 10; i++) into[i] += q[i] * weight;
    }
}

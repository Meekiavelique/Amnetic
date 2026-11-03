package com.meekdev.amnetic.client.light.internal;

import org.joml.Matrix4fc;

public final class LightTileBounds {
    private static final double MIN_W = 1e-6;
    private static final int CORNERS = 8;
    private static final ThreadLocal<Projection> PROJECTION = ThreadLocal.withInitial(Projection::new);

    private LightTileBounds() {}

    public static void project(Matrix4fc matrix, float x, float y, float z, float radius, int columns, int rows, int[] bounds) {
        Projection projected = PROJECTION.get();
        projected.reset();
        for (int corner = 0; corner < CORNERS; corner++) {
            double a = x + ((corner & 1) == 0 ? -(double) radius : radius);
            double b = y + ((corner & 2) == 0 ? -(double) radius : radius);
            double c = z + ((corner & 4) == 0 ? -(double) radius : radius);
            double clipX = matrix.m00() * a + matrix.m10() * b + matrix.m20() * c + matrix.m30();
            double clipY = matrix.m01() * a + matrix.m11() * b + matrix.m21() * c + matrix.m31();
            double w = matrix.m03() * a + matrix.m13() * b + matrix.m23() * c + matrix.m33();
            projected.set(corner, clipX, clipY, w);
            if (!Double.isFinite(w) || !Double.isFinite(clipX) || !Double.isFinite(clipY)) {
                full(columns, rows, bounds);
                return;
            }
            if (w >= MIN_W) projected.add(clipX, clipY, w);
        }
        for (int corner = 0; corner < CORNERS; corner++) {
            for (int axis = 1; axis <= 4; axis <<= 1) {
                if ((corner & axis) == 0) projected.clipEdge(corner, corner | axis);
            }
        }
        if (projected.minX > 1 || projected.maxX < -1 || projected.minY > 1 || projected.maxY < -1) {
            bounds[0] = 0;
            bounds[1] = 0;
            bounds[2] = -1;
            bounds[3] = -1;
            return;
        }
        bounds[0] = Math.max(0, (int) Math.floor((Math.max(-1, projected.minX) * .5 + .5) * columns) - 1);
        bounds[1] = Math.max(0, (int) Math.floor((Math.max(-1, projected.minY) * .5 + .5) * rows) - 1);
        bounds[2] = Math.min(columns - 1, (int) Math.floor((Math.min(1, projected.maxX) * .5 + .5) * columns) + 1);
        bounds[3] = Math.min(rows - 1, (int) Math.floor((Math.min(1, projected.maxY) * .5 + .5) * rows) + 1);
    }

    public static void full(int columns, int rows, int[] bounds) {
        bounds[0] = 0;
        bounds[1] = 0;
        bounds[2] = columns - 1;
        bounds[3] = rows - 1;
    }

    private static final class Projection {
        final double[] x = new double[CORNERS];
        final double[] y = new double[CORNERS];
        final double[] w = new double[CORNERS];
        double minX;
        double minY;
        double maxX;
        double maxY;

        void reset() {
            minX = Double.POSITIVE_INFINITY;
            minY = Double.POSITIVE_INFINITY;
            maxX = Double.NEGATIVE_INFINITY;
            maxY = Double.NEGATIVE_INFINITY;
        }

        void set(int corner, double clipX, double clipY, double clipW) {
            x[corner] = clipX;
            y[corner] = clipY;
            w[corner] = clipW;
        }

        void clipEdge(int from, int to) {
            double wFrom = w[from];
            double wTo = w[to];
            if ((wFrom >= MIN_W) == (wTo >= MIN_W)) return;
            double t = (MIN_W - wFrom) / (wTo - wFrom);
            add(x[from] + t * (x[to] - x[from]), y[from] + t * (y[to] - y[from]), MIN_W);
        }

        void add(double px, double py, double pw) {
            px /= pw;
            py /= pw;
            minX = Math.min(minX, px);
            maxX = Math.max(maxX, px);
            minY = Math.min(minY, py);
            maxY = Math.max(maxY, py);
        }
    }
}

package com.meekdev.amnetic.client.model.internal.parse;

public final class CoplanarSeparation {
    public static final double EPSILON = 0.002;

    private CoplanarSeparation() {}

    public static void separate(double[][] boxes) {
        if (boxes == null || boxes.length == 0) {
            return;
        }
        for (double[] box : boxes) {
            for (int axis = 0; axis < 3; axis++) {
                double lo = Math.min(box[axis], box[axis + 3]);
                double hi = Math.max(box[axis], box[axis + 3]);
                if (hi - lo < EPSILON) {
                    double mid = (lo + hi) * 0.5;
                    lo = mid - EPSILON * 0.5;
                    hi = mid + EPSILON * 0.5;
                }
                box[axis] = lo;
                box[axis + 3] = hi;
            }
        }

        for (int i = 0; i < boxes.length; i++) {
            for (int j = i + 1; j < boxes.length; j++) {
                double[] a = boxes[i];
                double[] b = boxes[j];
                for (int axis = 0; axis < 3; axis++) {
                    int u = (axis + 1) % 3;
                    int v = (axis + 2) % 3;
                    if (!overlaps(a[u], a[u + 3], b[u], b[u + 3])
                            || !overlaps(a[v], a[v + 3], b[v], b[v + 3])) {
                        continue;
                    }
                    if (coincident(a[axis], b[axis])) {
                        b[axis] += EPSILON;
                    }
                    if (coincident(a[axis + 3], b[axis + 3])) {
                        b[axis + 3] += EPSILON;
                    }
                    if (coincident(a[axis + 3], b[axis])) {
                        b[axis] += EPSILON;
                    }
                }
            }
        }
    }

    private static boolean overlaps(double aLo, double aHi, double bLo, double bHi) {
        return aLo < bHi - 1.0e-9 && bLo < aHi - 1.0e-9;
    }

    private static boolean coincident(double a, double b) {
        return Math.abs(a - b) < 1.0e-9;
    }
}

package com.meekdev.amnetic.client.light.internal;


public final class LightDepthSlices {
    public static final int COUNT = 16;
    private static final double LOG_2 = Math.log(2);

    private LightDepthSlices() {}

    public static int index(double depth) {
        return Math.max(0, Math.min(COUNT - 1, (int)Math.floor(Math.log(Math.max(1, depth)) / LOG_2 * 2)));
    }

    public static int first(double center, double radius) {
        return index(center - radius - margin(center, radius));
    }

    public static int last(double center, double radius) {
        return index(center + radius + margin(center, radius));
    }

    private static double margin(double center, double radius) {
        return Math.max(.001, Math.max(Math.abs(center), radius) * .0001);
    }
}

package com.meekdev.amnetic.client.model.internal;

import com.meekdev.amnetic.client.model.ModelStats;

// counts what the model pass sent to the graphics card, read back as the last whole frame
public final class DrawCounts {

    public static final int LEVELS = 4;

    private static boolean counting;
    private static int draws;
    private static int shadowDraws;
    private static int instances;
    private static long triangles;
    private static final int[] levels = new int[LEVELS];
    private static volatile ModelStats last = new ModelStats(0, 0, 0, 0, new int[LEVELS]);

    private DrawCounts() {}

    static void begin() {
        counting = true;
        draws = 0;
        instances = 0;
        triangles = 0;
        java.util.Arrays.fill(levels, 0);
    }

    static void end() {
        if (!counting) return;
        counting = false;
        last = new ModelStats(draws, shadowDraws, instances, triangles, levels.clone());
        shadowDraws = 0;
    }

    static void draw(int count, int indices, int level) {
        if (!counting) return;
        draws++;
        instances += count;
        triangles += (long) count * (indices / 3);
        levels[Math.min(Math.max(level, 0), LEVELS - 1)] += count;
    }

    // shadow passes run between two main passes, they land in the next frame's numbers
    static void shadow() {
        shadowDraws++;
    }

    public static ModelStats last() {
        return last;
    }
}

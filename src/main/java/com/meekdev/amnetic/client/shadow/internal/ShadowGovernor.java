package com.meekdev.amnetic.client.shadow.internal;

final class ShadowGovernor {
    private static final int[] RESOLUTION_SHIFT = {0, 0, 1, 1, 1, 2, 2};
    private static final int[] CASCADES_DROPPED = {0, 0, 0, 1, 1, 2, 3};
    private static final float[] DISTANCE = {1f, 0.75f, 0.75f, 0.75f, 0.6f, 0.6f, 0.5f};
    private static final int STEPS = DISTANCE.length - 1;
    private static final float SMOOTHING = 0.1f;
    private static final int OVER_FRAMES = 10;
    private static final int UNDER_FRAMES = 90;
    private static final int MAX_WAIT = 90 * 32;
    private static final float ROOM = 0.6f;

    private int step;
    private float average = -1f;
    private int over;
    private int under;
    private int wait = UNDER_FRAMES;
    private int sinceRaise = Integer.MAX_VALUE;

    int step() { return step; }

    boolean observe(float ms, float budget) {
        if (budget <= 0f) {
            boolean was = step != 0;
            step = 0;
            average = -1f;
            over = under = 0;
            wait = UNDER_FRAMES;
            return was;
        }
        if (sinceRaise < Integer.MAX_VALUE) sinceRaise++;
        average = average < 0f ? ms : average + (ms - average) * SMOOTHING;
        if (average > budget) {
            under = 0;
            if (++over < OVER_FRAMES || step >= STEPS) return false;
            if (sinceRaise < UNDER_FRAMES * 2) wait = Math.min(wait * 2, MAX_WAIT);
            step++;
            settle();
            return true;
        }
        over = 0;
        if (average >= budget * ROOM) {
            under = 0;
            return false;
        }
        if (++under < wait || step == 0) return false;
        step--;
        sinceRaise = 0;
        settle();
        return true;
    }

    private void settle() {
        average = -1f;
        over = under = 0;
    }

    int resolution(int set) {
        return Math.min(set, Math.max(512, set >> RESOLUTION_SHIFT[step]));
    }

    int cascades(int set) {
        return Math.max(1, set - CASCADES_DROPPED[step]);
    }

    float distance(float set) {
        return Math.min(set, Math.max(16f, set * DISTANCE[step]));
    }
}

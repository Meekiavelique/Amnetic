package com.meekdev.amnetic.client.light.internal;

import java.util.Arrays;

public final class LightClusterLists {
    private static final int REGION_STRIDE = 6;
    private static final int BITS_PER_WORD = 64;

    private long[] events = new long[0];
    private long[] mask = new long[0];
    private long[] previous = new long[0];
    private int[] entries = new int[0];
    private int length;
    private int references;

    public void build(int columns, int rows, int slices, int[] regions, int lights) {
        int clusters = columns * rows * slices;
        int words = (lights + BITS_PER_WORD - 1) / BITS_PER_WORD;
        int eventLength = (columns + 1) * rows * slices * words;
        if (events.length < eventLength) {
            events = new long[eventLength];
        } else {
            Arrays.fill(events, 0, eventLength, 0L);
        }
        if (mask.length != words) {
            mask = new long[words];
            previous = new long[words];
        }
        for (int light = 0; light < lights; light++) markLight(regions, light, columns, rows, words);
        ensure(clusters + 1);
        int next = clusters;
        int last = -1;
        int count = 0;
        references = 0;
        for (int row = 0; row < rows * slices; row++) {
            Arrays.fill(mask, 0L);
            for (int x = 0; x < columns; x++) {
                int at = (row * (columns + 1) + x) * words;
                boolean same = last >= 0;
                for (int w = 0; w < words; w++) {
                    mask[w] ^= events[at + w];
                    same &= mask[w] == previous[w];
                }
                if (!same) {
                    count = 0;
                    for (long bits : mask) count += Long.bitCount(bits);
                    ensure(next + count + 1);
                    last = next;
                    entries[next++] = count;
                    next = writeMask(next, words);
                }
                entries[row * columns + x] = last;
                references += count;
            }
        }
        length = next;
    }

    private void markLight(int[] regions, int light, int columns, int rows, int words) {
        int at = light * REGION_STRIDE;
        int min = regions[at];
        int max = regions[at + 2];
        if (min > max) return;
        long bit = 1L << (light & (BITS_PER_WORD - 1));
        int word = light >>> 6;
        for (int z = regions[at + 4]; z <= regions[at + 5]; z++) {
            for (int y = regions[at + 1]; y <= regions[at + 3]; y++) {
                int row = (y + z * rows) * (columns + 1) * words;
                events[row + min * words + word] ^= bit;
                events[row + (max + 1) * words + word] ^= bit;
            }
        }
    }

    private int writeMask(int next, int words) {
        for (int w = 0; w < words; w++) {
            long bits = mask[w];
            while (bits != 0) {
                entries[next++] = w * BITS_PER_WORD + Long.numberOfTrailingZeros(bits);
                bits &= bits - 1;
            }
            previous[w] = mask[w];
        }
        return next;
    }

    private void ensure(int capacity) {
        if (entries.length < capacity) {
            entries = Arrays.copyOf(entries, Math.max(capacity, entries.length + entries.length / 2));
        }
    }

    public int[] entries() {
        return entries;
    }

    public int length() {
        return length;
    }

    public int references() {
        return references;
    }

    public void clear() {
        events = new long[0];
        mask = events;
        previous = events;
        entries = new int[0];
        length = 0;
        references = 0;
    }
}

package com.meekdev.amnetic.client.content.font;

import com.meekdev.amnetic.client.content.Bounded;
import com.meekdev.amnetic.client.content.RejectedContentException;

final class CmapCheck {
    private static final int PLATFORM_UNICODE = 0;
    private static final int PLATFORM_MICROSOFT = 3;
    private static final int MICROSOFT_BMP = 1;
    private static final int MICROSOFT_FULL = 10;
    private static final int TABLE_COUNT_AT = 2;
    private static final int RECORDS_AT = 4;
    private static final int ENCODING_AT = 2;
    private static final int OFFSET_AT = 4;
    private static final int BYTE_FORMAT = 0;
    private static final int HIGH_BYTE_FORMAT = 2;
    private static final int SEGMENT_FORMAT = 4;
    private static final int TRIMMED_FORMAT = 6;
    private static final int GROUP_FORMAT = 12;
    private static final int MANY_TO_ONE_FORMAT = 13;
    private static final int RECORD = 8;
    private static final int BYTE_MAP = 6 + 256;
    private static final int SEGMENTS_AT = 6;
    private static final int SEARCH_RANGE_AT = 8;
    private static final int ENTRY_SELECTOR_AT = 10;
    private static final int RANGE_SHIFT_AT = 12;
    private static final int SEGMENT_ARRAYS_AT = 14;
    private static final int TRIMMED_COUNT_AT = 8;
    private static final int TRIMMED_ENTRIES_AT = 10;
    private static final int GROUPS_COUNT_AT = 12;
    private static final int GROUPS_AT = 16;
    private static final int GROUP = 12;

    private CmapCheck() {}

    static void check(FontSpan cmap) {
        int count = cmap.u16(TABLE_COUNT_AT);
        cmap.need(RECORDS_AT, (long) count * RECORD);
        long chosen = -1;
        for (int i = 0; i < count; i++) {
            int record = RECORDS_AT + i * RECORD;
            int platform = cmap.u16(record);
            int encoding = cmap.u16(record + ENCODING_AT);
            boolean unicode = platform == PLATFORM_UNICODE
                    || platform == PLATFORM_MICROSOFT && (encoding == MICROSOFT_BMP || encoding == MICROSOFT_FULL);
            if (unicode) chosen = cmap.u32(record + OFFSET_AT);
        }
        Bounded.require(chosen >= 0, "the font has no unicode character map");
        FontSpan map = cmap.from(chosen);
        switch (map.u16(0)) {
            case BYTE_FORMAT -> map.need(0, BYTE_MAP);
            case SEGMENT_FORMAT -> segments(map);
            case TRIMMED_FORMAT -> map.need(TRIMMED_ENTRIES_AT, map.u16(TRIMMED_COUNT_AT) * 2L);
            case GROUP_FORMAT, MANY_TO_ONE_FORMAT -> map.need(GROUPS_AT, map.u32(GROUPS_COUNT_AT) * GROUP);
            case HIGH_BYTE_FORMAT -> throw new RejectedContentException("the font's character map is format 2, which cannot be read");
            default -> {
            }
        }
    }

    private static void segments(FontSpan map) {
        int segments = map.u16(SEGMENTS_AT) >> 1;
        Bounded.require(segments > 0, "the font's character map has no segments");
        int range = Integer.highestOneBit(segments);
        Bounded.require(map.u16(SEARCH_RANGE_AT) == range * 2 && map.u16(ENTRY_SELECTOR_AT) == Integer.numberOfTrailingZeros(range)
                && map.u16(RANGE_SHIFT_AT) == segments * 2 - range * 2, "the font's character map has a bad search header");
        int ends = SEGMENT_ARRAYS_AT;
        int starts = ends + segments * 2 + 2;
        int offsets = starts + segments * 4;
        map.need(ends, segments * 8L + 2);
        for (int s = 0; s < segments; s++) {
            int start = map.u16(starts + s * 2);
            int end = map.u16(ends + s * 2);
            int offset = map.u16(offsets + s * 2);
            if (offset != 0 && start <= end) map.need(offsets + s * 2L + offset + (end - start) * 2L, 2);
        }
    }
}

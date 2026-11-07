package com.meekdev.amnetic.client.content.font;

// the kern table and the pair adjustments in GPOS, read only where stb_truetype reads them for kerning
final class KerningCheck {

    private static final int KERN_PAIRS_AT = 10;
    private static final int KERN_FIRST_PAIR_AT = 18;
    private static final int KERN_PAIR = 6;
    private static final int KERN_HORIZONTAL_AT = 8;
    private static final int KERN_TABLES_AT = 2;
    private static final int GPOS_HEADER = 10;
    private static final int GPOS_MINOR_AT = 2;
    private static final int GPOS_LOOKUPS_AT = 8;
    private static final int OFFSETS_AT = 2;
    private static final int COVERAGE_AT = 2;
    private static final int FIRST_VALUE_FORMAT_AT = 4;
    private static final int SECOND_VALUE_FORMAT_AT = 6;
    private static final int COUNT_AT = 2;
    private static final int LIST_AT = 4;
    private static final int CLASS_COUNT_AT = 4;
    private static final int CLASSES_AT = 6;
    private static final int RANGE_END_AT = 2;
    private static final int RANGE_INDEX_AT = 4;
    private static final int PAIR_ADJUSTMENT = 2;
    private static final int LOOKUP_HEADER = 6;
    private static final int SUBTABLES_AT = 4;
    private static final int PAIR_HEADER = 10;
    private static final int ADVANCE_ONLY = 4;
    private static final int PAIR_SETS_AT = 8;
    private static final int PAIR_SET_OFFSETS_AT = 10;
    private static final int PAIR_VALUE = 4;
    private static final int CLASS_PAIR_HEADER = 16;
    private static final int CLASS_DEFS_AT = 8;
    private static final int CLASS_COUNTS_AT = 12;
    private static final int RANGE = 6;
    private static final int PAIR_SETS = 1;
    private static final int CLASS_PAIRS = 2;
    private static final int GLYPH_LIST = 1;
    private static final int GLYPH_RANGES = 2;

    private KerningCheck() {}

    static void check(FontSpan kern, FontSpan gpos) {
        if (gpos != null) gpos(gpos);
        else if (kern != null) kern(kern);
    }

    private static void kern(FontSpan kern) {
        if (kern.u16(KERN_TABLES_AT) < 1 || kern.u16(KERN_HORIZONTAL_AT) != 1) return;
        kern.need(KERN_FIRST_PAIR_AT, (long) kern.u16(KERN_PAIRS_AT) * KERN_PAIR);
    }

    private static void gpos(FontSpan gpos) {
        gpos.need(0, GPOS_HEADER);
        if (gpos.u16(0) != 1 || gpos.u16(GPOS_MINOR_AT) != 0) return;
        FontSpan lookups = gpos.from(gpos.u16(GPOS_LOOKUPS_AT));
        int count = lookups.u16(0);
        for (int i = 0; i < count; i++) {
            FontSpan lookup = lookups.from(lookups.u16(OFFSETS_AT + i * 2));
            lookup.need(0, LOOKUP_HEADER);
            int subtables = lookup.u16(SUBTABLES_AT);
            if (lookup.u16(0) != PAIR_ADJUSTMENT) continue;
            for (int s = 0; s < subtables; s++) pairs(lookup.from(lookup.u16(LOOKUP_HEADER + s * 2)));
        }
    }

    private static void pairs(FontSpan table) {
        table.need(0, PAIR_HEADER);
        int covered = coverage(table.from(table.u16(COVERAGE_AT)));
        if (covered == 0 || table.u16(FIRST_VALUE_FORMAT_AT) != ADVANCE_ONLY || table.u16(SECOND_VALUE_FORMAT_AT) != 0) return;
        switch (table.u16(0)) {
            case PAIR_SETS -> {
                table.need(PAIR_SET_OFFSETS_AT, covered * 2L);
                int sets = Math.min(covered, table.u16(PAIR_SETS_AT));
                for (int i = 0; i < sets; i++) {
                    FontSpan set = table.from(table.u16(PAIR_SET_OFFSETS_AT + i * 2));
                    set.need(COUNT_AT, (long) set.u16(0) * PAIR_VALUE);
                }
            }
            case CLASS_PAIRS -> {
                classes(table.from(table.u16(CLASS_DEFS_AT)));
                classes(table.from(table.u16(CLASS_DEFS_AT + 2)));
                table.need(CLASS_PAIR_HEADER, 2L * table.u16(CLASS_COUNTS_AT) * table.u16(CLASS_COUNTS_AT + 2));
            }
            default -> {
            }
        }
    }

    // how many coverage indices the table can hand out, the highest one plus one
    private static int coverage(FontSpan coverage) {
        int count = coverage.u16(COUNT_AT);
        return switch (coverage.u16(0)) {
            case GLYPH_LIST -> {
                coverage.need(LIST_AT, count * 2L);
                yield count;
            }
            case GLYPH_RANGES -> {
                coverage.need(LIST_AT, (long) count * RANGE);
                int highest = 0;
                for (int r = 0; r < count; r++) {
                    int at = LIST_AT + r * RANGE;
                    int start = coverage.u16(at);
                    int end = coverage.u16(at + RANGE_END_AT);
                    if (end >= start) highest = Math.max(highest, coverage.u16(at + RANGE_INDEX_AT) + end - start + 1);
                }
                yield highest;
            }
            default -> 0;
        };
    }

    private static void classes(FontSpan definition) {
        switch (definition.u16(0)) {
            case GLYPH_LIST -> definition.need(CLASSES_AT, definition.u16(CLASS_COUNT_AT) * 2L);
            case GLYPH_RANGES -> definition.need(LIST_AT, (long) definition.u16(COUNT_AT) * RANGE);
            default -> {
            }
        }
    }
}

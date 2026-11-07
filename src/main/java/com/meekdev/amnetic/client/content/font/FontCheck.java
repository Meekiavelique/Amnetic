package com.meekdev.amnetic.client.content.font;

import com.meekdev.amnetic.client.content.Bounded;
import com.meekdev.amnetic.client.content.ContentLimits;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public final class FontCheck {
    private static final long TRUETYPE = 0x00010000L;
    private static final long APPLE_TRUETYPE = 0x74727565L;
    private static final long OPENTYPE_CFF = 0x4f54544fL;
    private static final int TABLE_COUNT_AT = 4;
    private static final int DIRECTORY = 12;
    private static final int RECORD = 16;
    private static final int TAG = 4;
    private static final int OFFSET_AT = 8;
    private static final int LENGTH_AT = 12;
    private static final int HEAD_BYTES = 54;
    private static final int LOCA_FORMAT_AT = 50;
    private static final int MAXP_BYTES = 6;
    private static final int GLYPHS_AT = 4;
    private static final int HHEA_BYTES = 36;
    private static final int LONG_METRICS_AT = 34;
    private static final int LONG_METRIC = 4;
    private static final int SHORT_METRIC = 2;

    private FontCheck() {}

    public static void check(byte[] font) {
        Bounded.require(font.length <= ContentLimits.FONT_BYTES, "a font larger than " + ContentLimits.FONT_BYTES + " bytes");
        FontSpan file = new FontSpan(font, 0, font.length, "directory");
        long version = file.u32(0);
        Bounded.require(version == TRUETYPE || version == APPLE_TRUETYPE || version == OPENTYPE_CFF,
                "not a single truetype or opentype font");
        Map<String, FontSpan> tables = tables(file);

        FontSpan head = required(tables, "head");
        head.need(0, HEAD_BYTES);
        int glyphs = required(tables, "maxp").slice(0, MAXP_BYTES).u16(GLYPHS_AT);
        FontSpan hhea = required(tables, "hhea");
        hhea.need(0, HHEA_BYTES);
        int longMetrics = hhea.u16(LONG_METRICS_AT);
        Bounded.require(longMetrics >= 1 && longMetrics <= glyphs, "the font's hhea table counts " + longMetrics + " metrics for " + glyphs + " glyphs");
        required(tables, "hmtx").need(0, (long) longMetrics * LONG_METRIC + (long) (glyphs - longMetrics) * SHORT_METRIC);

        if (tables.containsKey("glyf")) {
            int locaFormat = head.s16(LOCA_FORMAT_AT);
            GlyphCheck.check(required(tables, "loca"), tables.get("glyf"), glyphs, locaFormat);
        } else {
            required(tables, "CFF ");
        }
        CmapCheck.check(required(tables, "cmap"));
        KerningCheck.check(tables.get("kern"), tables.get("GPOS"));
    }

    private static Map<String, FontSpan> tables(FontSpan file) {
        int count = file.u16(TABLE_COUNT_AT);
        Bounded.require(count > 0 && count <= ContentLimits.FONT_TABLES, "a font with " + count + " tables");
        file.need(DIRECTORY, (long) count * RECORD);
        Map<String, FontSpan> tables = new HashMap<>();
        for (int i = 0; i < count; i++) {
            int at = DIRECTORY + i * RECORD;
            String tag = new String(file.data(), at, TAG, StandardCharsets.ISO_8859_1);
            long offset = file.u32(at + OFFSET_AT);
            long length = file.u32(at + LENGTH_AT);
            Bounded.require(offset + length <= file.length(), "the font's " + tag + " table lies past the end of the file");
            tables.putIfAbsent(tag, new FontSpan(file.data(), (int) offset, (int) length, tag.strip()));
        }
        return tables;
    }

    private static FontSpan required(Map<String, FontSpan> tables, String tag) {
        FontSpan table = tables.get(tag);
        Bounded.require(table != null, "the font has no " + tag.strip() + " table");
        return table;
    }
}

package com.meekdev.amnetic.client.content.font;

import com.meekdev.amnetic.client.content.Bounded;
import com.meekdev.amnetic.client.content.ContentLimits;
import java.util.Arrays;

// every truetype outline stb_truetype could walk, and that composite glyphs never loop or nest too deep
final class GlyphCheck {

    private static final int SHORT_LOCA = 0;
    private static final int LONG_LOCA = 1;
    private static final int HEADER = 10;
    // stb_truetype sets aside two more vertices for each contour it closes
    private static final int VERTICES_PER_CONTOUR = 2;
    private static final int X_SHORT = 0x02;
    private static final int Y_SHORT = 0x04;
    private static final int REPEAT = 0x08;
    private static final int X_SAME = 0x10;
    private static final int Y_SAME = 0x20;
    private static final int ARGS_ARE_WORDS = 0x0001;
    private static final int HAS_SCALE = 0x0008;
    private static final int MORE_COMPONENTS = 0x0020;
    private static final int HAS_XY_SCALE = 0x0040;
    private static final int HAS_TWO_BY_TWO = 0x0080;
    // flags and glyph index, then two offsets as bytes or as words
    private static final int COMPONENT_HEADER = 4;
    private static final int BYTE_ARGS = 2;
    private static final int WORD_ARGS = 4;
    private static final int UNCOUNTED = -1;
    private static final int COUNTING = -2;

    private final FontSpan glyf;
    private final long[] starts;
    private final int[] points;
    private final int[] nesting;

    private GlyphCheck(FontSpan glyf, long[] starts) {
        this.glyf = glyf;
        this.starts = starts;
        this.points = new int[starts.length - 1];
        this.nesting = new int[starts.length - 1];
        Arrays.fill(points, UNCOUNTED);
    }

    static void check(FontSpan loca, FontSpan glyf, int glyphs, int format) {
        Bounded.require(format == SHORT_LOCA || format == LONG_LOCA, "the font's head table names loca format " + format);
        long[] starts = new long[glyphs + 1];
        for (int i = 0; i <= glyphs; i++) {
            starts[i] = format == SHORT_LOCA ? loca.u16(i * 2) * 2L : loca.u32(i * 4);
            Bounded.require(starts[i] <= glyf.length() && (i == 0 || starts[i] >= starts[i - 1]),
                    "the font's loca table points glyph " + i + " outside its glyf table");
        }
        GlyphCheck check = new GlyphCheck(glyf, starts);
        for (int glyph = 0; glyph < glyphs; glyph++) check.points(glyph, 0);
    }

    // depth is how many composite glyphs hold this one, nesting how many it holds below itself
    private int points(int glyph, int depth) {
        if (glyph >= points.length) return 0;
        Bounded.require(points[glyph] != COUNTING, "the font's glyph " + glyph + " is made of itself");
        if (points[glyph] == UNCOUNTED) count(glyph, depth);
        requireShallow(depth + nesting[glyph]);
        return points[glyph];
    }

    private static void requireShallow(int depth) {
        Bounded.require(depth <= ContentLimits.FONT_COMPOSITE_DEPTH,
                "the font nests composite glyphs over " + ContentLimits.FONT_COMPOSITE_DEPTH + " deep");
    }

    private void count(int glyph, int depth) {
        long length = starts[glyph + 1] - starts[glyph];
        if (length == 0) {
            points[glyph] = 0;
            return;
        }
        FontSpan outline = glyf.slice(starts[glyph], length);
        outline.need(0, HEADER);
        int contours = outline.s16(0);
        points[glyph] = COUNTING;
        int counted = contours > 0 ? simple(outline, contours) : contours < 0 ? composite(glyph, outline, depth) : 0;
        Bounded.require(counted <= ContentLimits.FONT_GLYPH_POINTS, "the font's glyph " + glyph + " has over "
                + ContentLimits.FONT_GLYPH_POINTS + " points");
        points[glyph] = counted;
    }

    private static int simple(FontSpan outline, int contours) {
        int last = -1;
        for (int c = 0; c < contours; c++) {
            int end = outline.u16(HEADER + c * 2);
            Bounded.require(end > last, "a glyph's contours end out of order");
            last = end;
        }
        int count = last + 1;
        int at = HEADER + contours * 2;
        at += 2 + outline.u16(at);
        long coordinates = 0;
        for (int point = 0; point < count; ) {
            int flags = outline.u8(at++);
            int repeat = (flags & REPEAT) != 0 ? outline.u8(at++) : 0;
            int covered = Math.min(1 + repeat, count - point);
            coordinates += (long) covered * (width(flags, X_SHORT, X_SAME) + width(flags, Y_SHORT, Y_SAME));
            point += covered;
        }
        outline.need(at, coordinates);
        return count + VERTICES_PER_CONTOUR * contours;
    }

    private static int width(int flags, int shortBit, int sameBit) {
        if ((flags & shortBit) != 0) return 1;
        return (flags & sameBit) != 0 ? 0 : 2;
    }

    private int composite(int glyph, FontSpan outline, int depth) {
        requireShallow(depth + 1);
        int at = HEADER;
        long total = 0;
        int flags;
        do {
            flags = outline.u16(at);
            int part = outline.u16(at + Short.BYTES);
            at += COMPONENT_HEADER + ((flags & ARGS_ARE_WORDS) != 0 ? WORD_ARGS : BYTE_ARGS);
            if ((flags & HAS_SCALE) != 0) at += Short.BYTES;
            else if ((flags & HAS_XY_SCALE) != 0) at += 2 * Short.BYTES;
            else if ((flags & HAS_TWO_BY_TWO) != 0) at += 4 * Short.BYTES;
            outline.need(at, 0);
            total += points(part, depth + 1);
            if (part < nesting.length) nesting[glyph] = Math.max(nesting[glyph], 1 + nesting[part]);
            Bounded.require(total <= ContentLimits.FONT_GLYPH_POINTS, "a composite glyph has over "
                    + ContentLimits.FONT_GLYPH_POINTS + " points");
        } while ((flags & MORE_COMPONENTS) != 0);
        return (int) total;
    }
}

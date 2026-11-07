package com.meekdev.amnetic.client.content.font;

import com.meekdev.amnetic.client.content.Hostile;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

// fonts built table by table, a sound one that passes and broken ones stb_truetype would read out of bounds
public final class FontCheckRegression {

    private static final int COMPOSITE = -1;

    private FontCheckRegression() {}

    public static void verify() throws IOException {
        FontCheck.check(font(simpleGlyph()));
        System.out.println("PASS: a sound font passes");
        Hostile.refused("font table past the end of the file", "past the end of the file", () -> FontCheck.check(tablePastEnd()));
        Hostile.refused("composite glyph made of itself", "made of itself", () -> FontCheck.check(font(selfComposite())));
        Hostile.refused("glyph whose points run past its outline", "points past its end", () -> FontCheck.check(font(overlongGlyph())));
        Hostile.refused("font with no tables", "0 tables", () -> FontCheck.check(directory(Map.of())));
    }

    private static byte[] font(byte[] glyph) throws IOException {
        Map<String, byte[]> tables = new LinkedHashMap<>();
        tables.put("head", head());
        tables.put("maxp", new Bytes().u32(0x00005000).u16(1).done());
        tables.put("hhea", new Bytes().zeros(34).u16(1).done());
        tables.put("hmtx", new Bytes().u16(500).u16(0).done());
        tables.put("loca", new Bytes().u32(0).u32(glyph.length).done());
        tables.put("glyf", glyph);
        tables.put("cmap", cmap());
        return directory(tables);
    }

    private static byte[] head() throws IOException {
        return new Bytes().zeros(50).u16(1).u16(0).done();
    }

    // one format 4 map with only the closing segment
    private static byte[] cmap() throws IOException {
        return new Bytes().u16(0).u16(1).u16(3).u16(1).u32(12)
                .u16(4).u16(24).u16(0).u16(2).u16(2).u16(0).u16(0)
                .u16(0xFFFF).u16(0).u16(0xFFFF).u16(1).u16(0).done();
    }

    // one contour of one point, on the curve at the origin so it needs no coordinate bytes
    private static byte[] simpleGlyph() throws IOException {
        return new Bytes().u16(1).zeros(8).u16(0).u16(0).u8(0x31).done();
    }

    private static byte[] selfComposite() throws IOException {
        return new Bytes().u16(COMPOSITE).zeros(8).u16(0).u16(0).zeros(2).done();
    }

    // a contour of 200 points with flags for one
    private static byte[] overlongGlyph() throws IOException {
        return new Bytes().u16(1).zeros(8).u16(199).u16(0).u8(0x01).done();
    }

    private static byte[] tablePastEnd() throws IOException {
        Bytes out = new Bytes().u32(0x00010000).u16(1).zeros(6);
        out.tag("head").u32(0).u32(0x7FFFFFF0).u32(54);
        return out.done();
    }

    private static byte[] directory(Map<String, byte[]> tables) throws IOException {
        Bytes out = new Bytes().u32(0x00010000).u16(tables.size()).zeros(6);
        int offset = 12 + 16 * tables.size();
        for (Map.Entry<String, byte[]> table : tables.entrySet()) {
            out.tag(table.getKey()).u32(0).u32(offset).u32(table.getValue().length);
            offset += table.getValue().length;
        }
        for (byte[] table : tables.values()) out.raw(table);
        return out.done();
    }

    private static final class Bytes {
        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        private final DataOutputStream out = new DataOutputStream(bytes);

        Bytes u8(int value) throws IOException {
            out.writeByte(value);
            return this;
        }

        Bytes u16(int value) throws IOException {
            out.writeShort(value);
            return this;
        }

        Bytes u32(int value) throws IOException {
            out.writeInt(value);
            return this;
        }

        Bytes zeros(int count) throws IOException {
            out.write(new byte[count]);
            return this;
        }

        Bytes tag(String tag) throws IOException {
            out.write(tag.getBytes(StandardCharsets.ISO_8859_1));
            return this;
        }

        Bytes raw(byte[] data) throws IOException {
            out.write(data);
            return this;
        }

        byte[] done() {
            return bytes.toByteArray();
        }
    }
}

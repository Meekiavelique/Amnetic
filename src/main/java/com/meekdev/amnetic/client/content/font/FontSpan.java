package com.meekdev.amnetic.client.content.font;

import com.meekdev.amnetic.client.content.RejectedContentException;

// a window of a font file read big endian, a read past its end refuses the font
record FontSpan(byte[] data, int start, int length, String table) {

    int u8(int at) {
        need(at, 1);
        return data[start + at] & 0xff;
    }

    int u16(int at) {
        need(at, 2);
        return (data[start + at] & 0xff) << 8 | data[start + at + 1] & 0xff;
    }

    int s16(int at) {
        return (short) u16(at);
    }

    long u32(int at) {
        need(at, 4);
        return (long) u16(at) << 16 | u16(at + 2);
    }

    void need(long at, long bytes) {
        if (at < 0 || bytes < 0 || at + bytes > length) {
            throw new RejectedContentException("the font's " + table + " table points past its end");
        }
    }

    // from at to the end of this window, how font tables point at their parts
    FontSpan from(long at) {
        need(at, 0);
        return new FontSpan(data, start + (int) at, length - (int) at, table);
    }

    FontSpan slice(long at, long bytes) {
        need(at, bytes);
        return new FontSpan(data, start + (int) at, (int) bytes, table);
    }
}

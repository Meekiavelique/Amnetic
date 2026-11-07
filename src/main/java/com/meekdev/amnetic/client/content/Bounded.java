package com.meekdev.amnetic.client.content;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

public final class Bounded {
    private static final int INFLATE_CHUNK = 8192;

    private Bounded() {}

    public static int count(ByteBuffer in, int entryBytes, String what) {
        int count = in.getInt();
        if (count < 0 || (long) count * entryBytes > in.remaining()) {
            throw new RejectedContentException(what + " claims " + count + " entries but only " + in.remaining() + " bytes are left");
        }
        return count;
    }

    public static void require(boolean holds, String problem) {
        if (!holds) throw new RejectedContentException(problem);
    }

    public static byte[] inflate(byte[] compressed, int offset, int limit) {
        Inflater inflater = new Inflater();
        try {
            inflater.setInput(compressed, offset, compressed.length - offset);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] chunk = new byte[INFLATE_CHUNK];
            while (!inflater.finished()) {
                int got = inflater.inflate(chunk);
                if (got == 0 && (inflater.needsInput() || inflater.needsDictionary())) {
                    throw new RejectedContentException("compressed data ends early");
                }
                if (out.size() + got > limit) throw new RejectedContentException("compressed data grows past " + limit + " bytes");
                out.write(chunk, 0, got);
            }
            return out.toByteArray();
        } catch (DataFormatException corrupt) {
            throw new RejectedContentException("compressed data is corrupt: " + corrupt.getMessage());
        } finally {
            inflater.end();
        }
    }
}

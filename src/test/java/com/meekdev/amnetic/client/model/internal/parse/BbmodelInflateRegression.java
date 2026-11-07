package com.meekdev.amnetic.client.model.internal.parse;

import com.meekdev.amnetic.client.content.Hostile;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.zip.DeflaterOutputStream;

public final class BbmodelInflateRegression {

    private static final int BOMB_BYTES = 512 * 1024 * 1024;
    private static final int CHUNK = 1 << 20;

    private BbmodelInflateRegression() {}

    public static void verify() throws IOException {
        byte[] bomb = deflated(BOMB_BYTES);
        Hostile.refused("compressed .bbmodel inflating to 512 MB", "grows past", () -> BbmodelParser.parse(compressed(bomb), "bomb"));
        byte[] whole = deflated(CHUNK);
        Hostile.refused("compressed .bbmodel cut in half", "ends early",
                () -> BbmodelParser.parse(compressed(Arrays.copyOf(whole, whole.length / 2)), "cut"));
    }

    // zeros, deflated the way blockbench writes them, behind its marker byte
    private static byte[] deflated(int size) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        bytes.write(1);
        try (DeflaterOutputStream out = new DeflaterOutputStream(bytes)) {
            byte[] zeros = new byte[CHUNK];
            for (int written = 0; written < size; written += CHUNK) out.write(zeros);
        }
        return bytes.toByteArray();
    }

    private static byte[] compressed(byte[] packed) {
        return ("<lz>" + Base64.getEncoder().encodeToString(packed)).getBytes(StandardCharsets.UTF_8);
    }
}

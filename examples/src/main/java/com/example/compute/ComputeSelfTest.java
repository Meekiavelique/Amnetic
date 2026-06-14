package com.example.compute;

import com.meekdev.amnetic.client.compute.ComputeCapabilities;
import com.meekdev.amnetic.client.compute.ComputeShader;
import com.meekdev.amnetic.client.compute.ShaderStorageBuffer;
import com.mojang.logging.LogUtils;
import java.nio.FloatBuffer;
import net.minecraft.resources.Identifier;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL43;
import org.slf4j.Logger;

public final class ComputeSelfTest {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static boolean ran;

    private ComputeSelfTest() {}

    public static void runOnce() {
        if (ran) return;
        ran = true;

        if (!ComputeCapabilities.isComputeAvailable()) return;

        final int count = 256;

        try (ComputeShader shader = ComputeShader.load(
                Identifier.fromNamespaceAndPath("example", "shaders/compute/selftest.comp"));
             ShaderStorageBuffer ssbo = new ShaderStorageBuffer((long) count * Float.BYTES)) {

            ssbo.bind(0);
            shader.dispatch(count / 64, 1, 1);
            ComputeShader.barrier(GL43.GL_SHADER_STORAGE_BARRIER_BIT);

            FloatBuffer out = BufferUtils.createFloatBuffer(count);
            ssbo.readInto(out);

            boolean ok = true;
            for (int i = 0; i < count; i++) {
                float expected = i * 2.0f;
                float actual = out.get(i);
                if (Math.abs(actual - expected) > 1e-3f) {
                    LOGGER.warn("[Amnetic] self-test mismatch at index {}: expected {}, got {}", i, expected, actual);
                    ok = false;
                    break;
                }
            }

            LOGGER.info("[Amnetic] compute self-test: {}", ok ? "PASS" : "FAIL");

        } catch (Exception e) {
            LOGGER.error("[Amnetic] compute self-test failed", e);
        }
    }
}

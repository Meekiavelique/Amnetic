package com.meekdev.amnetic.client.compute;

import com.mojang.logging.LogUtils;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL43;
import org.lwjgl.opengl.GLCapabilities;
import org.slf4j.Logger;

import java.util.concurrent.atomic.AtomicBoolean;

public final class ComputeCapabilities {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final AtomicBoolean probed = new AtomicBoolean(false);
    private static volatile boolean computeAvailable;

    private ComputeCapabilities() {}

    public static void probeOnce() {
        if (!probed.compareAndSet(false, true)) {
            return;
        }

        GLCapabilities caps = GL.getCapabilities();
        // either full GL 4.3 or the ARB extension works for us
        boolean hasCompute = caps.OpenGL43 || caps.GL_ARB_compute_shader;
        computeAvailable = hasCompute;

        logContextInfo(caps, hasCompute);
    }

    public static boolean isComputeAvailable() {
        return probed.get() && computeAvailable;
    }

    private static void logContextInfo(GLCapabilities caps, boolean hasCompute) {
        LOGGER.info("[Amnetic] GL_VERSION  = {}", GL11.glGetString(GL11.GL_VERSION));
        LOGGER.info("[Amnetic] GL_RENDERER = {}", GL11.glGetString(GL11.GL_RENDERER));
        LOGGER.info("[Amnetic] OpenGL43={}, ARB_compute_shader={} -> compute available: {}",
                caps.OpenGL43, caps.GL_ARB_compute_shader, hasCompute);

        if (hasCompute) {
            logComputeLimits();
        } else {
            LOGGER.warn("[Amnetic] No compute shader support on this machine, will have to fall back.");
        }
    }

    private static void logComputeLimits() {
        int maxInvocations = GL11.glGetInteger(GL43.GL_MAX_COMPUTE_WORK_GROUP_INVOCATIONS);
        int maxSharedMem = GL11.glGetInteger(GL43.GL_MAX_COMPUTE_SHARED_MEMORY_SIZE);
        LOGGER.info("[Amnetic] Compute shaders ready - max invocations: {}, shared mem: {} KiB",
                maxInvocations, maxSharedMem / 1024);
    }
}
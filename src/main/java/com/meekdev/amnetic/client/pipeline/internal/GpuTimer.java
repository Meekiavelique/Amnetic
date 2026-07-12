package com.meekdev.amnetic.client.pipeline.internal;

import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL33;

// double-buffered GL_TIME_ELAPSED query. CPU nanoTime only measures submit time, not what the GPU
// actually spends, so a GPU-bound pass can look free on the CPU timeline. queries are async so the
// result lags a couple frames behind, fine for a live profiler
public final class GpuTimer {

    private final int[] queries = new int[2];
    private int writeSlot;
    private boolean[] pending = new boolean[2];
    private volatile float lastMs = -1f;

    public GpuTimer() {
        GL15.glGenQueries(queries);
    }

    public void begin() {
        GL33.glBeginQuery(GL33.GL_TIME_ELAPSED, queries[writeSlot]);
    }

    public void end() {
        GL33.glEndQuery(GL33.GL_TIME_ELAPSED);
        pending[writeSlot] = true;
        int readSlot = 1 - writeSlot;
        if (pending[readSlot] && GL15.glGetQueryObjecti(queries[readSlot], GL15.GL_QUERY_RESULT_AVAILABLE) != 0) {
            long nanos = GL33.glGetQueryObjectui64(queries[readSlot], GL15.GL_QUERY_RESULT);
            lastMs = nanos / 1_000_000f;
            pending[readSlot] = false;
        }
        writeSlot = readSlot;
    }

    // last completed GPU time in ms, -1 if no result has landed yet
    public float lastMs() {
        return lastMs;
    }

    public void dispose() {
        GL15.glDeleteQueries(queries);
    }
}

package com.meekdev.amnetic.client.pipeline.internal;

import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL33;
//? if >=26.2 {
import com.mojang.renderpearl.api.commands.GpuQueryPool;
//?}

// a ring of GL_TIME_ELAPSED queries. CPU nanoTime only measures submit time, not what the GPU actually
// spends, so a GPU-bound pass can look free on the CPU timeline. results are read a few frames later and
// only once the driver says they are ready, so reading never stalls the frame
public final class GpuTimer {

    private static final int RING = 4;

    private final int[] queries = new int[RING];
    private final boolean[] pending = new boolean[RING];
    private int write;
    private int read;
    //? if >=26.2 {
    private final GpuQueryPool nativeQueries;
    //?}

    public GpuTimer() {
        //? if >=26.2 {
        nativeQueries = com.mojang.blaze3d.systems.RenderSystem.getDevice().createTimestampQueryPool(RING * 2);
        //?} else {
        /*GL15.glGenQueries(queries);
        *///?}
    }

    // false when every query is still in flight; the pass then runs untimed this frame
    public boolean begin() {
        if (pending[write]) return false;
        //? if >=26.2 {
        timestamp(write * 2);
        //?} else {
        /*GL33.glBeginQuery(GL33.GL_TIME_ELAPSED, queries[write]);
        *///?}
        return true;
    }

    public void end() {
        //? if >=26.2 {
        timestamp(write * 2 + 1);
        //?} else {
        /*GL33.glEndQuery(GL33.GL_TIME_ELAPSED);
        *///?}
        pending[write] = true;
        write = (write + 1) % RING;
    }

    // the oldest finished result in ms, -1 when none has landed since the last call
    public float poll() {
        float ms = -1f;
        //? if >=26.2 {
        while (pending[read]) {
            var values = nativeQueries.getValues(read * 2, 2);
            if (values[0].isEmpty() || values[1].isEmpty()) break;
            ms = (values[1].getAsLong() - values[0].getAsLong())
                    * com.mojang.blaze3d.systems.RenderSystem.getDevice().getDeviceInfo().timestampPeriod() / 1_000_000f;
            pending[read] = false;
            read = (read + 1) % RING;
        }
        //?} else {
        /*while (pending[read] && GL15.glGetQueryObjecti(queries[read], GL15.GL_QUERY_RESULT_AVAILABLE) != 0) {
            ms = GL33.glGetQueryObjectui64(queries[read], GL15.GL_QUERY_RESULT) / 1_000_000f;
            pending[read] = false;
            read = (read + 1) % RING;
        }
        *///?}
        return ms;
    }

    public void dispose() {
        //? if >=26.2 {
        nativeQueries.close();
        //?} else {
        /*GL15.glDeleteQueries(queries);
        *///?}
    }

    //? if >=26.2 {
    private void timestamp(int index) {
        var encoder = com.mojang.blaze3d.systems.RenderSystem.getDevice().createCommandEncoder();
        encoder.writeTimestamp(nativeQueries, index);
        encoder.submit();
    }
    //?}
}

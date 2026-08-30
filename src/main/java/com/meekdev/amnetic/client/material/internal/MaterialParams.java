package com.meekdev.amnetic.client.material.internal;

import com.meekdev.amnetic.client.compute.ShaderStorageBuffer;
import java.nio.FloatBuffer;
import org.lwjgl.BufferUtils;

public final class MaterialParams {

    public static final MaterialParams INSTANCE = new MaterialParams();

    public static final int LANES = 3;

    private static final int MAX_MATERIALS = 256;
    private static final int FLOATS = MAX_MATERIALS * LANES * 4;

    private final float[] values = new float[FLOATS];
    private final FloatBuffer scratch = BufferUtils.createFloatBuffer(FLOATS);
    private ShaderStorageBuffer ssbo;
    private boolean dirty = true;
    private int subsurfaceCount;

    private MaterialParams() {
    }

    public synchronized void set(int materialId, float... floats) {
        if (materialId < 0 || materialId >= MAX_MATERIALS) {
            throw new IllegalArgumentException("material id out of range: " + materialId);
        }
        if (floats.length > LANES * 4) {
            throw new IllegalArgumentException(
                    "at most " + (LANES * 4) + " floats per material, got " + floats.length);
        }
        int base = materialId * LANES * 4;
        boolean wasScattering = values[base + 3] > 0f;
        System.arraycopy(floats, 0, values, base, floats.length);
        boolean nowScattering = values[base + 3] > 0f;
        if (wasScattering != nowScattering) {
            subsurfaceCount += nowScattering ? 1 : -1;
        }
        dirty = true;
    }

    public synchronized boolean hasSubsurfaceMaterial() {
        return subsurfaceCount > 0;
    }

    public synchronized void bind(int binding) {
        if (ssbo == null) {
            ssbo = new ShaderStorageBuffer((long) FLOATS * Float.BYTES);
        }
        if (dirty) {
            scratch.clear();
            scratch.put(values);
            scratch.flip();
            ssbo.upload(scratch);
            dirty = false;
        }
        ssbo.bind(binding);
    }
}

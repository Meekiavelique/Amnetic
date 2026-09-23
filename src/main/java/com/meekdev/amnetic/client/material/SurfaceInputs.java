package com.meekdev.amnetic.client.material;

public final class SurfaceInputs {
    public static final int VEC4_SLOTS = 16;
    public static final int TEXTURE_SLOTS = 4;

    private final float[] values = new float[VEC4_SLOTS * 4];
    private final int[] textures = new int[TEXTURE_SLOTS];

    public SurfaceInputs set(int slot, float... components) {
        System.arraycopy(components, 0, values, slot * 4, Math.min(components.length, 4));
        return this;
    }

    public SurfaceInputs texture(int slot, int glTexture) {
        textures[slot] = glTexture;
        return this;
    }

    public float[] values() {
        return values;
    }

    public int[] textures() {
        return textures;
    }
}

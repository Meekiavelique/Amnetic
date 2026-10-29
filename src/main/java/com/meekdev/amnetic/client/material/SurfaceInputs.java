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

    public boolean sameAs(SurfaceInputs other) {
        return other == this || other != null && java.util.Arrays.equals(values, other.values)
                && java.util.Arrays.equals(textures, other.textures);
    }

    public int contentHash() {
        return 31 * java.util.Arrays.hashCode(values) + java.util.Arrays.hashCode(textures);
    }

    public float[] values() {
        return values;
    }

    public int[] textures() {
        return textures;
    }
}

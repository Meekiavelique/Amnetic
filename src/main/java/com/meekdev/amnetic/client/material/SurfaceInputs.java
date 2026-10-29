package com.meekdev.amnetic.client.material;

/**
 * the values and textures one draw hands its {@link ShadingModel#surface(String) surface} snippet.
 * the snippet's own {@code uniform} declarations are mapped onto these slots, so a caller fills them
 * through {@link ShadingModel#layout()} by name rather than by index
 */
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

    // whether a draw with these inputs and one with other hand the snippet the same values and textures
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

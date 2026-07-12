package com.meekdev.amnetic.client.pipeline;

/** the isolated layer handed to a LayerPass. inputTexture() is the captured layer, read-only for this
 *  pass; draw the processed result into the framebuffer the pipeline has already bound (same size).
 *  the pipeline alpha-blends that result back over the scene after all the layer's passes have run */
public final class Layer {

    private final RenderLayer kind;
    private final int inputTexture;
    private final int width;
    private final int height;

    public Layer(RenderLayer kind, int inputTexture, int width, int height) {
        this.kind = kind;
        this.inputTexture = inputTexture;
        this.width = width;
        this.height = height;
    }

    public RenderLayer kind() {
        return kind;
    }

    // GL id of the captured layer colour (RGBA, transparent where the layer drew nothing)
    public int inputTexture() {
        return inputTexture;
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }
}

package com.meekdev.amnetic.client.pipeline;

/** processes a single isolated RenderLayer. on entry the pipeline has bound the layer's framebuffer as
 *  the draw target and given you the captured layer via Layer.inputTexture(); sample that and draw your
 *  result (a fullscreen pass is typical). the pipeline composites it back over the scene afterwards */
@FunctionalInterface
public interface LayerPass {

    void render(FrameContext ctx, Layer layer);
    default boolean supportsNative() {return false;}
    default void renderNative(FrameContext ctx, Layer layer) {throw new UnsupportedOperationException("This layer pass requires a native renderer");}

    default boolean enabled() {
        return true;
    }
}

package com.meekdev.amnetic.client.pipeline;

@FunctionalInterface
public interface LayerPass {
    void render(FrameContext ctx, Layer layer);
    default boolean supportsNative() {return false;}
    default void renderNative(FrameContext ctx, Layer layer) {throw new UnsupportedOperationException("This layer pass requires a native renderer");}

    default boolean enabled() {
        return true;
    }
}

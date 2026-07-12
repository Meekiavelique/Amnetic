package com.meekdev.amnetic.client.pipeline;

@FunctionalInterface
public interface LayerPass {
    void render(FrameContext ctx, Layer layer);

    default boolean enabled() {
        return true;
    }
}

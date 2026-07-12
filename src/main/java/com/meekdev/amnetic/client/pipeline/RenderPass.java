package com.meekdev.amnetic.client.pipeline;

@FunctionalInterface
public interface RenderPass {
    void render(FrameContext ctx);

    default boolean enabled() {
        return true;
    }
}

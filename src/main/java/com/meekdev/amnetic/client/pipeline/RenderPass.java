package com.meekdev.amnetic.client.pipeline;

/** one unit of rendering work slotted into a RenderStage. draw using only what you pull from the
 *  FrameContext; GL state is at a clean baseline on entry and the pipeline resets it again after the pass,
 *  so a pass never cleans up after itself or its neighbours */
@FunctionalInterface
public interface RenderPass {

    void render(FrameContext ctx);

    // return false to skip this pass this frame without removing it
    default boolean enabled() {
        return true;
    }
}

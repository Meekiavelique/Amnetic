package com.meekdev.amnetic.client.pipeline;

import com.meekdev.amnetic.client.render.CameraSnapshot;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;

public final class FrameContext {

    private final CameraSnapshot camera;
    private final LevelRenderContext fabric;
    private final int opaqueDepthGlId;

    public FrameContext(CameraSnapshot camera, LevelRenderContext fabric, int opaqueDepthGlId) {
        this.camera = camera;
        this.fabric = fabric;
        this.opaqueDepthGlId = opaqueDepthGlId;
    }

    // camera snapshot for this frame, null if unavailable
    public CameraSnapshot camera() {
        return camera;
    }

    // fabric level-render context for the current stage, null outside the world render
    public LevelRenderContext fabric() {
        return fabric;
    }

    // GL id of the opaque-only depth snapshot taken before translucents, 0 if not captured this frame
    public int opaqueDepth() {
        return opaqueDepthGlId;
    }
}

package com.meekdev.amnetic.client.pipeline;

import com.meekdev.amnetic.client.render.CameraSnapshot;
import com.meekdev.amnetic.client.render.LevelCamera;

public final class FrameContext {

    private final CameraSnapshot camera;
    private final LevelCamera levelCamera;
    private final int opaqueDepthGlId;

    public FrameContext(CameraSnapshot camera, LevelCamera levelCamera, int opaqueDepthGlId) {
        this.camera = camera;
        this.levelCamera = levelCamera;
        this.opaqueDepthGlId = opaqueDepthGlId;
    }

    // camera snapshot for this frame, null if unavailable
    public CameraSnapshot camera() {
        return camera;
    }

    public LevelCamera levelCamera() {
        return levelCamera;
    }

    // GL id of the opaque-only depth snapshot taken before translucents, 0 if not captured this frame
    public int opaqueDepth() {
        return opaqueDepthGlId;
    }
}

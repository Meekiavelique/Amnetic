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

    public CameraSnapshot camera() {
        return camera;
    }

    public LevelCamera levelCamera() {
        return levelCamera;
    }

    public int opaqueDepth() {
        return opaqueDepthGlId;
    }
}

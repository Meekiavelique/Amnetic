package com.meekdev.amnetic.client.pipeline;

import com.meekdev.amnetic.client.render.CameraSnapshot;
import com.meekdev.amnetic.client.render.LevelCamera;
//? if >=26.1 {
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
//?} else if >=1.21.9 {
/*import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
*///?} else {
/*import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
*///?}

public final class FrameContext {

    private final CameraSnapshot camera;
    //? if >=26.1 {
    private final LevelRenderContext fabric;
    //?} else {
    /*private final WorldRenderContext fabric;
    *///?}
    private final LevelCamera levelCamera;
    private final int opaqueDepthGlId;

    //? if >=26.1 {
    public FrameContext(CameraSnapshot camera, LevelRenderContext fabric, int opaqueDepthGlId) {
    //?} else {
    /*public FrameContext(CameraSnapshot camera, WorldRenderContext fabric, int opaqueDepthGlId) {
    *///?}
        this.camera = camera;
        this.fabric = fabric;
        //? if >=26.1 {
        this.levelCamera = fabric != null ? LevelCamera.of(fabric.levelState().cameraRenderState) : null;
        //?} else {
        /*this.levelCamera = fabric != null ? LevelCamera.main() : null;
        *///?}
        this.opaqueDepthGlId = opaqueDepthGlId;
    }

    // camera snapshot for this frame, null if unavailable
    public CameraSnapshot camera() {
        return camera;
    }

    // fabric level-render context for the current stage, null outside the world render
    //? if >=26.1 {
    public LevelRenderContext fabric() {
    //?} else {
    /*public WorldRenderContext fabric() {
    *///?}
        return fabric;
    }

    public LevelCamera levelCamera() {
        return levelCamera;
    }

    // GL id of the opaque-only depth snapshot taken before translucents, 0 if not captured this frame
    public int opaqueDepth() {
        return opaqueDepthGlId;
    }
}

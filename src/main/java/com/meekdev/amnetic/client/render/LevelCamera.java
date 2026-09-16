package com.meekdev.amnetic.client.render;

import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4fc;
//? if >=26.1 {
import net.minecraft.client.renderer.state.level.CameraRenderState;
//?}

public final class LevelCamera {

    public final Vec3 pos;
    public final Matrix4fc projectionMatrix;
    public final Matrix4fc viewRotationMatrix;

    private LevelCamera(Vec3 pos, Matrix4fc projectionMatrix, Matrix4fc viewRotationMatrix) {
        this.pos = pos;
        this.projectionMatrix = projectionMatrix;
        this.viewRotationMatrix = viewRotationMatrix;
    }

    //? if >=26.1 {
    public static LevelCamera of(CameraRenderState cam) {
        if (cam == null || cam.projectionMatrix == null || cam.viewRotationMatrix == null) return null;
        return new LevelCamera(cam.pos, cam.projectionMatrix, cam.viewRotationMatrix);
    }
    //?} else {
    /*private static LevelCamera main;

    public static void record(Vec3 pos, Matrix4fc projection, Matrix4fc viewRotation) {
        main = new LevelCamera(pos, projection, viewRotation);
    }

    public static LevelCamera main() {
        return main;
    }
    *///?}
}

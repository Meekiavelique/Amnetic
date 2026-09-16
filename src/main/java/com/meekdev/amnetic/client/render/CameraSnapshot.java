package com.meekdev.amnetic.client.render;

import com.meekdev.amnetic.client.camera.internal.FrameView;
import com.meekdev.amnetic.client.compat.VanillaCompat;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

public final class CameraSnapshot {

    public final Vec3 eye;
    public final Matrix4f projection;
    public final Matrix4f view;
    public final Matrix4f viewProj;
    public final Matrix4f invViewProj;
    public final boolean zeroToOne;

    private CameraSnapshot(Vec3 eye, Matrix4f projection, Matrix4f view,
                           Matrix4f viewProj, Matrix4f invViewProj, boolean zeroToOne) {
        this.eye = eye;
        this.projection = projection;
        this.view = view;
        this.viewProj = viewProj;
        this.invViewProj = invViewProj;
        this.zeroToOne = zeroToOne;
    }

    private static final Matrix4f PROJ_SCRATCH = new Matrix4f();
    private static final Matrix4f VIEW_SCRATCH = new Matrix4f();
    private static CameraSnapshot cached;

    public static CameraSnapshot current() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.getMainRenderTarget() == null) return null;
        //? if >=26.1 {
        var grs = mc.gameRenderer.getGameRenderState();
        if (grs == null || grs.levelRenderState == null) return null;
        LevelCamera crs = LevelCamera.of(grs.levelRenderState.cameraRenderState);
        //?} else {
        /*LevelCamera crs = LevelCamera.main();
        *///?}
        if (crs == null) return null;

        Matrix4f projection = FrameView.INSTANCE.getProjection(PROJ_SCRATCH, crs.projectionMatrix);
        Matrix4f view = FrameView.INSTANCE.get(VIEW_SCRATCH, crs.viewRotationMatrix);

        CameraSnapshot c = cached;
        if (c != null && c.eye.equals(crs.pos) && c.projection.equals(projection) && c.view.equals(view)) {
            return c;
        }

        Matrix4f proj = new Matrix4f(projection);
        Matrix4f v = new Matrix4f(view);
        Matrix4f viewProj = new Matrix4f(proj).mul(v);
        Matrix4f invViewProj = new Matrix4f(viewProj).invert();
        cached = new CameraSnapshot(crs.pos, proj, v, viewProj, invViewProj,
                VanillaCompat.zeroToOne());
        return cached;
    }
}

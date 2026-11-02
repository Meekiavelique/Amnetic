package com.meekdev.amnetic.client.render;

import com.meekdev.amnetic.client.camera.internal.FrameView;
import com.meekdev.amnetic.client.compat.VanillaCompat;
import com.meekdev.amnetic.client.compat.IrisCompat;
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
    private static CameraSnapshot override;

    public static void handProjection(Matrix4f projection) {
        var world=current();if(world==null)return;
        var p=new Matrix4f(projection);var v=new Matrix4f(world.view);var vp=new Matrix4f(p).mul(v);
        override=new CameraSnapshot(world.eye,p,v,vp,new Matrix4f(vp).invert(),world.zeroToOne);
    }
    public static void endHand(){override=null;}

    public static CameraSnapshot current() {
        if(override!=null)return override;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.gameRenderer.mainRenderTarget() == null) return null;
        //? if >=26.1 {
        var grs = mc.gameRenderer.gameRenderState();
        if (grs == null || grs.levelRenderState == null) return null;
        LevelCamera crs = LevelCamera.of(grs.levelRenderState.cameraRenderState);
        //?} else {
        /*LevelCamera crs = LevelCamera.main();
        *///?}
        if (crs == null) return null;

        Matrix4f projection = FrameView.INSTANCE.getProjection(PROJ_SCRATCH, crs.projectionMatrix);
        Matrix4f view = FrameView.INSTANCE.get(VIEW_SCRATCH, crs.viewRotationMatrix);
        boolean zeroToOne = VanillaCompat.zeroToOne();
        if (IrisCompat.compositing()) {
            IrisCompat.projection(projection);
            IrisCompat.view(view);
            // Iris uses the OpenGL shader-pack projection, including on 26.1.
            zeroToOne = false;
        }

        CameraSnapshot c = cached;
        if (c != null && c.zeroToOne == zeroToOne && c.eye.equals(crs.pos) && c.projection.equals(projection) && c.view.equals(view)) {
            return c;
        }

        Matrix4f proj = new Matrix4f(projection);
        Matrix4f v = new Matrix4f(view);
        Matrix4f viewProj = new Matrix4f(proj).mul(v);
        Matrix4f invViewProj = new Matrix4f(viewProj).invert();
        cached = new CameraSnapshot(crs.pos, proj, v, viewProj, invViewProj,
                zeroToOne);
        return cached;
    }
}

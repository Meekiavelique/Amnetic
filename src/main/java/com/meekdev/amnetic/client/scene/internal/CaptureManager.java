package com.meekdev.amnetic.client.scene.internal;

import com.meekdev.amnetic.client.scene.CaptureContext;
import com.meekdev.amnetic.client.scene.CaptureResult;
import com.meekdev.amnetic.client.scene.PerspectiveCapture;
import com.meekdev.amnetic.client.scene.PerspectiveView;
import com.meekdev.amnetic.mixin.accessor.CameraInvoker;
import com.meekdev.amnetic.mixin.accessor.LevelRendererAccessor;
import com.mojang.blaze3d.pipeline.RenderTarget;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.TextureFilteringMethod;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class CaptureManager {

    public static final CaptureManager INSTANCE = new CaptureManager();

    private static final Logger LOG = LoggerFactory.getLogger("Amnetic/Capture");
    private static final int MAX_CAPTURES = 4; // nearest-first cap

    private final List<PerspectiveCapture> registry = new ArrayList<>();
    private final List<PerspectiveCapture> active = new ArrayList<>();
    private boolean enabled = true;

    private boolean capturing;
    private Matrix4f currentViewRotation;
    private RenderTarget currentTarget;

    private final CaptureContext ctx = new CaptureContext();
    private final Matrix4f mainView = new Matrix4f();
    private final Matrix4f mainProj = new Matrix4f();
    private final Matrix4f obliqueProj = new Matrix4f();
    private final ObjectArrayList<SectionRenderDispatcher.RenderSection> isolatedVisible = new ObjectArrayList<>(8192);
    private final ObjectArrayList<SectionRenderDispatcher.RenderSection> isolatedNearby = new ObjectArrayList<>(64);

    private CaptureManager() {}

    public void register(PerspectiveCapture capture) {
        if (!registry.contains(capture)) registry.add(capture);
    }

    public boolean isCapturing() { return capturing; }
    public Matrix4f currentCaptureViewRotation() { return capturing ? currentViewRotation : null; }
    public RenderTarget currentCaptureTarget() { return capturing ? currentTarget : null; }

    public void runCaptures(GameRenderer renderer, DeltaTracker ticker) {
        if (!enabled || capturing || registry.isEmpty()) return;

        Minecraft mc = Minecraft.getInstance();
        LevelRenderer lr = mc.levelRenderer;
        Camera cam = renderer.getMainCamera();
        var grs = renderer.getGameRenderState();
        if (lr == null || cam == null || grs == null || grs.levelRenderState == null
                || grs.levelRenderState.cameraRenderState == null || mc.level == null) {
            return;
        }
        CameraRenderState crs = grs.levelRenderState.cameraRenderState;
        float pt = cam.getCameraEntityPartialTicks(ticker);

        mainView.set(crs.viewRotationMatrix);
        mainProj.set(crs.projectionMatrix);
        ctx.set(cam, pt, mainView, mainProj, cam.position());

        active.clear();
        for (PerspectiveCapture capture : registry) {
            if (capture.requestView(ctx) == CaptureResult.RENDER) active.add(capture);
        }
        if (active.isEmpty()) return;
        active.sort(Comparator.comparingDouble(c -> c.view().distanceValue()));

        int n = Math.min(active.size(), MAX_CAPTURES);
        for (int i = 0; i < n; i++) {
            renderOne(active.get(i), renderer, ticker, mc, lr, cam, crs, pt);
        }
    }

    private void renderOne(PerspectiveCapture capture, GameRenderer renderer, DeltaTracker ticker,
                           Minecraft mc, LevelRenderer lr, Camera cam, CameraRenderState crs, float pt) {
        PerspectiveView view = capture.view();
        capture.target().ensure(capture.resolution());

        CameraInvoker inv = (CameraInvoker) cam;
        LevelRendererAccessor lra = (LevelRendererAccessor) lr;
        var bufferSource = mc.renderBuffers().bufferSource();

        Vec3 savedPos = cam.position();
        float savedYaw = cam.yRot();
        float savedPitch = cam.xRot();
        Frustum savedFrustum = inv.amnetic$getCullFrustum();
        var savedVisible = lra.amnetic$getVisibleSections();
        var savedNearby = lra.amnetic$getNearbyVisibleSections();
        Vec3 savedCrsPos = crs.pos;

        Vec3 eye = view.eye();
        Matrix4f baseProj = view.matchMainProjection_() ? mainProj : view.projection();
        obliqueProj.set(baseProj);
        if (view.hasClip()) {
            Vector3f cn = view.clipNormal();
            ReflectionMath.obliqueProjection(obliqueProj, baseProj, view.viewRotation(), eye,
                    cn.x, cn.y, cn.z, view.clipD());
        }
        Frustum frustum = new Frustum(view.viewRotation(), obliqueProj);
        frustum.prepare(eye.x, eye.y, eye.z);

        bufferSource.endBatch();
        capturing = true;
        currentViewRotation = view.viewRotation();
        currentTarget = capture.target().target();
        try {
            poseCamera(inv, view);
            inv.amnetic$setCullFrustum(frustum);

            lra.amnetic$setVisibleSections(isolatedVisible);
            lra.amnetic$setNearbyVisibleSections(isolatedNearby);
            isolatedVisible.clear();
            isolatedNearby.clear();
            lr.getSectionOcclusionGraph().addSectionsInFrustum(frustum, isolatedVisible, isolatedNearby);

            lr.extractLevel(ticker, cam, pt);
            crs.viewRotationMatrix.set(view.viewRotation());
            crs.projectionMatrix.set(obliqueProj);
            crs.pos = eye;   // entities read crs.pos
            uploadCameraGlobals(renderer, ticker, eye);
            renderer.renderLevel(ticker); // into the capture target (redirected)
            bufferSource.endBatch();
        } catch (Throwable e) {
            LOG.error("[Capture] failed; disabling all captures", e);
            enabled = false;
        } finally {
            capturing = false;
            currentTarget = null;
            lra.amnetic$setVisibleSections(savedVisible);
            lra.amnetic$setNearbyVisibleSections(savedNearby);
            inv.amnetic$setRotation(savedYaw, savedPitch);
            inv.amnetic$setPosition(savedPos);
            inv.amnetic$setCullFrustum(savedFrustum);
            crs.pos = savedCrsPos;
            uploadCameraGlobals(renderer, ticker, savedCrsPos);
            lr.extractLevel(ticker, cam, pt); // refill shared state for the main view
            crs.viewRotationMatrix.set(mainView);
            crs.projectionMatrix.set(mainProj);
            bufferSource.endBatch();
        }
        capture.target().registerColor(capture.id());
    }

    private void poseCamera(CameraInvoker inv, PerspectiveView view) {
        Matrix4f invView = new Matrix4f(view.viewRotation()).invert();
        Vector3f look = new Vector3f(0f, 0f, -1f).mulDirection(invView).normalize();
        float yaw = (float) Math.toDegrees(Math.atan2(-look.x, look.z));
        float pitch = (float) Math.toDegrees(Math.asin(Math.max(-1.0, Math.min(1.0, -look.y))));
        inv.amnetic$setPosition(view.eye());
        inv.amnetic$setRotation(yaw, pitch);
    }

    private void uploadCameraGlobals(GameRenderer renderer, DeltaTracker ticker, Vec3 pos) {
        Minecraft mc = Minecraft.getInstance();
        var grs = renderer.getGameRenderState();
        long gameTime = mc.level == null ? 0L : mc.level.getGameTime();
        boolean rgss = grs.optionsRenderState.textureFiltering == TextureFilteringMethod.RGSS;
        renderer.getGlobalSettingsUniform().update(
                grs.windowRenderState.width, grs.windowRenderState.height,
                grs.optionsRenderState.glintStrength, gameTime, ticker,
                grs.optionsRenderState.menuBackgroundBlurriness, pos, rgss);
    }
}

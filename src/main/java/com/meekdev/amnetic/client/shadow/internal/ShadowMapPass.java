package com.meekdev.amnetic.client.shadow.internal;

import com.meekdev.amnetic.client.dev.ShaderHotReload;
import com.meekdev.amnetic.client.gbuffer.internal.GBufferTargets;
import com.meekdev.amnetic.client.instanced.internal.InstanceMeshRegistry;
import com.meekdev.amnetic.client.light.Light;
import com.meekdev.amnetic.client.light.LightType;
import com.meekdev.amnetic.client.light.internal.LightRegistry;
import com.meekdev.amnetic.client.model.internal.ModelRegistry;
import com.meekdev.amnetic.client.render.CameraSnapshot;
import com.meekdev.amnetic.client.shadow.ShadowSettings;
import com.meekdev.amnetic.client.shadow.Shadows;
import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.opengl.GlTexture;
import com.mojang.blaze3d.textures.GpuTexture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL33;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class ShadowMapPass {

    public static final ShadowMapPass INSTANCE = new ShadowMapPass();
    private static final Logger LOGGER = LoggerFactory.getLogger("Amnetic/Shadows");

    private static final float NEAR = 0.05f;
    // OccluderCache key for the sun's block occluders, real light ids start at 1 so no collision
    private static final long SUN_OCCLUDER_ID = -1L;
    private static final Identifier DEPTH_VSH = Identifier.fromNamespaceAndPath("amnetic", "shaders/shadow/depth.vsh");
    private static final Identifier DEPTH_FSH = Identifier.fromNamespaceAndPath("amnetic", "shaders/shadow/depth.fsh");
    private static final Identifier CUTOUT_VSH = Identifier.fromNamespaceAndPath("amnetic", "shaders/shadow/depth_cutout.vsh");
    private static final Identifier CUTOUT_FSH = Identifier.fromNamespaceAndPath("amnetic", "shaders/shadow/depth_cutout.fsh");
    private static final Identifier TRANSLUCENT_FSH = Identifier.fromNamespaceAndPath("amnetic", "shaders/shadow/depth_translucent.fsh");

    private final OccluderMeshCache meshes = new OccluderMeshCache();
    private final EntityOccluders entities = new EntityOccluders();
    private final Set<Long> liveIds = new HashSet<>();
    private final HashMap<Long, long[]> bakeState = new HashMap<>();
    private final ArrayList<Light> candidates = new ArrayList<>();
    private int dirtyBaked; // dirty GPU bakes done this frame

    private static double dist2(Light l, double ex, double ey, double ez) {
        double dx = l.x() - ex, dy = l.y() - ey, dz = l.z() - ez;
        return dx * dx + dy * dy + dz * dz;
    }
    private ShadowDepthProgram opaqueProgram;
    private ShadowDepthProgram cutoutProgram;
    private ShadowDepthProgram translucentProgram;

    private final Matrix4f proj = new Matrix4f();
    private final Matrix4f view = new Matrix4f();
    private final Matrix4f viewProj = new Matrix4f();
    // view-proj pair for custom models/instanced meshes: unlike the occluder bake (relative to an int
    // block-grid anchor) these are relative to the light itself, since neither system knows the anchor grid
    private final Matrix4f dynamicView = new Matrix4f();
    private final Matrix4f dynamicViewProj = new Matrix4f();
    private boolean dynamicCasters;

    private final float[] spotViewProj = new float[ShadowSettings.MAX_SPOT * 16];
    private final int[] savedViewport = new int[4]; // reused each bake frame (no per-frame allocation)

    // sun cascades: camera-relative sampling matrices plus world radius per cascade, tightest first
    private final float[] sunViewProj = new float[ShadowSettings.MAX_CASCADES * 16];
    private final float[] sunCascadeRadius = new float[ShadowSettings.MAX_CASCADES];
    private final Matrix4f sunCamViewProj = new Matrix4f();
    private final Vector3f camFwd = new Vector3f();
    private boolean sunActive;
    private int sunCascadeCount;

    private boolean active;
    private int spotCount, pointCount, entityBoxes;
    private boolean overflowLogged;
    private boolean atlasWarned;
    private long lastBakeNanos;
    private float partialTick;

    private ShadowMapPass() {
        // dev hot reload, closing a depth program makes its next begin() recompile
        ShaderHotReload.onReload(() -> {
            if (opaqueProgram != null) opaqueProgram.close();
            if (cutoutProgram != null) cutoutProgram.close();
            if (translucentProgram != null) translucentProgram.close();
        });
    }

    public boolean isActive() { return active; }
    public int spotCount() { return spotCount; }
    public int pointCount() { return pointCount; }
    public int entityBoxes() { return entityBoxes; }
    public float[] spotViewProj() { return spotViewProj; }
    public boolean sunActive() { return sunActive; }
    public int sunCascadeCount() { return sunCascadeCount; }
    public float[] sunViewProj() { return sunViewProj; }
    public float[] sunCascadeRadius() { return sunCascadeRadius; }

    public float lastBakeMs() { return lastBakeNanos / 1_000_000f; }

    public long vramBytes() {
        long spot = (long) SpotShadowAtlas.atlasSize() * SpotShadowAtlas.atlasSize() * 4L;
        int fs = PointShadowArray.faceSize();
        long point = (long) fs * fs * 4L * 6L * ShadowSettings.defaults().maxPointShadows();
        int sr = SunShadowCascades.resolution();
        long sun = (long) sr * sr * 4L * SunShadowCascades.cascades();
        return (SpotShadowAtlas.glTextureId() != 0 ? spot : 0)
                + (PointShadowArray.glTextureId() != 0 ? point : 0)
                + (SunShadowCascades.glTextureId() != 0 ? sun : 0);
    }

    public void render(CameraSnapshot cam) {
        active = false;
        spotCount = 0;
        pointCount = 0;
        entityBoxes = 0;
        sunActive = false;
        sunCascadeCount = 0;

        Minecraft mc = Minecraft.getInstance();
        Level level = mc.level;
        if (!Shadows.isEnabled() || cam == null || level == null) {
            for (Light l : LightRegistry.INSTANCE.all()) l.setShadowRef(-1);
            return;
        }

        ShadowSettings s = ShadowSettings.defaults();
        int res = s.resolution();
        float maxDist2 = s.maxDistance() * s.maxDistance();
        partialTick = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);

        // gather candidates before any GL work so a lightless scene skips it all, notably the glGetInteger
        // state save which drains the GL pipeline (worse under the profiler's per-pass timer queries).
        // most frames with no nearby casters exit right here
        candidates.clear();
        Light sun = null;
        for (Light l : LightRegistry.INSTANCE.all()) {
            l.setShadowRef(-1);
            if (!l.isEnabled() || !l.castsShadow()) continue;
            LightType t = l.type();
            if (t == LightType.DIRECTIONAL) {
                if (sun == null) sun = l; // first directional caster is the sun; extras don't cast
                continue;
            }
            if (t != LightType.SPOT && t != LightType.POINT) continue;
            double dxe = l.x() - cam.eye.x, dye = l.y() - cam.eye.y, dze = l.z() - cam.eye.z;
            if (dxe * dxe + dye * dye + dze * dze > maxDist2) continue;
            candidates.add(l);
        }
        if (candidates.isEmpty() && sun == null) {
            // no casters this frame, drop cached occluders/bake state without touching GL state
            liveIds.clear();
            meshes.retainOnly(liveIds);
            OccluderCache.retainOnly(liveIds);
            bakeState.keySet().retainAll(liveIds);
            active = false;
            return;
        }

        if (opaqueProgram == null) opaqueProgram = new ShadowDepthProgram(DEPTH_VSH, DEPTH_FSH);
        if (cutoutProgram == null) cutoutProgram = new ShadowDepthProgram(CUTOUT_VSH, CUTOUT_FSH);
        if (translucentProgram == null) translucentProgram = new ShadowDepthProgram(CUTOUT_VSH, TRANSLUCENT_FSH);

        // no glGet* state saves here, three synchronous reads per frame drain the GL queue. the outer FBO
        // was captured once in beginFrame(), the world-pass viewport is always the main target's full size,
        // and the world never renders with scissor on (GlStateManager re-establishes it for the GUI)
        int savedFbo = GBufferTargets.INSTANCE.outerFboId();
        var mainRt = mc.getMainRenderTarget();
        savedViewport[0] = 0;
        savedViewport[1] = 0;
        savedViewport[2] = mainRt.width;
        savedViewport[3] = mainRt.height;
        boolean savedScissor = false;

        boolean atlasReady = false, arrayReady = false;
        liveIds.clear();
        long bakeStart = System.nanoTime();

        try {
            GlStateManager._disableBlend(); GL11.glDisable(GL11.GL_BLEND);
            GL11.glEnable(GL11.GL_DEPTH_TEST);
            GL11.glDepthFunc(GL11.GL_LESS);
            GL11.glDepthMask(true);
            GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glColorMask(true, true, true, true); // spot atlas has a color attachment
            GL11.glClearColor(1f, 1f, 1f, 1f); // unoccluded color-map texels = white
            GL11.glEnable(GL11.GL_SCISSOR_TEST);

            dirtyBaked = 0;
            dynamicCasters = ModelRegistry.INSTANCE.hasShadowCasters() || InstanceMeshRegistry.INSTANCE.hasShadowCasters();

            final double ex = cam.eye.x, ey = cam.eye.y, ez = cam.eye.z;
            candidates.sort((a, b) -> Double.compare(dist2(a, ex, ey, ez), dist2(b, ex, ey, ez)));

            for (Light l : candidates) {
                LightType type = l.type();
                if (type == LightType.SPOT && spotCount < s.maxSpotShadows()) {
                    if (!atlasReady) { SpotShadowAtlas.ensure(res); atlasReady = true; }
                    l.setShadowRef(spotCount);
                    liveIds.add(l.id());
                    bakeSpot(l, cam, level, spotCount, s); // static-skips internally
                    spotCount++;
                } else if (type == LightType.POINT && pointCount < s.maxPointShadows()) {
                    if (!arrayReady) { PointShadowArray.ensure(s.pointFaceSize(), s.maxPointShadows()); arrayReady = true; }
                    l.setShadowRef(1000 + pointCount);
                    liveIds.add(l.id());
                    bakePoint(l, cam, level, pointCount, s);
                    pointCount++;
                } else {
                    if (!overflowLogged) {
                        overflowLogged = true;
                        LOGGER.info("shadow caster cap reached (spot {}/{}, point {}/{}); extra lights won't cast",
                                spotCount, s.maxSpotShadows(), pointCount, s.maxPointShadows());
                    }
                }
            }

            if (sun != null) {
                sun.setShadowRef(2000);
                bakeSun(sun, cam, level, s);
            }

            meshes.retainOnly(liveIds);
            OccluderCache.retainOnly(liveIds);
            bakeState.keySet().retainAll(liveIds);
            active = (spotCount > 0 || pointCount > 0 || sunActive);
        } finally {
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, savedFbo);
            GL11.glViewport(savedViewport[0], savedViewport[1], savedViewport[2], savedViewport[3]);
            if (savedScissor) GL11.glEnable(GL11.GL_SCISSOR_TEST); else GL11.glDisable(GL11.GL_SCISSOR_TEST);
            GL11.glColorMask(true, true, true, true);
            GL11.glClearColor(0f, 0f, 0f, 0f);
            GL11.glEnable(GL11.GL_CULL_FACE);
            GL11.glDepthFunc(GL11.GL_LEQUAL);
            GL11.glDepthMask(true);
            GlStateManager._blendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL14.glBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GlStateManager._disableBlend(); GL11.glDisable(GL11.GL_BLEND);
            GlStateManager._glUseProgram(0);
            lastBakeNanos = System.nanoTime() - bakeStart;
        }
    }

    private void bakeSpot(Light l, CameraSnapshot cam, Level level, int tile, ShadowSettings s) {
        OccluderCache.Collected col = OccluderCache.getOrCompute(l.id(), level,
                (float) l.x(), (float) l.y(), (float) l.z(), l.range());
        OccluderMesh mesh = meshes.get(l.id(), col);
        boolean ents = s.entityShadows() && entities.build(level, l.x(), l.y(), l.z(), l.range(),
                col.anchorX, col.anchorY, col.anchorZ, s.entityModels(), partialTick);
        if (ents) entityBoxes += entities.boxCount();

        float fovDeg = Math.max(1f, 2f * (float) Math.toDegrees(Math.acos(clampCos(l.cosOuter()))));
        float far = Math.max(l.range(), NEAR + 0.1f);
        proj.identity().perspective((float) Math.toRadians(fovDeg), 1f, NEAR, far);

        viewProj.set(proj).mul(lookAt(view,
                (float) (l.x() - cam.eye.x), (float) (l.y() - cam.eye.y), (float) (l.z() - cam.eye.z),
                l.dirX(), l.dirY(), l.dirZ()));
        viewProj.get(spotViewProj, tile * 16);

        if (!shouldBake(l.id(), sigOf(col.list, l.x(), l.y(), l.z(), l.dirX(), l.dirY(), l.dirZ()), tile, ents || dynamicCasters, s)) {
            return;
        }

        SpotShadowAtlas.bindFbo();
        int px = SpotShadowAtlas.tilePixelX(tile), py = SpotShadowAtlas.tilePixelY(tile), ts = SpotShadowAtlas.tileSize();
        GL11.glViewport(px, py, ts, ts);
        GL11.glScissor(px, py, ts, ts);
        GL11.glClearDepth(1.0);
        GL11.glClear(GL11.GL_DEPTH_BUFFER_BIT | GL11.GL_COLOR_BUFFER_BIT);

        viewProj.set(proj).mul(lookAt(view,
                (float) (l.x() - col.anchorX), (float) (l.y() - col.anchorY), (float) (l.z() - col.anchorZ),
                l.dirX(), l.dirY(), l.dirZ()));
        drawAll(mesh, ents, viewProj);

        // custom models/instanced meshes are light-relative, not block-anchor-relative (see field note)
        dynamicViewProj.set(proj).mul(lookAt(dynamicView, 0f, 0f, 0f, l.dirX(), l.dirY(), l.dirZ()));
        ModelRegistry.INSTANCE.renderShadow(dynamicViewProj, l.x(), l.y(), l.z(), l.range());
        dynamicViewProj.set(proj).mul(lookAt(dynamicView, (float) l.x(), (float) l.y(), (float) l.z(),
                l.dirX(), l.dirY(), l.dirZ()));
        InstanceMeshRegistry.INSTANCE.renderShadow(dynamicViewProj);
    }

    private void bakePoint(Light l, CameraSnapshot cam, Level level, int slot, ShadowSettings s) {
        OccluderCache.Collected col = OccluderCache.getOrCompute(l.id(), level,
                (float) l.x(), (float) l.y(), (float) l.z(), l.range());
        OccluderMesh mesh = meshes.get(l.id(), col);
        boolean ents = s.entityShadows() && entities.build(level, l.x(), l.y(), l.z(), l.range(),
                col.anchorX, col.anchorY, col.anchorZ, s.entityModels(), partialTick);
        if (ents) entityBoxes += entities.boxCount();

        if (!shouldBake(l.id(), sigOf(col.list, l.x(), l.y(), l.z(), 0f, 0f, 0f), 1000 + slot, ents || dynamicCasters, s)) {
            return;
        }

        float far = Math.max(l.range(), NEAR + 0.1f);
        proj.identity().perspective((float) Math.toRadians(90.0), 1f, NEAR, far);
        int fs = PointShadowArray.faceSize();

        for (int face = 0; face < 6; face++) {
            PointShadowArray.bindFaceForRender(slot, face);
            GL11.glViewport(0, 0, fs, fs);
            GL11.glScissor(0, 0, fs, fs);
            GL11.glClearDepth(1.0);
            GL11.glClear(GL11.GL_DEPTH_BUFFER_BIT);

            float[] f = FACE_DIR[face];
            viewProj.set(proj).mul(lookAtUp(view,
                    (float) (l.x() - col.anchorX), (float) (l.y() - col.anchorY), (float) (l.z() - col.anchorZ),
                    f[0], f[1], f[2], f[3], f[4], f[5]));
            drawAll(mesh, ents, viewProj);

            dynamicViewProj.set(proj).mul(lookAtUp(dynamicView, 0f, 0f, 0f, f[0], f[1], f[2], f[3], f[4], f[5]));
            ModelRegistry.INSTANCE.renderShadow(dynamicViewProj, l.x(), l.y(), l.z(), l.range());
            dynamicViewProj.set(proj).mul(lookAtUp(dynamicView, (float) l.x(), (float) l.y(), (float) l.z(),
                    f[0], f[1], f[2], f[3], f[4], f[5]));
            InstanceMeshRegistry.INSTANCE.renderShadow(dynamicViewProj);
        }
    }

    // bakes the sun's cascaded shadow maps. each cascade fits an ortho to the bounding sphere of a
    // camera-frustum slice (sphere fit keeps the radius rotation-stable, so texel-snapping the center is
    // enough to kill shimmer) and draws the same caster sources as the spot path, each in its own frame:
    // blocks/entities anchor-relative, custom models cascade-center-relative, instanced meshes
    // camera-relative. re-baked every frame, the casters move with the camera so there is no stable
    // signature to cache on
    private void bakeSun(Light sun, CameraSnapshot cam, Level level, ShadowSettings s) {
        float dx = sun.dirX(), dy = sun.dirY(), dz = sun.dirZ();
        float dl = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (dl < 1e-5f) return;
        dx /= dl; dy /= dl; dz /= dl;

        int cascades = Math.max(1, Math.min(ShadowSettings.MAX_CASCADES, s.sunCascades()));
        SunShadowCascades.ensure(s.sunResolution(), cascades);
        int res = SunShadowCascades.resolution();

        // light-space basis in double precision so world-space texel snapping stays exact far from origin
        // up choice mirrors lookAt() so the snap axes match the view matrix
        float upx = 0f, upy = 1f, upz = 0f;
        if (Math.abs(dy) > 0.99f) { upy = 0f; upz = 1f; }
        double rxd = (double) dy * upz - (double) dz * upy;
        double ryd = (double) dz * upx - (double) dx * upz;
        double rzd = (double) dx * upy - (double) dy * upx;
        double rl = Math.sqrt(rxd * rxd + ryd * ryd + rzd * rzd);
        rxd /= rl; ryd /= rl; rzd /= rl;
        double uxd = ryd * dz - rzd * dy;
        double uyd = rzd * dx - rxd * dz;
        double uzd = rxd * dy - ryd * dx;

        // camera frustum shape from the projection (reversed-Z only changes the depth rows)
        cam.view.positiveZ(camFwd).negate();
        float tanH = 1f / cam.projection.m00();
        float tanV = 1f / cam.projection.m11();
        float k2 = tanH * tanH + tanV * tanV;

        // practical split scheme: blend of logarithmic (tight near the camera) and uniform
        float near = NEAR, far = s.sunDistance(), lambda = s.sunSplitLambda();
        float[] splits = new float[cascades + 1];
        splits[0] = near;
        splits[cascades] = far;
        for (int i = 1; i < cascades; i++) {
            float f = i / (float) cascades;
            float log = near * (float) Math.pow(far / near, f);
            float uni = near + (far - near) * f;
            splits[i] = lambda * log + (1f - lambda) * uni;
        }

        float ext = s.sunCasterExtension();

        // block occluders: one collection around the camera, snapped to an 8-block grid so the cached mesh
        // survives camera motion. every cascade draws the same mesh, the ortho clips the rest for free
        OccluderCache.Collected col = null;
        OccluderMesh mesh = null;
        float blockR = Math.min(s.sunBlockOccluderRadius(), far);
        if (blockR > 0f) {
            float sx = (float) (Math.floor(cam.eye.x / 8.0) * 8.0 + 4.0);
            float sy = (float) (Math.floor(cam.eye.y / 8.0) * 8.0 + 4.0);
            float sz = (float) (Math.floor(cam.eye.z / 8.0) * 8.0 + 4.0);
            col = OccluderCache.getOrCompute(SUN_OCCLUDER_ID, level, sx, sy, sz, blockR + 7f);
            mesh = meshes.get(SUN_OCCLUDER_ID, col);
            liveIds.add(SUN_OCCLUDER_ID);
        }
        int ax = col != null ? col.anchorX : (int) Math.floor(cam.eye.x);
        int ay = col != null ? col.anchorY : (int) Math.floor(cam.eye.y);
        int az = col != null ? col.anchorZ : (int) Math.floor(cam.eye.z);
        boolean ents = s.entityShadows() && entities.build(level, cam.eye.x, cam.eye.y, cam.eye.z,
                Math.min(far, 64f), ax, ay, az, s.entityModels(), partialTick);
        if (ents) entityBoxes += entities.boxCount();

        for (int i = 0; i < cascades; i++) {
            float a = splits[i], b = splits[i + 1];
            // bounding-sphere center distance t equalizes the near/far corner distances of the slice
            float t = Math.min(Math.max((a + b) * (1f + k2) * 0.5f, a), b);
            float radius = (float) Math.sqrt(Math.max(
                    b * b * k2 + (t - b) * (t - b),
                    a * a * k2 + (t - a) * (t - a)));
            radius = (float) Math.ceil(radius * 2f) / 2f; // quantize so float noise doesn't wobble the texel size

            // cascade center in absolute world coords, snapped to the light-space texel grid
            double cx = cam.eye.x + camFwd.x * t;
            double cy = cam.eye.y + camFwd.y * t;
            double cz = cam.eye.z + camFwd.z * t;
            double texel = 2.0 * radius / res;
            double sr = cx * rxd + cy * ryd + cz * rzd;
            double su = cx * uxd + cy * uyd + cz * uzd;
            double snapR = Math.floor(sr / texel) * texel - sr;
            double snapU = Math.floor(su / texel) * texel - su;
            cx += rxd * snapR + uxd * snapU;
            cy += ryd * snapR + uyd * snapU;
            cz += rzd * snapR + uzd * snapU;

            float eyeDist = radius + ext;
            proj.setOrtho(-radius, radius, -radius, radius, 0f, 2f * radius + ext);

            SunShadowCascades.bindLayerForRender(i);
            GL11.glViewport(0, 0, res, res);
            GL11.glScissor(0, 0, res, res);
            GL11.glClearDepth(1.0);
            GL11.glClear(GL11.GL_DEPTH_BUFFER_BIT);

            // blocks and entities live in anchor-relative space
            float eax = (float) (cx - ax) - dx * eyeDist;
            float eay = (float) (cy - ay) - dy * eyeDist;
            float eaz = (float) (cz - az) - dz * eyeDist;
            viewProj.set(proj).mul(lookAtUp(view, eax, eay, eaz, dx, dy, dz, upx, upy, upz));
            drawAll(mesh, ents, viewProj);

            // custom models are cascade-center-relative (same precision trick as the spot path)
            dynamicViewProj.set(proj).mul(lookAtUp(dynamicView,
                    -dx * eyeDist, -dy * eyeDist, -dz * eyeDist, dx, dy, dz, upx, upy, upz));
            ModelRegistry.INSTANCE.renderShadow(dynamicViewProj, cx, cy, cz, radius + ext);

            // instanced matrices and the deferred pass's fragment positions are both camera-relative,
            // so one matrix serves both the instanced bake and the shader-side sampling
            float ecx = (float) (cx - cam.eye.x) - dx * eyeDist;
            float ecy = (float) (cy - cam.eye.y) - dy * eyeDist;
            float ecz = (float) (cz - cam.eye.z) - dz * eyeDist;
            sunCamViewProj.set(proj).mul(lookAtUp(dynamicView, ecx, ecy, ecz, dx, dy, dz, upx, upy, upz));
            InstanceMeshRegistry.INSTANCE.renderShadow(sunCamViewProj);
            sunCamViewProj.get(sunViewProj, i * 16);
            sunCascadeRadius[i] = radius;
        }

        sunCascadeCount = cascades;
        sunActive = true;
    }

    private boolean shouldBake(long id, long sig, int slot, boolean ents, ShadowSettings s) {
        long[] st = bakeState.get(id);
        boolean dirty = st == null || st[0] != sig || st[1] != slot || ents || st[2] == 1L;
        if (!dirty) { st[1] = slot; st[2] = 0L; return false; }
        int budget = s.bakeBudget();
        if (st != null && budget > 0 && dirtyBaked >= budget) return false;
        dirtyBaked++;
        bakeState.put(id, new long[]{sig, slot, ents ? 1L : 0L});
        return true;
    }

    private static long sigOf(List<?> list, double x, double y, double z, float dx, float dy, float dz) {
        long h = 1469598103934665603L;
        h = (h ^ System.identityHashCode(list)) * 1099511628211L;
        h = (h ^ Float.floatToIntBits((float) x)) * 1099511628211L;
        h = (h ^ Float.floatToIntBits((float) y)) * 1099511628211L;
        h = (h ^ Float.floatToIntBits((float) z)) * 1099511628211L;
        h = (h ^ Float.floatToIntBits(dx)) * 1099511628211L;
        h = (h ^ Float.floatToIntBits(dy)) * 1099511628211L;
        h = (h ^ Float.floatToIntBits(dz)) * 1099511628211L;
        return h;
    }

    private void drawAll(OccluderMesh mesh, boolean ents, Matrix4f bakeVP) {
        boolean anyOpaque = (mesh != null && mesh.hasOpaque()) || ents;
        if (anyOpaque) {
            opaqueProgram.begin();
            opaqueProgram.setViewProj(bakeVP);
            if (mesh != null) mesh.drawOpaque();
            if (ents) entities.draw();
        }
        if (mesh != null && (mesh.hasCutout() || mesh.hasTranslucent())) {
            int atlas = blockAtlasGlId();
            if (atlas == 0) {
                if (!atlasWarned) { atlasWarned = true; LOGGER.warn("block atlas GL id unavailable; textured shadows disabled this session"); }
            } else {
                GlStateManager._activeTexture(GL13.GL_TEXTURE0);
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, atlas);
                GL33.glBindSampler(0, 0);
                if (mesh.hasCutout()) {
                    cutoutProgram.begin();
                    cutoutProgram.setViewProj(bakeVP);
                    cutoutProgram.setSampler("uAtlas", 0);
                    mesh.drawCutout();
                }
                if (mesh.hasTranslucent()) {
                    translucentProgram.begin();
                    translucentProgram.setViewProj(bakeVP);
                    translucentProgram.setSampler("uAtlas", 0);
                    mesh.drawTranslucent();
                }
            }
        }
    }

    private static int blockAtlasGlId() {
        try {
            AbstractTexture t = Minecraft.getInstance().getTextureManager().getTexture(TextureAtlas.LOCATION_BLOCKS);
            GpuTexture g = t.getTexture();
            return (g instanceof GlTexture gl) ? gl.glId() : 0;
        } catch (Throwable e) {
            return 0;
        }
    }

    private static Matrix4f lookAt(Matrix4f dst, float ex, float ey, float ez, float fx, float fy, float fz) {
        float ux = 0, uy = 1, uz = 0;
        if (Math.abs(fy) > 0.99f) { ux = 0; uy = 0; uz = 1; }
        return lookAtUp(dst, ex, ey, ez, fx, fy, fz, ux, uy, uz);
    }

    private static Matrix4f lookAtUp(Matrix4f dst, float ex, float ey, float ez,
                                     float fx, float fy, float fz, float ux, float uy, float uz) {
        return dst.identity().lookAt(ex, ey, ez, ex + fx, ey + fy, ez + fz, ux, uy, uz);
    }

    private static float clampCos(float c) { return Math.max(-0.9999f, Math.min(0.9999f, c)); }

    private static final float[][] FACE_DIR = {
            { 1, 0, 0, 0, -1, 0 },
            { -1, 0, 0, 0, -1, 0 },
            { 0, 1, 0, 0, 0, 1 },
            { 0, -1, 0, 0, 0, -1 },
            { 0, 0, 1, 0, -1, 0 },
            { 0, 0, -1, 0, -1, 0 },
    };

    public void dispose() {
        meshes.dispose();
        entities.dispose();
        bakeState.clear();
        OccluderCache.clear();
        CutoutGeometry.clear();
        SpotShadowAtlas.delete();
        PointShadowArray.delete();
        SunShadowCascades.delete();
        if (opaqueProgram != null) { opaqueProgram.close(); opaqueProgram = null; }
        if (cutoutProgram != null) { cutoutProgram.close(); cutoutProgram = null; }
        if (translucentProgram != null) { translucentProgram.close(); translucentProgram = null; }
        active = false;
        sunActive = false;
        spotCount = pointCount = sunCascadeCount = 0;
    }
}

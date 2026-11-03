package com.meekdev.amnetic.client.shadow.internal;

import com.meekdev.amnetic.client.compat.VanillaCompat;
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
import com.mojang.renderpearl.backend.opengl.GlStateManager;
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
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class ShadowMapPass {
    public static final ShadowMapPass INSTANCE = new ShadowMapPass();
    private static final Logger LOGGER = LoggerFactory.getLogger("Amnetic/Shadows");

    private static final float NEAR = 0.05f;
    private static final long FNV_OFFSET = 1469598103934665603L;
    private static final long FNV_PRIME = 1099511628211L;
    private static final Identifier DEPTH_VSH = Identifier.fromNamespaceAndPath("amnetic", "shaders/shadow/depth.vsh");
    private static final Identifier DEPTH_FSH = Identifier.fromNamespaceAndPath("amnetic", "shaders/shadow/depth.fsh");
    private static final Identifier CUTOUT_VSH = Identifier.fromNamespaceAndPath("amnetic", "shaders/shadow/depth_cutout.vsh");
    private static final Identifier CUTOUT_FSH = Identifier.fromNamespaceAndPath("amnetic", "shaders/shadow/depth_cutout.fsh");
    private static final Identifier TRANSLUCENT_FSH = Identifier.fromNamespaceAndPath("amnetic", "shaders/shadow/depth_translucent.fsh");

    private final OccluderMeshCache meshes = new OccluderMeshCache();
    private final SunOccluders sunBlocks = new SunOccluders();
    private final Matrix4f meshViewProj = new Matrix4f();
    private final EntityOccluders entities = new EntityOccluders();
    private final Set<Long> liveIds = new HashSet<>();
    private final HashMap<Long, long[]> bakeState = new HashMap<>();
    private final ArrayList<Light> candidates = new ArrayList<>();
    private int dirtyBaked;
    private final long[] pointSlots = new long[ShadowSettings.MAX_POINT];
    private final long[] spotSlots = new long[ShadowSettings.MAX_SPOT];
    private final HashMap<Long, Light> byId = new HashMap<>();
    private final HashMap<Long, Float> blendIn = new HashMap<>();
    private final Set<Long> wanted = new HashSet<>();
    private int pointArrayId;
    private int spotAtlasId;
    private long lastRenderNanos;
    private static final double HOLD = 0.6;
    private static final float BLEND_SECONDS = 0.4f;

    private static double dist2(Light l, double ex, double ey, double ez) {
        double dx = l.x() - ex;
        double dy = l.y() - ey;
        double dz = l.z() - ez;
        return dx * dx + dy * dy + dz * dz;
    }
    private ShadowDepthProgram opaqueProgram;
    private ShadowDepthProgram cutoutProgram;
    private ShadowDepthProgram translucentProgram;

    private final Matrix4f proj = new Matrix4f();
    private final Matrix4f view = new Matrix4f();
    private final Matrix4f viewProj = new Matrix4f();
    private final Matrix4f dynamicView = new Matrix4f();
    private final Matrix4f dynamicViewProj = new Matrix4f();
    private boolean dynamicCasters;
    private int casterFlushFrames;
    private static final int CASTER_FLUSH_FRAMES = 4;

    private final float[] spotViewProj = new float[ShadowSettings.MAX_SPOT * 16];
    private final int[] savedViewport = new int[4];

    private final float[] sunViewProj = new float[ShadowSettings.MAX_CASCADES * 16];
    private final float[] sunCascadeRadius = new float[ShadowSettings.MAX_CASCADES];
    private final Matrix4f sunCamViewProj = new Matrix4f();
    private final Vector3f camFwd = new Vector3f();
    private boolean sunActive;
    private int sunCascadeCount;
    private final Matrix4f[] cascadeViewProj = {new Matrix4f(), new Matrix4f(), new Matrix4f(), new Matrix4f()};
    private final double[] cascadeCenter = new double[ShadowSettings.MAX_CASCADES * 3];
    private final float[] cascadeDir = new float[ShadowSettings.MAX_CASCADES * 3];
    private final boolean[] cascadeBaked = new boolean[ShadowSettings.MAX_CASCADES];
    private int cascadeLayout;
    private int sunFrame;
    private static final int[][] STAGGER = {{0}, {0, 1}, {1, 2}, {1, 2, 1, 3}};
    private static final float CASCADE_DRIFT = 0.05f;
    private static final float SUN_TURN_COS = 0.9998f;

    private boolean active;
    private int spotCount;
    private int pointCount;
    private int entityBoxes;
    private boolean overflowLogged;
    private boolean atlasWarned;
    private long lastBakeNanos;
    private float partialTick;

    private final ShadowGovernor governor = new ShadowGovernor();
    private int settingsVersion = -1;
    private int sunResolution;
    private int sunCascades;
    private float sunDistance;

    private ShadowMapPass() {
        ShaderHotReload.onReload(() -> {
            if (opaqueProgram != null) opaqueProgram.close();
            if (cutoutProgram != null) cutoutProgram.close();
            if (translucentProgram != null) translucentProgram.close();
        });
    }

    public boolean isActive() {
        return active;
    }

    public int spotCount() {
        return spotCount;
    }

    public int pointCount() {
        return pointCount;
    }

    public int entityBoxes() {
        return entityBoxes;
    }

    public float[] spotViewProj() {
        return spotViewProj;
    }

    public boolean sunActive() {
        return sunActive;
    }

    public int sunCascadeCount() {
        return sunCascadeCount;
    }

    public float[] sunViewProj() {
        return sunViewProj;
    }

    public float[] sunCascadeRadius() {
        return sunCascadeRadius;
    }

    public float lastBakeMs() {
        return lastBakeNanos / 1_000_000f;
    }

    public int reduction() {
        return governor.step();
    }

    public float sunDistance() {
        return sunDistance > 0f ? sunDistance : ShadowSettings.defaults().sunDistance();
    }

    private void settle(ShadowSettings s) {
        if (s.version() == settingsVersion && sunCascades > 0) return;
        settingsVersion = s.version();
        sunResolution = governor.resolution(s.sunResolution());
        sunCascades = Math.max(1, Math.min(ShadowSettings.MAX_CASCADES, governor.cascades(s.sunCascades())));
        sunDistance = governor.distance(s.sunDistance());
    }

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
        //? if >=26.2 {
        NativeLocalShadows.INSTANCE.render(cam);
        active = NativeLocalShadows.INSTANCE.points > 0 || NativeLocalShadows.INSTANCE.spots > 0;
        pointCount = NativeLocalShadows.INSTANCE.points;
        spotCount = NativeLocalShadows.INSTANCE.spots;
        sunActive = NativeLocalShadows.INSTANCE.sunCascades > 0;
        sunCascadeCount = NativeLocalShadows.INSTANCE.sunCascades;
        active = active || sunActive;
        System.arraycopy(NativeLocalShadows.INSTANCE.sunMatrices, 0, sunViewProj, 0, sunViewProj.length);
        System.arraycopy(NativeLocalShadows.INSTANCE.sunRadii, 0, sunCascadeRadius, 0, sunCascadeRadius.length);
        return;
        //? } else {
        /*active = false;
        spotCount = 0;
        pointCount = 0;
        entityBoxes = 0;
        sunActive = false;
        sunCascadeCount = 0;

        Minecraft mc = Minecraft.getInstance();
        Level level = mc.level;
        if (!Shadows.isEnabled() || cam == null || level == null) {
            for (Light l : LightRegistry.INSTANCE.all()) l.setShadowRef(-1);
            releaseAll();
            return;
        }

        ShadowSettings s = ShadowSettings.defaults();
        int res = s.resolution();
        float maxDist2 = s.maxDistance() * s.maxDistance();
        partialTick = VanillaCompat.partialTick(false);

        candidates.clear();
        Light sun = null;
        for (Light l : LightRegistry.INSTANCE.all()) {
            l.setShadowRef(-1);
            if (!l.isEnabled() || !l.castsShadow()) continue;
            LightType t = l.type();
            if (t == LightType.DIRECTIONAL) {
                if (sun == null) sun = l;
                continue;
            }
            if (t != LightType.SPOT && t != LightType.POINT && t != LightType.TUBE
                    && t != LightType.AREA_RECT && t != LightType.AREA_DISC) continue;
            double dxe = l.x() - cam.eye.x;
            double dye = l.y() - cam.eye.y;
            double dze = l.z() - cam.eye.z;
            if (dxe * dxe + dye * dye + dze * dze > maxDist2) continue;
            candidates.add(l);
        }
        if (candidates.isEmpty() && sun == null) {
            liveIds.clear();
            meshes.retainOnly(liveIds);
            OccluderCache.retainOnly(liveIds);
            releaseAll();
            active = false;
            return;
        }

        if (opaqueProgram == null) opaqueProgram = new ShadowDepthProgram(DEPTH_VSH, DEPTH_FSH);
        if (cutoutProgram == null) cutoutProgram = new ShadowDepthProgram(CUTOUT_VSH, CUTOUT_FSH);
        if (translucentProgram == null) translucentProgram = new ShadowDepthProgram(CUTOUT_VSH, TRANSLUCENT_FSH);

        int savedFbo = GBufferTargets.INSTANCE.outerFboId();
        var mainRt = mc.gameRenderer.mainRenderTarget();
        savedViewport[0] = 0;
        savedViewport[1] = 0;
        savedViewport[2] = mainRt.width;
        savedViewport[3] = mainRt.height;
        boolean savedScissor = false;

        boolean atlasReady = false;
        boolean arrayReady = false;
        liveIds.clear();
        long bakeStart = System.nanoTime();

        try {
            GlStateManager._disableBlend(0);
            GL11.glDisable(GL11.GL_BLEND);
            GL11.glEnable(GL11.GL_DEPTH_TEST);
            GL11.glDepthFunc(GL11.GL_LESS);
            GL11.glDepthMask(true);
            GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glColorMask(true, true, true, true);
            GL11.glClearColor(1f, 1f, 1f, 1f);
            GL11.glEnable(GL11.GL_SCISSOR_TEST);

            dirtyBaked = 0;
            boolean casters = ModelRegistry.INSTANCE.hasShadowCasters() || InstanceMeshRegistry.INSTANCE.hasShadowCasters();
            if (casters) casterFlushFrames = CASTER_FLUSH_FRAMES;
            else if (casterFlushFrames > 0) casterFlushFrames--;
            dynamicCasters = casters || casterFlushFrames > 0;

            final double ex = cam.eye.x;
            final double ey = cam.eye.y;
            final double ez = cam.eye.z;
            long now = System.nanoTime();
            float dt = lastRenderNanos == 0L ? 0f : Math.min(0.1f, (now - lastRenderNanos) / 1e9f);
            lastRenderNanos = now;

            byId.clear();
            for (Light l : candidates) byId.put(l.id(), l);
            int maxPoint = s.maxPointShadows();
            int maxSpot = s.maxSpotShadows();
            releaseStale(pointSlots, maxPoint);
            releaseStale(spotSlots, maxSpot);
            candidates.sort((a, b) -> Double.compare(score(a, ex, ey, ez), score(b, ex, ey, ez)));

            wanted.clear();
            int wantPoint = 0;
            int wantSpot = 0;
            for (Light l : candidates) {
                boolean spot = l.type() == LightType.SPOT;
                if (spot ? wantSpot++ < maxSpot : wantPoint++ < maxPoint) wanted.add(l.id());
            }
            for (Light l : candidates) {
                if (!wanted.contains(l.id())) continue;
                boolean spot = l.type() == LightType.SPOT;
                long[] slots = spot ? spotSlots : pointSlots;
                if (slotOf(slots, l.id()) < 0) take(slots, spot ? maxSpot : maxPoint, l.id(), ex, ey, ez);
            }

            if (pointArrayId != 0 && pointArrayId != PointShadowArray.glTextureId()) forget(pointSlots);
            if (spotAtlasId != 0 && spotAtlasId != SpotShadowAtlas.glTextureId()) forget(spotSlots);

            int highestSpot = -1;
            int highestPoint = -1;
            for (Light l : candidates) {
                boolean spot = l.type() == LightType.SPOT;
                int slot = slotOf(spot ? spotSlots : pointSlots, l.id());
                if (slot < 0) {
                    blendIn.remove(l.id());
                    continue;
                }
                int ref;
                if (spot) {
                    if (!atlasReady) {
                        SpotShadowAtlas.ensure(res);
                        atlasReady = true;
                    }
                    bakeSpot(l, cam, level, slot, s);
                    ref = slot;
                    highestSpot = Math.max(highestSpot, slot);
                } else {
                    if (!arrayReady) {
                        PointShadowArray.ensure(s.pointFaceSize(), s.maxPointShadows());
                        arrayReady = true;
                    }
                    bakePoint(l, cam, level, slot, s);
                    ref = 1000 + slot;
                    highestPoint = Math.max(highestPoint, slot);
                }
                liveIds.add(l.id());
                long[] st = bakeState.get(l.id());
                if (st == null || st[1] != ref) {
                    blendIn.remove(l.id());
                    continue;
                }
                float blend = Math.min(1f, blendIn.getOrDefault(l.id(), 0f) + dt / BLEND_SECONDS);
                blendIn.put(l.id(), blend);
                l.setShadowRef(ref);
                l.setShadowBlend(blend * blend * (3f - 2f * blend));
            }
            spotCount = highestSpot + 1;
            pointCount = highestPoint + 1;
            pointArrayId = PointShadowArray.glTextureId();
            spotAtlasId = SpotShadowAtlas.glTextureId();
            blendIn.keySet().retainAll(liveIds);

            if (sun != null) {
                sun.setShadowRef(2000);
                bakeSun(sun, cam, level, s);
            }

            meshes.retainOnly(byId.keySet());
            OccluderCache.retainOnly(byId.keySet());
            bakeState.keySet().retainAll(liveIds);
            active = (spotCount > 0 || pointCount > 0 || sunActive);
        } finally {
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, savedFbo);
            GL11.glViewport(savedViewport[0], savedViewport[1], savedViewport[2], savedViewport[3]);
            if (savedScissor) {
                GL11.glEnable(GL11.GL_SCISSOR_TEST);
            } else {
                GL11.glDisable(GL11.GL_SCISSOR_TEST);
            }
            GL11.glColorMask(true, true, true, true);
            GL11.glClearColor(0f, 0f, 0f, 0f);
            GL11.glEnable(GL11.GL_CULL_FACE);
            GL11.glDepthFunc(VanillaCompat.depthNearerOrEqual());
            GL11.glDepthMask(true);
            GlStateManager._blendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL14.glBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GlStateManager._disableBlend(0);
            GL11.glDisable(GL11.GL_BLEND);
            GlStateManager._glUseProgram(0);
            lastBakeNanos = System.nanoTime() - bakeStart;
            if (governor.observe(lastBakeMs(), s.budgetMs())) settingsVersion = -1;
        }
    *///? }
    }

    private void bakeSpot(Light l, CameraSnapshot cam, Level level, int tile, ShadowSettings s) {
        OccluderCache.Collected col = OccluderCache.getOrCompute(l.id(), level,
                (float) l.x(), (float) l.y(), (float) l.z(), l.range());
        OccluderMesh mesh = meshes.get(l.id(), col);
        long entitySig = s.entityShadows() ? entities.gather(level, l.x(), l.y(), l.z(), l.range(), partialTick) : 0L;

        float fovDeg = Math.max(1f, 2f * (float) Math.toDegrees(Math.acos(clampCos(l.cosOuter()))));
        float far = Math.max(l.range(), NEAR + 0.1f);
        proj.identity().perspective((float) Math.toRadians(fovDeg), 1f, NEAR, far);

        viewProj.set(proj).mul(lookAt(view,
                (float) (l.x() - cam.eye.x), (float) (l.y() - cam.eye.y), (float) (l.z() - cam.eye.z),
                l.dirX(), l.dirY(), l.dirZ()));
        viewProj.get(spotViewProj, tile * 16);

        if (!shouldBake(l.id(), sigOf(col.list, l.x(), l.y(), l.z(), l.dirX(), l.dirY(), l.dirZ() + l.range() * 7f) ^ entitySig, tile, dynamicCasters, s)) {
            return;
        }
        boolean ents = entitySig != 0L && entities.build(col.anchorX, col.anchorY, col.anchorZ, s.entityModels(), partialTick);
        if (ents) entityBoxes += entities.boxCount();

        SpotShadowAtlas.bindFbo();
        int px = SpotShadowAtlas.tilePixelX(tile);
        int py = SpotShadowAtlas.tilePixelY(tile);
        int ts = SpotShadowAtlas.tileSize();
        GL11.glViewport(px, py, ts, ts);
        GL11.glScissor(px, py, ts, ts);
        GL11.glClearDepth(1.0);
        GL11.glClear(GL11.GL_DEPTH_BUFFER_BIT | GL11.GL_COLOR_BUFFER_BIT);

        viewProj.set(proj).mul(lookAt(view,
                (float) (l.x() - col.anchorX), (float) (l.y() - col.anchorY), (float) (l.z() - col.anchorZ),
                l.dirX(), l.dirY(), l.dirZ()));
        drawAll(mesh, ents, viewProj);

        dynamicViewProj.set(proj).mul(lookAt(dynamicView, 0f, 0f, 0f, l.dirX(), l.dirY(), l.dirZ()));
        ModelRegistry.INSTANCE.renderShadow(dynamicViewProj, l.x(), l.y(), l.z(), l.range());
        dynamicViewProj.set(proj).mul(lookAt(dynamicView, (float) l.x(), (float) l.y(), (float) l.z(),
                l.dirX(), l.dirY(), l.dirZ()));
        InstanceMeshRegistry.INSTANCE.renderShadow(dynamicViewProj, l.x(), l.y(), l.z(), l.range());
    }

    private void bakePoint(Light l, CameraSnapshot cam, Level level, int slot, ShadowSettings s) {
        OccluderCache.Collected col = OccluderCache.getOrCompute(l.id(), level,
                (float) l.x(), (float) l.y(), (float) l.z(), l.range());
        OccluderMesh mesh = meshes.get(l.id(), col);
        long entitySig = s.entityShadows() ? entities.gather(level, l.x(), l.y(), l.z(), l.range(), partialTick) : 0L;

        if (!shouldBake(l.id(), sigOf(col.list, l.x(), l.y(), l.z(), l.range(), 0f, 0f) ^ entitySig, 1000 + slot, dynamicCasters, s)) {
            return;
        }
        boolean ents = entitySig != 0L && entities.build(col.anchorX, col.anchorY, col.anchorZ, s.entityModels(), partialTick);
        if (ents) entityBoxes += entities.boxCount();

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
            InstanceMeshRegistry.INSTANCE.renderShadow(dynamicViewProj, l.x(), l.y(), l.z(), l.range());
        }
    }

    private void bakeSun(Light sun, CameraSnapshot cam, Level level, ShadowSettings s) {
        float dx = sun.dirX();
        float dy = sun.dirY();
        float dz = sun.dirZ();
        float dl = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (dl < 1e-5f) return;
        dx /= dl;
        dy /= dl;
        dz /= dl;

        settle(s);
        int cascades = sunCascades;
        SunShadowCascades.ensure(sunResolution, cascades);
        int res = SunShadowCascades.resolution();
        int layout = SunShadowCascades.glTextureId() * 128 + cascades * 16 + Integer.numberOfTrailingZeros(res);
        if (cascadeLayout != layout) {
            cascadeLayout = layout;
            Arrays.fill(cascadeBaked, false);
        }
        int[] stagger = STAGGER[cascades - 1];
        int extra = stagger[sunFrame % stagger.length];
        sunFrame++;

        float upx = 0f;
        float upy = 1f;
        float upz = 0f;
        if (Math.abs(dy) > 0.99f) {
            upy = 0f;
            upz = 1f;
        }
        double rxd = (double) dy * upz - (double) dz * upy;
        double ryd = (double) dz * upx - (double) dx * upz;
        double rzd = (double) dx * upy - (double) dy * upx;
        double rl = Math.sqrt(rxd * rxd + ryd * ryd + rzd * rzd);
        rxd /= rl;
        ryd /= rl;
        rzd /= rl;
        double uxd = ryd * dz - rzd * dy;
        double uyd = rzd * dx - rxd * dz;
        double uzd = rxd * dy - ryd * dx;

        cam.view.positiveZ(camFwd).negate();
        float tanH = 1f / cam.projection.m00();
        float tanV = 1f / cam.projection.m11();
        float k2 = tanH * tanH + tanV * tanV;

        float far = sunDistance;
        float[] splits = cascadeSplits(cascades, NEAR, far, s.sunSplitLambda());

        float ext = s.sunCasterExtension();

        float blockR = Math.min(s.sunBlockOccluderRadius(), far);
        int ax = (int) (Math.floor(cam.eye.x / 8.0) * 8.0 + 4.0);
        int ay = (int) (Math.floor(cam.eye.y / 8.0) * 8.0 + 4.0);
        int az = (int) (Math.floor(cam.eye.z / 8.0) * 8.0 + 4.0);
        List<OccluderMesh> blocks = blockR > 0f ? sunBlocks.update(level, ax, ay, az, blockR + 7f) : List.of();
        boolean ents = s.entityShadows() && entities.build(level, cam.eye.x, cam.eye.y, cam.eye.z,
                Math.min(far, 64f), ax, ay, az, s.entityModels(), partialTick);
        if (ents) entityBoxes += entities.boxCount();

        for (int i = 0; i < cascades; i++) {
            float a = splits[i];
            float b = splits[i + 1];
            float t = Math.min(Math.max((a + b) * (1f + k2) * 0.5f, a), b);
            float radius = (float) Math.sqrt(Math.max(
                    b * b * k2 + (t - b) * (t - b),
                    a * a * k2 + (t - a) * (t - a)));
            radius = (float) Math.ceil(radius * 2f) / 2f;

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

            int o = i * 3;
            double mx = cx - cascadeCenter[o];
            double my = cy - cascadeCenter[o + 1];
            double mz = cz - cascadeCenter[o + 2];
            boolean due = i == 0 || i == extra || !cascadeBaked[i] || sunCascadeRadius[i] != radius
                    || dx * cascadeDir[o] + dy * cascadeDir[o + 1] + dz * cascadeDir[o + 2] < SUN_TURN_COS
                    || mx * mx + my * my + mz * mz > CASCADE_DRIFT * CASCADE_DRIFT * radius * radius;
            if (!due) {
                cascadeViewProj[i].translate((float) (cam.eye.x - cascadeCenter[o]), (float) (cam.eye.y - cascadeCenter[o + 1]),
                        (float) (cam.eye.z - cascadeCenter[o + 2]), sunCamViewProj).get(sunViewProj, i * 16);
                continue;
            }

            float eyeDist = radius + ext;
            proj.setOrtho(-radius, radius, -radius, radius, 0f, 2f * radius + ext);

            SunShadowCascades.bindLayerForRender(i);
            GL11.glViewport(0, 0, res, res);
            GL11.glScissor(0, 0, res, res);
            GL11.glClearDepth(1.0);
            GL11.glClear(GL11.GL_DEPTH_BUFFER_BIT);

            float eax = (float) (cx - ax) - dx * eyeDist;
            float eay = (float) (cy - ay) - dy * eyeDist;
            float eaz = (float) (cz - az) - dz * eyeDist;
            viewProj.set(proj).mul(lookAtUp(view, eax, eay, eaz, dx, dy, dz, upx, upy, upz));
            drawAll(blocks, ax, ay, az, ents, viewProj);

            dynamicViewProj.set(proj).mul(lookAtUp(dynamicView,
                    -dx * eyeDist, -dy * eyeDist, -dz * eyeDist, dx, dy, dz, upx, upy, upz));
            ModelRegistry.INSTANCE.renderShadow(dynamicViewProj, cx, cy, cz, radius + ext);

            float ecx = (float) (cx - cam.eye.x) - dx * eyeDist;
            float ecy = (float) (cy - cam.eye.y) - dy * eyeDist;
            float ecz = (float) (cz - cam.eye.z) - dz * eyeDist;
            sunCamViewProj.set(proj).mul(lookAtUp(dynamicView, ecx, ecy, ecz, dx, dy, dz, upx, upy, upz));
            InstanceMeshRegistry.INSTANCE.renderShadow(sunCamViewProj);
            sunCamViewProj.get(sunViewProj, i * 16);
            sunCascadeRadius[i] = radius;
            cascadeViewProj[i].set(proj).mul(lookAtUp(dynamicView, -dx * eyeDist, -dy * eyeDist, -dz * eyeDist, dx, dy, dz, upx, upy, upz));
            cascadeCenter[o] = cx;
            cascadeCenter[o + 1] = cy;
            cascadeCenter[o + 2] = cz;
            cascadeDir[o] = dx;
            cascadeDir[o + 1] = dy;
            cascadeDir[o + 2] = dz;
            cascadeBaked[i] = true;
        }

        sunCascadeCount = cascades;
        sunActive = true;
    }

    private static float[] cascadeSplits(int cascades, float near, float far, float lambda) {
        float[] splits = new float[cascades + 1];
        splits[0] = near;
        splits[cascades] = far;
        for (int i = 1; i < cascades; i++) {
            float f = i / (float) cascades;
            float log = near * (float) Math.pow(far / near, f);
            float uni = near + (far - near) * f;
            splits[i] = lambda * log + (1f - lambda) * uni;
        }
        return splits;
    }

    private double score(Light l, double ex, double ey, double ez) {
        double d = dist2(l, ex, ey, ez);
        long[] slots = l.type() == LightType.SPOT ? spotSlots : pointSlots;
        return slotOf(slots, l.id()) >= 0 ? d * HOLD : d;
    }

    private static int slotOf(long[] slots, long id) {
        for (int i = 0; i < slots.length; i++) {
            if (slots[i] == id) return i;
        }
        return -1;
    }

    private void take(long[] slots, int cap, long id, double ex, double ey, double ez) {
        int pick = -1;
        double far = -1;
        for (int i = 0; i < cap; i++) {
            if (slots[i] == 0L) {
                pick = i;
                break;
            }
            if (wanted.contains(slots[i])) continue;
            Light holder = byId.get(slots[i]);
            double d = holder == null ? Double.MAX_VALUE : dist2(holder, ex, ey, ez);
            if (d > far) {
                far = d;
                pick = i;
            }
        }
        if (pick < 0) return;
        if (slots[pick] != 0L) release(slots, pick);
        slots[pick] = id;
    }

    private void releaseStale(long[] slots, int cap) {
        for (int i = 0; i < slots.length; i++) {
            if (slots[i] != 0L && (i >= cap || !byId.containsKey(slots[i]))) release(slots, i);
        }
    }

    private void release(long[] slots, int i) {
        bakeState.remove(slots[i]);
        blendIn.remove(slots[i]);
        slots[i] = 0L;
    }

    private void forget(long[] slots) {
        for (long id : slots) {
            if (id != 0L) bakeState.remove(id);
        }
    }

    private void releaseAll() {
        Arrays.fill(pointSlots, 0L);
        Arrays.fill(spotSlots, 0L);
        bakeState.clear();
        blendIn.clear();
        lastRenderNanos = 0L;
    }

    private boolean shouldBake(long id, long sig, int slot, boolean ents, ShadowSettings s) {
        long[] st = bakeState.get(id);
        boolean dirty = st == null || st[0] != sig || st[1] != slot || ents || st[2] == 1L;
        if (!dirty) {
            st[1] = slot;
            st[2] = 0L;
            return false;
        }
        int budget = s.bakeBudget();
        if (st != null && budget > 0 && dirtyBaked >= budget) return false;
        dirtyBaked++;
        bakeState.put(id, new long[]{sig, slot, ents ? 1L : 0L});
        return true;
    }

    private static long sigOf(List<?> list, double x, double y, double z, float dx, float dy, float dz) {
        long h = FNV_OFFSET;
        h = (h ^ System.identityHashCode(list)) * FNV_PRIME;
        h = (h ^ Float.floatToIntBits((float) x)) * FNV_PRIME;
        h = (h ^ Float.floatToIntBits((float) y)) * FNV_PRIME;
        h = (h ^ Float.floatToIntBits((float) z)) * FNV_PRIME;
        h = (h ^ Float.floatToIntBits(dx)) * FNV_PRIME;
        h = (h ^ Float.floatToIntBits(dy)) * FNV_PRIME;
        h = (h ^ Float.floatToIntBits(dz)) * FNV_PRIME;
        return h;
    }

    private void drawAll(OccluderMesh mesh, boolean ents, Matrix4f bakeVP) {
        drawAll(mesh == null ? List.of() : List.of(mesh), mesh == null ? 0 : mesh.anchorX,
                mesh == null ? 0 : mesh.anchorY, mesh == null ? 0 : mesh.anchorZ, ents, bakeVP);
    }

    private void drawAll(List<OccluderMesh> meshes, int ax, int ay, int az, boolean ents, Matrix4f bakeVP) {
        boolean cutout = false;
        boolean translucent = false;
        opaqueProgram.begin();
        opaqueProgram.setViewProj(bakeVP);
        if (ents) entities.draw();
        for (OccluderMesh mesh : meshes) {
            cutout |= mesh.hasCutout();
            translucent |= mesh.hasTranslucent();
            if (!mesh.hasOpaque()) continue;
            opaqueProgram.setViewProj(placed(bakeVP, mesh, ax, ay, az));
            mesh.drawOpaque();
        }
        if (!cutout && !translucent) return;
        int atlas = blockAtlasGlId();
        if (atlas == 0) {
            if (!atlasWarned) {
                atlasWarned = true;
                LOGGER.warn("block atlas GL id unavailable; textured shadows disabled this session");
            }
            return;
        }
        GlStateManager._activeTexture(GL13.GL_TEXTURE0);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, atlas);
        GL33.glBindSampler(0, 0);
        if (cutout) {
            cutoutProgram.begin();
            cutoutProgram.setSampler("uAtlas", 0);
            for (OccluderMesh mesh : meshes) {
                if (!mesh.hasCutout()) continue;
                cutoutProgram.setViewProj(placed(bakeVP, mesh, ax, ay, az));
                mesh.drawCutout();
            }
        }
        if (translucent) {
            translucentProgram.begin();
            translucentProgram.setSampler("uAtlas", 0);
            for (OccluderMesh mesh : meshes) {
                if (!mesh.hasTranslucent()) continue;
                translucentProgram.setViewProj(placed(bakeVP, mesh, ax, ay, az));
                mesh.drawTranslucent();
            }
        }
    }

    private Matrix4f placed(Matrix4f bakeVP, OccluderMesh mesh, int ax, int ay, int az) {
        return meshViewProj.set(bakeVP).translate(mesh.anchorX - ax, mesh.anchorY - ay, mesh.anchorZ - az);
    }

    private static int blockAtlasGlId() {
        try {
            AbstractTexture t = Minecraft.getInstance().getTextureManager().getTexture(TextureAtlas.LOCATION_BLOCKS);
            return VanillaCompat.glId(t);
        } catch (Throwable e) {
            return 0;
        }
    }

    private static Matrix4f lookAt(Matrix4f dst, float ex, float ey, float ez, float fx, float fy, float fz) {
        boolean vertical = Math.abs(fy) > 0.99f;
        return lookAtUp(dst, ex, ey, ez, fx, fy, fz, 0, vertical ? 0 : 1, vertical ? 1 : 0);
    }

    private static Matrix4f lookAtUp(Matrix4f dst, float ex, float ey, float ez,
                                     float fx, float fy, float fz, float ux, float uy, float uz) {
        return dst.identity().lookAt(ex, ey, ez, ex + fx, ey + fy, ez + fz, ux, uy, uz);
    }

    private static float clampCos(float c) {
        return Math.max(-0.9999f, Math.min(0.9999f, c));
    }

    private static final float[][] FACE_DIR = {
            { 1, 0, 0, 0, -1, 0 },
            { -1, 0, 0, 0, -1, 0 },
            { 0, 1, 0, 0, 0, 1 },
            { 0, -1, 0, 0, 0, -1 },
            { 0, 0, 1, 0, -1, 0 },
            { 0, 0, -1, 0, -1, 0 },
    };

    public void dispose() {
        //? if >=26.2 {
        NativeLocalShadows.INSTANCE.close();
        //? }
        meshes.dispose();
        sunBlocks.clear();
        entities.dispose();
        releaseAll();
        OccluderCache.clear();
        CutoutGeometry.clear();
        SpotShadowAtlas.delete();
        PointShadowArray.delete();
        SunShadowCascades.delete();
        if (opaqueProgram != null) {
            opaqueProgram.close();
            opaqueProgram = null;
        }
        if (cutoutProgram != null) {
            cutoutProgram.close();
            cutoutProgram = null;
        }
        if (translucentProgram != null) {
            translucentProgram.close();
            translucentProgram = null;
        }
        active = false;
        sunActive = false;
        spotCount = 0;
        pointCount = 0;
        sunCascadeCount = 0;
    }
}

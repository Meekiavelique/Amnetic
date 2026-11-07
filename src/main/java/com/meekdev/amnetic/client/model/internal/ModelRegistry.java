package com.meekdev.amnetic.client.model.internal;

import com.meekdev.amnetic.client.compat.VanillaCompat;
import com.meekdev.amnetic.client.material.internal.ShadingModelRegistry;
import com.meekdev.amnetic.client.render.GlState;
import com.meekdev.amnetic.client.render.LevelCamera;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

import com.meekdev.amnetic.client.camera.internal.FrameView;
import com.meekdev.amnetic.client.dev.ShaderHotReload;
import com.meekdev.amnetic.client.gbuffer.GBuffer;
import com.meekdev.amnetic.client.gbuffer.internal.GBufferTargets;
import com.meekdev.amnetic.client.instanced.InstanceRenderContext;
import com.meekdev.amnetic.client.instanced.RenderState;
import com.meekdev.amnetic.client.instanced.internal.MainTargetFramebuffer;
import com.meekdev.amnetic.client.model.KeptDraws;
import com.meekdev.amnetic.client.model.Model;
import com.meekdev.amnetic.client.model.ModelConfig;
import com.meekdev.amnetic.client.ibl.internal.EnvProbe;
import com.meekdev.amnetic.client.model.ModelLighting;
import com.mojang.renderpearl.backend.opengl.GlStateManager;
import org.lwjgl.opengl.GL13;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.Vec3;
import org.joml.FrustumIntersection;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ModelRegistry {
    public static final ModelRegistry INSTANCE = new ModelRegistry();
    private static final Logger LOG = LoggerFactory.getLogger("Amnetic/Model");

    private final FrustumIntersection frustum = new FrustumIntersection();
    private final Vector3f boundsMin = new Vector3f();
    private final Vector3f boundsMax = new Vector3f();
    private final Vector3f cullMin = new Vector3f();
    private final Vector3f cullMax = new Vector3f();
    private final Matrix4f relScratch = new Matrix4f();
    private final Vector3f poseMin = new Vector3f();
    private final Vector3f poseMax = new Vector3f();
    private final Vector3f poseScratch = new Vector3f();
    private final BlockPos.MutableBlockPos lightSamplePos = new BlockPos.MutableBlockPos();
    private final ArrayList<GpuModel.DrawInstance> mainPool = new ArrayList<>();
    private final ArrayList<GpuModel.DrawInstance> shadowPool = new ArrayList<>();

    private final CopyOnWriteArrayList<Model> models = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<Consumer<InstanceRenderContext>> frameCallbacks = new CopyOnWriteArrayList<>();
    private final Map<Identifier, Model> cache = new ConcurrentHashMap<>();
    private final Map<Model, Identifier> cacheKeys = new ConcurrentHashMap<>();
    private final Map<Model, Integer> refCounts = new ConcurrentHashMap<>();
    private ModelShader shader;
    private final Map<String, ModelShader> customShaders = new ConcurrentHashMap<>();
    private ModelShadowProgram shadowProgram;
    private static final int UPLOADS_PER_FRAME = 4;
    private int frameUploadBudget;

    private ModelRegistry() {
        ShaderHotReload.onReload(this::invalidatePrograms);
    }

    private void invalidatePrograms() {
        for (ModelShader prog : customShaders.values()) {
            if (prog != null && prog != shader) prog.close();
        }
        customShaders.clear();
        if (shader != null) {
            shader.close();
            shader = null;
        }
        if (shadowProgram != null) {
            shadowProgram.close();
            shadowProgram = null;
        }
    }

    public void warmup() {
        if (models.isEmpty()) return;
        if (shader == null) {
            try {
                shader = ModelShader.load();
            } catch (Exception e) {
                LOG.error("Amnetic: model shader warmup failed", e);
            }
        }
    }

    public synchronized Model acquire(Identifier id) {
        Model model = cache.get(id);
        if (model != null) {
            refCounts.merge(model, 1, Integer::sum);
        }
        return model;
    }

    public synchronized Model registerCached(Identifier id, Model model) {
        cache.put(id, model);
        cacheKeys.put(model, id);
        refCounts.put(model, 1);
        models.add(model);
        return model;
    }

    public synchronized Model register(Model model) {
        refCounts.put(model, 1);
        models.add(model);
        return model;
    }

    public synchronized void release(Model model) {
        Integer count = refCounts.get(model);
        if (count == null) {
            return;
        }
        if (count > 1) {
            refCounts.put(model, count - 1);
            return;
        }
        refCounts.remove(model);
        models.remove(model);
        Identifier key = cacheKeys.remove(model);
        if (key != null) {
            cache.remove(key);
        }
        model.internalDispose();
    }

    public void onFrame(Consumer<InstanceRenderContext> cb) {
        frameCallbacks.add(cb);
    }

    public void flush(LevelCamera cam) {
        GlReaper.drain();
        DrawCounts.begin();
        try {
            render(cam, false);
        } finally {
            DrawCounts.end();
        }
    }

    public void flushCapture(LevelCamera cam) {
        render(cam, true);
    }

    private void render(LevelCamera cam, boolean capture) {
        for (Model m : models) {
            m.internalUploadPendingIfAny();
        }

        if (cam == null) {
            return;
        }

        if (!frameCallbacks.isEmpty()) {
            InstanceRenderContext ctx = buildContext(cam);
            for (Consumer<InstanceRenderContext> cb : frameCallbacks) {
                try {
                    cb.accept(ctx);
                } catch (Exception e) {
                    LOG.error("Amnetic: model frame callback failed", e);
                }
            }
        }

        boolean any = false;
        for (Model m : models) {
            if (m.isReady() && m.internalHasPending()) {
                any = true;
                break;
            }
        }
        if (!any) {
            return;
        }

        Vec3 camPos = cam.pos;
        ClientLevel level = Minecraft.getInstance().level;
        Matrix4f view = capture
                ? new Matrix4f(cam.viewRotationMatrix)
                : FrameView.INSTANCE.get(new Matrix4f(), cam.viewRotationMatrix);
        Matrix4f projection = capture
                ? new Matrix4f(cam.projectionMatrix)
                : FrameView.INSTANCE.getProjection(new Matrix4f(), cam.projectionMatrix);
        Matrix4f projView = projection.mul(view);

        if (ShadingModelRegistry.INSTANCE.consumeVertexDirty() | ShadingModelRegistry.INSTANCE.consumeSurfaceDirty()) {
            invalidatePrograms();
        }

        if (shader == null) {
            try {
                shader = ModelShader.load();
            } catch (Exception e) {
                LOG.error("Amnetic: failed to load model shader", e);
                clearAllPending();
                return;
            }
        }
        if (!shader.ready()) {
            clearAllPending();
            return;
        }

        EnvProbe.INSTANCE.update(level != null ? (float) ((level.getGameTime() % 24000L) / 24000.0) : 0.25f);

        float time = 0f;
        if (level != null) {
            float partial = VanillaCompat.partialTick(false);
            time = (level.getGameTime() + partial) / 20.0f;
        }

        frustum.set(projView);
        lodCamera = camPos;
        lodScale = projection.m11() * Minecraft.getInstance().getWindow().getHeight() / 2f;
        frameUploadBudget = UPLOADS_PER_FRAME;
        ModelShader current = null;
        blendedCount = 0;
        try {
            for (Model model : models) {
                if (model.isReady() && model.internalHasPending()) {
                    ModelShader prog = programFor(model.internalConfig());
                    if (prog != current) {
                        prepareProgram(prog, projView, time);
                        current = prog;
                    }
                    renderModel(model, prog, camPos, level, capture);
                }
            }
            renderBlended(projView, time, capture, current);
        } finally {
            for (Model model : models) {
                model.internalClearPending();
            }
            for (int n = 0; n < blendedCount; n++) {
                blended.get(n).model = null;
            }
            GlStateManager._glUseProgram(0);
            GlStateManager._glBindVertexArray(0);
        }
    }

    private ModelShader programFor(ModelConfig config) {
        if (!config.hasCustomShader()) {
            return shader;
        }
        String key = config.customVsh() + "|" + config.customFsh();
        ModelShader prog = customShaders.get(key);
        if (prog == null) {
            try {
                prog = ModelShader.loadCustom(config.customVsh(), config.customFsh());
            } catch (Exception e) {
                LOG.error("Amnetic: failed to load custom model shader {} (using default)", key, e);
                prog = shader;
            }
            customShaders.put(key, prog);
        }
        return prog.ready() ? prog : shader;
    }

    private void prepareProgram(ModelShader prog, Matrix4f projView, float time) {
        prog.bind();
        prog.uploadProjView(projView);
        GlStateManager._activeTexture(GL13.GL_TEXTURE0 + 5);
        GL11.glBindTexture(GL13.GL_TEXTURE_CUBE_MAP, EnvProbe.INSTANCE.glId());
        GlStateManager._activeTexture(GL13.GL_TEXTURE0 + ModelShader.LIGHTMAP_UNIT);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, VanillaCompat.levelLightmapGlId());
        GlStateManager._activeTexture(GL13.GL_TEXTURE0);
        prog.uploadEnv(EnvProbe.INSTANCE.isReady(), EnvProbe.INSTANCE.maxLod());
        prog.uploadLighting(ModelLighting.INSTANCE);
        prog.uploadTime(time);
    }

    private int lodFor(List<GpuModel.DrawInstance> instances) {
        float best = Float.MAX_VALUE;
        for (GpuModel.DrawInstance di : instances) {
            Matrix4f w = di.world();
            float d2 = w.m30() * w.m30() + w.m31() * w.m31() + w.m32() * w.m32();
            if (d2 < best) {
                best = d2;
            }
        }
        float d = (float) Math.sqrt(best);
        if (d < 22f) return 0;
        if (d < 45f) return 1;
        if (d < 80f) return 2;
        return 3;
    }

    private void renderModel(Model model, ModelShader prog, Vec3 camPos, ClientLevel level, boolean capture) {
        model.internalEnsureLod();
        boolean upload = !model.internalGpu().isUploaded();
        if (upload && frameUploadBudget <= 0) {
            return;
        }

        ModelConfig config = model.internalConfig();
        boolean useGBuffer = !capture && GBuffer.isEnabled() && config.writeGBuffer();
        GBufferTargets gbuffer = GBufferTargets.INSTANCE;
        int prevFbo = useGBuffer ? gbuffer.bind() : MainTargetFramebuffer.bind();
        if (useGBuffer) gbuffer.program(prog.programId());
        RenderState state = config.renderState() == null ? RenderState.DEFAULT : config.renderState();
        state.apply();
        GlStateManager._depthFunc(VanillaCompat.depthNearerOrEqual());
        try {
            float defaultEmissive = config.isEmissive() ? config.emissiveStrength() : 1f;
            emissiveGroups.clear();
            for (Model.Draw draw : model.internalPending()) {
                float value = Float.isNaN(draw.emissive()) ? defaultEmissive : draw.emissive();
                if (!emissiveGroups.contains(value)) {
                    emissiveGroups.add(value);
                }
            }
            KeptDraws kept = model.internalKept();
            if (kept != null && kept.size() > 0) {
                if (!emissiveGroups.contains(defaultEmissive)) emissiveGroups.add(defaultEmissive);
                kept.internalNextFrame();
            }
            if (emissiveGroups.isEmpty()) {
                emissiveGroups.add(defaultEmissive);
            }
            for (int group = 0; group < emissiveGroups.size(); group++) {
                float value = emissiveGroups.get(group);
                prog.uploadEmissiveStrength(value);
                List<GpuModel.DrawInstance> instances =
                        toInstances(model, camPos, level, value, defaultEmissive);
                if (!instances.isEmpty()) {
                    if (upload) {
                        frameUploadBudget--;
                        upload = false;
                    }
                    int fallback = config.isLod() ? lodFor(instances) : 0;
                    for (GpuModel.DrawInstance instance : instances) {
                        if (instance.lod < 0) instance.lod = fallback;
                    }
                    group(instances);
                    model.internalGpu().drawSolid(prog, instances, fallback,
                            (part, instance) -> later(model, prog, part, instance, value));
                }
            }
            if (useGBuffer) {
                gbuffer.setPopulated(true);
                if (config.isEmissive() || model.internalGpu().mayEmit(model.internalPending())
                        || kept != null && model.internalGpu().mayEmit(kept.internalDraws())) gbuffer.markEmissive();
            }
        } catch (Exception e) {
            LOG.error("Amnetic: error rendering model {}", model.name(), e);
        } finally {
            state.restore();
            if (useGBuffer) {
                gbuffer.restore(prevFbo);
            } else {
                MainTargetFramebuffer.restore(prevFbo);
            }
        }
    }

    private final ArrayList<Float> emissiveGroups = new ArrayList<>();

    private static final class Blended {
        Model model;
        ModelShader prog;
        int part;
        float emissive;
        float distance;
        final GpuModel.DrawInstance instance = new GpuModel.DrawInstance();
    }

    private final ArrayList<Blended> blended = new ArrayList<>();
    private final ArrayList<Blended> sorted = new ArrayList<>();
    private int blendedCount;

    private void later(Model model, ModelShader prog, int part, GpuModel.DrawInstance instance, float emissive) {
        while (blended.size() <= blendedCount) blended.add(new Blended());
        Blended item = blended.get(blendedCount++);
        item.model = model;
        item.prog = prog;
        item.part = part;
        item.emissive = emissive;
        item.instance.set(instance);
        item.distance = model.internalGpu().distance(part, instance);
    }

    private void renderBlended(Matrix4f projView, float time, boolean capture, ModelShader current) {
        if (blendedCount == 0) {
            return;
        }
        sorted.clear();
        sorted.addAll(blended.subList(0, blendedCount));
        sorted.sort((a, b) -> Float.compare(b.distance, a.distance));
        boolean useGBuffer = false;
        int prevFbo = -1;
        RenderState state = null;
        try {
            for (Blended item : sorted) {
                ModelConfig config = item.model.internalConfig();
                boolean wantGBuffer = !capture && GBuffer.isEnabled() && config.writeGBuffer();
                if (prevFbo == -1 || wantGBuffer != useGBuffer) {
                    if (prevFbo != -1) restoreTarget(useGBuffer, prevFbo);
                    useGBuffer = wantGBuffer;
                    prevFbo = useGBuffer ? GBufferTargets.INSTANCE.bind() : MainTargetFramebuffer.bind();
                    colorMasks(!useGBuffer);
                }
                RenderState wanted = config.renderState() == null ? RenderState.DEFAULT : config.renderState();
                if (wanted != state) {
                    if (state != null) state.restore();
                    state = wanted;
                    state.apply();
                    GlStateManager._depthFunc(VanillaCompat.depthNearerOrEqual());
                }
                if (item.prog != current) {
                    prepareProgram(item.prog, projView, time);
                    current = item.prog;
                }
                item.prog.uploadEmissiveStrength(item.emissive);
                try {
                    item.model.internalGpu().drawBlended(item.prog, item.part, item.instance);
                } catch (Exception e) {
                    LOG.error("Amnetic: error rendering model {}", item.model.name(), e);
                }
            }
        } finally {
            if (state != null) state.restore();
            if (prevFbo != -1) {
                colorMasks(true);
                restoreTarget(useGBuffer, prevFbo);
            }
        }
    }

    private static void colorMasks(boolean write) {
        for (int buffer : new int[] {1, 2, 4, 5, 6}) {
            GL30.glColorMaski(buffer, write, write, write, write);
        }
    }

    private static void restoreTarget(boolean gbuffer, int prevFbo) {
        if (gbuffer) {
            GBufferTargets.INSTANCE.restore(prevFbo);
        } else {
            MainTargetFramebuffer.restore(prevFbo);
        }
    }

    private List<GpuModel.DrawInstance> toInstances(Model model, Vec3 camPos, ClientLevel level) {
        return toInstances(model, camPos, level, Float.NaN, Float.NaN);
    }

    private List<GpuModel.DrawInstance> toInstances(Model model, Vec3 camPos, ClientLevel level,
                                                    float wantEmissive, float defaultEmissive) {
        List<Model.Draw> pending = model.internalPending();
        KeptDraws kept = model.internalKept();
        int fresh = pending.size();
        int total = fresh + (kept == null ? 0 : kept.size());
        float[] boxes = kept == null ? null : kept.internalBoxes();
        model.internalGpu().localBounds(boundsMin, boundsMax);
        boolean cull = model.internalConfig().frustumCull();
        int n = 0;
        for (int index = 0; index < total; index++) {
            int keptAt = index - fresh;
            Model.Draw draw = keptAt < 0 ? pending.get(index) : kept.internalDraw(keptAt);
            if (!Float.isNaN(wantEmissive)) {
                float value = Float.isNaN(draw.emissive()) ? defaultEmissive : draw.emissive();
                if (Float.compare(value, wantEmissive) != 0) {
                    continue;
                }
            }
            Matrix4f world = draw.world();
            Matrix4f camRel = relScratch.set(world);
            camRel.m30(world.m30() - (float) camPos.x);
            camRel.m31(world.m31() - (float) camPos.y);
            camRel.m32(world.m32() - (float) camPos.z);
            if (cull) {
                Model.Piece piece = draw.look() == null ? null : draw.look().piece();
                if (keptAt >= 0) {
                    int at = keptAt * 6;
                    cullMin.set(boxes[at] - (float) camPos.x, boxes[at + 1] - (float) camPos.y, boxes[at + 2] - (float) camPos.z);
                    cullMax.set(boxes[at + 3] - (float) camPos.x, boxes[at + 4] - (float) camPos.y, boxes[at + 5] - (float) camPos.z);
                } else if (piece != null) {
                    camRel.transformAab(piece.min(), piece.max(), cullMin, cullMax);
                } else if (draw.pose() != null) {
                    posedBounds(draw.pose(), boundsMin, boundsMax, poseMin, poseMax);
                    camRel.transformAab(poseMin, poseMax, cullMin, cullMax);
                } else {
                    camRel.transformAab(boundsMin, boundsMax, cullMin, cullMax);
                }
                if (!frustum.testAab(cullMin.x, cullMin.y, cullMin.z, cullMax.x, cullMax.y, cullMax.z)) {
                    continue;
                }
            }
            float block = 1f;
            float sky = 0f;
            if (keptAt >= 0 && level != null) {
                float[] light = kept.internalLight();
                if (kept.internalRelight(keptAt)) {
                    lightSamplePos.set(world.m30(), world.m31(), world.m32());
                    light[keptAt * 2] = level.getBrightness(LightLayer.BLOCK, lightSamplePos);
                    light[keptAt * 2 + 1] = level.getBrightness(LightLayer.SKY, lightSamplePos);
                }
                block = light[keptAt * 2] / 15f;
                sky = Math.max(0, light[keptAt * 2 + 1] - level.getSkyDarken()) / 15f;
            } else if (level != null) {
                lightSamplePos.set(world.m30(), world.m31(), world.m32());
                block = level.getBrightness(LightLayer.BLOCK, lightSamplePos) / 15f;
                sky = Math.max(0, level.getBrightness(LightLayer.SKY, lightSamplePos) - level.getSkyDarken()) / 15f;
            }
            if (!Float.isNaN(draw.blockLight())) {
                block = draw.blockLight();
            }
            if (!Float.isNaN(draw.skyLight())) {
                sky = draw.skyLight();
            }
            GpuModel.DrawInstance instance = pooled(mainPool, n++);
            instance.set(camRel, draw.pose(), block, sky, draw.inputs(), draw.look());
            if (keptAt >= 0) {
                int[] levels = kept.internalLevels();
                instance.lod = levels[keptAt] = level(model, draw, camRel, levels[keptAt]);
                continue;
            }
            Model.Moved moved = draw.key() == null ? null : model.internalMoved(draw.key());
            if (moved != null) moved(moved, draw, camPos, instance);
            instance.lod = level(model, draw, camRel, moved == null ? -1 : moved.lod);
            if (moved != null) moved.lod = instance.lod;
        }
        return mainPool.subList(0, n);
    }

    private Vec3 lodCamera = Vec3.ZERO;
    private float lodScale = 1000f;
    private final Vector3f lodCentre = new Vector3f();
    private final Vector3f lodAxes = new Vector3f();

    private int level(Model model, Model.Draw draw, Matrix4f camRel, int current) {
        float pixels = draw.lodPixels();
        if (Float.isNaN(pixels)) return -1;
        float[] errors = model.internalLodErrors();
        if (pixels <= 0 || errors == null) return 0;
        camRel.getScale(lodAxes);
        float scale = Math.max(lodAxes.x, Math.max(lodAxes.y, lodAxes.z));
        camRel.transformPosition(model.center(), lodCentre);
        float near = lodCentre.length() - model.radius() * scale;
        if (near <= 0.05f) return 0;
        return ModelLod.pick(errors, lodScale * scale / near, pixels, current);
    }

    private static void group(List<GpuModel.DrawInstance> instances) {
        if (instances.size() < 2) return;
        boolean sorted = true;
        GpuModel.DrawInstance last = null;
        for (GpuModel.DrawInstance instance : instances) {
            instance.batch = (instance.look == null ? 0 : instance.look.contentHash()) * 31
                    + (instance.inputs == null ? 0 : instance.inputs.contentHash());
            if (last != null && order(last, instance) > 0) sorted = false;
            last = instance;
        }
        if (!sorted) instances.sort(ModelRegistry::order);
    }

    private static int order(GpuModel.DrawInstance a, GpuModel.DrawInstance b) {
        int c = Integer.compare(a.batch, b.batch);
        return c != 0 ? c : Integer.compare(a.lod, b.lod);
    }

    private final Matrix4f lastScratch = new Matrix4f();

    private void moved(Model.Moved m, Model.Draw draw, Vec3 camPos, GpuModel.DrawInstance instance) {
        long frame = GBuffer.frame();
        if (m.frame != frame) {
            if (m.frame == frame - 1) {
                m.lastWorld.set(m.world);
                Matrix4f[] spare = m.lastPose;
                m.lastPose = m.pose;
                m.pose = spare;
            } else {
                m.lastWorld.set(draw.world());
                m.lastPose = copy(draw.pose(), m.lastPose);
            }
            m.frame = frame;
        }
        m.world.set(draw.world());
        m.pose = copy(draw.pose(), m.pose);
        Matrix4f last = lastScratch.set(m.lastWorld);
        last.m30(m.lastWorld.m30() - (float) camPos.x);
        last.m31(m.lastWorld.m31() - (float) camPos.y);
        last.m32(m.lastWorld.m32() - (float) camPos.z);
        instance.moved(last, draw.pose() == null ? null : m.lastPose);
    }

    private static Matrix4f[] copy(Matrix4f[] from, Matrix4f[] into) {
        if (from == null) return into;
        if (into == null || into.length != from.length) {
            into = new Matrix4f[from.length];
            for (int i = 0; i < into.length; i++) into[i] = new Matrix4f();
        }
        for (int i = 0; i < from.length; i++) {
            if (from[i] == null) into[i].identity();
            else into[i].set(from[i]);
        }
        return into;
    }

    private void posedBounds(Matrix4f[] pose, Vector3f restMin, Vector3f restMax,
                             Vector3f outMin, Vector3f outMax) {
        outMin.set(restMin);
        outMax.set(restMax);
        float ex = Math.max(Math.abs(restMin.x), Math.abs(restMax.x));
        float ey = Math.max(Math.abs(restMin.y), Math.abs(restMax.y));
        float ez = Math.max(Math.abs(restMin.z), Math.abs(restMax.z));
        for (Matrix4f bone : pose) {
            if (bone == null) {
                continue;
            }
            bone.getTranslation(poseScratch);
            outMin.set(Math.min(outMin.x, poseScratch.x - ex),
                    Math.min(outMin.y, poseScratch.y - ey),
                    Math.min(outMin.z, poseScratch.z - ez));
            outMax.set(Math.max(outMax.x, poseScratch.x + ex),
                    Math.max(outMax.y, poseScratch.y + ey),
                    Math.max(outMax.z, poseScratch.z + ez));
        }
    }

    private static GpuModel.DrawInstance pooled(ArrayList<GpuModel.DrawInstance> pool, int index) {
        while (pool.size() <= index) pool.add(new GpuModel.DrawInstance());
        return pool.get(index);
    }

    public boolean hasShadowCasters() {
        for (Model m : models) {
            KeptDraws kept = m.internalKept();
            if (m.isReady() && m.internalConfig().castsShadow() && (!m.internalLastFrameDraws().isEmpty() || kept != null && kept.size() > 0)) {
                return true;
            }
        }
        return false;
    }

    public void renderShadow(Matrix4f lightViewProj, double lightX, double lightY, double lightZ, float range) {
        if (!hasShadowCasters()) {
            return;
        }
        if (shadowProgram == null) {
            try {
                shadowProgram = ModelShadowProgram.load();
            } catch (Exception e) {
                LOG.error("Amnetic: failed to load model shadow shader", e);
                return;
            }
        }
        if (!shadowProgram.ready()) return;

        shadowProgram.bind();
        shadowProgram.uploadProjView(lightViewProj);
        float r2 = range * range;
        try {
            for (Model model : models) {
                if (!model.isReady() || !model.internalConfig().castsShadow()) {
                    continue;
                }
                List<Model.Draw> draws = model.internalLastFrameDraws();
                KeptDraws kept = model.internalKept();
                if (draws.isEmpty() && (kept == null || kept.size() == 0)) {
                    continue;
                }
                List<GpuModel.DrawInstance> instances = toShadowInstances(model, draws,
                        kept == null ? List.of() : kept.internalDraws(), lightX, lightY, lightZ, r2);
                if (instances.isEmpty()) {
                    continue;
                }
                try {
                    model.internalGpu().drawShadow(shadowProgram, instances);
                } catch (Exception e) {
                    LOG.error("Amnetic: error rendering shadow for model {}", model.name(), e);
                }
            }
        } finally {
            GlStateManager._glUseProgram(0);
            GlState.resetVertexArray();
        }
    }

    private final Matrix4f shadowCamRel = new Matrix4f();

    private List<GpuModel.DrawInstance> toShadowInstances(Model model, List<Model.Draw> draws, List<Model.Draw> kept,
                                                          double lx, double ly, double lz, float r2) {
        int n = 0;
        boolean levels = false;
        int fresh = draws.size();
        for (int index = 0; index < fresh + kept.size(); index++) {
            Model.Draw draw = index < fresh ? draws.get(index) : kept.get(index - fresh);
            Matrix4f world = draw.world();
            double dx = world.m30() - lx, dy = world.m31() - ly, dz = world.m32() - lz;
            if (dx * dx + dy * dy + dz * dz > r2) {
                continue;
            }
            Matrix4f lightRel = relScratch.set(world);
            lightRel.m30((float) dx);
            lightRel.m31((float) dy);
            lightRel.m32((float) dz);
            GpuModel.DrawInstance instance = pooled(shadowPool, n++);
            instance.set(lightRel, draw.pose(), 1f, 1f, null, draw.look());
            Matrix4f camRel = shadowCamRel.set(world);
            camRel.m30((float) (world.m30() - lodCamera.x));
            camRel.m31((float) (world.m31() - lodCamera.y));
            camRel.m32((float) (world.m32() - lodCamera.z));
            instance.lod = Math.max(0, level(model, draw, camRel, -1));
            levels |= instance.lod > 0;
        }
        List<GpuModel.DrawInstance> out = shadowPool.subList(0, n);
        if (levels) out.sort((a, b) -> Integer.compare(a.lod, b.lod));
        return out;
    }

    private void clearAllPending() {
        for (Model m : models) {
            m.internalClearPending();
        }
    }

    private InstanceRenderContext buildContext(LevelCamera cam) {
        Minecraft client = Minecraft.getInstance();
        float delta = VanillaCompat.partialTick(true);
        Matrix4f view = FrameView.INSTANCE.get(new Matrix4f(), cam.viewRotationMatrix);
        Matrix4f projection = FrameView.INSTANCE.getProjection(new Matrix4f(), cam.projectionMatrix);
        Vec3 camPos = cam.pos;
        return new InstanceRenderContext() {
            @Override
            public Minecraft client() {
                return client;
            }

            @Override
            public ClientLevel world() {
                return client.level;
            }

            @Override
            public float deltaTick() {
                return delta;
            }

            @Override
            public Vec3 cameraPos() {
                return camPos;
            }

            @Override
            public Matrix4f viewMatrix() {
                return view;
            }

            @Override
            public Matrix4f projectionMatrix() {
                return projection;
            }
        };
    }

    public void closeAll() {
        for (Model m : models) {
            try {
                m.internalDispose();
            } catch (Exception ignored) {
            }
        }
        models.clear();
        cache.clear();
        cacheKeys.clear();
        refCounts.clear();
        frameCallbacks.clear();
        for (ModelShader prog : customShaders.values()) {
            if (prog != null && prog != shader) {
                prog.close();
            }
        }
        customShaders.clear();
        if (shader != null) {
            shader.close();
            shader = null;
        }
        if (shadowProgram != null) {
            shadowProgram.close();
            shadowProgram = null;
        }
        GlReaper.drain();
    }
}

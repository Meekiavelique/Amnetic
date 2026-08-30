package com.meekdev.amnetic.client.model.internal;

import com.meekdev.amnetic.client.material.internal.ShadingModelRegistry;
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
import com.meekdev.amnetic.client.model.Model;
import com.meekdev.amnetic.client.model.ModelConfig;
import com.meekdev.amnetic.client.ibl.internal.EnvProbe;
import com.meekdev.amnetic.client.model.ModelLighting;
import com.mojang.blaze3d.opengl.GlStateManager;
import org.lwjgl.opengl.GL13;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.Vec3;
import org.joml.FrustumIntersection;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;
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
    private static final int UPLOADS_PER_FRAME = 1;
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

    public void flush(LevelRenderContext fabricCtx) {
        GlReaper.drain();
        render(fabricCtx.levelState().cameraRenderState, false);
    }

    public void flushCapture(CameraRenderState cam) {
        render(cam, true);
    }

    private void render(CameraRenderState cam, boolean capture) {
        for (Model m : models) {
            m.internalUploadPendingIfAny();
        }

        if (cam == null || cam.projectionMatrix == null || cam.viewRotationMatrix == null) {
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

        if (ShadingModelRegistry.INSTANCE.consumeVertexDirty()) {
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

        EnvProbe.INSTANCE.update(level != null ? (float) ((level.getGameTime() % 24000L) / 24000.0) : 0.25f);

        float time = 0f;
        if (level != null) {
            float partial = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);
            time = (level.getGameTime() + partial) / 20.0f;
        }

        frustum.set(projView);
        frameUploadBudget = UPLOADS_PER_FRAME;
        ModelShader current = null;
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
                model.internalClearPending();
            }
        } finally {
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
        return prog;
    }

    private void prepareProgram(ModelShader prog, Matrix4f projView, float time) {
        prog.bind();
        prog.uploadProjView(projView);
        GlStateManager._activeTexture(GL13.GL_TEXTURE0 + 5);
        GL11.glBindTexture(GL13.GL_TEXTURE_CUBE_MAP, EnvProbe.INSTANCE.glId());
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
        if (!model.internalGpu().isUploaded()) {
            if (frameUploadBudget <= 0) {
                return;
            }
            frameUploadBudget--;
        }

        ModelConfig config = model.internalConfig();
        boolean useGBuffer = !capture && GBuffer.isEnabled() && config.writeGBuffer();
        GBufferTargets gbuffer = GBufferTargets.INSTANCE;
        int prevFbo = useGBuffer ? gbuffer.bind() : MainTargetFramebuffer.bind();
        RenderState state = config.renderState() == null ? RenderState.DEFAULT : config.renderState();
        state.apply();
        GlStateManager._depthFunc(GL11.GL_LEQUAL);
        try {
            float defaultEmissive = config.isEmissive() ? config.emissiveStrength() : 1f;
            emissiveGroups.clear();
            for (Model.Draw draw : model.internalPending()) {
                float value = Float.isNaN(draw.emissive()) ? defaultEmissive : draw.emissive();
                if (!emissiveGroups.contains(value)) {
                    emissiveGroups.add(value);
                }
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
                    model.internalGpu().draw(prog, instances, lodFor(instances));
                }
            }
            if (useGBuffer) {
                gbuffer.setPopulated(true);
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

    private List<GpuModel.DrawInstance> toInstances(Model model, Vec3 camPos, ClientLevel level) {
        return toInstances(model, camPos, level, Float.NaN, Float.NaN);
    }

    private List<GpuModel.DrawInstance> toInstances(Model model, Vec3 camPos, ClientLevel level,
                                                    float wantEmissive, float defaultEmissive) {
        List<Model.Draw> pending = model.internalPending();
        model.internalGpu().localBounds(boundsMin, boundsMax);
        boolean cull = model.internalConfig().frustumCull();
        int n = 0;
        for (Model.Draw draw : pending) {
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
                // a posed model can reach well outside its rest bounds - an arm swung out, a bone
                // driven by a cutscene - and culling it against the rest box makes it vanish while
                // it is still on screen. widen the box by how far the pose actually moved things
                if (draw.pose() != null) {
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
            if (level != null) {
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
            pooled(mainPool, n++).set(camRel, draw.pose(), block, sky);
        }
        return mainPool.subList(0, n);
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
            if (m.isReady() && m.internalConfig().castsShadow() && !m.internalLastFrameDraws().isEmpty()) {
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

        shadowProgram.bind();
        shadowProgram.uploadProjView(lightViewProj);
        float r2 = range * range;
        try {
            for (Model model : models) {
                if (!model.isReady() || !model.internalConfig().castsShadow()) {
                    continue;
                }
                List<Model.Draw> draws = model.internalLastFrameDraws();
                if (draws.isEmpty()) {
                    continue;
                }
                List<GpuModel.DrawInstance> instances = toShadowInstances(draws, lightX, lightY, lightZ, r2);
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
            GlStateManager._glBindVertexArray(0);
        }
    }

    private List<GpuModel.DrawInstance> toShadowInstances(List<Model.Draw> draws, double lx, double ly, double lz, float r2) {
        int n = 0;
        for (Model.Draw draw : draws) {
            Matrix4f world = draw.world();
            double dx = world.m30() - lx, dy = world.m31() - ly, dz = world.m32() - lz;
            if (dx * dx + dy * dy + dz * dz > r2) {
                continue;
            }
            Matrix4f lightRel = relScratch.set(world);
            lightRel.m30((float) dx);
            lightRel.m31((float) dy);
            lightRel.m32((float) dz);
            pooled(shadowPool, n++).set(lightRel, draw.pose(), 1f, 1f);
        }
        return shadowPool.subList(0, n);
    }

    private void clearAllPending() {
        for (Model m : models) {
            m.internalClearPending();
        }
    }

    private InstanceRenderContext buildContext(CameraRenderState cam) {
        Minecraft client = Minecraft.getInstance();
        float delta = client.getDeltaTracker().getGameTimeDeltaPartialTick(true);
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

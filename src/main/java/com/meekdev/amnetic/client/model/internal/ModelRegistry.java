package com.meekdev.amnetic.client.model.internal;

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

    // reused per-frame scratch for frustum culling, no per-instance allocation
    private final FrustumIntersection frustum = new FrustumIntersection();
    private final Vector3f boundsMin = new Vector3f();
    private final Vector3f boundsMax = new Vector3f();
    private final Vector3f cullMin = new Vector3f();
    private final Vector3f cullMax = new Vector3f();

    private final CopyOnWriteArrayList<Model> models = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<Consumer<InstanceRenderContext>> frameCallbacks = new CopyOnWriteArrayList<>();
    private final Map<Identifier, Model> cache = new ConcurrentHashMap<>();
    private final Map<Model, Identifier> cacheKeys = new ConcurrentHashMap<>();
    private final Map<Model, Integer> refCounts = new ConcurrentHashMap<>();
    private ModelShader shader;
    private final Map<String, ModelShader> customShaders = new ConcurrentHashMap<>();
    private ModelShadowProgram shadowProgram;
    // how many not-yet-uploaded models may upload their geometry this frame, so a heavy scene's
    // uploads spread over a few frames instead of hitching on one
    private static final int UPLOADS_PER_FRAME = 1;
    private int frameUploadBudget;

    private ModelRegistry() {
        // dev hot reload, drop the compiled programs so the null-checked load sites rebuild them
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

    // compiles the model shader up front (render thread) so the first draw doesn't pay for it
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
        // runs every frame regardless of pending draws: a model can finish its background .ammesh
        // conversion and need its one-time GPU upload on a frame where nothing queued a draw for it yet
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

        if (shader == null) {
            try {
                shader = ModelShader.load();
            } catch (Exception e) {
                LOG.error("Amnetic: failed to load model shader", e);
                clearAllPending();
                return;
            }
        }

        // refresh the environment probe (cheap, only re-bakes when the sun bucket changes), bound per
        // program in prepareProgram so custom model shaders can also sample prefiltered reflections
        EnvProbe.INSTANCE.update(level != null ? (float) ((level.getGameTime() % 24000L) / 24000.0) : 0.25f);

        float time = 0f;
        if (level != null) {
            float partial = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);
            time = (level.getGameTime() + partial) / 20.0f;
        }

        // frustum is built from the camera-relative proj*view (rotation-only view, camera at origin),
        // same space the per-instance camera-relative matrices live in, so AABB tests are consistent
        frustum.set(projView);
        frameUploadBudget = UPLOADS_PER_FRAME;
        ModelShader current = null;
        try {
            for (Model model : models) {
                if (model.isReady() && model.internalHasPending()) {
                    // each model may run a custom program, bind + re-upload frame uniforms only when it changes
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

    // default shader, or the model's cached custom program (compiled once)
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
                prog = shader; // cache the fallback so we don't retry a broken compile every frame
            }
            customShaders.put(key, prog);
        }
        return prog;
    }

    // binds a program and uploads the per-frame uniforms shared by all models drawn with it
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

    // picks a LOD level from the nearest instance's distance (instance matrices are camera-relative)
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
        model.internalEnsureLod(); // opt-in LOD generation (off-thread), once the consumer's config is set
        // spread first-time geometry uploads across frames: if this model hasn't uploaded yet and the
        // frame's upload budget is spent, skip it. pending draws are re-submitted each frame so it
        // just pops in a frame or two later without a hitch
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
            prog.uploadEmissiveStrength(config.isEmissive() ? config.emissiveStrength() : 1f);
            List<GpuModel.DrawInstance> instances = toInstances(model, camPos, level);
            model.internalGpu().draw(prog, instances, lodFor(instances));
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

    private List<GpuModel.DrawInstance> toInstances(Model model, Vec3 camPos, ClientLevel level) {
        List<Model.Draw> pending = model.internalPending();
        List<GpuModel.DrawInstance> out = new ArrayList<>(pending.size());
        model.internalGpu().localBounds(boundsMin, boundsMax);
        for (Model.Draw draw : pending) {
            Matrix4f world = draw.world();
            Matrix4f camRel = new Matrix4f(world);
            camRel.m30(world.m30() - (float) camPos.x);
            camRel.m31(world.m31() - (float) camPos.y);
            camRel.m32(world.m32() - (float) camPos.z);
            // frustum cull: transform the model AABB into camera-relative space and skip if fully off-screen
            // opt-out for large tiled geometry (streamed terrain) where per-chunk AABB tests leave holes
            if (model.internalConfig().frustumCull()) {
                camRel.transformAab(boundsMin, boundsMax, cullMin, cullMax);
                if (!frustum.testAab(cullMin.x, cullMin.y, cullMin.z, cullMax.x, cullMax.y, cullMax.z)) {
                    continue;
                }
            }
            float block = 1f;
            float sky = 0f;
            if (level != null) {
                BlockPos bp = BlockPos.containing(world.m30(), world.m31(), world.m32());
                block = level.getBrightness(LightLayer.BLOCK, bp) / 15f;
                sky = Math.max(0, level.getBrightness(LightLayer.SKY, bp) - level.getSkyDarken()) / 15f;
            }
            out.add(new GpuModel.DrawInstance(camRel, draw.pose(), block, sky));
        }
        return out;
    }

    // whether any registered model has draws from this frame that want to cast a shadow, lets the
    // shadow bake force a re-render whenever a custom model moves
    public boolean hasShadowCasters() {
        for (Model m : models) {
            if (m.isReady() && m.internalConfig().castsShadow() && !m.internalLastFrameDraws().isEmpty()) {
                return true;
            }
        }
        return false;
    }

    // re-draws this frame's model instances into the shadow depth target. lightViewProj must be built
    // light-relative (same frame as lightX/Y/Z), matching the precision trick the rest of the shadow
    // bake uses instead of raw world-space floats
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
        List<GpuModel.DrawInstance> out = new ArrayList<>(draws.size());
        for (Model.Draw draw : draws) {
            Matrix4f world = draw.world();
            double dx = world.m30() - lx, dy = world.m31() - ly, dz = world.m32() - lz;
            if (dx * dx + dy * dy + dz * dz > r2) {
                continue;
            }
            Matrix4f lightRel = new Matrix4f(world);
            lightRel.m30((float) dx);
            lightRel.m31((float) dy);
            lightRel.m32((float) dz);
            out.add(new GpuModel.DrawInstance(lightRel, draw.pose(), 1f, 1f));
        }
        return out;
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

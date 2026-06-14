package com.meekdev.amnetic.client.model.internal;

import com.meekdev.amnetic.client.instanced.InstanceRenderContext;
import com.meekdev.amnetic.client.instanced.RenderState;
import com.meekdev.amnetic.client.instanced.internal.MainTargetFramebuffer;
import com.meekdev.amnetic.client.model.Model;
import com.mojang.blaze3d.opengl.GlStateManager;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ModelRegistry {

    public static final ModelRegistry INSTANCE = new ModelRegistry();
    private static final Logger LOG = LoggerFactory.getLogger("Amnetic/Model");

    private final CopyOnWriteArrayList<Model> models = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<Consumer<InstanceRenderContext>> frameCallbacks = new CopyOnWriteArrayList<>();
    private ModelShader shader;

    private ModelRegistry() {}

    public void register(Model model) { models.add(model); }

    public void unregister(Model model) { models.remove(model); }

    public void onFrame(Consumer<InstanceRenderContext> cb) { frameCallbacks.add(cb); }

    public void flush(LevelRenderContext fabricCtx) {
        CameraRenderState cam = fabricCtx.levelState().cameraRenderState;
        if (cam == null || cam.projectionMatrix == null || cam.viewRotationMatrix == null) return;

        InstanceRenderContext ctx = buildContext(fabricCtx, cam);
        for (Consumer<InstanceRenderContext> cb : frameCallbacks) {
            try { cb.accept(ctx); } catch (Exception e) { LOG.error("Amnetic: model frame callback failed", e); }
        }

        boolean any = false;
        for (Model m : models) if (m.hasPending()) { any = true; break; }
        if (!any) return;

        Vec3 camPos = cam.pos;
        ClientLevel level = Minecraft.getInstance().level;
        Matrix4f projView = new Matrix4f(cam.projectionMatrix).mul(cam.viewRotationMatrix);

        if (shader == null) {
            try { shader = ModelShader.load(); }
            catch (Exception e) { LOG.error("Amnetic: failed to load model shader", e); clearAllPending(); return; }
        }

        int prevFbo = MainTargetFramebuffer.bind();
        RenderState.DEFAULT.apply();
        try {
            shader.bind();
            shader.uploadProjView(projView);
            for (Model model : models) {
                if (!model.hasPending()) continue;
                try {
                    List<GpuModel.DrawInstance> instances = toInstances(model, camPos, level);
                    model.gpu().draw(shader, instances);
                } catch (Exception e) {
                    LOG.error("Amnetic: error rendering model {}", model.name(), e);
                }
                model.clearPending();
            }
        } finally {
            GlStateManager._glUseProgram(0);
            GlStateManager._glBindVertexArray(0);
            RenderState.DEFAULT.restore();
            MainTargetFramebuffer.restore(prevFbo);
        }
    }

    private List<GpuModel.DrawInstance> toInstances(Model model, Vec3 camPos, ClientLevel level) {
        List<Matrix4fc> pending = model.pending();
        List<GpuModel.DrawInstance> out = new ArrayList<>(pending.size());
        for (Matrix4fc world : pending) {
            Matrix4f camRel = new Matrix4f(world);
            camRel.m30(world.m30() - (float) camPos.x);
            camRel.m31(world.m31() - (float) camPos.y);
            camRel.m32(world.m32() - (float) camPos.z);
            float block = 1f, sky = 0f;
            if (level != null) {
                BlockPos bp = BlockPos.containing(world.m30(), world.m31(), world.m32());
                block = level.getBrightness(LightLayer.BLOCK, bp) / 15f;
                sky = Math.max(0, level.getBrightness(LightLayer.SKY, bp) - level.getSkyDarken()) / 15f;
            }
            out.add(new GpuModel.DrawInstance(camRel, block, sky));
        }
        return out;
    }

    private void clearAllPending() {
        for (Model m : models) m.clearPending();
    }

    private InstanceRenderContext buildContext(LevelRenderContext fabricCtx, CameraRenderState cam) {
        Minecraft client = Minecraft.getInstance();
        float delta = client.getDeltaTracker().getGameTimeDeltaPartialTick(true);
        Matrix4f view = new Matrix4f(cam.viewRotationMatrix);
        Matrix4f projection = new Matrix4f(cam.projectionMatrix);
        Vec3 camPos = cam.pos;
        return new InstanceRenderContext() {
            @Override public Minecraft client() { return client; }
            @Override public ClientLevel world() { return client.level; }
            @Override public float deltaTick() { return delta; }
            @Override public Vec3 cameraPos() { return camPos; }
            @Override public Matrix4f viewMatrix() { return view; }
            @Override public Matrix4f projectionMatrix() { return projection; }
        };
    }

    public void closeAll() {
        for (Model m : models) {
            try { m.disposeInternal(); } catch (Exception ignored) {}
        }
        models.clear();
        frameCallbacks.clear();
        if (shader != null) { shader.close(); shader = null; }
    }
}

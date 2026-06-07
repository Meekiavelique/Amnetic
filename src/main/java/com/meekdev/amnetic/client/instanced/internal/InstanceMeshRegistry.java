package com.meekdev.amnetic.client.instanced.internal;

import com.meekdev.amnetic.client.instanced.InstancePhase;
import com.meekdev.amnetic.client.instanced.InstanceRenderContext;
import com.meekdev.amnetic.client.instanced.InstancedMesh;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public final class InstanceMeshRegistry {

    private static final Logger LOGGER = LoggerFactory.getLogger(InstanceMeshRegistry.class);
    public static final InstanceMeshRegistry INSTANCE = new InstanceMeshRegistry();

    private final CopyOnWriteArrayList<InstanceMeshEntry<?>> entries = new CopyOnWriteArrayList<>();
    private final Map<InstancePhase, CopyOnWriteArrayList<Consumer<InstanceRenderContext>>> prePhase =
            new EnumMap<>(InstancePhase.class);

    private InstanceMeshRegistry() {}

    public <T> void register(Identifier id, InstancedMesh<T> mesh) {
        entries.add(new InstanceMeshEntry<>(id, mesh));
    }

    public void addPrePhaseCallback(InstancePhase phase, Consumer<InstanceRenderContext> callback) {
        prePhase.computeIfAbsent(phase, p -> new CopyOnWriteArrayList<>()).add(callback);
    }

    public void renderAll(InstancePhase phase, LevelRenderContext fabricCtx) {
        CameraRenderState cam = fabricCtx.levelState().cameraRenderState;
        if (cam == null || cam.projectionMatrix == null || cam.viewRotationMatrix == null) return;

        Minecraft client = Minecraft.getInstance();
        float deltaTick = client.getDeltaTracker().getGameTimeDeltaPartialTick(true);

        Matrix4f view = new Matrix4f(cam.viewRotationMatrix);
        Matrix4f projection = new Matrix4f(cam.projectionMatrix);
        InstanceRenderContext ctx = new MinecraftRenderContext(client, deltaTick, view, projection);

        int prevFbo = MainTargetFramebuffer.bind();
        try {
            dispatchAll(phase, ctx);
        } finally {
            MainTargetFramebuffer.restore(prevFbo);
        }
    }

    public void renderAll(InstancePhase phase, InstanceRenderContext ctx) {
        dispatchAll(phase, ctx);
    }

    public void renderAll(InstancePhase phase, Minecraft client, float deltaTick, Matrix4f view, Matrix4f projection) {
        dispatchAll(phase, new MinecraftRenderContext(client, deltaTick, view, projection));
    }

    private void dispatchAll(InstancePhase phase, InstanceRenderContext ctx) {
        var callbacks = prePhase.get(phase);
        if (callbacks != null) {
            for (Consumer<InstanceRenderContext> cb : callbacks) {
                try {
                    cb.accept(ctx);
                } catch (Exception e) {
                    LOGGER.error("Amnetic: error in pre-phase callback for {}", phase, e);
                }
            }
        }
        for (InstanceMeshEntry<?> entry : entries) {
            if (entry.mesh().phase() == phase) {
                try {
                    entry.render(ctx);
                } catch (Exception e) {
                    LOGGER.error("Amnetic: error rendering instanced mesh {}", entry.id(), e);
                }
            }
        }
    }

    public void reloadShaders() {
        for (InstanceMeshEntry<?> entry : entries) {
            entry.invalidateShader();
        }
    }

    public void closeAll() {
        for (InstanceMeshEntry<?> entry : entries) {
            entry.close();
        }
        entries.clear();
    }
}
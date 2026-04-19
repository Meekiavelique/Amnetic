package com.meekdev.amnetic.client.instanced.internal;

import com.meekdev.amnetic.client.instanced.InstancePhase;
import com.meekdev.amnetic.client.instanced.InstanceRenderContext;
import com.meekdev.amnetic.client.instanced.InstancedMesh;
import com.meekdev.amnetic.mixin.accessor.GameRendererInvoker;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CopyOnWriteArrayList;

public final class InstanceMeshRegistry {

    private static final Logger LOGGER = LoggerFactory.getLogger(InstanceMeshRegistry.class);
    public static final InstanceMeshRegistry INSTANCE = new InstanceMeshRegistry();

    private final CopyOnWriteArrayList<InstanceMeshEntry<?>> entries = new CopyOnWriteArrayList<>();

    private InstanceMeshRegistry() {}

    public <T> void register(Identifier id, InstancedMesh<T> mesh) {
        entries.add(new InstanceMeshEntry<>(id, mesh));
    }

    public void renderAll(InstancePhase phase, WorldRenderContext fabricCtx) {
        MatrixStack matrices = fabricCtx.matrices();
        if (matrices == null) return;

        MinecraftClient client = MinecraftClient.getInstance();
        float deltaTick = client.getRenderTickCounter().getTickProgress(true);

        Matrix4f view = new Matrix4f(matrices.peek().getPositionMatrix());

        // Clear translation for world-relative rendering
        view.m30(0);
        view.m31(0);
        view.m32(0);

        Matrix4f projection = new Matrix4f(((GameRendererInvoker) fabricCtx.gameRenderer()).amnetic$invokeGetProjectionMatrix(deltaTick));
        InstanceRenderContext ctx = new MinecraftRenderContext(client, deltaTick, view, projection);

        dispatchAll(phase, ctx);
    }

    public void renderAll(InstancePhase phase, InstanceRenderContext ctx) {
        dispatchAll(phase, ctx);
    }

    public void renderAll(InstancePhase phase, MinecraftClient client, float deltaTick, Matrix4f view, Matrix4f projection) {
        dispatchAll(phase, new MinecraftRenderContext(client, deltaTick, view, projection));
    }

    private void dispatchAll(InstancePhase phase, InstanceRenderContext ctx) {
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
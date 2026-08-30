package com.meekdev.amnetic.client.instanced.internal;

import com.meekdev.amnetic.client.camera.internal.FrameView;
import com.meekdev.amnetic.client.dev.ShaderHotReload;
import com.meekdev.amnetic.client.framebuffer.Framebuffer;
import com.meekdev.amnetic.client.gbuffer.GBuffer;
import com.meekdev.amnetic.client.gbuffer.internal.GBufferTargets;
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
    private final Map<InstancePhase, CopyOnWriteArrayList<Consumer<InstanceRenderContext>>> postPhase =
            new EnumMap<>(InstancePhase.class);

    private InstanceMeshRegistry() {
        // dev hot reload for per-mesh instanced shaders
        ShaderHotReload.onReload(() -> {
            for (InstanceMeshEntry<?> entry : entries) entry.invalidateShader();
        });
    }

    public <T> void register(Identifier id, InstancedMesh<T> mesh) {
        entries.add(new InstanceMeshEntry<>(id, mesh));
    }

    public boolean unregister(Identifier id) {
        boolean[] removed = {false};
        entries.removeIf(entry -> {
            if (entry.id().equals(id)) {
                entry.close();
                removed[0] = true;
                return true;
            }
            return false;
        });
        return removed[0];
    }

    public void invalidate(Identifier id) {
        for (InstanceMeshEntry<?> entry : entries) {
            if (entry.id().equals(id)) {
                entry.invalidate();
            }
        }
    }

    public void addPrePhaseCallback(InstancePhase phase, Consumer<InstanceRenderContext> callback) {
        prePhase.computeIfAbsent(phase, p -> new CopyOnWriteArrayList<>()).add(callback);
    }

    public void addPostPhaseCallback(InstancePhase phase, Consumer<InstanceRenderContext> callback) {
        postPhase.computeIfAbsent(phase, p -> new CopyOnWriteArrayList<>()).add(callback);
    }

    public void render(Identifier id, InstanceRenderContext ctx) {
        for (InstanceMeshEntry<?> entry : entries) {
            if (entry.id().equals(id)) {
                entry.render(ctx);
                return;
            }
        }
    }

    public void renderAll(InstancePhase phase, LevelRenderContext fabricCtx) {
        InstanceRenderContext ctx = buildContext(fabricCtx);
        if (ctx == null) return;

        int prevFbo = MainTargetFramebuffer.bind();
        try {
            dispatchAll(phase, ctx);
        } finally {
            MainTargetFramebuffer.restore(prevFbo);
        }
    }

    public void renderEmissive(InstancePhase phase, LevelRenderContext fabricCtx,
                               Framebuffer target, boolean all) {
        InstanceRenderContext ctx = buildContext(fabricCtx);
        if (ctx == null) return;

        target.begin();
        try {
            for (InstanceMeshEntry<?> entry : entries) {
                if (entry.mesh().phase() != phase) continue;
                if (entry.mesh().manual()) continue;
                if (!all && !entry.mesh().isEmissive()) continue;
                try {
                    entry.render(ctx);
                } catch (Exception e) {
                    LOGGER.error("Amnetic: error rendering emissive mesh {}", entry.id(), e);
                }
            }
        } finally {
            target.end();
        }
    }

    private InstanceRenderContext buildContext(LevelRenderContext fabricCtx) {
        CameraRenderState cam = fabricCtx.levelState().cameraRenderState;
        if (cam == null || cam.projectionMatrix == null || cam.viewRotationMatrix == null) return null;

        Minecraft client = Minecraft.getInstance();
        float deltaTick = client.getDeltaTracker().getGameTimeDeltaPartialTick(true);

        Matrix4f view = FrameView.INSTANCE
                .get(new Matrix4f(), cam.viewRotationMatrix);
        Matrix4f projection = FrameView.INSTANCE
                .getProjection(new Matrix4f(), cam.projectionMatrix);
        return new MinecraftRenderContext(client, deltaTick, view, projection);
    }

    public boolean hasEmissive(InstancePhase phase, boolean all) {
        for (InstanceMeshEntry<?> entry : entries) {
            if (entry.mesh().manual()) continue;
            if (entry.mesh().phase() == phase && (all || entry.mesh().isEmissive())) return true;
        }
        return false;
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
            if (entry.mesh().manual()) continue;
            if (entry.mesh().phase() == phase && !entry.mesh().writeGBuffer()) {
                try {
                    entry.render(ctx);
                } catch (Exception e) {
                    LOGGER.error("Amnetic: error rendering instanced mesh {}", entry.id(), e);
                }
            }
        }
        var after = postPhase.get(phase);
        if (after != null) {
            for (Consumer<InstanceRenderContext> cb : after) {
                try {
                    cb.accept(ctx);
                } catch (Exception e) {
                    LOGGER.error("Amnetic: error in post-phase callback for {}", phase, e);
                }
            }
        }
    }

    public void renderGBuffer(InstancePhase phase, LevelRenderContext fabricCtx) {
        if (!GBuffer.isEnabled()) return;
        InstanceRenderContext ctx = buildContext(fabricCtx);
        if (ctx == null) return;

        boolean any = false;
        for (InstanceMeshEntry<?> entry : entries) {
            if (entry.mesh().manual()) continue;
            if (entry.mesh().phase() == phase && entry.mesh().writeGBuffer()) { any = true; break; }
        }
        if (!any) return;

        int prevFbo = GBufferTargets.INSTANCE.bind();
        if (prevFbo == -1) return;
        try {
            for (InstanceMeshEntry<?> entry : entries) {
                if (entry.mesh().phase() != phase || !entry.mesh().writeGBuffer()) continue;
                try {
                    entry.render(ctx);
                } catch (Exception e) {
                    LOGGER.error("Amnetic: error rendering G-buffer mesh {}", entry.id(), e);
                }
            }
            GBufferTargets.INSTANCE.setPopulated(true);
        } finally {
            GBufferTargets.INSTANCE.restore(prevFbo);
        }
    }

    // redraws whatever every shadow-casting mesh already rendered this frame, reprojected through the light's
    // view-proj. must run after renderAll for the geometry phases so each entry's instance buffer reflects
    // the current frame
    public void renderShadow(Matrix4f lightViewProj) {
        for (InstanceMeshEntry<?> entry : entries) {
            if (!entry.castsShadow()) continue;
            try {
                entry.renderShadow(lightViewProj);
            } catch (Exception e) {
                LOGGER.error("Amnetic: error rendering shadow for instanced mesh {}", entry.id(), e);
            }
        }
    }

    public boolean hasShadowCasters() {
        for (InstanceMeshEntry<?> entry : entries) {
            if (entry.castsShadow() && entry.lastInstanceCount() > 0) return true;
        }
        return false;
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
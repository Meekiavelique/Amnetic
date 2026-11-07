package com.meekdev.amnetic.client.instanced.internal;

import com.meekdev.amnetic.client.camera.internal.FrameView;
import com.meekdev.amnetic.client.compat.VanillaCompat;
import com.meekdev.amnetic.client.dev.ShaderHotReload;
import com.meekdev.amnetic.client.emissive.EmissiveContext;
import com.meekdev.amnetic.client.emissive.EmissiveSources;
import com.meekdev.amnetic.client.framebuffer.Framebuffer;
import com.meekdev.amnetic.client.gbuffer.GBuffer;
import com.meekdev.amnetic.client.gbuffer.internal.GBufferTargets;
import com.meekdev.amnetic.client.instanced.InstancePhase;
import com.meekdev.amnetic.client.instanced.InstanceRenderContext;
import com.meekdev.amnetic.client.instanced.InstancedMesh;
import com.meekdev.amnetic.client.render.LevelCamera;
import net.minecraft.client.Minecraft;
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

    public void renderAll(InstancePhase phase, LevelCamera cam) {
        InstanceRenderContext ctx = buildContext(cam);
        if (ctx == null) return;

        //? if >=26.2 {
        dispatchAll(phase, ctx);
        //?} else {
        /*int prevFbo = MainTargetFramebuffer.bind();
        try {
            dispatchAll(phase, ctx);
        } finally {
            MainTargetFramebuffer.restore(prevFbo);
        }
        *///?}
    }

    public void renderEmissive(InstancePhase phase, LevelCamera cam,
                               Framebuffer target, boolean all) {
        InstanceRenderContext ctx = buildContext(cam);
        if (ctx == null) return;

        //? if >=26.2 {
        var previous = NativeInstanceRenderer.emissiveTarget;
        NativeInstanceRenderer.emissiveTarget = target;
        //?} else {
        /*target.begin();
        *///?}
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
            //? if >=26.2 {
            NativeInstanceRenderer.emissiveTarget = previous;
            //?} else {
            /*target.end();
            *///?}
        }
    }

    //? if >=26.2 {
    public void renderNativeSources(LevelCamera camera, Framebuffer target) {
        var context = buildContext(camera);
        if(context == null || context.world() == null) return;
        var previous = NativeInstanceRenderer.emissiveTarget;
        NativeInstanceRenderer.emissiveTarget = target;
        try {
            EmissiveSources.emitAll(
                    new EmissiveContext(context.world(), context.cameraPos(),
                            context.viewMatrix(), context.projectionMatrix(), context.deltaTick(), target.width(), target.height()));
        } finally { NativeInstanceRenderer.emissiveTarget = previous; }
    }
    //?}

    private InstanceRenderContext buildContext(LevelCamera cam) {
        if (cam == null) return null;

        Minecraft client = Minecraft.getInstance();
        float deltaTick = VanillaCompat.partialTick(true);

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

    public void renderGBuffer(InstancePhase phase, LevelCamera cam) {
        if (!GBuffer.isEnabled()) return;
        InstanceRenderContext ctx = buildContext(cam);
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
                if (entry.mesh().phase() != phase || !entry.mesh().depthPrepass()) continue;
                try {
                    entry.renderDepth(ctx);
                } catch (Exception e) {
                    LOGGER.error("Amnetic: error rendering G-buffer depth of mesh {}", entry.id(), e);
                }
            }
            for (InstanceMeshEntry<?> entry : entries) {
                if (entry.mesh().phase() != phase || !entry.mesh().writeGBuffer()) continue;
                try {
                    entry.render(ctx);
                    if (entry.mesh().isEmissive() || entry.mesh().glowsInGBuffer() && entry.lastInstanceCount() > 0) {
                        GBufferTargets.INSTANCE.markEmissive();
                    }
                } catch (Exception e) {
                    LOGGER.error("Amnetic: error rendering G-buffer mesh {}", entry.id(), e);
                }
            }
            GBufferTargets.INSTANCE.setPopulated(true);
        } finally {
            GBufferTargets.INSTANCE.restore(prevFbo);
        }
    }

    public void renderShadow(Matrix4f lightViewProj) {
        renderShadow(lightViewProj, 0.0, 0.0, 0.0, Float.POSITIVE_INFINITY);
    }

    public void renderShadow(Matrix4f lightViewProj, double x, double y, double z, float range) {
        for (InstanceMeshEntry<?> entry : entries) {
            if (!entry.castsShadow() || !entry.reaches(x, y, z, range)) continue;
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
        InstanceMeshEntry.invalidateDepthProgram();
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
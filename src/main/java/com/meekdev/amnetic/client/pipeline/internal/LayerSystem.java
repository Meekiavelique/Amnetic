package com.meekdev.amnetic.client.pipeline.internal;

import com.meekdev.amnetic.client.framebuffer.ColorFormat;
import com.meekdev.amnetic.client.framebuffer.Framebuffer;
import com.meekdev.amnetic.client.framebuffer.FramebufferSpec;
import com.meekdev.amnetic.client.framebuffer.Framebuffers;
import com.meekdev.amnetic.client.instanced.internal.MainTargetFramebuffer;
import com.meekdev.amnetic.client.pipeline.FrameContext;
import com.meekdev.amnetic.client.pipeline.Layer;
import com.meekdev.amnetic.client.pipeline.LayerPass;
import com.meekdev.amnetic.client.pipeline.Pipeline;
import com.meekdev.amnetic.client.pipeline.RenderLayer;
import com.meekdev.amnetic.client.render.CameraSnapshot;
import com.meekdev.amnetic.client.render.GlState;
import com.meekdev.amnetic.client.render.ShaderProgram;
import com.meekdev.amnetic.client.scene.internal.CaptureManager;
import com.meekdev.amnetic.client.scene.internal.CaptureTarget;
import com.mojang.blaze3d.opengl.GlStateManager;
import net.minecraft.resources.Identifier;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL30;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

// isolates a screen layer (hand / GUI) into its own transparent texture, runs the registered LayerPasses
// on it, and composites the result back over the scene. driven by GameRendererMixin via begin/end around
// the layer's vanilla draw. inert unless a pass is registered for the layer (begin returns false), so it
// never touches normal rendering until a dev opts in
public final class LayerSystem {

    public static final LayerSystem INSTANCE = new LayerSystem();

    private static final Logger LOGGER = LoggerFactory.getLogger("Amnetic/Layers");
    private static final Identifier VSH = Identifier.fromNamespaceAndPath("amnetic", "shaders/layer/fullscreen.vsh");
    private static final Identifier FSH = Identifier.fromNamespaceAndPath("amnetic", "shaders/layer/composite.fsh");

    private final Map<RenderLayer, CaptureTarget> targets = new EnumMap<>(RenderLayer.class);
    private final Map<RenderLayer, Boolean> active = new EnumMap<>(RenderLayer.class);

    private Framebuffer work; // ping-pong scratch (B); the captured target colour is A
    private int rawFbo; // lets us bind an arbitrary colour+depth texture as the draw target
    private ShaderProgram composite;

    private LayerSystem() {}

    // begin isolating a layer: redirect vanilla's draws into a cleared transparent target. no-op if unused
    public boolean begin(RenderLayer layer) {
        if (!Pipeline.hasLayer(layer) || CaptureManager.INSTANCE.isCapturing()) return false;
        CaptureTarget t = targets.computeIfAbsent(layer, k -> new CaptureTarget());
        t.ensure(1f);
        bindRaw(t.colorTextureGlId(), t.depthTextureGlId(), t.width(), t.height());
        GL11.glClearColor(0f, 0f, 0f, 0f);
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
        LayerRedirect.set(t.target());
        active.put(layer, Boolean.TRUE);
        return true;
    }

    // finish a layer: run its passes on the captured texture and composite the result over the scene
    public void end(RenderLayer layer) {
        if (!Boolean.TRUE.equals(active.remove(layer))) return;
        LayerRedirect.set(null);
        CaptureTarget t = targets.get(layer);
        if (t == null || t.colorTextureGlId() == 0) return;

        int w = t.width(), h = t.height();
        FrameContext ctx = new FrameContext(CameraSnapshot.current(), null, 0);
        if (composite == null) composite = new ShaderProgram(VSH, FSH);

        try {
            GlState.beginFullscreen();
            int curTex = t.colorTextureGlId();
            boolean destWork = true;

            for (LayerPass p : Pipeline.layerPasses(layer)) {
                if (!safeEnabled(p)) continue;
                int destColor;
                if (destWork) {
                    ensureWork();
                    work.begin();
                    destColor = work.colorTextureGlId(0);
                } else {
                    bindRaw(t.colorTextureGlId(), t.depthTextureGlId(), w, h);
                    destColor = t.colorTextureGlId();
                }
                try {
                    p.render(ctx, new Layer(layer, curTex, w, h));
                } catch (Exception e) {
                    LOGGER.error("layer pass for {} threw; skipping it", layer, e);
                }
                curTex = destColor;
                destWork = !destWork;
            }

            int prev = MainTargetFramebuffer.bind();
            try {
                GlState.bindTexture(0, curTex);
                GlStateManager._enableBlend();
                GlStateManager._blendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
                GL11.glEnable(GL11.GL_BLEND);
                GL14.glBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
                composite.begin();
                composite.setSampler("LayerSampler", 0);
                composite.draw();
            } finally {
                MainTargetFramebuffer.restore(prev);
            }
        } catch (Exception e) {
            LOGGER.error("layer composite for {} failed", layer, e);
        } finally {
            GlState.endFullscreen();
        }
    }

    private void ensureWork() {
        if (work == null) {
            work = Framebuffers.screen("Layer System Work", 1f, FramebufferSpec.builder().color(ColorFormat.RGBA8).build());
        }
        work.begin();
        work.end();
    }

    private void bindRaw(int colorId, int depthId, int w, int h) {
        if (rawFbo == 0) rawFbo = GL30.glGenFramebuffers();
        GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, rawFbo);
        GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, colorId, 0);
        GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT, GL11.GL_TEXTURE_2D, depthId, 0);
        GlStateManager._viewport(0, 0, w, h);
    }

    private static boolean safeEnabled(LayerPass p) {
        try {
            return p.enabled();
        } catch (Exception e) {
            return false;
        }
    }

    public void dispose() {
        if (composite != null) { composite.close(); composite = null; }
        if (work != null) { work.dispose(); work = null; }
        if (rawFbo != 0) { GL30.glDeleteFramebuffers(rawFbo); rawFbo = 0; }
        active.clear();
    }
}

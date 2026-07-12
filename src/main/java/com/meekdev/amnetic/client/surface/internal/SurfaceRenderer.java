package com.meekdev.amnetic.client.surface.internal;

import com.meekdev.amnetic.client.instanced.internal.MainTargetFramebuffer;
import com.meekdev.amnetic.client.pipeline.Pipeline;
import com.meekdev.amnetic.client.pipeline.RenderStage;
import com.meekdev.amnetic.client.surface.HudSurface;
import com.meekdev.amnetic.client.surface.draw.UiDraw;
import com.mojang.blaze3d.opengl.GlStateManager;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// draws every visible hud surface in one batched pass just before the vanilla gui
public final class SurfaceRenderer {

    public static final SurfaceRenderer INSTANCE = new SurfaceRenderer();
    private static final Logger LOG = LoggerFactory.getLogger("Amnetic/Surface");

    private final CopyOnWriteArrayList<HudSurface> huds = new CopyOnWriteArrayList<>();
    private boolean registered;

    private SurfaceRenderer() {}

    public void add(HudSurface surface) {
        huds.add(surface);
        register();
    }

    public void remove(HudSurface surface) {
        huds.remove(surface);
    }

    private void register() {
        if (registered) return;
        registered = true;
        Pipeline.add(RenderStage.BEFORE_GUI, 50, "Surface HUD", ctx -> render());
    }

    private void render() {
        if (huds.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        float w = mc.getWindow().getGuiScaledWidth();
        float h = mc.getWindow().getGuiScaledHeight();

        UiBatcher batcher = UiBatcher.INSTANCE;
        batcher.begin(w, h);
        UiDraw draw = new UiDraw(batcher, w, h);
        boolean any = false;
        for (HudSurface hud : huds) {
            if (!hud.isVisible() || hud.internalDrawCallback() == null) continue;
            try {
                hud.internalDrawCallback().accept(draw);
                any = true;
            } catch (Exception e) {
                // one broken hud never takes the frame or its neighbours down
                LOG.warn("hud surface draw failed, hiding it", e);
                hud.setVisible(false);
            }
        }
        if (!any) return;

        int prevFbo = MainTargetFramebuffer.bind();
        // raw + cached pairs, cached-only calls get skipped when the cache is stale
        GlStateManager._disableScissorTest(); GL11.glDisable(GL11.GL_SCISSOR_TEST);
        GlStateManager._enableBlend(); GL11.glEnable(GL11.GL_BLEND);
        GlStateManager._blendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL14.glBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GlStateManager._disableDepthTest(); GL11.glDisable(GL11.GL_DEPTH_TEST);
        GlStateManager._depthMask(false); GL11.glDepthMask(false);
        GlStateManager._disableCull(); GL11.glDisable(GL11.GL_CULL_FACE);
        batcher.flush();
        GlStateManager._depthMask(true); GL11.glDepthMask(true);
        GlStateManager._enableDepthTest(); GL11.glEnable(GL11.GL_DEPTH_TEST);
        MainTargetFramebuffer.restore(prevFbo);
    }
}

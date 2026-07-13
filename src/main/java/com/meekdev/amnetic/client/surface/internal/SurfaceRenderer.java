package com.meekdev.amnetic.client.surface.internal;

import com.meekdev.amnetic.client.framebuffer.Framebuffer;
import com.meekdev.amnetic.client.framebuffer.Framebuffers;
import com.meekdev.amnetic.client.instanced.internal.MainTargetFramebuffer;
import com.meekdev.amnetic.client.pipeline.Pipeline;
import com.meekdev.amnetic.client.pipeline.RenderStage;
import com.meekdev.amnetic.client.surface.HudSurface;
import com.meekdev.amnetic.client.surface.ScreenSurface;
import com.meekdev.amnetic.client.surface.draw.UiDraw;
import com.mojang.blaze3d.opengl.GlStateManager;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// lays out and draws every visible surface just before the vanilla gui, gl state is set
// up before the trees run so material draws can flush mid-stream in painter's order
public final class SurfaceRenderer {

    public static final SurfaceRenderer INSTANCE = new SurfaceRenderer();
    private static final Logger LOG = LoggerFactory.getLogger("Amnetic/Surface");

    private final CopyOnWriteArrayList<HudSurface> huds = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<ScreenSurface> screens = new CopyOnWriteArrayList<>();
    private boolean registered;

    private SurfaceRenderer() {}

    public void add(HudSurface surface) {
        huds.add(surface);
        register();
    }

    public void add(ScreenSurface surface) {
        screens.add(surface);
        register();
    }

    public void remove(HudSurface surface) {
        huds.remove(surface);
    }

    public void remove(ScreenSurface surface) {
        screens.remove(surface);
    }

    private void register() {
        if (registered) return;
        registered = true;
        Pipeline.add(RenderStage.BEFORE_GUI, 50, "Surface", ctx -> render());
    }

    // interactive huds get whatever mouse events an open surface screen didn't consume
    public void hudMouseMoved(float mx, float my) {
        for (HudSurface hud : huds) {
            if (hud.isVisible() && hud.isInteractive()) hud.internalInput().mouseMoved(mx, my);
        }
    }

    public boolean hudMouseDown(float mx, float my, int button) {
        for (HudSurface hud : huds) {
            if (hud.isVisible() && hud.isInteractive() && hud.internalInput().mouseDown(mx, my, button)) return true;
        }
        return false;
    }

    public boolean hudMouseUp(float mx, float my, int button) {
        for (HudSurface hud : huds) {
            if (hud.isVisible() && hud.isInteractive() && hud.internalInput().mouseUp(mx, my, button)) return true;
        }
        return false;
    }

    private Framebuffer sceneCapture;

    private void render() {
        if (huds.isEmpty() && screens.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        float w = mc.getWindow().getGuiScaledWidth();
        float h = mc.getWindow().getGuiScaledHeight();

        // scene snapshot for blur-behind panels, taken before any ui lands in the target
        if (sceneCapture == null) sceneCapture = Framebuffers.captureColor();
        sceneCapture.blitColorFromMain();
        int sceneTex = sceneCapture.colorTextureGlId(0);

        UiBatcher batcher = UiBatcher.INSTANCE;
        batcher.begin(w, h);
        UiDraw draw = new UiDraw(batcher, w, h, sceneTex);

        // state first so material draws can interleave real gl mid-tree
        int prevFbo = MainTargetFramebuffer.bind();
        GlStateManager._disableScissorTest(); GL11.glDisable(GL11.GL_SCISSOR_TEST);
        GlStateManager._enableBlend(); GL11.glEnable(GL11.GL_BLEND);
        GlStateManager._blendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL14.glBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GlStateManager._disableDepthTest(); GL11.glDisable(GL11.GL_DEPTH_TEST);
        GlStateManager._depthMask(false); GL11.glDepthMask(false);
        GlStateManager._disableCull(); GL11.glDisable(GL11.GL_CULL_FACE);

        for (HudSurface hud : huds) {
            if (!hud.isVisible()) continue;
            try {
                hud.internalTree().layout(0, 0, w, h);
                hud.internalTree().draw(draw, 1f);
                if (hud.internalDrawCallback() != null) hud.internalDrawCallback().accept(draw);
            } catch (Exception e) {
                // one broken hud never takes the frame or its neighbours down
                LOG.warn("hud surface draw failed, hiding it", e);
                hud.setVisible(false);
            }
        }

        for (ScreenSurface screen : screens) {
            if (!screen.isOpen()) continue;
            try {
                if ((screen.dimValue() >>> 24) != 0) draw.rect(0, 0, w, h, screen.dimValue());
                screen.internalTree().layout(0, 0, w, h);
                screen.internalTree().draw(draw, 1f);
                // drag ghost rides the cursor, next frame's layout puts the widget back
                var dragging = screen.internalInput().dragging();
                if (dragging != null) {
                    dragging.layout(screen.internalInput().dragX() - dragging.w * 0.5f,
                            screen.internalInput().dragY() - dragging.h * 0.5f, dragging.w, dragging.h);
                    dragging.draw(draw, 0.55f);
                }
            } catch (Exception e) {
                LOG.warn("screen surface {} draw failed, closing it", screen.name(), e);
                screen.close();
            }
        }

        batcher.flush();
        GlStateManager._depthMask(true); GL11.glDepthMask(true);
        GlStateManager._enableDepthTest(); GL11.glEnable(GL11.GL_DEPTH_TEST);
        MainTargetFramebuffer.restore(prevFbo);
    }
}

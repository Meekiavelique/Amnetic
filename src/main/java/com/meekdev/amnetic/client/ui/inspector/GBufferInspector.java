package com.meekdev.amnetic.client.ui.inspector;

import com.meekdev.amnetic.client.framebuffer.DepthMode;
import com.meekdev.amnetic.client.framebuffer.internal.FramebufferRegistry;
import com.meekdev.amnetic.client.framebuffer.internal.GlFramebuffer;
import com.meekdev.amnetic.client.gbuffer.GBuffer;
import com.meekdev.amnetic.client.ui.Inspector;
import com.meekdev.amnetic.client.ui.TexturePreview;
import com.mojang.blaze3d.pipeline.RenderTarget;
import imgui.ImGui;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;

public final class GBufferInspector extends Inspector {

    private static final float MAX_DISPLAY_HEIGHT = 512f;
    private static final float CONTENT_MARGIN = 16f;

    private final List<TexturePreview> pool = new ArrayList<>();
    private int poolCursor;

    public GBufferInspector() {
        super("Renderer", "GBuffer", false);
    }

    @Override
    public void render() {
        poolCursor = 0;
        if (ImGui.beginTabBar("framebuffers")) {
            if (ImGui.beginTabItem("GBuffer")) {
                gbufferTab();
                ImGui.endTabItem();
            }
            int idx = 0;
            for (GlFramebuffer fb : FramebufferRegistry.INSTANCE.registered()) {
                String label = (fb.name() != null && !fb.name().isEmpty() ? fb.name() : "FB " + idx) + "##" + idx;
                if (ImGui.beginTabItem(label)) {
                    framebufferTab(fb);
                    ImGui.endTabItem();
                }
                idx++;
            }
            ImGui.endTabBar();
        }
    }

    private void gbufferTab() {
        if (!GBuffer.isPopulated()) {
            ImGui.textDisabled("GBuffer not populated this frame.");
            return;
        }
        RenderTarget main = Minecraft.getInstance().getMainRenderTarget();
        int w = main == null ? 0 : main.width;
        int h = main == null ? 0 : main.height;
        attachment("normal (RGBA16F)", GBuffer.normalTextureGlId(), w, h);
        attachment("material (RGBA8)", GBuffer.materialTextureGlId(), w, h);
        attachment("depth", GBuffer.depthTextureGlId(), w, h);
    }

    private void framebufferTab(GlFramebuffer fb) {
        int w = fb.width();
        int h = fb.height();
        ImGui.text("Size: " + w + " x " + h);
        if (!fb.isAllocated()) {
            ImGui.textDisabled("Not allocated.");
            return;
        }
        for (int i = 0; i < fb.colorCount(); i++) {
            String fmt = fb.spec().colorFormats().get(i).name();
            attachment("color " + i + " (" + fmt + ")", fb.colorTextureGlId(i), w, h);
        }
        if (fb.spec().depthMode() == DepthMode.TEXTURE) {
            attachment("depth", fb.depthTextureGlId(), w, h);
        }
    }

    private void attachment(String label, int glId, int w, int h) {
        ImGui.separator();
        ImGui.text(label);
        float displayW = Math.max(1f, ImGui.getContentRegionAvailX() - CONTENT_MARGIN);
        float displayH = w > 0 ? displayW * h / w : displayW;
        if (displayH > MAX_DISPLAY_HEIGHT) {
            displayW *= MAX_DISPLAY_HEIGHT / displayH;
            displayH = MAX_DISPLAY_HEIGHT;
        }
        preview().draw(glId, w, h, displayW, displayH);
    }

    private TexturePreview preview() {
        if (poolCursor >= pool.size()) pool.add(new TexturePreview());
        return pool.get(poolCursor++);
    }
}

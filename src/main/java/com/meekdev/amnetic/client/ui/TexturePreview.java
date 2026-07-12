package com.meekdev.amnetic.client.ui;

import com.meekdev.amnetic.client.framebuffer.internal.WrappedGlTexture;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTextureView;
import foundry.imgui.api.ImGuiMC;
import foundry.imgui.api.ImGuiTextureProvider;
import imgui.ImGui;

public final class TexturePreview {

    private int glId = -1;
    private int width;
    private int height;
    private ImGuiTextureProvider provider;

    public void draw(int glId, int texWidth, int texHeight, float displayW, float displayH) {
        if (glId <= 0 || texWidth <= 0 || texHeight <= 0) {
            ImGui.textDisabled("(not populated)");
            return;
        }
        if (glId != this.glId || texWidth != this.width || texHeight != this.height) {
            this.glId = glId;
            this.width = texWidth;
            this.height = texHeight;
            WrappedGlTexture tex = new WrappedGlTexture(glId, texWidth, texHeight);
            GpuTextureView view = RenderSystem.getDevice().createTextureView(tex);
            this.provider = ImGuiMC.getTexture(view);
        }
        if (provider != null) {
            // GL textures are bottom-up, flip V so the preview isn't upside down
            ImGuiMC.image(provider, displayW, displayH, 0f, 1f, 1f, 0f);
        } else {
            ImGui.textDisabled("(no provider)");
        }
    }
}

package com.meekdev.amnetic.client.ui;

//? if >=1.21.5 {
import com.meekdev.amnetic.client.framebuffer.internal.WrappedGlTexture;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.textures.GpuTextureView;
//?} else {
/*import foundry.imgui.impl.renderer.v0.RawOpenGlTextureProvider;
*///?}
//? if <26.2 {
/*import foundry.imgui.api.ImGuiMC;
import foundry.imgui.api.ImGuiTextureProvider;
*///?}
import imgui.ImGui;

public final class TexturePreview {
    private int glId = -1;
    private int width;
    private int height;
    //? if >=26.2 {
    private long nativeId;
    private GpuTextureView nativeView;
    private boolean ownsView;

    public void draw(GpuTextureView view, float displayW, float displayH) {
        if (view == null || view.isClosed()) { ImGui.textDisabled("(not populated)"); return; }
        if (nativeView != view) {
            if (nativeId != 0) NativeEditorBackend.unregisterTexture(nativeId);
            if (nativeView != null && ownsView) nativeView.close();
            nativeView = view;
            ownsView = false;
            nativeId = NativeEditorBackend.registerTexture(view);
        }
        ImGui.image(nativeId, displayW, displayH, 0, 0, 1, 1);
    }
    //?} else {
    /*private ImGuiTextureProvider provider;
    *///?}

    public void draw(int glId, int texWidth, int texHeight, float displayW, float displayH) {
        if (glId <= 0 || texWidth <= 0 || texHeight <= 0) {
            ImGui.textDisabled("(not populated)");
            return;
        }
        if (glId != this.glId || texWidth != this.width || texHeight != this.height) {
            this.glId = glId;
            this.width = texWidth;
            this.height = texHeight;
            //? if >=1.21.5 {
            WrappedGlTexture tex = new WrappedGlTexture(glId, texWidth, texHeight);
            GpuTextureView view = RenderSystem.getDevice().createTextureView(tex);
            //? if >=26.2 {
            if (nativeView != null && ownsView) nativeView.close();
            if (nativeId != 0) NativeEditorBackend.unregisterTexture(nativeId);
            nativeView = view;
            ownsView = true;
            nativeId = NativeEditorBackend.registerTexture(view);
            //?} else {
            /*this.provider = ImGuiMC.getTexture(view);
            *///?}
            //?} else {
            /*this.provider = new RawOpenGlTextureProvider(glId);
            *///?}
        }
        //? if >=26.2 {
        if (nativeId != 0) ImGui.image(nativeId, displayW, displayH, 0, 1, 1, 0);
        //?} else {
        /*if (provider != null) {
            ImGuiMC.image(provider, displayW, displayH, 0f, 1f, 1f, 0f);
        } else {
            ImGui.textDisabled("(no provider)");
        }
        *///?}
    }
}

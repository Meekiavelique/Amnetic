package com.meekdev.amnetic.client.ui;

import com.meekdev.amnetic.client.compat.KeyInput;
import com.meekdev.amnetic.client.render.AmneticResources;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.pipeline.*;
import com.mojang.renderpearl.api.textures.*;
import com.mojang.renderpearl.api.vertex.VertexFormat;
import imgui.*;
import imgui.flag.ImGuiBackendFlags;
import imgui.flag.ImGuiKey;
import imgui.type.ImInt;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.lwjgl.BufferUtils;

import java.util.*;

/** SDL input via Screen, and ImGui drawing via RenderPearl. No OpenGL context required. */
public final class NativeEditorBackend {
    private static Runnable drawEditor;
    private static CompiledRenderPipeline pipeline;
    private static GpuTexture fontTexture;
    private static GpuTextureView fontView;
    private static GpuSampler sampler;
    private static long lastFrame;
    private static final Map<Long, GpuTextureView> textures = new HashMap<>();
    private static long nextTexture = 2;
    private static Screen previousScreen;
    private static final java.nio.ByteBuffer editorParams = BufferUtils.createByteBuffer(16);

    private NativeEditorBackend() {}

    public static void init(Runnable draw) {
        ImGui.createContext();
        drawEditor = draw;
        var io = ImGui.getIO();
        io.setBackendPlatformName("Amnetic SDL Screen");
        io.setBackendRendererName("Amnetic RenderPearl");
        io.addBackendFlags(ImGuiBackendFlags.RendererHasVtxOffset);
        io.setIniFilename("amnetic-editor.ini");
        io.setGetClipboardTextFn(new imgui.callback.ImStrSupplier() {
            @Override public String get() { return Minecraft.getInstance().keyboardHandler.getClipboard(); }
        });
        io.setSetClipboardTextFn(new imgui.callback.ImStrConsumer() {
            @Override public void accept(String text) { Minecraft.getInstance().keyboardHandler.setClipboard(text); }
        });
        var config = new ImFontConfig();
        config.setSizePixels(16);
        io.setFontDefault(io.getFonts().addFontDefault(config));
        config.destroy();
        AmneticResources.register(NativeEditorBackend::dispose);
    }

    public static void setOpen(boolean open) {
        var mc = Minecraft.getInstance();
        if (open) {
            lastFrame = 0;
            if (!(mc.gui.screen() instanceof EditorScreen)) {
                previousScreen = mc.gui.screen();
                mc.gui.setScreen(new EditorScreen());
            }
        } else if (mc.gui.screen() instanceof EditorScreen) mc.gui.setScreen(previousScreen);
    }

    static long registerTexture(GpuTextureView view) {
        long id = nextTexture++;
        textures.put(id, view);
        return id;
    }

    static void unregisterTexture(long id) { textures.remove(id); }

    public static void render() {
        if (drawEditor == null || !AmneticEditor.isEnabled()
                || !(Minecraft.getInstance().gui.screen() instanceof EditorScreen)) return;
        ensureGpu();
        var target = Minecraft.getInstance().gameRenderer.mainRenderTarget();
        var io = ImGui.getIO();
        io.setDisplaySize(target.width, target.height);
        io.setDisplayFramebufferScale(1, 1);
        long now = System.nanoTime();
        io.setDeltaTime(lastFrame == 0 ? 1f / 60 : Math.max(0.0001f, (now - lastFrame) / 1e9f));
        lastFrame = now;
        ImGui.newFrame();
        drawEditor.run();
        ImGui.render();
        var data = ImGui.getDrawData();
        if (data.getTotalVtxCount() == 0) return;
        var encoder = RenderSystem.getDevice().createCommandEncoder();
        var transientMemory = encoder.transientMemory();
        var vertices = new ArrayList<com.mojang.renderpearl.api.buffers.GpuBufferSlice>();
        var indices = new ArrayList<com.mojang.renderpearl.api.buffers.GpuBufferSlice>();
        for (int list = 0; list < data.getCmdListsCount(); list++) {
            vertices.add(transientMemory.uploadGpu(data.getCmdListVtxBufferData(list), 4, GpuBuffer.USAGE_VERTEX));
            indices.add(transientMemory.uploadGpu(data.getCmdListIdxBufferData(list), 4, GpuBuffer.USAGE_INDEX));
        }
        var params = editorParams.clear();
        params.putFloat(data.getDisplayPosX()).putFloat(data.getDisplayPosY())
                .putFloat(data.getDisplaySizeX()).putFloat(data.getDisplaySizeY()).flip();
        var uniform = transientMemory.uploadGpu(params, 256, GpuBuffer.USAGE_UNIFORM);
        int indexBytes = ImDrawData.sizeOfImDrawIdx();
        try (var pass = encoder.createRenderPass(() -> "Amnetic editor", target.getColorTextureView(), Optional.empty())) {
            pass.setPipeline(pipeline);
            pass.setUniform("EditorParams", uniform);
            for (int list = 0; list < data.getCmdListsCount(); list++) {
                pass.setVertexBuffer(0, vertices.get(list));
                var index = indices.get(list);
                pass.setIndexBuffer(index.buffer(), indexBytes == 2 ? IndexType.SHORT : IndexType.INT);
                for (int command = 0; command < data.getCmdListCmdBufferSize(list); command++) {
                    var clip = data.getCmdListCmdBufferClipRect(list, command);
                    int x = Math.max(0, (int) Math.floor(clip.x - data.getDisplayPosX()));
                    int y = Math.max(0, (int) Math.floor(clip.y - data.getDisplayPosY()));
                    int right = Math.min(target.width, (int) Math.ceil(clip.z - data.getDisplayPosX()));
                    int bottom = Math.min(target.height, (int) Math.ceil(clip.w - data.getDisplayPosY()));
                    if (right <= x || bottom <= y) continue;
                    var texture = textures.get(data.getCmdListCmdBufferTextureId(list, command));
                    if (texture == null || texture.isClosed()) continue;
                    // ImGui clips from the top; main-target render passes clip from the bottom.
                    pass.enableScissor(x, target.height - bottom, right - x, bottom - y);
                    pass.setUniform("EditorTexture", texture, sampler);
                    pass.drawIndexed(data.getCmdListCmdBufferElemCount(list, command), 1,
                            Math.toIntExact(index.offset() / indexBytes) + data.getCmdListCmdBufferIdxOffset(list, command),
                            data.getCmdListCmdBufferVtxOffset(list, command), 0);
                }
            }
        }
        encoder.submit();
    }

    private static void ensureGpu() {
        if (pipeline != null) return;
        var device = RenderSystem.getDevice();
        var id = Identifier.fromNamespaceAndPath("amnetic", "native/editor");
        var format = VertexFormat.builder(0).addAttribute("Position", GpuFormat.RG32_FLOAT)
                .addAttribute("UV", GpuFormat.RG32_FLOAT).addAttribute("Color", GpuFormat.RGBA8_UNORM).build();
        var definition = RenderPipeline.builder().withLocation(id).withVertexShader(id).withFragmentShader(id)
                .withBindGroupLayout(BindGroupLayout.builder().withUniform("EditorParams", UniformType.UNIFORM_BUFFER)
                        .withUniform("EditorTexture", UniformType.COMBINED_IMAGE_SAMPLER).build())
                .withVertexBinding(0, format).withPrimitiveTopology(PrimitiveTopology.TRIANGLES).withCull(false)
                .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
                .withDepthStencilState(Optional.empty()).build();
        try (ShaderSource sources = new ShaderSource() {
            @Override public String getShader(Identifier location, ShaderType type) {
                return type == ShaderType.VERTEX ? """
                        #version 450
                        layout(location=0) in vec2 Position;
                        layout(location=1) in vec2 UV;
                        layout(location=2) in vec4 Color;
                        layout(std140) uniform EditorParams { vec2 DisplayPos; vec2 DisplaySize; };
                        layout(location=0) out vec2 vUV;
                        layout(location=1) out vec4 vColor;
                        void main() {
                            vUV = UV; vColor = Color;
                            vec2 p = (Position - DisplayPos) / DisplaySize;
                            // Match Minecraft's GUI projection into the main render target.
                            // Input and clip rectangles already use ImGui's top-left coordinates.
                            gl_Position = vec4(p.x * 2.0 - 1.0, 1.0 - p.y * 2.0, 0.0, 1.0);
                        }
                        """ : """
                        #version 450
                        layout(location=0) in vec2 vUV;
                        layout(location=1) in vec4 vColor;
                        uniform sampler2D EditorTexture;
                        layout(location=0) out vec4 FragColor;
                        void main() { FragColor = vColor * texture(EditorTexture, vUV); }
                        """;
            }
            @Override public CachedIncludeSource getInclude(Identifier location) { return null; }
            @Override public void close() {}
        }) {
//? if >=26.3 {
pipeline = device.compilePipeline(definition, sources, Runnable::run).join().finishCompile();
//? } else {
/*pipeline=definition; if(!device.precompilePipeline(definition,sources).isValid())throw new IllegalStateException("Native shader compilation failed");
*///? }
}
        if (pipeline == null) throw new IllegalStateException("Could not compile Amnetic editor pipeline");
        try {
            var width = new ImInt();
            var height = new ImInt();
            var pixels = ImGui.getIO().getFonts().getTexDataAsRGBA32(width, height);
            fontTexture = device.createTexture("Amnetic editor font", GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_COPY_DST,
                    GpuFormat.RGBA8_UNORM, width.get(), height.get(), 1, 1);
            fontView = device.createTextureView(fontTexture);
            sampler = device.createSampler(AddressMode.CLAMP_TO_EDGE, AddressMode.CLAMP_TO_EDGE,
                    FilterMode.LINEAR, FilterMode.LINEAR, 1, OptionalDouble.empty());
            var encoder = device.createCommandEncoder();
            encoder.writeToTexture(fontTexture, pixels, 0, 0, 0, 0, width.get(), height.get());
            encoder.submit();
            textures.put(1L, fontView);
            ImGui.getIO().getFonts().setTexID(1);
        } catch (RuntimeException failure) { disposeGpu(); throw failure; }
    }

    private static void disposeGpu() {
        textures.clear();
        if (fontView != null) { fontView.close(); fontView = null; }
        if (fontTexture != null) { fontTexture.close(); fontTexture = null; }
        if (sampler != null) { sampler.close(); sampler = null; }
        if (pipeline != null) {
//? if >=26.3 {
            pipeline.close();
            //? }
pipeline = null; }
    }

    private static void dispose() {
        disposeGpu();
        if (drawEditor != null) { ImGui.destroyContext(); drawEditor = null; }
    }

    private static int key(int scan) {
        //? if <26.3 {
        /*if(scan>=65 && scan<=90)return ImGuiKey.A+scan-65;
        if(scan>=48 && scan<=57)return ImGuiKey._0+scan-48;
        if(scan>=290 && scan<=301)return ImGuiKey.F1+scan-290;
        scan=switch(scan) {
            case 256->41;case 257->40;case 258->43;case 259->42;case 260->73;case 261->76;
            case 262->79;case 263->80;case 264->81;case 265->82;case 266->75;case 267->78;
            case 268->74;case 269->77;case 280->57;case 281->71;case 282->83;case 283->70;case 284->72;
            case 32->44;case 39->52;case 44->54;case 45->45;case 46->55;case 47->56;case 59->51;case 61->46;
            case 91->47;case 92->49;case 93->48;case 96->53;
            case 320->98;case 321->89;case 322->90;case 323->91;case 324->92;case 325->93;case 326->94;case 327->95;case 328->96;case 329->97;
            case 330->99;case 331->84;case 332->85;case 333->86;case 334->87;case 335->88;
            case 340->225;case 341->224;case 342->226;case 343->227;case 344->229;case 345->228;case 346->230;case 347->231;
            default->-1;
        };
        *///? }
        if (scan >= 4 && scan <= 29) return ImGuiKey.A + scan - 4;
        if (scan >= 30 && scan <= 38) return ImGuiKey._1 + scan - 30;
        if (scan >= 58 && scan <= 69) return ImGuiKey.F1 + scan - 58;
        return switch (scan) {
            case 39 -> ImGuiKey._0; case 40 -> ImGuiKey.Enter; case 41 -> ImGuiKey.Escape;
            case 42 -> ImGuiKey.Backspace; case 43 -> ImGuiKey.Tab; case 44 -> ImGuiKey.Space;
            case 45 -> ImGuiKey.Minus; case 46 -> ImGuiKey.Equal;
            case 47 -> ImGuiKey.LeftBracket; case 48 -> ImGuiKey.RightBracket;
            case 49 -> ImGuiKey.Backslash; case 51 -> ImGuiKey.Semicolon;
            case 52 -> ImGuiKey.Apostrophe; case 53 -> ImGuiKey.GraveAccent;
            case 54 -> ImGuiKey.Comma; case 55 -> ImGuiKey.Period; case 56 -> ImGuiKey.Slash;
            case 57 -> ImGuiKey.CapsLock; case 70 -> ImGuiKey.PrintScreen;
            case 71 -> ImGuiKey.ScrollLock; case 72 -> ImGuiKey.Pause;
            case 73 -> ImGuiKey.Insert; case 74 -> ImGuiKey.Home; case 75 -> ImGuiKey.PageUp;
            case 76 -> ImGuiKey.Delete; case 77 -> ImGuiKey.End; case 78 -> ImGuiKey.PageDown;
            case 79 -> ImGuiKey.RightArrow; case 80 -> ImGuiKey.LeftArrow;
            case 81 -> ImGuiKey.DownArrow; case 82 -> ImGuiKey.UpArrow;
            case 83 -> ImGuiKey.NumLock; case 84 -> ImGuiKey.KeypadDivide;
            case 85 -> ImGuiKey.KeypadMultiply; case 86 -> ImGuiKey.KeypadSubtract;
            case 87 -> ImGuiKey.KeypadAdd; case 88 -> ImGuiKey.KeypadEnter;
            case 89 -> ImGuiKey.Keypad1; case 90 -> ImGuiKey.Keypad2; case 91 -> ImGuiKey.Keypad3;
            case 92 -> ImGuiKey.Keypad4; case 93 -> ImGuiKey.Keypad5; case 94 -> ImGuiKey.Keypad6;
            case 95 -> ImGuiKey.Keypad7; case 96 -> ImGuiKey.Keypad8; case 97 -> ImGuiKey.Keypad9;
            case 98 -> ImGuiKey.Keypad0; case 99 -> ImGuiKey.KeypadDecimal;
            case 224 -> ImGuiKey.LeftCtrl; case 225 -> ImGuiKey.LeftShift; case 226 -> ImGuiKey.LeftAlt;
            case 227 -> ImGuiKey.LeftSuper; case 228 -> ImGuiKey.RightCtrl; case 229 -> ImGuiKey.RightShift;
            case 230 -> ImGuiKey.RightAlt; case 231 -> ImGuiKey.RightSuper;
            default -> ImGuiKey.None;
        };
    }

    private static final class EditorScreen extends Screen {
        EditorScreen() { super(Component.literal("Amnetic editor")); }
        @Override public boolean isPauseScreen() { return false; }
        @Override public void extractBackground(GuiGraphicsExtractor extractor, int mouseX, int mouseY, float tick) {}
        @Override public void removed() {
            AmneticEditor.closed();
            if (drawEditor != null) ImGui.getIO().addFocusEvent(false);
        }
        @Override public void added() { if (drawEditor != null) ImGui.getIO().addFocusEvent(true); }
        @Override public void onClose() { Minecraft.getInstance().gui.setScreen(previousScreen); }
        @Override public void mouseMoved(double x, double y) {
            var target = Minecraft.getInstance().gameRenderer.mainRenderTarget();
            ImGui.getIO().addMousePosEvent((float) (x * target.width / width), (float) (y * target.height / height));
        }
        @Override public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
            mouseMoved(event.x(), event.y());
            ImGui.getIO().addMouseButtonEvent(mouseButton(event.button()), true); return true;
        }
        @Override public boolean mouseReleased(MouseButtonEvent event) {
            mouseMoved(event.x(), event.y());
            ImGui.getIO().addMouseButtonEvent(mouseButton(event.button()), false); return true;
        }
        @Override public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
            mouseMoved(event.x(), event.y());
            return true;
        }
        @Override public boolean mouseScrolled(double x, double y, double dx, double dy) {
            ImGui.getIO().addMouseWheelEvent((float) dx, (float) dy); return true;
        }
        @Override public boolean charTyped(CharacterEvent event) {
            ImGui.getIO().addInputCharacter(event.codepoint()); return true;
        }
        private boolean keyEvent(KeyEvent event, boolean down) {
            var io = ImGui.getIO();
            int translated = key(event.key());
            if (translated != ImGuiKey.None) io.addKeyEvent(translated, down);
            io.addKeyEvent(ImGuiKey.ImGuiMod_Ctrl, (event.modifiers() & KeyInput.MOD_CONTROL) != 0);
            io.addKeyEvent(ImGuiKey.ImGuiMod_Shift, (event.modifiers() & KeyInput.MOD_SHIFT) != 0);
            return true;
        }
        @Override public boolean keyPressed(KeyEvent event) {
            if (event.key() == KeyInput.KEY_ESCAPE && !ImGui.getIO().getWantTextInput()) { onClose(); return true; }
            return keyEvent(event, true);
        }
        @Override public boolean keyReleased(KeyEvent event) { return keyEvent(event, false); }
        private static int mouseButton(int button) {
            //? if >=26.3 {
            return switch (button) { case 1 -> 0; case 3 -> 1; case 2 -> 2; default -> button - 1; };
            //? } else {
            /*return button;
            *///? }
        }
    }
}

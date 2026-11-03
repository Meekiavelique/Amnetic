package com.meekdev.amnetic.client.gbuffer.internal;

import com.meekdev.amnetic.client.compat.VanillaCompat;
import com.meekdev.amnetic.client.framebuffer.ColorFormat;
import com.meekdev.amnetic.client.framebuffer.FramebufferSpec;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.renderpearl.backend.opengl.GlStateManager;
import it.unimi.dsi.fastutil.ints.Int2BooleanOpenHashMap;
import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryStack;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
//? if >=26.2 {
import com.meekdev.amnetic.client.framebuffer.internal.NativeFramebuffer;
import com.mojang.renderpearl.api.textures.GpuTextureView;
//?}

public final class GBufferTargets {

    public static final GBufferTargets INSTANCE = new GBufferTargets();

    private int fbo;
    private int normalTex;
    private int materialTex;
    private int emissiveTex;
    private int albedoTex;
    private int velocityTex;
    private int shadeTex;
    private int width;
    private int height;
    private int cachedColor = -1;
    private int cachedDepth = -1;
    private int cachedNormal = -1;
    private int cachedMaterial = -1;
    private int cachedEmissive = -1;
    private int cachedAlbedo = -1;
    private int cachedVelocity = -1;
    private int cachedShade = -1;
    private final Int2BooleanOpenHashMap shades = new Int2BooleanOpenHashMap();
    private final Int2BooleanOpenHashMap moves = new Int2BooleanOpenHashMap();
    private long frame;
    private boolean bound;
    private final int[] savedViewport = new int[4];
    private int outerFbo;
    private int savedReadFbo;
    private boolean attachedThisFrame;
    private boolean populated;
    //? if >=26.2 {
    private final NativeFramebuffer nativeTargets =
            new NativeFramebuffer(
                    FramebufferSpec.builder()
                            .color(ColorFormat.RGBA16F)
                            .color(ColorFormat.RGBA8)
                            .color(ColorFormat.RGBA16F)
                            .color(ColorFormat.RGBA8).build(), "Amnetic GBuffer");

    public GpuTextureView nativeColorView(int index) {
        return nativeTargets.colorView(index);
    }
    //?}

    private GBufferTargets() {}

    public boolean isPopulated() { return populated; }
    public boolean hasEmissive() { return populated && emissiveWritten; }
    public void markEmissive() { emissiveWritten = true; }
    public void setPopulated(boolean v) { populated = v; }
    private boolean emissiveWritten;
    public int normalGlId() { return normalTex; }
    public int materialGlId() { return materialTex; }
    public int emissiveGlId() { return emissiveTex; }
    public int albedoGlId() { return albedoTex; }
    public int velocityGlId() { return velocityTex; }
    public int shadeGlId() { return shadeTex; }
    public long frame() { return frame; }
    public int depthGlId() { return cachedDepth; }

    public int bind() {
        RenderTarget main = Minecraft.getInstance().gameRenderer.mainRenderTarget();
        if (main == null) return -1;
        int colorId = VanillaCompat.colorTextureGlId(main);
        if (colorId <= 0) return -1;
        int depthId = VanillaCompat.depthTextureGlId(main);

        outerFbo = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        savedReadFbo = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, savedViewport);
        ensureTextures(main.width, main.height);
        if (fbo == 0) fbo = GL30.glGenFramebuffers();

        int prevFbo = outerFbo;
        GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, fbo);

        // re-attach only once per frame (or when an attachment id changes), not on every per-object bind.
        // GL reuses freed texture ids so an id-only cache can miss a resize that hands back the same id at a
        // new size (model rendered into limbo after going fullscreen). targets never change size mid-frame and
        // captureOuterState() clears attachedThisFrame each frame, so re-attaching once per frame keeps the
        // resize safety while cutting per-model churn
        if (!attachedThisFrame || colorId != cachedColor || depthId != cachedDepth
                || normalTex != cachedNormal || materialTex != cachedMaterial || emissiveTex != cachedEmissive || albedoTex != cachedAlbedo
                || velocityTex != cachedVelocity || shadeTex != cachedShade) {
            GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, colorId, 0);
            GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT1, GL11.GL_TEXTURE_2D, normalTex, 0);
            GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT2, GL11.GL_TEXTURE_2D, materialTex, 0);
            GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT3, GL11.GL_TEXTURE_2D, emissiveTex, 0);
            GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT4, GL11.GL_TEXTURE_2D, albedoTex, 0);
            GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT5, GL11.GL_TEXTURE_2D, velocityTex, 0);
            GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT6, GL11.GL_TEXTURE_2D, shadeTex, 0);
            GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT, GL11.GL_TEXTURE_2D, depthId, 0);
            drawAll();
            cachedColor = colorId;
            cachedDepth = depthId;
            cachedNormal = normalTex;
            cachedMaterial = materialTex;
            cachedEmissive = emissiveTex;
            cachedAlbedo = albedoTex;
            cachedVelocity = velocityTex;
            cachedShade = shadeTex;
            attachedThisFrame = true;
        }

        GlStateManager._viewport(0, 0, main.width, main.height);
        bound = true;
        return prevFbo;
    }

    public static void drawAll() {
        try (MemoryStack s = MemoryStack.stackPush()) {
            GL30.glDrawBuffers(s.ints(GL30.GL_COLOR_ATTACHMENT0, GL30.GL_COLOR_ATTACHMENT1, GL30.GL_COLOR_ATTACHMENT2,
                    GL30.GL_COLOR_ATTACHMENT3, GL30.GL_COLOR_ATTACHMENT4, GL30.GL_COLOR_ATTACHMENT5,
                    GL30.GL_COLOR_ATTACHMENT6));
        }
    }

    public void program(int program) {
        if (!bound) return;
        boolean writes = moves.computeIfAbsent(program, p -> GL30.glGetFragDataLocation(p, "GVelocity") == 5);
        GL30.glColorMaski(5, writes, writes, writes, writes);
        boolean shaded = shades.computeIfAbsent(program, p -> GL30.glGetFragDataLocation(p, "GShade") == 6);
        GL30.glColorMaski(6, shaded, shaded, shaded, shaded);
    }

    public void clearSideTargets() {
        try (MemoryStack s = MemoryStack.stackPush()) {
            FloatBuffer zero = s.floats(0f, 0f, 0f, 0f);
            GL30.glClearBufferfv(GL11.GL_COLOR, 1, zero);
            GL30.glClearBufferfv(GL11.GL_COLOR, 2, zero);
            GL30.glClearBufferfv(GL11.GL_COLOR, 3, zero);
            GL30.glClearBufferfv(GL11.GL_COLOR, 4, zero);
            GL30.glClearBufferfv(GL11.GL_COLOR, 5, zero);
            GL30.glClearBufferfv(GL11.GL_COLOR, 6, zero);
        }
    }

    public void restore(int prevFbo) {
        if (prevFbo < 0) return;
        if (bound) {
            GL30.glColorMaski(5, true, true, true, true);
            GL30.glColorMaski(6, true, true, true, true);
        }
        bound = false;
        GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, savedReadFbo);
        GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, prevFbo);
        GlStateManager._viewport(savedViewport[0], savedViewport[1], savedViewport[2], savedViewport[3]);
    }

    // the outer FBO captured once this frame, lets other passes (shadows) restore it without a glGet sync
    public int outerFboId() {
        return outerFbo;
    }

    public void captureOuterState() {
        RenderTarget main = Minecraft.getInstance().gameRenderer.mainRenderTarget();
        if (main == null) return;
        // new frame: force one FBO re-attach for resize safety, then skip it for later binds
        attachedThisFrame = false;
        outerFbo = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        savedReadFbo = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, savedViewport);
    }

    public void beginFrame() {
        frame++;
        //? if >=26.2 {
        var main = Minecraft.getInstance().gameRenderer.mainRenderTarget();
        ensureTextures(main.width, main.height);
        nativeTargets.clear(0, 0, 0, 0);
        if (VanillaCompat.colorTextureGlId(main) > 0) captureOuterState();
        populated = false;
        emissiveWritten = false;
        //?} else {
        /*captureOuterState();
        int prev = bind();
        if (prev == -1) { populated = false; return; }
        clearSideTargets();
        restore(prev);
        populated = false;
        emissiveWritten = false;
        *///?}
    }

    public void dispose() {
        //? if >=26.2 {
        nativeTargets.close();
        normalTex = materialTex = emissiveTex = albedoTex = 0;
        //?}
        if (fbo != 0) { GL30.glDeleteFramebuffers(fbo); fbo = 0; }
        if (normalTex != 0) { GlStateManager._deleteTexture(normalTex); normalTex = 0; }
        if (materialTex != 0) { GlStateManager._deleteTexture(materialTex); materialTex = 0; }
        if (emissiveTex != 0) { GlStateManager._deleteTexture(emissiveTex); emissiveTex = 0; }
        if (albedoTex != 0) { GlStateManager._deleteTexture(albedoTex); albedoTex = 0; }
        if (velocityTex != 0) { GlStateManager._deleteTexture(velocityTex); velocityTex = 0; }
        if (shadeTex != 0) { GlStateManager._deleteTexture(shadeTex); shadeTex = 0; }
        width = height = 0;
        cachedColor = cachedDepth = cachedNormal = cachedMaterial = cachedEmissive = cachedAlbedo = cachedVelocity = cachedShade = -1;
        moves.clear();
        shades.clear();
        populated = false;
    }

    private void ensureTextures(int w, int h) {
        //? if >=26.2 {
        nativeTargets.allocate(w, h);
        normalTex = VanillaCompat.glId(nativeTargets.color(0));
        materialTex = VanillaCompat.glId(nativeTargets.color(1));
        emissiveTex = VanillaCompat.glId(nativeTargets.color(2));
        albedoTex = VanillaCompat.glId(nativeTargets.color(3));
        width = w; height = h;
        //?} else {
        /*if (normalTex != 0 && w == width && h == height) return;
        width = w;
        height = h;
        if (normalTex == 0) normalTex = GlStateManager._genTexture();
        if (materialTex == 0) materialTex = GlStateManager._genTexture();
        if (emissiveTex == 0) emissiveTex = GlStateManager._genTexture();
        if (albedoTex == 0) albedoTex = GlStateManager._genTexture();
        if (velocityTex == 0) velocityTex = GlStateManager._genTexture();
        if (shadeTex == 0) shadeTex = GlStateManager._genTexture();

        GlStateManager._activeTexture(GL13.GL_TEXTURE0);
        allocTexture(normalTex, GL30.GL_RGBA16F, GL11.GL_RGBA, GL11.GL_FLOAT, w, h);
        allocTexture(materialTex, GL11.GL_RGBA8, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, w, h);
        allocTexture(emissiveTex, GL30.GL_RGBA16F, GL11.GL_RGBA, GL11.GL_FLOAT, w, h);
        allocTexture(albedoTex, GL11.GL_RGBA8, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, w, h);
        allocTexture(velocityTex, GL30.GL_RGBA16F, GL11.GL_RGBA, GL11.GL_FLOAT, w, h);
        allocTexture(shadeTex, GL11.GL_RGBA8, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, w, h);
        GlStateManager._bindTexture(0);
        *///?}
    }

    private static void allocTexture(int tex, int internalFormat, int format, int type, int w, int h) {
        GlStateManager._bindTexture(tex);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, internalFormat, w, h, 0, format, type, (ByteBuffer) null);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL13.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL13.GL_CLAMP_TO_EDGE);
    }
}

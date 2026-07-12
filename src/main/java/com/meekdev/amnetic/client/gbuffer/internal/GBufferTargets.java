package com.meekdev.amnetic.client.gbuffer.internal;

import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.opengl.GlTexture;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.textures.GpuTexture;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryStack;

public final class GBufferTargets {

    public static final GBufferTargets INSTANCE = new GBufferTargets();

    private int fbo;
    private int normalTex;
    private int materialTex;
    private int emissiveTex;
    private int width;
    private int height;
    private int cachedColor = -1;
    private int cachedDepth = -1;
    private int cachedNormal = -1;
    private int cachedMaterial = -1;
    private int cachedEmissive = -1;
    private final int[] savedViewport = new int[4];
    // outer framebuffer to restore to. querying it (glGetInteger) drains the GL pipeline so we read it at
    // most once per main-target (re)creation, keyed on the main color texture's GL id
    private int outerFbo;
    private int outerKey = -1;
    private boolean attachedThisFrame;
    private boolean populated;

    private GBufferTargets() {}

    public boolean isPopulated() { return populated; }
    public void setPopulated(boolean v) { populated = v; }
    public int normalGlId() { return normalTex; }
    public int materialGlId() { return materialTex; }
    public int emissiveGlId() { return emissiveTex; }
    public int depthGlId() { return cachedDepth; }

    public int bind() {
        RenderTarget main = Minecraft.getInstance().getMainRenderTarget();
        if (main == null) return -1;
        GpuTexture color = main.getColorTexture();
        if (!(color instanceof GlTexture glColor)) return -1;
        GpuTexture depth = main.useDepth ? main.getDepthTexture() : null;
        int colorId = glColor.glId();
        int depthId = (depth instanceof GlTexture glDepth) ? glDepth.glId() : 0;

        ensureTextures(main.width, main.height);
        if (fbo == 0) fbo = GL30.glGenFramebuffers();

        // outer FBO/viewport were captured once this frame in captureOuterState, reusing them avoids a
        // glGetInteger driver sync on every per-object bind
        int prevFbo = outerFbo;
        GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, fbo);

        // re-attach only once per frame (or when an attachment id changes), not on every per-object bind.
        // GL reuses freed texture ids so an id-only cache can miss a resize that hands back the same id at a
        // new size (model rendered into limbo after going fullscreen). targets never change size mid-frame and
        // captureOuterState() clears attachedThisFrame each frame, so re-attaching once per frame keeps the
        // resize safety while cutting per-model churn
        if (!attachedThisFrame || colorId != cachedColor || depthId != cachedDepth
                || normalTex != cachedNormal || materialTex != cachedMaterial || emissiveTex != cachedEmissive) {
            GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, colorId, 0);
            GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT1, GL11.GL_TEXTURE_2D, normalTex, 0);
            GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT2, GL11.GL_TEXTURE_2D, materialTex, 0);
            GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT3, GL11.GL_TEXTURE_2D, emissiveTex, 0);
            GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT, GL11.GL_TEXTURE_2D, depthId, 0);
            try (MemoryStack s = MemoryStack.stackPush()) {
                IntBuffer bufs = s.ints(GL30.GL_COLOR_ATTACHMENT0, GL30.GL_COLOR_ATTACHMENT1, GL30.GL_COLOR_ATTACHMENT2,
                        GL30.GL_COLOR_ATTACHMENT3);
                GL30.glDrawBuffers(bufs);
            }
            cachedColor = colorId;
            cachedDepth = depthId;
            cachedNormal = normalTex;
            cachedMaterial = materialTex;
            cachedEmissive = emissiveTex;
            attachedThisFrame = true;
        }

        GlStateManager._viewport(0, 0, main.width, main.height);
        return prevFbo;
    }

    public void clearSideTargets() {
        try (MemoryStack s = MemoryStack.stackPush()) {
            FloatBuffer zero = s.floats(0f, 0f, 0f, 0f);
            GL30.glClearBufferfv(GL11.GL_COLOR, 1, zero);
            GL30.glClearBufferfv(GL11.GL_COLOR, 2, zero);
            GL30.glClearBufferfv(GL11.GL_COLOR, 3, zero);
        }
    }

    public void restore(int prevFbo) {
        if (prevFbo < 0) return;
        GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, prevFbo);
        GlStateManager._viewport(savedViewport[0], savedViewport[1], savedViewport[2], savedViewport[3]);
    }

    // the outer FBO captured once this frame, lets other passes (shadows) restore it without a glGet sync
    public int outerFboId() {
        return outerFbo;
    }

    public void captureOuterState() {
        RenderTarget main = Minecraft.getInstance().getMainRenderTarget();
        if (main == null) return;
        // new frame: force one FBO re-attach for resize safety, then skip it for later binds
        attachedThisFrame = false;
        // viewport at bind() time is always the main target's full size, derive it instead of querying GL
        savedViewport[0] = 0;
        savedViewport[1] = 0;
        savedViewport[2] = main.width;
        savedViewport[3] = main.height;
        // only re-read the bound FBO when the main target was (re)created (its color texture id changes on
        // resize / same-size reload). steady state does no GL query at all, so no pipeline drain
        int colorId = (main.getColorTexture() instanceof GlTexture glColor) ? glColor.glId() : -1;
        if (colorId != outerKey) {
            outerFbo = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
            outerKey = colorId;
        }
    }

    public void beginFrame() {
        captureOuterState();
        int prev = bind();
        if (prev == -1) { populated = false; return; }
        clearSideTargets();
        restore(prev);
        populated = false;
    }

    public void dispose() {
        if (fbo != 0) { GL30.glDeleteFramebuffers(fbo); fbo = 0; }
        if (normalTex != 0) { GlStateManager._deleteTexture(normalTex); normalTex = 0; }
        if (materialTex != 0) { GlStateManager._deleteTexture(materialTex); materialTex = 0; }
        if (emissiveTex != 0) { GlStateManager._deleteTexture(emissiveTex); emissiveTex = 0; }
        width = height = 0;
        cachedColor = cachedDepth = cachedNormal = cachedMaterial = cachedEmissive = -1;
        populated = false;
    }

    private void ensureTextures(int w, int h) {
        if (normalTex != 0 && w == width && h == height) return;
        width = w;
        height = h;
        if (normalTex == 0) normalTex = GlStateManager._genTexture();
        if (materialTex == 0) materialTex = GlStateManager._genTexture();
        if (emissiveTex == 0) emissiveTex = GlStateManager._genTexture();

        GlStateManager._activeTexture(GL13.GL_TEXTURE0);
        allocTexture(normalTex, GL30.GL_RGBA16F, GL11.GL_RGBA, GL11.GL_FLOAT, w, h);
        allocTexture(materialTex, GL11.GL_RGBA8, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, w, h);
        allocTexture(emissiveTex, GL30.GL_RGBA16F, GL11.GL_RGBA, GL11.GL_FLOAT, w, h);
        GlStateManager._bindTexture(0);
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

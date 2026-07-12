package com.meekdev.amnetic.client.framebuffer.internal;

import com.meekdev.amnetic.client.framebuffer.ColorFormat;
import com.meekdev.amnetic.client.framebuffer.DepthMode;
import com.meekdev.amnetic.client.framebuffer.FramebufferException;
import com.meekdev.amnetic.client.framebuffer.FramebufferSpec;
import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.opengl.GlTexture;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.textures.GpuTexture;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryStack;

public final class GlFramebuffer {

    private final FramebufferSpec spec;
    private final String name;
    private final List<Attachment> colorAttachments = new ArrayList<>();
    private Attachment depthAttachment; // null when DepthMode.NONE

    private int fbo;
    private int readFbo;
    private int writeFbo;
    private int width;
    private int height;
    private boolean allocated;

    private final int[] savedViewport = new int[4];
    private int savedFbo = -1;

    public GlFramebuffer(FramebufferSpec spec) {
        this(spec, null);
    }

    public GlFramebuffer(FramebufferSpec spec, String name) {
        this.spec = spec;
        this.name = name;
    }

    public FramebufferSpec spec() {
        return spec;
    }

    public String name() {
        return name;
    }

    public int colorCount() {
        return spec.colorCount();
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public boolean isAllocated() {
        return allocated;
    }

    public int colorTextureGlId(int index) {
        return colorAttachments.get(index).glId();
    }

    public int depthTextureGlId() {
        if (depthAttachment == null || depthAttachment.kind() != Attachment.Kind.DEPTH_TEXTURE) {
            return 0;
        }
        return depthAttachment.glId();
    }

    public void allocate(int newWidth, int newHeight) {
        if (newWidth <= 0 || newHeight <= 0) {
            throw new FramebufferException("Framebuffer size must be positive, got "
                    + newWidth + "x" + newHeight);
        }
        if (allocated && newWidth == width && newHeight == height) {
            return;
        }
        this.width = newWidth;
        this.height = newHeight;

        if (fbo == 0) {
            fbo = GL30.glGenFramebuffers();
        }
        if (colorAttachments.isEmpty()) {
            for (ColorFormat fmt : spec.colorFormats()) {
                colorAttachments.add(Attachment.colorTexture(fmt));
            }
            if (spec.depthMode() == DepthMode.TEXTURE) {
                depthAttachment = Attachment.depthTexture();
            } else if (spec.depthMode() == DepthMode.RENDERBUFFER) {
                depthAttachment = Attachment.depthRenderbuffer();
            }
        }

        for (Attachment a : colorAttachments) {
            a.allocate(width, height);
        }
        if (depthAttachment != null) {
            depthAttachment.allocate(width, height);
        }

        int prev = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, fbo);
        for (int i = 0; i < colorAttachments.size(); i++) {
            colorAttachments.get(i).attachTo(i);
        }
        if (depthAttachment != null) {
            depthAttachment.attachTo(0);
        }
        setDrawBuffers();
        checkComplete();
        GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, prev);
        allocated = true;
    }

    private void setDrawBuffers() {
        int n = colorAttachments.size();
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer bufs = stack.mallocInt(n);
            for (int i = 0; i < n; i++) {
                bufs.put(i, GL30.GL_COLOR_ATTACHMENT0 + i);
            }
            GL30.glDrawBuffers(bufs);
        }
    }

    private void checkComplete() {
        int status = GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER);
        if (status != GL30.GL_FRAMEBUFFER_COMPLETE) {
            throw new FramebufferException("Framebuffer incomplete (status 0x"
                    + Integer.toHexString(status) + ") for " + spec.colorCount()
                    + " color attachment(s), depth=" + spec.depthMode());
        }
    }

    public void begin() {
        if (!allocated) {
            throw new FramebufferException("begin() before allocate()");
        }
        savedFbo = currentDrawFbo();
        saveOuterViewport();
        GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, fbo);
        GlStateManager._viewport(0, 0, width, height);
    }

    // glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING) drains the GL pipeline, a big stall since begin()/blits run
    // dozens of times per frame. GlStateManager shadows the binding CPU-side (every bind goes through it,
    // vanilla's included) so read the shadow instead of the driver
    private static int currentDrawFbo() {
        return GlStateManager.getFrameBuffer(GL30.GL_DRAW_FRAMEBUFFER);
    }

    // the viewport to restore is the main target's full size wherever begin() runs, so derive it instead of
    // glGetIntegerv(GL_VIEWPORT) which also drains the pipeline. falls back to the query if the main target
    // isn't up yet
    private void saveOuterViewport() {
        RenderTarget main = Minecraft.getInstance().getMainRenderTarget();
        if (main != null) {
            savedViewport[0] = 0;
            savedViewport[1] = 0;
            savedViewport[2] = main.width;
            savedViewport[3] = main.height;
        } else {
            GL11.glGetIntegerv(GL11.GL_VIEWPORT, savedViewport);
        }
    }

    public void clear(float r, float g, float b, float a) {
        GL11.glClearColor(r, g, b, a);
        int mask = GL11.GL_COLOR_BUFFER_BIT;
        if (depthAttachment != null) {
            GL11.glClearDepth(1.0);
            mask |= GL11.GL_DEPTH_BUFFER_BIT;
        }
        GL11.glClear(mask);
    }

    public void end() {
        if (savedFbo < 0) return;
        GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, savedFbo);
        GlStateManager._viewport(savedViewport[0], savedViewport[1], savedViewport[2], savedViewport[3]);
        savedFbo = -1;
    }

    public void blitColorFromMain() {
        RenderTarget main = Minecraft.getInstance().getMainRenderTarget();
        if (main == null) return;
        int srcId = glId(main.getColorTexture());
        if (srcId <= 0) return;
        blitFromMain(srcId, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_COLOR_BUFFER_BIT, GL11.GL_LINEAR, main);
    }

    public void blitDepthFromMain() {
        RenderTarget main = Minecraft.getInstance().getMainRenderTarget();
        if (main == null || !main.useDepth) return;
        int srcId = glId(main.getDepthTexture());
        if (srcId <= 0) return;
        blitFromMain(srcId, GL30.GL_DEPTH_ATTACHMENT, GL11.GL_DEPTH_BUFFER_BIT, GL11.GL_NEAREST, main);
    }

    public void blitDepthFrom(int srcDepthGlId, int srcW, int srcH) {
        if (srcDepthGlId <= 0 || srcW <= 0 || srcH <= 0) return;
        int prev = currentDrawFbo();
        if (readFbo == 0) {
            readFbo = GL30.glGenFramebuffers();
        }
        GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, readFbo);
        GL30.glFramebufferTexture2D(GL30.GL_READ_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT, GL11.GL_TEXTURE_2D, srcDepthGlId, 0);
        GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, fbo);
        GL30.glBlitFramebuffer(0, 0, srcW, srcH, 0, 0, width, height, GL11.GL_DEPTH_BUFFER_BIT, GL11.GL_NEAREST);
        GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, readFbo);
        GL30.glFramebufferTexture2D(GL30.GL_READ_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT, GL11.GL_TEXTURE_2D, 0, 0);
        GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, prev);
    }

    private void blitFromMain(int srcGlId, int attachment, int mask, int filter, RenderTarget main) {
        int prev = currentDrawFbo();
        if (readFbo == 0) {
            readFbo = GL30.glGenFramebuffers();
        }
        GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, readFbo);
        GL30.glFramebufferTexture2D(GL30.GL_READ_FRAMEBUFFER, attachment, GL11.GL_TEXTURE_2D, srcGlId, 0);
        if (attachment == GL30.GL_COLOR_ATTACHMENT0) {
            GL30.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0);
        }
        GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, fbo);
        GL30.glBlitFramebuffer(0, 0, main.width, main.height, 0, 0, width, height, mask, filter);
        // detach so we never dangle a reference to a resized/destroyed main texture
        GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, readFbo);
        GL30.glFramebufferTexture2D(GL30.GL_READ_FRAMEBUFFER, attachment, GL11.GL_TEXTURE_2D, 0, 0);
        GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, prev);
    }

    public void blitColorToMain() {
        RenderTarget main = Minecraft.getInstance().getMainRenderTarget();
        if (main == null) return;
        int dstId = glId(main.getColorTexture());
        if (dstId <= 0) return;

        int prev = currentDrawFbo();
        if (writeFbo == 0) {
            writeFbo = GL30.glGenFramebuffers();
        }
        GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, writeFbo);
        GL30.glFramebufferTexture2D(GL30.GL_DRAW_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, dstId, 0);
        GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, fbo);
        GL30.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0);
        GL30.glBlitFramebuffer(0, 0, width, height, 0, 0, main.width, main.height,
                GL11.GL_COLOR_BUFFER_BIT, GL11.GL_LINEAR);
        GL30.glFramebufferTexture2D(GL30.GL_DRAW_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, 0, 0);
        GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, prev);
    }

    private static int glId(GpuTexture texture) {
        return texture instanceof GlTexture gl ? gl.glId() : -1;
    }

    public void dispose() {
        for (Attachment a : colorAttachments) {
            a.free();
        }
        colorAttachments.clear();
        if (depthAttachment != null) {
            depthAttachment.free();
            depthAttachment = null;
        }
        if (fbo != 0) {
            GL30.glDeleteFramebuffers(fbo);
            fbo = 0;
        }
        if (writeFbo != 0) {
            GL30.glDeleteFramebuffers(writeFbo);
            writeFbo = 0;
        }
        if (readFbo != 0) {
            GL30.glDeleteFramebuffers(readFbo);
            readFbo = 0;
        }
        allocated = false;
    }
}

package com.meekdev.amnetic.client.outline.internal;

import java.nio.FloatBuffer;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

import com.meekdev.amnetic.client.compat.VanillaCompat;
import com.meekdev.amnetic.client.framebuffer.ColorFormat;
import com.meekdev.amnetic.client.framebuffer.Framebuffer;
import com.meekdev.amnetic.client.framebuffer.FramebufferSpec;
import com.meekdev.amnetic.client.framebuffer.Framebuffers;
import com.meekdev.amnetic.client.instanced.internal.MainTargetFramebuffer;
import com.meekdev.amnetic.client.render.CameraSnapshot;
import com.meekdev.amnetic.client.render.FailureGuard;
import com.meekdev.amnetic.client.render.GlState;
import com.meekdev.amnetic.client.render.ShaderProgram;
import com.meekdev.amnetic.client.scene.internal.CaptureManager;
import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4f;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL30;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// draws queued shapes into a mask, then grows the mask by a few pixels over the main image
public final class OutlinePass {

    public static final OutlinePass INSTANCE = new OutlinePass();

    private static final Logger LOG = LoggerFactory.getLogger("Amnetic/Outline");
    private static final Identifier FULLSCREEN = Identifier.fromNamespaceAndPath("amnetic", "shaders/util/fullscreen.vsh");
    private static final Identifier MASK_VSH = Identifier.fromNamespaceAndPath("amnetic", "shaders/outline/mask.vsh");
    private static final Identifier MASK_FSH = Identifier.fromNamespaceAndPath("amnetic", "shaders/outline/mask.fsh");
    private static final Identifier COMPOSITE_FSH = Identifier.fromNamespaceAndPath("amnetic", "shaders/outline/composite.fsh");
    // x y z u v r g b a
    private static final int STRIDE = 9;
    // matches MAX_RADIUS in composite.fsh
    private static final float MAX_WIDTH = 8f;

    // null key holds solid geometry
    private final Map<Identifier, Batch> batches = new LinkedHashMap<>();
    private final FailureGuard guard = new FailureGuard("Outline", 60);
    private final Matrix4f invProj = new Matrix4f();
    private float width = 2f;
    private Framebuffer mask;
    private ShaderProgram maskProgram;
    private ShaderProgram compositeProgram;
    private int vao;
    private int vbo;
    private FloatBuffer upload;

    private OutlinePass() {}

    public void width(float pixels) {
        this.width = Math.max(0.5f, pixels);
    }

    public void quad(Identifier texture, float[] corners, float r, float g, float b, float a) {
        // capture renders replay entity submits from another camera, those must not leak into the real frame
        if (CaptureManager.INSTANCE.isCapturing()) return;
        Batch batch = batches.computeIfAbsent(texture, k -> new Batch());
        // two triangles, 0 1 2 and 0 2 3
        batch.vertex(corners, 0, r, g, b, a);
        batch.vertex(corners, 1, r, g, b, a);
        batch.vertex(corners, 2, r, g, b, a);
        batch.vertex(corners, 0, r, g, b, a);
        batch.vertex(corners, 2, r, g, b, a);
        batch.vertex(corners, 3, r, g, b, a);
    }

    public void render() {
        try {
            // empty batches linger a while for reuse, skipping on them would composite last frame's stale mask
            if (queuedFloats() == 0 || !guard.alive() || CaptureManager.INSTANCE.isCapturing()) return;
            CameraSnapshot cam = CameraSnapshot.current();
            RenderTarget main = Minecraft.getInstance().getMainRenderTarget();
            if (cam == null || main == null) return;
            try {
                GlState.beginFullscreen();
                try {
                    drawMask(cam);
                    composite(cam, main);
                } finally {
                    GlState.endFullscreen();
                }
                guard.success();
            } catch (Throwable e) {
                guard.fail(LOG, e);
            }
        } finally {
            for (Batch batch : batches.values()) batch.size = 0;
            batches.values().removeIf(batch -> batch.idle++ > 120);
        }
    }

    private int queuedFloats() {
        int floats = 0;
        for (Batch batch : batches.values()) floats += batch.size;
        return floats;
    }

    private void drawMask(CameraSnapshot cam) {
        if (mask == null) {
            mask = Framebuffers.screen("Outline Mask", FramebufferSpec.builder()
                    .color(ColorFormat.RGBA8).color(ColorFormat.R32F).depthRenderbuffer().build());
        }
        if (maskProgram == null) maskProgram = new ShaderProgram(MASK_VSH, MASK_FSH);

        int floats = queuedFloats();
        if (upload == null || upload.capacity() < floats) upload = BufferUtils.createFloatBuffer(Math.max(floats, upload == null ? 4096 : upload.capacity() * 2));
        upload.clear();
        for (Batch batch : batches.values()) upload.put(batch.data, 0, batch.size);
        upload.flip();

        mask.begin();
        try {
            mask.clear(0f, 0f, 0f, 0f);
            GlStateManager._enableDepthTest(); GL11.glEnable(GL11.GL_DEPTH_TEST);
            GlStateManager._depthFunc(VanillaCompat.depthNearerOrEqual()); GL11.glDepthFunc(VanillaCompat.depthNearerOrEqual());
            GlStateManager._depthMask(true); GL11.glDepthMask(true);

            maskProgram.begin();
            maskProgram.setMatrix4("Proj", cam.projection);
            maskProgram.setMatrix4("View", cam.view);
            maskProgram.setSampler("Sampler0", 0);

            if (vao == 0) {
                vao = GlStateManager._glGenVertexArrays();
                vbo = GlStateManager._glGenBuffers();
                GlStateManager._glBindVertexArray(vao);
                GlStateManager._glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
                GlStateManager._vertexAttribPointer(0, 3, GL11.GL_FLOAT, false, STRIDE * 4, 0L);
                GlStateManager._enableVertexAttribArray(0);
                GlStateManager._vertexAttribPointer(1, 2, GL11.GL_FLOAT, false, STRIDE * 4, 12L);
                GlStateManager._enableVertexAttribArray(1);
                GlStateManager._vertexAttribPointer(2, 4, GL11.GL_FLOAT, false, STRIDE * 4, 20L);
                GlStateManager._enableVertexAttribArray(2);
            }
            GlStateManager._glBindVertexArray(vao);
            GlStateManager._glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
            GL15.glBufferData(GL15.GL_ARRAY_BUFFER, upload, GL15.GL_STREAM_DRAW);

            int first = 0;
            for (Map.Entry<Identifier, Batch> e : batches.entrySet()) {
                int count = e.getValue().size / STRIDE;
                if (count == 0) continue;
                int tex = textureGlId(e.getKey());
                maskProgram.setInt("Textured", tex > 0 ? 1 : 0);
                if (tex > 0) GlState.bindTexture(0, tex);
                GL11.glDrawArrays(GL11.GL_TRIANGLES, first, count);
                first += count;
            }
            GlStateManager._glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        } finally {
            GlStateManager._disableDepthTest(); GL11.glDisable(GL11.GL_DEPTH_TEST);
            GlStateManager._depthMask(false); GL11.glDepthMask(false);
            mask.end();
        }
    }

    private void composite(CameraSnapshot cam, RenderTarget main) {
        int depthId = VanillaCompat.depthTextureGlId(main);
        if (depthId <= 0 || !mask.isAllocated()) return;
        if (compositeProgram == null) compositeProgram = new ShaderProgram(FULLSCREEN, COMPOSITE_FSH);

        int prev = MainTargetFramebuffer.bind();
        if (prev < 0) return;
        try {
            // main depth gets sampled below, so it can't stay attached
            GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT, GL11.GL_TEXTURE_2D, 0, 0);
            GlStateManager._enableBlend(); GL11.glEnable(GL11.GL_BLEND);
            // keeps the main target's alpha untouched
            GlStateManager._blendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ZERO, GL11.GL_ONE);
            GL14.glBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ZERO, GL11.GL_ONE);

            compositeProgram.begin();
            GlState.bindTexture(0, mask.colorTextureGlId(0));
            GlState.bindTexture(1, mask.colorTextureGlId(1));
            GlState.bindTexture(2, depthId);
            compositeProgram.setSampler("MaskColor", 0);
            compositeProgram.setSampler("MaskDist", 1);
            compositeProgram.setSampler("SceneDepth", 2);
            compositeProgram.setMatrix4("InvProj", invProj.set(cam.projection).invert());
            compositeProgram.setInt("ZeroToOne", cam.zeroToOne ? 1 : 0);
            compositeProgram.setFloat("Width", Math.min(MAX_WIDTH, width * Math.max(1f, main.height / 1080f)));
            compositeProgram.setVec2("Texel", 1f / mask.width(), 1f / mask.height());
            compositeProgram.draw();
        } finally {
            MainTargetFramebuffer.restore(prev);
        }
    }

    private static int textureGlId(Identifier id) {
        if (id == null) return 0;
        AbstractTexture texture = Minecraft.getInstance().getTextureManager().getTexture(id);
        return texture == null ? 0 : VanillaCompat.glId(texture);
    }

    private static final class Batch {
        float[] data = new float[STRIDE * 96];
        int size;
        int idle;

        void vertex(float[] corners, int corner, float r, float g, float b, float a) {
            if (size + STRIDE > data.length) data = Arrays.copyOf(data, data.length * 2);
            int o = corner * 5;
            data[size++] = corners[o];
            data[size++] = corners[o + 1];
            data[size++] = corners[o + 2];
            data[size++] = corners[o + 3];
            data[size++] = corners[o + 4];
            data[size++] = r;
            data[size++] = g;
            data[size++] = b;
            data[size++] = a;
            idle = 0;
        }
    }
}

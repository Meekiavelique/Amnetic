package com.meekdev.amnetic.client.surface.internal;

import com.meekdev.amnetic.client.render.GlState;
import com.meekdev.amnetic.client.render.ShaderProgram;
import com.meekdev.amnetic.client.surface.material.SurfaceMaterial;
import com.meekdev.amnetic.client.surface.reactive.Reactive;
import com.mojang.blaze3d.opengl.GlStateManager;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL33;

import java.nio.FloatBuffer;

public final class UiBatcher {

    public static final UiBatcher INSTANCE = new UiBatcher();

    private static final int FLOATS = 18;
    private static final int MAX_SEGMENTS = 256;

    private ShaderProgram program;
    private int vao, vbo;
    private FloatBuffer verts = BufferUtils.createFloatBuffer(8192 * FLOATS);
    private final Matrix4f ortho = new Matrix4f();
    private float guiW, guiH;

    private final int[] segTexture = new int[MAX_SEGMENTS];
    private final int[] segCount = new int[MAX_SEGMENTS];
    private final boolean[] segNearest = new boolean[MAX_SEGMENTS];
    private int nearestSampler;
    private int segments;

    private float clipX0, clipY0, clipX1, clipY1;

    private float m00 = 1, m01, m10, m11 = 1, m02, m12;

    private UiBatcher() {}

    public float[] transform() {
        return new float[]{m00, m01, m10, m11, m02, m12};
    }

    public void setTransform(float[] t) {
        m00 = t[0]; m01 = t[1]; m10 = t[2]; m11 = t[3]; m02 = t[4]; m12 = t[5];
    }

    public void composeTransform(float pivotX, float pivotY, float dx, float dy, float scale, float rotation) {
        float cos = (float) Math.cos(rotation) * scale;
        float sin = (float) Math.sin(rotation) * scale;
        float a00 = cos, a01 = -sin, a10 = sin, a11 = cos;
        float a02 = pivotX + dx - (a00 * pivotX + a01 * pivotY);
        float a12 = pivotY + dy - (a10 * pivotX + a11 * pivotY);
        float n00 = m00 * a00 + m01 * a10;
        float n01 = m00 * a01 + m01 * a11;
        float n10 = m10 * a00 + m11 * a10;
        float n11 = m10 * a01 + m11 * a11;
        float n02 = m00 * a02 + m01 * a12 + m02;
        float n12 = m10 * a02 + m11 * a12 + m12;
        m00 = n00; m01 = n01; m10 = n10; m11 = n11; m02 = n02; m12 = n12;
    }

    // the same batch, projected by any matrix instead of the screen: a flat surface in the world hands
    // in its camera and plane, and everything the fragment shader does is in canvas pixels either way
    public void begin(float guiW, float guiH, Matrix4fc projection) {
        begin(guiW, guiH);
        ortho.set(projection);
    }

    public void begin(float guiW, float guiH) {
        this.guiW = guiW;
        this.guiH = guiH;
        ortho.setOrtho(0, guiW, guiH, 0, -1000, 1000);
        verts.clear();
        segments = 0;
        clearClip();
    }

    public void setClip(float x0, float y0, float x1, float y1) {
        clipX0 = x0; clipY0 = y0; clipX1 = x1; clipY1 = y1;
    }

    public void clearClip() {
        clipX0 = 0; clipY0 = 0; clipX1 = -1; clipY1 = -1;
    }

    private void segment(int texture) {
        segment(texture, false);
    }

    private void segment(int texture, boolean nearest) {
        if (segments > 0 && segTexture[segments - 1] == texture && segNearest[segments - 1] == nearest) return;
        if (segments == MAX_SEGMENTS) return;
        segTexture[segments] = texture;
        segNearest[segments] = nearest;
        segCount[segments] = 0;
        segments++;
    }

    private void grow(int needed) {
        if (verts.remaining() >= needed) return;
        FloatBuffer bigger = BufferUtils.createFloatBuffer(Math.max(verts.capacity() * 2, verts.capacity() + needed));
        verts.flip();
        bigger.put(verts);
        verts = bigger;
    }

    private void vert(float x, float y, float u, float v,
                      float r, float g, float b, float a,
                      float mode, float radius, float hw, float hh,
                      float borderW, float softness) {
        float tx = m00 * x + m01 * y + m02;
        float ty = m10 * x + m11 * y + m12;
        verts.put(tx).put(ty).put(u).put(v).put(r).put(g).put(b).put(a)
             .put(mode).put(radius).put(hw).put(hh).put(borderW).put(softness)
             .put(clipX0).put(clipY0).put(clipX1).put(clipY1);
        if (segments > 0) segCount[segments - 1] += 1;
    }

    public void rect(float x, float y, float w, float h, float radius,
                     float borderW, float softness, int argb) {
        rectGradient(x, y, w, h, radius, borderW, softness, argb, argb);
    }

    public void rectGradient(float x, float y, float w, float h, float radius,
                             float borderW, float softness, int topArgb, int bottomArgb) {
        segment(0);
        grow(6 * FLOATS);
        float tr = ((topArgb >> 16) & 0xFF) / 255f, tg = ((topArgb >> 8) & 0xFF) / 255f;
        float tb = (topArgb & 0xFF) / 255f, ta = ((topArgb >>> 24) & 0xFF) / 255f;
        float br = ((bottomArgb >> 16) & 0xFF) / 255f, bg = ((bottomArgb >> 8) & 0xFF) / 255f;
        float bb = (bottomArgb & 0xFF) / 255f, ba = ((bottomArgb >>> 24) & 0xFF) / 255f;
        float hw = w * 0.5f, hh = h * 0.5f;
        float cx = x + hw, cy = y + hh;
        float e = softness + 1f;
        float ew = hw + e, eh = hh + e;
        vert(cx - ew, cy - eh, -ew, -eh, tr, tg, tb, ta, 0f, radius, hw, hh, borderW, softness);
        vert(cx - ew, cy + eh, -ew,  eh, br, bg, bb, ba, 0f, radius, hw, hh, borderW, softness);
        vert(cx + ew, cy - eh,  ew, -eh, tr, tg, tb, ta, 0f, radius, hw, hh, borderW, softness);
        vert(cx + ew, cy - eh,  ew, -eh, tr, tg, tb, ta, 0f, radius, hw, hh, borderW, softness);
        vert(cx - ew, cy + eh, -ew,  eh, br, bg, bb, ba, 0f, radius, hw, hh, borderW, softness);
        vert(cx + ew, cy + eh,  ew,  eh, br, bg, bb, ba, 0f, radius, hw, hh, borderW, softness);
    }

    public void rectStops(float x, float y, float w, float h, float radius, float borderW,
                          float[] stops, int[] argbs, float rotation, float offsetX, float offsetY) {
        if (stops.length == 0 || w <= 0 || h <= 0) return;
        segment(0);
        float hw = w * 0.5f, hh = h * 0.5f;
        float cx = x + hw, cy = y + hh;
        float e = 1f;
        float cos = (float) Math.cos(rotation), sin = (float) Math.sin(rotation);
        float ax = cos / w, ay = sin / h;
        float at = 0.5f - (cx / w + offsetX) * cos - (cy / h + offsetY) * sin;
        float[] quad = {cx - hw - e, cy - hh - e, cx + hw + e, cy - hh - e, cx + hw + e, cy + hh + e, cx - hw - e, cy + hh + e};
        for (int n = 0; n <= stops.length; n++) {
            float lo = n == 0 ? Float.NEGATIVE_INFINITY : stops[n - 1];
            float hi = n == stops.length ? Float.POSITIVE_INFINITY : stops[n];
            if (hi < lo) continue;
            float[] piece = clip(clip(quad, ax, ay, at, lo, true), ax, ay, at, hi, false);
            int corners = piece.length / 2;
            if (corners < 3) continue;
            grow((corners - 2) * 3 * FLOATS);
            for (int k = 1; k < corners - 1; k++) {
                stop(piece, 0, cx, cy, ax, ay, at, stops, argbs, radius, hw, hh, borderW);
                stop(piece, k, cx, cy, ax, ay, at, stops, argbs, radius, hw, hh, borderW);
                stop(piece, k + 1, cx, cy, ax, ay, at, stops, argbs, radius, hw, hh, borderW);
            }
        }
    }

    private void stop(float[] piece, int corner, float cx, float cy, float ax, float ay, float at, float[] stops,
                      int[] argbs, float radius, float hw, float hh, float borderW) {
        float px = piece[corner * 2], py = piece[corner * 2 + 1];
        int argb = sample(stops, argbs, ax * px + ay * py + at);
        float r = ((argb >> 16) & 0xFF) / 255f, g = ((argb >> 8) & 0xFF) / 255f;
        float b = (argb & 0xFF) / 255f, a = ((argb >>> 24) & 0xFF) / 255f;
        vert(px, py, px - cx, py - cy, r, g, b, a, 0f, radius, hw, hh, borderW, 0f);
    }

    private static int sample(float[] stops, int[] argbs, float t) {
        if (t <= stops[0]) return argbs[0];
        for (int n = 1; n < stops.length; n++) {
            if (t > stops[n]) continue;
            float span = stops[n] - stops[n - 1];
            float f = span <= 0 ? 1 : (t - stops[n - 1]) / span;
            return lerp(argbs[n - 1], argbs[n], f);
        }
        return argbs[argbs.length - 1];
    }

    private static int lerp(int from, int to, float f) {
        int a = Math.round(((from >>> 24) & 0xFF) + (((to >>> 24) & 0xFF) - ((from >>> 24) & 0xFF)) * f);
        int r = Math.round(((from >> 16) & 0xFF) + (((to >> 16) & 0xFF) - ((from >> 16) & 0xFF)) * f);
        int g = Math.round(((from >> 8) & 0xFF) + (((to >> 8) & 0xFF) - ((from >> 8) & 0xFF)) * f);
        int b = Math.round((from & 0xFF) + ((to & 0xFF) - (from & 0xFF)) * f);
        return a << 24 | r << 16 | g << 8 | b;
    }

    private static float[] clip(float[] polygon, float ax, float ay, float at, float limit, boolean above) {
        if (Float.isInfinite(limit)) return polygon;
        int corners = polygon.length / 2;
        float[] out = new float[(corners + 2) * 2];
        int count = 0;
        for (int n = 0; n < corners; n++) {
            float x0 = polygon[n * 2], y0 = polygon[n * 2 + 1];
            float x1 = polygon[(n + 1) % corners * 2], y1 = polygon[(n + 1) % corners * 2 + 1];
            float d0 = (ax * x0 + ay * y0 + at - limit) * (above ? 1 : -1);
            float d1 = (ax * x1 + ay * y1 + at - limit) * (above ? 1 : -1);
            if (d0 >= 0) {
                out[count++] = x0;
                out[count++] = y0;
            }
            if ((d0 >= 0) != (d1 >= 0)) {
                float f = d0 / (d0 - d1);
                out[count++] = x0 + (x1 - x0) * f;
                out[count++] = y0 + (y1 - y0) * f;
            }
        }
        float[] trimmed = new float[count];
        System.arraycopy(out, 0, trimmed, 0, count);
        return trimmed;
    }

    public void glyph(int atlasTexture, float x0, float y0, float x1, float y1,
                      float u0, float v0, float u1, float v1,
                      float r, float g, float b, float a) {
        glyph(atlasTexture, x0, y0, x1, y1, u0, v0, u1, v1, r, g, b, a, 0f, 0f);
    }

    public void glyph(int atlasTexture, float x0, float y0, float x1, float y1,
                      float u0, float v0, float u1, float v1,
                      float r, float g, float b, float a,
                      float edgeOffset, float softness) {
        segment(atlasTexture);
        grow(6 * FLOATS);
        vert(x0, y0, u0, v0, r, g, b, a, 1f, edgeOffset, softness, 0, 0, 0);
        vert(x0, y1, u0, v1, r, g, b, a, 1f, edgeOffset, softness, 0, 0, 0);
        vert(x1, y0, u1, v0, r, g, b, a, 1f, edgeOffset, softness, 0, 0, 0);
        vert(x1, y0, u1, v0, r, g, b, a, 1f, edgeOffset, softness, 0, 0, 0);
        vert(x0, y1, u0, v1, r, g, b, a, 1f, edgeOffset, softness, 0, 0, 0);
        vert(x1, y1, u1, v1, r, g, b, a, 1f, edgeOffset, softness, 0, 0, 0);
    }

    public void blurBehind(int sceneTexture, float x, float y, float w, float h,
                           float radius, float blurPx, int argb) {
        if (sceneTexture == 0) {
            rect(x, y, w, h, radius, 0f, 0f, argb);
            return;
        }
        segment(sceneTexture);
        grow(6 * FLOATS);
        float r = ((argb >> 16) & 0xFF) / 255f, g = ((argb >> 8) & 0xFF) / 255f;
        float b = (argb & 0xFF) / 255f, a = ((argb >>> 24) & 0xFF) / 255f;
        float hw = w * 0.5f, hh = h * 0.5f;
        float cx = x + hw, cy = y + hh;
        vert(cx - hw, cy - hh, -hw, -hh, r, g, b, a, 3f, radius, hw, hh, 0, blurPx);
        vert(cx - hw, cy + hh, -hw,  hh, r, g, b, a, 3f, radius, hw, hh, 0, blurPx);
        vert(cx + hw, cy - hh,  hw, -hh, r, g, b, a, 3f, radius, hw, hh, 0, blurPx);
        vert(cx + hw, cy - hh,  hw, -hh, r, g, b, a, 3f, radius, hw, hh, 0, blurPx);
        vert(cx - hw, cy + hh, -hw,  hh, r, g, b, a, 3f, radius, hw, hh, 0, blurPx);
        vert(cx + hw, cy + hh,  hw,  hh, r, g, b, a, 3f, radius, hw, hh, 0, blurPx);
    }

    public void image(int texture, float x, float y, float w, float h, int argb) {
        segment(texture);
        grow(6 * FLOATS);
        float r = ((argb >> 16) & 0xFF) / 255f, g = ((argb >> 8) & 0xFF) / 255f;
        float b = (argb & 0xFF) / 255f, a = ((argb >>> 24) & 0xFF) / 255f;
        vert(x, y, 0, 0, r, g, b, a, 2f, 0, 0, 0, 0, 0);
        vert(x, y + h, 0, 1, r, g, b, a, 2f, 0, 0, 0, 0, 0);
        vert(x + w, y, 1, 0, r, g, b, a, 2f, 0, 0, 0, 0, 0);
        vert(x + w, y, 1, 0, r, g, b, a, 2f, 0, 0, 0, 0, 0);
        vert(x, y + h, 0, 1, r, g, b, a, 2f, 0, 0, 0, 0, 0);
        vert(x + w, y + h, 1, 1, r, g, b, a, 2f, 0, 0, 0, 0, 0);
    }

    // part of a texture, by its corners in uv. nearest keeps texel edges hard however far it is scaled,
    // which is what a pixel font or pixel art wants
    public void imageRegion(int texture, float x0, float y0, float x1, float y1,
                            float u0, float v0, float u1, float v1, int argb, boolean nearest) {
        segment(texture, nearest);
        grow(6 * FLOATS);
        float r = ((argb >> 16) & 0xFF) / 255f, g = ((argb >> 8) & 0xFF) / 255f;
        float b = (argb & 0xFF) / 255f, a = ((argb >>> 24) & 0xFF) / 255f;
        vert(x0, y0, u0, v0, r, g, b, a, 2f, 0, 0, 0, 0, 0);
        vert(x0, y1, u0, v1, r, g, b, a, 2f, 0, 0, 0, 0, 0);
        vert(x1, y0, u1, v0, r, g, b, a, 2f, 0, 0, 0, 0, 0);
        vert(x1, y0, u1, v0, r, g, b, a, 2f, 0, 0, 0, 0, 0);
        vert(x0, y1, u0, v1, r, g, b, a, 2f, 0, 0, 0, 0, 0);
        vert(x1, y1, u1, v1, r, g, b, a, 2f, 0, 0, 0, 0, 0);
    }

    // part of a texture on any four corners, top left, top right, bottom right, bottom left, which is
    // what slanted or turning text needs
    public void imageQuad(int texture, float x0, float y0, float x1, float y1, float x2, float y2, float x3, float y3,
                          float u0, float v0, float u1, float v1, int argb, boolean nearest) {
        segment(texture, nearest);
        grow(6 * FLOATS);
        float r = ((argb >> 16) & 0xFF) / 255f, g = ((argb >> 8) & 0xFF) / 255f;
        float b = (argb & 0xFF) / 255f, a = ((argb >>> 24) & 0xFF) / 255f;
        vert(x0, y0, u0, v0, r, g, b, a, 2f, u0, v0, u1, v1, 0);
        vert(x3, y3, u0, v1, r, g, b, a, 2f, u0, v0, u1, v1, 0);
        vert(x1, y1, u1, v0, r, g, b, a, 2f, u0, v0, u1, v1, 0);
        vert(x1, y1, u1, v0, r, g, b, a, 2f, u0, v0, u1, v1, 0);
        vert(x3, y3, u0, v1, r, g, b, a, 2f, u0, v0, u1, v1, 0);
        vert(x2, y2, u1, v1, r, g, b, a, 2f, u0, v0, u1, v1, 0);
    }

    // what was queued so far is drawn, then everything queued inside draw is drawn through program
    // instead of the surface's own shader. the program gets Ortho, Tex, ScreenSize and whatever
    // uniforms sets; each vertex carries its uv rect in Params, so a shader can tell where inside a
    // glyph it is
    public void withProgram(ShaderProgram custom, java.util.function.Consumer<ShaderProgram> uniforms, Runnable draw) {
        flush();
        draw.run();
        if (segments == 0 || verts.position() == 0) return;
        ensureGl();
        custom.begin();
        custom.setMatrix4("Ortho", ortho);
        custom.setSampler("Tex", 0);
        custom.setVec2("ScreenSize", guiW, guiH);
        uniforms.accept(custom);
        drawPending();
        GlStateManager._glUseProgram(0);
    }

    public void material(SurfaceMaterial mat, float x, float y, float w, float h, float radius,
                         float hover, float pressed, float focus, int argb) {
        flush();
        if (!mat.beginDraw(ortho, guiW, guiH, Reactive.clock().peek(), hover, pressed, focus)) {
            rect(x, y, w, h, radius, 0, 0, 0xFF3A2A3A);
            return;
        }
        segment(0);
        grow(6 * FLOATS);
        float r = ((argb >> 16) & 0xFF) / 255f, g = ((argb >> 8) & 0xFF) / 255f;
        float b = (argb & 0xFF) / 255f, a = ((argb >>> 24) & 0xFF) / 255f;
        float hw = w * 0.5f, hh = h * 0.5f;
        float cx = x + hw, cy = y + hh;
        vert(cx - hw, cy - hh, -hw, -hh, r, g, b, a, 0f, radius, hw, hh, 0, 0);
        vert(cx - hw, cy + hh, -hw,  hh, r, g, b, a, 0f, radius, hw, hh, 0, 0);
        vert(cx + hw, cy - hh,  hw, -hh, r, g, b, a, 0f, radius, hw, hh, 0, 0);
        vert(cx + hw, cy - hh,  hw, -hh, r, g, b, a, 0f, radius, hw, hh, 0, 0);
        vert(cx - hw, cy + hh, -hw,  hh, r, g, b, a, 0f, radius, hw, hh, 0, 0);
        vert(cx + hw, cy + hh,  hw,  hh, r, g, b, a, 0f, radius, hw, hh, 0, 0);

        applyBlend(mat.blend());
        drawPending();
        applyBlend(SurfaceMaterial.Blend.MIX);
    }

    private int nearestSampler() {
        if (nearestSampler == 0) {
            nearestSampler = GL33.glGenSamplers();
            GL33.glSamplerParameteri(nearestSampler, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
            GL33.glSamplerParameteri(nearestSampler, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
            GL33.glSamplerParameteri(nearestSampler, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
            GL33.glSamplerParameteri(nearestSampler, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        }
        return nearestSampler;
    }

    public void flush() {
        if (segments == 0 || verts.position() == 0) return;
        ensureGl();
        program.begin();
        program.setMatrix4("Ortho", ortho);
        program.setSampler("Tex", 0);
        program.setVec2("ScreenSize", guiW, guiH);
        drawPending();
        GlStateManager._glUseProgram(0);
    }

    private void drawPending() {
        if (segments == 0 || verts.position() == 0) return;
        ensureGl();
        verts.flip();
        GlStateManager._glBindVertexArray(vao);
        GlStateManager._glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, verts, GL15.GL_STREAM_DRAW);

        int offset = 0;
        for (int i = 0; i < segments; i++) {
            GlState.bindTexture(0, segTexture[i]);
            GL33.glBindSampler(0, segNearest[i] ? nearestSampler() : 0);
            GL11.glDrawArrays(GL11.GL_TRIANGLES, offset, segCount[i]);
            offset += segCount[i];
        }
        // left bound, the sampler would override whatever the game draws on unit zero next
        GL33.glBindSampler(0, 0);

        GlStateManager._glBindVertexArray(0);
        verts.clear();
        segments = 0;
    }

    public void restorePassState() {
        GlStateManager._disableScissorTest(); GL11.glDisable(GL11.GL_SCISSOR_TEST);
        GlStateManager._enableBlend(); GL11.glEnable(GL11.GL_BLEND);
        applyBlend(SurfaceMaterial.Blend.MIX);
        GlStateManager._disableDepthTest(); GL11.glDisable(GL11.GL_DEPTH_TEST);
        GlStateManager._depthMask(false); GL11.glDepthMask(false);
        GlStateManager._disableCull(); GL11.glDisable(GL11.GL_CULL_FACE);
    }

    private void applyBlend(SurfaceMaterial.Blend blend) {
        switch (blend) {
            case ADD -> {
                GlStateManager._blendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE, GL11.GL_ONE);
                GL14.glBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE, GL11.GL_ONE);
            }
            case PREMUL -> {
                GlStateManager._blendFuncSeparate(GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
                GL14.glBlendFuncSeparate(GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
            }
            default -> {
                GlStateManager._blendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
                GL14.glBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
            }
        }
    }

    private void ensureGl() {
        if (program == null) {
            program = new ShaderProgram(
                    Identifier.fromNamespaceAndPath("amnetic", "shaders/surface/ui.vsh"),
                    Identifier.fromNamespaceAndPath("amnetic", "shaders/surface/ui.fsh"));
        }
        if (vao != 0) return;
        vao = GL30.glGenVertexArrays();
        GlStateManager._glBindVertexArray(vao);
        vbo = GL15.glGenBuffers();
        GlStateManager._glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
        int stride = FLOATS * 4;
        GL20.glEnableVertexAttribArray(0);
        GL20.glVertexAttribPointer(0, 2, GL11.GL_FLOAT, false, stride, 0);
        GL20.glEnableVertexAttribArray(1);
        GL20.glVertexAttribPointer(1, 2, GL11.GL_FLOAT, false, stride, 8);
        GL20.glEnableVertexAttribArray(2);
        GL20.glVertexAttribPointer(2, 4, GL11.GL_FLOAT, false, stride, 16);
        GL20.glEnableVertexAttribArray(3);
        GL20.glVertexAttribPointer(3, 4, GL11.GL_FLOAT, false, stride, 32);
        GL20.glEnableVertexAttribArray(4);
        GL20.glVertexAttribPointer(4, 2, GL11.GL_FLOAT, false, stride, 48);
        GL20.glEnableVertexAttribArray(5);
        GL20.glVertexAttribPointer(5, 4, GL11.GL_FLOAT, false, stride, 56);
        GlStateManager._glBindVertexArray(0);
    }
}

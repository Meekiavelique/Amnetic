package com.meekdev.amnetic.client.surface.internal;

import com.meekdev.amnetic.client.render.GlState;
import com.meekdev.amnetic.client.render.ShaderProgram;
import com.meekdev.amnetic.client.surface.material.SurfaceMaterial;
import com.meekdev.amnetic.client.surface.reactive.Reactive;
import com.mojang.blaze3d.opengl.GlStateManager;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4f;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

import java.nio.FloatBuffer;

// one interleaved stream for every surface primitive, split into segments on texture change,
// material draws flush what came before so painter's order always holds
// vertex: pos2 uv2 color4 params4 extra2 clip4 = 18 floats
// params = mode(0 rect sdf, 1 glyph, 2 textured), radius, halfW, halfH; extra = borderW, softness
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
    private int segments;

    // active clip in gui px, x1 <= 0 means none
    private float clipX0, clipY0, clipX1, clipY1;

    // current affine transform applied to vertex positions cpu-side, identity by default
    // sdf params stay in local space so rotated/scaled shapes and glyphs remain crisp
    private float m00 = 1, m01, m10, m11 = 1, m02, m12;

    private UiBatcher() {}

    public float[] transform() {
        return new float[]{m00, m01, m10, m11, m02, m12};
    }

    public void setTransform(float[] t) {
        m00 = t[0]; m01 = t[1]; m10 = t[2]; m11 = t[3]; m02 = t[4]; m12 = t[5];
    }

    // compose translate + uniform scale + rotation around a pivot onto the current transform
    public void composeTransform(float pivotX, float pivotY, float dx, float dy, float scale, float rotation) {
        float cos = (float) Math.cos(rotation) * scale;
        float sin = (float) Math.sin(rotation) * scale;
        // local: p' = pivot + d + R*S*(p - pivot)
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
        if (segments > 0 && segTexture[segments - 1] == texture) return;
        if (segments == MAX_SEGMENTS) return; // merge into the last, better than crashing
        segTexture[segments] = texture;
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

    // sdf rounded rect, uv carries the pixel offset from the rect center
    public void rect(float x, float y, float w, float h, float radius,
                     float borderW, float softness, int argb) {
        rectGradient(x, y, w, h, radius, borderW, softness, argb, argb);
    }

    // vertical gradient comes free from per-vertex color interpolation
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
        float e = softness + 1f; // expand so shadow feather is not clipped by the geometry
        float ew = hw + e, eh = hh + e;
        vert(cx - ew, cy - eh, -ew, -eh, tr, tg, tb, ta, 0f, radius, hw, hh, borderW, softness);
        vert(cx - ew, cy + eh, -ew,  eh, br, bg, bb, ba, 0f, radius, hw, hh, borderW, softness);
        vert(cx + ew, cy - eh,  ew, -eh, tr, tg, tb, ta, 0f, radius, hw, hh, borderW, softness);
        vert(cx + ew, cy - eh,  ew, -eh, tr, tg, tb, ta, 0f, radius, hw, hh, borderW, softness);
        vert(cx - ew, cy + eh, -ew,  eh, br, bg, bb, ba, 0f, radius, hw, hh, borderW, softness);
        vert(cx + ew, cy + eh,  ew,  eh, br, bg, bb, ba, 0f, radius, hw, hh, borderW, softness);
    }

    // sdf glyph quad, uv is atlas uv
    public void glyph(int atlasTexture, float x0, float y0, float x1, float y1,
                      float u0, float v0, float u1, float v1,
                      float r, float g, float b, float a) {
        segment(atlasTexture);
        grow(6 * FLOATS);
        vert(x0, y0, u0, v0, r, g, b, a, 1f, 0, 0, 0, 0, 0);
        vert(x0, y1, u0, v1, r, g, b, a, 1f, 0, 0, 0, 0, 0);
        vert(x1, y0, u1, v0, r, g, b, a, 1f, 0, 0, 0, 0, 0);
        vert(x1, y0, u1, v0, r, g, b, a, 1f, 0, 0, 0, 0, 0);
        vert(x0, y1, u0, v1, r, g, b, a, 1f, 0, 0, 0, 0, 0);
        vert(x1, y1, u1, v1, r, g, b, a, 1f, 0, 0, 0, 0, 0);
    }

    // frosted glass: samples the blurred scene behind the rect, masked by the rounded sdf
    public void blurBehind(int sceneTexture, float x, float y, float w, float h,
                           float radius, float blurPx, int argb) {
        if (sceneTexture == 0) { // capture unavailable, translucent fallback
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

    // textured quad with a raw gl texture id
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

    // custom-material rect: flush what came before, then draw this quad with the material's
    // program so painter's order is preserved
    public void material(SurfaceMaterial mat, float x, float y, float w, float h, float radius,
                         float hover, float pressed, float focus, int argb) {
        flush();
        if (!mat.beginDraw(ortho, guiW, guiH, Reactive.clock().peek(), hover, pressed, focus)) {
            // broken material falls back to an obvious flat fill so layout stays debuggable
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
        drawPending(); // program already bound by beginDraw
        applyBlend(SurfaceMaterial.Blend.MIX);
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
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo); // raw too, cached bind can be stale
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, verts, GL15.GL_STREAM_DRAW);

        int offset = 0;
        for (int i = 0; i < segments; i++) {
            // raw bind + cache sync, a cached-only bind gets skipped when vanilla's new
            // backend changed the real binding behind GlStateManager's back
            GlState.bindTexture(0, segTexture[i]);
            GL11.glDrawArrays(GL11.GL_TRIANGLES, offset, segCount[i]);
            offset += segCount[i];
        }

        GlStateManager._glBindVertexArray(0);
        verts.clear();
        segments = 0;
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

package com.meekdev.amnetic.client.surface.internal;

import com.meekdev.amnetic.client.render.GlState;
import com.meekdev.amnetic.client.render.ShaderProgram;
import com.mojang.blaze3d.opengl.GlStateManager;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4f;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

import java.nio.FloatBuffer;

// one interleaved stream for every surface primitive, split into segments on texture change
// vertex: pos2 uv2 color4 params4 extra2 = 14 floats
// params = mode(0 rect sdf, 1 glyph, 2 textured), radius, halfW, halfH; extra = borderW, softness
public final class UiBatcher {

    public static final UiBatcher INSTANCE = new UiBatcher();

    private static final int FLOATS = 14;
    private static final int MAX_SEGMENTS = 256;

    private ShaderProgram program;
    private int vao, vbo;
    private FloatBuffer verts = BufferUtils.createFloatBuffer(8192 * FLOATS);
    private final Matrix4f ortho = new Matrix4f();

    // segment ring: texture id (0 = none) + vert count, in submission order
    private final int[] segTexture = new int[MAX_SEGMENTS];
    private final int[] segCount = new int[MAX_SEGMENTS];
    private int segments;
    private int currentTexture = -1;

    private UiBatcher() {}

    public void begin(float guiW, float guiH) {
        ortho.setOrtho(0, guiW, guiH, 0, -1000, 1000);
        verts.clear();
        segments = 0;
        currentTexture = -1;
    }

    private void segment(int texture) {
        if (segments > 0 && segTexture[segments - 1] == texture) return;
        if (segments == MAX_SEGMENTS) return; // silently merge into the last, better than crashing
        segTexture[segments] = texture;
        segCount[segments] = 0;
        segments++;
        currentTexture = texture;
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
        verts.put(x).put(y).put(u).put(v).put(r).put(g).put(b).put(a)
             .put(mode).put(radius).put(hw).put(hh).put(borderW).put(softness);
        if (segments > 0) segCount[segments - 1] += 1;
    }

    // sdf rounded rect, uv carries the pixel offset from the rect center
    public void rect(float x, float y, float w, float h, float radius,
                     float borderW, float softness, int argb) {
        segment(0);
        grow(6 * FLOATS);
        float r = ((argb >> 16) & 0xFF) / 255f, g = ((argb >> 8) & 0xFF) / 255f;
        float b = (argb & 0xFF) / 255f, a = ((argb >>> 24) & 0xFF) / 255f;
        float hw = w * 0.5f, hh = h * 0.5f;
        float cx = x + hw, cy = y + hh;
        // expand the quad by the softness so shadow feather isn't clipped at the geometry edge
        float e = softness + 1f;
        quad(cx, cy, hw + e, hh + e, r, g, b, a, 0f, radius, hw, hh, borderW, softness);
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

    private void quad(float cx, float cy, float ew, float eh,
                      float r, float g, float b, float a,
                      float mode, float radius, float hw, float hh, float borderW, float softness) {
        vert(cx - ew, cy - eh, -ew, -eh, r, g, b, a, mode, radius, hw, hh, borderW, softness);
        vert(cx - ew, cy + eh, -ew,  eh, r, g, b, a, mode, radius, hw, hh, borderW, softness);
        vert(cx + ew, cy - eh,  ew, -eh, r, g, b, a, mode, radius, hw, hh, borderW, softness);
        vert(cx + ew, cy - eh,  ew, -eh, r, g, b, a, mode, radius, hw, hh, borderW, softness);
        vert(cx - ew, cy + eh, -ew,  eh, r, g, b, a, mode, radius, hw, hh, borderW, softness);
        vert(cx + ew, cy + eh,  ew,  eh, r, g, b, a, mode, radius, hw, hh, borderW, softness);
    }

    public void flush() {
        if (segments == 0 || verts.position() == 0) return;
        ensureGl();
        verts.flip();

        program.begin();
        program.setMatrix4("Ortho", ortho);
        program.setSampler("Tex", 0);

        GlStateManager._glBindVertexArray(vao);
        GlStateManager._glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
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
        GlStateManager._glUseProgram(0);
        verts.clear();
        segments = 0;
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
        GlStateManager._glBindVertexArray(0);
    }
}

package com.meekdev.amnetic.client.light.internal;

import java.util.ArrayList;
import com.meekdev.amnetic.client.light.Light;
import com.meekdev.amnetic.client.light.LightType;
import com.meekdev.amnetic.client.render.CameraSnapshot;
import com.meekdev.amnetic.client.render.ShaderProgram;
import com.mojang.blaze3d.opengl.GlStateManager;
import java.nio.FloatBuffer;
import java.util.List;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4f;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

public final class LightVolumePass {

    public static final LightVolumePass INSTANCE = new LightVolumePass();

    private static final Identifier VSH =
            Identifier.fromNamespaceAndPath("amnetic", "shaders/light/volume.vsh");
    private static final Identifier FSH =
            Identifier.fromNamespaceAndPath("amnetic", "shaders/light/volume.fsh");

    private ShaderProgram program;
    private int vao;
    private int vbo;
    private int vertexCount;

    private LightVolumePass() {}

    public static boolean handles(LightType type) {
        return type != LightType.DIRECTIONAL;
    }

    public void render(CameraSnapshot cam, List<Light> packed, float screenW, float screenH) {
        if (packed.isEmpty()) return;
        ensureMesh();
        if (program == null) program = new ShaderProgram(VSH, FSH);

        Matrix4f viewProj = new Matrix4f(cam.projection).mul(new Matrix4f(cam.view));

        GlStateManager._enableBlend();
        GL11.glEnable(GL11.GL_BLEND);
        GlStateManager._blendFuncSeparate(GL11.GL_ONE, GL11.GL_ONE, GL11.GL_ONE, GL11.GL_ONE);
        GL14.glBlendFuncSeparate(GL11.GL_ONE, GL11.GL_ONE, GL11.GL_ONE, GL11.GL_ONE);
        GlStateManager._depthMask(false);
        GL11.glDepthMask(false);
        GlStateManager._disableDepthTest();
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GlStateManager._enableCull();
        GL11.glEnable(GL11.GL_CULL_FACE);

        program.begin();
        program.setSampler("AlbedoSampler", 0);
        program.setSampler("DepthSampler", 1);
        program.setSampler("GNormalSampler", 2);
        program.setSampler("GMaterialSampler", 3);
        program.setMatrix4("ViewProjVolume", viewProj);
        program.setMatrix4("InvViewProj", cam.invViewProj);
        program.setMatrix4("ViewProj", viewProj);
        program.setInt("ZeroToOne", cam.zeroToOne ? 1 : 0);
        program.setVec2("ScreenSize", screenW, screenH);

        GL30.glBindVertexArray(vao);
        for (int i = 0; i < packed.size(); i++) {
            Light light = packed.get(i);
            if (!handles(light.type())) continue;
            float range = light.range();
            float px = (float) (light.x() - cam.eye.x);
            float py = (float) (light.y() - cam.eye.y);
            float pz = (float) (light.z() - cam.eye.z);

            // camera inside the volume: its front faces are behind us, so draw the back faces
            float d = px * px + py * py + pz * pz;
            float pad = range * 1.05f;
            GL11.glCullFace(d < pad * pad ? GL11.GL_FRONT : GL11.GL_BACK);

            program.setInt("LightIndex", i);
            program.setVec3("LightPos", px, py, pz);
            program.setFloat("LightRadius", pad);
            GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, vertexCount);
        }
        GL30.glBindVertexArray(0);

        GL11.glCullFace(GL11.GL_BACK);
        GlStateManager._depthMask(true);
        GL11.glDepthMask(true);
    }

    private void ensureMesh() {
        if (vao != 0) return;
        float[] verts = icosphere(2);
        FloatBuffer buf = BufferUtils.createFloatBuffer(verts.length);
        buf.put(verts).flip();
        vertexCount = verts.length / 3;

        vao = GL30.glGenVertexArrays();
        vbo = GL15.glGenBuffers();
        GL30.glBindVertexArray(vao);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, buf, GL15.GL_STATIC_DRAW);
        GL20.glEnableVertexAttribArray(0);
        GL20.glVertexAttribPointer(0, 3, GL11.GL_FLOAT, false, 3 * Float.BYTES, 0L);
        GL30.glBindVertexArray(0);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
    }

    private static float[] icosphere(int subdivisions) {
        float t = (float) ((1.0 + Math.sqrt(5.0)) / 2.0);
        float[][] base = {
                {-1, t, 0}, {1, t, 0}, {-1, -t, 0}, {1, -t, 0},
                {0, -1, t}, {0, 1, t}, {0, -1, -t}, {0, 1, -t},
                {t, 0, -1}, {t, 0, 1}, {-t, 0, -1}, {-t, 0, 1},
        };
        int[][] faces = {
                {0,11,5},{0,5,1},{0,1,7},{0,7,10},{0,10,11},
                {1,5,9},{5,11,4},{11,10,2},{10,7,6},{7,1,8},
                {3,9,4},{3,4,2},{3,2,6},{3,6,8},{3,8,9},
                {4,9,5},{2,4,11},{6,2,10},{8,6,7},{9,8,1},
        };
        List<float[]> tris = new ArrayList<>();
        for (int[] f : faces) {
            tris.add(norm(base[f[0]]));
            tris.add(norm(base[f[1]]));
            tris.add(norm(base[f[2]]));
        }
        for (int s = 0; s < subdivisions; s++) {
            List<float[]> next = new ArrayList<>(tris.size() * 4);
            for (int i = 0; i < tris.size(); i += 3) {
                float[] a = tris.get(i), b = tris.get(i + 1), c = tris.get(i + 2);
                float[] ab = norm(mid(a, b)), bc = norm(mid(b, c)), ca = norm(mid(c, a));
                next.add(a); next.add(ab); next.add(ca);
                next.add(b); next.add(bc); next.add(ab);
                next.add(c); next.add(ca); next.add(bc);
                next.add(ab); next.add(bc); next.add(ca);
            }
            tris = next;
        }
        float[] out = new float[tris.size() * 3];
        int w = 0;
        for (float[] v : tris) { out[w++] = v[0]; out[w++] = v[1]; out[w++] = v[2]; }
        return out;
    }

    private static float[] norm(float[] v) {
        float l = (float) Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
        return new float[]{v[0] / l, v[1] / l, v[2] / l};
    }

    private static float[] mid(float[] a, float[] b) {
        return new float[]{(a[0] + b[0]) * 0.5f, (a[1] + b[1]) * 0.5f, (a[2] + b[2]) * 0.5f};
    }

    public void dispose() {
        if (program != null) { program.close(); program = null; }
        if (vbo != 0) { GL15.glDeleteBuffers(vbo); vbo = 0; }
        if (vao != 0) { GL30.glDeleteVertexArrays(vao); vao = 0; }
        vertexCount = 0;
    }
}

package com.meekdev.amnetic.client.surface.internal;

import com.meekdev.amnetic.client.framebuffer.ColorFormat;
import com.meekdev.amnetic.client.framebuffer.Framebuffer;
import com.meekdev.amnetic.client.framebuffer.Framebuffers;
import com.meekdev.amnetic.client.framebuffer.FramebufferSpec;
import com.meekdev.amnetic.client.pipeline.FrameContext;
import com.meekdev.amnetic.client.pipeline.Pipeline;
import com.meekdev.amnetic.client.pipeline.RenderStage;
import com.meekdev.amnetic.client.render.GlState;
import com.meekdev.amnetic.client.render.ShaderProgram;
import com.meekdev.amnetic.client.surface.WorldSurface;
import com.meekdev.amnetic.client.surface.draw.UiDraw;
import com.mojang.blaze3d.opengl.GlStateManager;
import java.nio.FloatBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.lwjgl.BufferUtils;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class WorldSurfaceRenderer {

    public static final WorldSurfaceRenderer INSTANCE = new WorldSurfaceRenderer();
    private static final Logger LOG = LoggerFactory.getLogger("Amnetic/Surface");

    private static final int SEGMENTS = 24;

    private final CopyOnWriteArrayList<WorldSurface> surfaces = new CopyOnWriteArrayList<>();
    private final Map<WorldSurface, Framebuffer> canvases = new HashMap<>();
    private boolean registered;

    private ShaderProgram program;
    private int vao, vbo;
    private final FloatBuffer grid = BufferUtils.createFloatBuffer((SEGMENTS + 1) * 2 * 2 * 5 * 3);
    private final Map<WorldSurface, float[]> pickTris = new HashMap<>();

    private boolean attackWasDown;
    private boolean pointerOverSurface;

    private WorldSurfaceRenderer() {}

    public boolean isPointerOverSurface() {
        return pointerOverSurface;
    }

    public boolean hasKeyboardFocus() {
        return focusedSurface() != null;
    }

    public boolean keyPressed(int key, int modifiers) {
        WorldSurface surface = focusedSurface();
        if (surface == null) {
            return false;
        }
        surface.internalInput().keyPressed(key, modifiers);
        return true;
    }

    public boolean charTyped(int codepoint) {
        WorldSurface surface = focusedSurface();
        if (surface == null) {
            return false;
        }
        surface.internalInput().charTyped(codepoint);
        return true;
    }

    private WorldSurface focusedSurface() {
        for (WorldSurface surface : surfaces) {
            if (surface.isVisible() && surface.internalInput().focused() != null) {
                return surface;
            }
        }
        return null;
    }

    public void add(WorldSurface surface) {
        surfaces.add(surface);
        register();
    }

    public void remove(WorldSurface surface) {
        surfaces.remove(surface);
        Framebuffer fb = canvases.remove(surface);
        if (fb != null) fb.dispose();
        pickTris.remove(surface);
    }

    private void register() {
        if (registered) return;
        registered = true;
        Pipeline.add(RenderStage.AFTER_WATER, 60, "World Surfaces", this::render);
        ClientTickEvents.END_CLIENT_TICK.register(mc -> pick(mc));
    }

    private void render(FrameContext fc) {
        if (surfaces.isEmpty() || fc.camera() == null) return;
        Vec3 eye = fc.camera().eye;

        for (WorldSurface s : surfaces) {
            if (!s.isVisible()) continue;
            double dist = eye.distanceTo(new Vec3(s.xPos(), s.yPos(), s.zPos()));
            if (dist > s.maxDistanceValue() + 16) continue;
            try {
                renderCanvas(s);
                drawInWorld(s, fc);
            } catch (Exception e) {
                LOG.warn("world surface draw failed, hiding it", e);
                s.setVisible(false);
            }
        }
    }

    private void renderCanvas(WorldSurface s) {
        Framebuffer fb = canvases.computeIfAbsent(s, k ->
                Framebuffers.fixed(k.canvasW(), k.canvasH(),
                        FramebufferSpec.builder().color(ColorFormat.RGBA8).build()));

        float w = s.canvasW(), h = s.canvasH();
        UiBatcher batcher = UiBatcher.INSTANCE;

        fb.begin();
        GL11.glClearColor(0f, 0f, 0f, 0f);
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
        GlStateManager._enableBlend(); GL11.glEnable(GL11.GL_BLEND);
        GlStateManager._blendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL14.glBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GlStateManager._disableDepthTest(); GL11.glDisable(GL11.GL_DEPTH_TEST);
        GlStateManager._depthMask(false); GL11.glDepthMask(false);
        GlStateManager._disableCull(); GL11.glDisable(GL11.GL_CULL_FACE);

        batcher.begin(w, h);
        UiDraw draw = new UiDraw(batcher, w, h);
        s.internalTree().layout(0, 0, w, h);
        s.internalTree().draw(draw, 1f);
        batcher.flush();

        GlStateManager._depthMask(true); GL11.glDepthMask(true);
        fb.end();
    }

    private void drawInWorld(WorldSurface s, FrameContext fc) {
        ensureGl();
        Vec3 eye = fc.camera().eye;

        Vector3f n = s.billboardValue()
                ? new Vector3f((float) (eye.x - s.xPos()), 0, (float) (eye.z - s.zPos()))
                : new Vector3f(s.facingValue().x, 0, s.facingValue().z);
        if (n.lengthSquared() < 1e-6f) n.set(0, 0, -1);
        n.normalize();
        Vector3f right = new Vector3f(-n.z, 0, n.x);

        float wM = s.widthM(), hM = s.heightM();
        float curve = s.curveValue();
        int segs = curve != 0 ? SEGMENTS : 1;
        float r = curve != 0 ? wM / curve : 0;

        grid.clear();
        float[] tris = new float[segs * 2 * 9];
        int t = 0;
        for (int i = 0; i < segs; i++) {
            float u0 = (float) i / segs, u1 = (float) (i + 1) / segs;
            float[] su = {u0, u1, u1, u0, u1, u0};
            boolean[] st = {true, true, false, true, false, false};
            for (int k = 0; k < 6; k++) {
                float u = su[k];
                boolean top = st[k];
                float local = (u - 0.5f) * wM;
                float ox, oz;
                if (curve != 0) {
                    float theta = local / r;
                    ox = r * (float) Math.sin(theta);
                    oz = r * (1f - (float) Math.cos(theta));
                } else {
                    ox = local;
                    oz = 0;
                }
                double wx = s.xPos() + right.x * ox + n.x * oz;
                double wy = s.yPos() + (top ? hM * 0.5f : -hM * 0.5f);
                double wz = s.zPos() + right.z * ox + n.z * oz;
                grid.put((float) (wx - eye.x)).put((float) (wy - eye.y)).put((float) (wz - eye.z));
                grid.put(u).put(top ? 1f : 0f);
                tris[t++] = (float) wx; tris[t++] = (float) wy; tris[t++] = (float) wz;
            }
        }
        pickTris.put(s, tris);
        grid.flip();

        Framebuffer fb = canvases.get(s);
        if (fb == null) return;

        program.begin();
        program.setMatrix4("ViewProj", fc.camera().viewProj);
        program.setSampler("Canvas", 0);
        program.setFloat("Opacity", 1f);
        GlState.bindTexture(0, fb.colorTextureGlId(0));

        GlStateManager._enableBlend(); GL11.glEnable(GL11.GL_BLEND);
        GlStateManager._blendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL14.glBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GlStateManager._disableCull(); GL11.glDisable(GL11.GL_CULL_FACE);
        if (s.alwaysOnTopValue()) {
            GlStateManager._disableDepthTest(); GL11.glDisable(GL11.GL_DEPTH_TEST);
        } else {
            GlStateManager._enableDepthTest(); GL11.glEnable(GL11.GL_DEPTH_TEST);
            GL11.glDepthFunc(GL11.GL_LEQUAL);
        }
        GlStateManager._depthMask(false); GL11.glDepthMask(false);

        GlStateManager._glBindVertexArray(vao);
        GlStateManager._glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, grid, GL15.GL_STREAM_DRAW);
        GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, segs * 6);
        GlStateManager._glBindVertexArray(0);
        GlStateManager._glUseProgram(0);
        GlStateManager._depthMask(true); GL11.glDepthMask(true);
        GlStateManager._enableDepthTest(); GL11.glEnable(GL11.GL_DEPTH_TEST);
    }

    private void pick(Minecraft mc) {
        pointerOverSurface = false;
        if (mc.player == null || mc.screen != null || surfaces.isEmpty()) return;
        Vec3 eye = mc.player.getEyePosition();
        Vec3 look = mc.player.getLookAngle();

        WorldSurface best = null;
        float bestT = Float.MAX_VALUE;
        float bestU = 0, bestV = 0;

        for (WorldSurface s : surfaces) {
            if (!s.isVisible()) continue;
            float[] tris = pickTris.get(s);
            if (tris == null) continue;
            int segs = tris.length / 18;
            for (int i = 0; i < segs * 2; i++) {
                float[] hit = rayTriangle(eye, look, tris, i * 9);
                if (hit != null && hit[0] < bestT && hit[0] <= s.maxDistanceValue()) {
                    bestT = hit[0];
                    int seg = i / 2;
                    boolean second = (i & 1) == 1;
                    float u0 = (float) seg / segs, u1 = (float) (seg + 1) / segs;
                    float[] us = second ? new float[]{u0, u1, u0} : new float[]{u0, u1, u1};
                    float[] vs = second ? new float[]{1, 0, 0} : new float[]{1, 1, 0};
                    bestU = us[0] * hit[1] + us[1] * hit[2] + us[2] * hit[3];
                    bestV = vs[0] * hit[1] + vs[1] * hit[2] + vs[2] * hit[3];
                    best = s;
                }
            }
        }

        for (WorldSurface s : surfaces) {
            if (s != best) s.internalInput().mouseMoved(-1, -1);
        }

        pointerOverSurface = best != null;
        boolean attack = GLFW.glfwGetMouseButton(mc.getWindow().handle(), GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
        if (best != null) {
            float mx = bestU * best.canvasW();
            float my = (1f - bestV) * best.canvasH();
            best.internalInput().mouseMoved(mx, my);
            if (attack && !attackWasDown) best.internalInput().mouseDown(mx, my, 0);
            if (!attack && attackWasDown) best.internalInput().mouseUp(mx, my, 0);
        }
        attackWasDown = attack;
    }

    private static float[] rayTriangle(Vec3 origin, Vec3 dir, float[] tris, int off) {
        float ax = tris[off], ay = tris[off + 1], az = tris[off + 2];
        float bx = tris[off + 3], by = tris[off + 4], bz = tris[off + 5];
        float cx = tris[off + 6], cy = tris[off + 7], cz = tris[off + 8];
        float e1x = bx - ax, e1y = by - ay, e1z = bz - az;
        float e2x = cx - ax, e2y = cy - ay, e2z = cz - az;
        float px = (float) (dir.y * e2z - dir.z * e2y);
        float py = (float) (dir.z * e2x - dir.x * e2z);
        float pz = (float) (dir.x * e2y - dir.y * e2x);
        float det = e1x * px + e1y * py + e1z * pz;
        if (Math.abs(det) < 1e-7f) return null;
        float inv = 1f / det;
        float tx = (float) (origin.x - ax), ty = (float) (origin.y - ay), tz = (float) (origin.z - az);
        float u = (tx * px + ty * py + tz * pz) * inv;
        if (u < 0 || u > 1) return null;
        float qx = ty * e1z - tz * e1y;
        float qy = tz * e1x - tx * e1z;
        float qz = tx * e1y - ty * e1x;
        float v = (float) (dir.x * qx + dir.y * qy + dir.z * qz) * inv;
        if (v < 0 || u + v > 1) return null;
        float t = (e2x * qx + e2y * qy + e2z * qz) * inv;
        if (t <= 0) return null;
        return new float[]{t, 1 - u - v, u, v};
    }

    private void ensureGl() {
        if (program == null) {
            program = new ShaderProgram(
                    Identifier.fromNamespaceAndPath("amnetic", "shaders/surface/world.vsh"),
                    Identifier.fromNamespaceAndPath("amnetic", "shaders/surface/world.fsh"));
        }
        if (vao != 0) return;
        vao = GL30.glGenVertexArrays();
        GlStateManager._glBindVertexArray(vao);
        vbo = GL15.glGenBuffers();
        GlStateManager._glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
        GL20.glEnableVertexAttribArray(0);
        GL20.glVertexAttribPointer(0, 3, GL11.GL_FLOAT, false, 20, 0);
        GL20.glEnableVertexAttribArray(1);
        GL20.glVertexAttribPointer(1, 2, GL11.GL_FLOAT, false, 20, 12);
        GlStateManager._glBindVertexArray(0);
    }
}

package com.meekdev.amnetic.client.ibl.internal;

import com.meekdev.amnetic.client.model.ModelLighting;
import com.meekdev.amnetic.client.render.ShaderProgram;
import com.mojang.blaze3d.opengl.GlStateManager;
import java.nio.ByteBuffer;
import net.minecraft.resources.Identifier;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL32;

public final class EnvProbe {
    public static final EnvProbe INSTANCE = new EnvProbe();

    private static final Identifier VSH = Identifier.fromNamespaceAndPath("amnetic", "shaders/bloom/fullscreen.vsh");
    private static final Identifier FSH = Identifier.fromNamespaceAndPath("amnetic", "shaders/env/capture.fsh");

    private static final int SIZE = 128;
    private static final int SUN_STEPS = 24;
    private static final float[][] FACES = {
            { 1, 0, 0, 0,-1, 0, 0, 0,-1},
            {-1, 0, 0, 0,-1, 0, 0, 0, 1},
            { 0, 1, 0, 0, 0, 1, 1, 0, 0},
            { 0,-1, 0, 0, 0,-1, 1, 0, 0},
            { 0, 0, 1, 0,-1, 0, 1, 0, 0},
            { 0, 0,-1, 0,-1, 0, -1, 0, 0},
    };

    private int cube;
    private int fbo;
    private int mipLevels;
    private ShaderProgram capture;
    private boolean ready;
    private int lastSunBucket = Integer.MIN_VALUE;

    private EnvProbe() {}

    public int glId() { return cube; }
    public boolean isReady() { return ready; }
    public int maxLod() { return Math.max(0, mipLevels - 1); }

    private static int bucketOf(float x, float y, float z) {
        int bx = Math.round(x * SUN_STEPS);
        int by = Math.round(y * SUN_STEPS);
        int bz = Math.round(z * SUN_STEPS);
        return (bx * 401 + by) * 401 + bz;
    }

    // bake the cube from the scene sun, only re-baking when it has moved enough to matter
    public void update(float dayFraction) {
        ModelLighting lighting = ModelLighting.INSTANCE;
        float sunX;
        float sunY;
        float sunZ;
        if (lighting.sunSet()) {
            sunX = lighting.sunX();
            sunY = lighting.sunY();
            sunZ = lighting.sunZ();
        } else {
            double phi = (dayFraction - 0.25) * 2.0 * Math.PI;
            sunX = (float) -Math.sin(phi);
            sunY = (float) Math.cos(phi);
            sunZ = 0f;
        }
        float inv = 1f / (float) Math.sqrt(sunX * sunX + sunY * sunY + sunZ * sunZ);
        sunX *= inv; sunY *= inv; sunZ *= inv;

        int bucket = bucketOf(sunX, sunY, sunZ);
        if (ready && bucket == lastSunBucket) return;
        lastSunBucket = bucket;

        if (capture == null) {
            capture = new ShaderProgram(VSH, FSH);
            allocate();
        }

        int prevFbo = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int[] vp = new int[4];
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, vp);

        GL32.glEnable(GL32.GL_TEXTURE_CUBE_MAP_SEAMLESS);
        GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, fbo);
        GL11.glViewport(0, 0, SIZE, SIZE);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDepthMask(false);
        capture.begin();
        capture.setVec3("SunDir", sunX, sunY, sunZ);
        for (int f = 0; f < 6; f++) {
            GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0,
                    GL13.GL_TEXTURE_CUBE_MAP_POSITIVE_X + f, cube, 0);
            float[] b = FACES[f];
            capture.setVec3("Forward", b[0], b[1], b[2]);
            capture.setVec3("Up", b[3], b[4], b[5]);
            capture.setVec3("Right", b[6], b[7], b[8]);
            capture.draw();
        }

        GlStateManager._activeTexture(GL13.GL_TEXTURE0);
        GL11.glBindTexture(GL13.GL_TEXTURE_CUBE_MAP, cube);
        GL30.glGenerateMipmap(GL13.GL_TEXTURE_CUBE_MAP);

        GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, prevFbo);
        GL11.glViewport(vp[0], vp[1], vp[2], vp[3]);
        GL11.glDepthMask(true);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        ready = true;
    }

    private void allocate() {
        cube = GL11.glGenTextures();
        fbo = GL30.glGenFramebuffers();
        mipLevels = 1 + (int) (Math.log(SIZE) / Math.log(2));
        GlStateManager._activeTexture(GL13.GL_TEXTURE0);
        GL11.glBindTexture(GL13.GL_TEXTURE_CUBE_MAP, cube);
        for (int f = 0; f < 6; f++) {
            GL11.glTexImage2D(GL13.GL_TEXTURE_CUBE_MAP_POSITIVE_X + f, 0, GL30.GL_RGBA16F,
                    SIZE, SIZE, 0, GL11.GL_RGBA, GL11.GL_FLOAT, (ByteBuffer) null);
        }
        GL11.glTexParameteri(GL13.GL_TEXTURE_CUBE_MAP, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR_MIPMAP_LINEAR);
        GL11.glTexParameteri(GL13.GL_TEXTURE_CUBE_MAP, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL13.GL_TEXTURE_CUBE_MAP, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL13.GL_TEXTURE_CUBE_MAP, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL13.GL_TEXTURE_CUBE_MAP, GL12.GL_TEXTURE_WRAP_R, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL13.GL_TEXTURE_CUBE_MAP, GL12.GL_TEXTURE_MAX_LEVEL, mipLevels - 1);
        GL30.glGenerateMipmap(GL13.GL_TEXTURE_CUBE_MAP);
        GL11.glBindTexture(GL13.GL_TEXTURE_CUBE_MAP, 0);
    }

    public void dispose() {
        if (capture != null) { capture.close(); capture = null; }
        if (fbo != 0) { GL30.glDeleteFramebuffers(fbo); fbo = 0; }
        if (cube != 0) { GL11.glDeleteTextures(cube); cube = 0; }
        ready = false;
        lastSunBucket = Integer.MIN_VALUE;
    }
}

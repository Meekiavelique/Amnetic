package com.meekdev.amnetic.client.ibl.internal;

import com.meekdev.amnetic.client.model.ModelLighting;
import com.meekdev.amnetic.client.render.ShaderProgram;
import com.mojang.renderpearl.backend.opengl.GlStateManager;
import java.nio.ByteBuffer;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL32;

// the sky as a cube for image based lighting. the sky (a panorama when one is set, the
// procedural one otherwise) is drawn into a source cube, then each mip of the probe is that
// source convolved with a wider GGX lobe, and the last mip is the cosine irradiance, so a shader
// reads diffuse at maxLod() and a reflection at roughness * maxLod()
public final class EnvProbe {

    public static final EnvProbe INSTANCE = new EnvProbe();

    private static final Identifier VSH = Identifier.fromNamespaceAndPath("amnetic", "shaders/bloom/fullscreen.vsh");
    private static final Identifier CAPTURE_FSH = Identifier.fromNamespaceAndPath("amnetic", "shaders/env/capture.fsh");
    private static final Identifier PREFILTER_FSH = Identifier.fromNamespaceAndPath("amnetic", "shaders/env/prefilter.fsh");
    private static final Identifier IRRADIANCE_FSH = Identifier.fromNamespaceAndPath("amnetic", "shaders/env/irradiance.fsh");

    private static final int SOURCE_SIZE = 256;
    private static final int SIZE = 128;
    private static final int LEVELS = 6;
    private static final int GGX_SAMPLES = 96;
    private static final int IRRADIANCE_SAMPLES = 256;
    private static final int SUN_STEPS = 24;
    private static final float[][] FACES = {
            { 1, 0, 0, 0,-1, 0, 0, 0,-1},
            {-1, 0, 0, 0,-1, 0, 0, 0, 1},
            { 0, 1, 0, 0, 0, 1, 1, 0, 0},
            { 0,-1, 0, 0, 0,-1, 1, 0, 0},
            { 0, 0, 1, 0,-1, 0, 1, 0, 0},
            { 0, 0,-1, 0,-1, 0, -1, 0, 0},
    };

    private int source;
    private int cube;
    private int fbo;
    private ShaderProgram capture;
    private ShaderProgram prefilter;
    private ShaderProgram irradiance;
    private boolean ready;
    private int lastSunBucket = Integer.MIN_VALUE;

    private int panorama;
    private int panoramaWidth;
    private final Matrix4f turn = new Matrix4f();
    private boolean panoramaChanged;

    private EnvProbe() {}

    public int glId() { return cube; }
    public boolean isReady() { return ready; }
    public int maxLod() { return LEVELS - 1; }
    public boolean usesPanorama() { return panorama != 0; }

    // an equirectangular HDR texture to light with in place of the procedural sky, turned by the
    // given rotation (world direction to panorama direction). 0 goes back to the procedural sky
    public void panorama(int texture, int width, Matrix4fc worldToPanorama) {
        if (texture == panorama && width == panoramaWidth && turn.equals(worldToPanorama)) return;
        panorama = texture;
        panoramaWidth = width;
        turn.set(worldToPanorama);
        panoramaChanged = true;
    }

    public void clearPanorama() {
        if (panorama == 0) return;
        panorama = 0;
        panoramaWidth = 0;
        panoramaChanged = true;
    }

    // the sun quantized to a direction bucket, so a still sun never re-bakes
    private static int bucketOf(float x, float y, float z) {
        int bx = Math.round(x * SUN_STEPS);
        int by = Math.round(y * SUN_STEPS);
        int bz = Math.round(z * SUN_STEPS);
        return (bx * 401 + by) * 401 + bz;
    }

    // bake the cube from the panorama or the scene sun, only when either changed enough to matter
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

        if (panorama != 0) {
            if (ready && !panoramaChanged) return;
        } else {
            int bucket = bucketOf(sunX, sunY, sunZ);
            if (ready && !panoramaChanged && bucket == lastSunBucket) return;
            lastSunBucket = bucket;
        }
        panoramaChanged = false;

        if (capture == null) {
            capture = new ShaderProgram(VSH, CAPTURE_FSH);
            prefilter = new ShaderProgram(VSH, PREFILTER_FSH);
            irradiance = new ShaderProgram(VSH, IRRADIANCE_FSH);
            allocate();
        }

        int prevFbo = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int[] vp = new int[4];
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, vp);

        GL32.glEnable(GL32.GL_TEXTURE_CUBE_MAP_SEAMLESS);
        GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, fbo);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glDepthMask(false);

        capture.begin();
        capture.setVec3("SunDir", sunX, sunY, sunZ);
        capture.setInt("HasPanorama", panorama != 0 ? 1 : 0);
        if (panorama != 0) {
            GlStateManager._activeTexture(GL13.GL_TEXTURE0);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, panorama);
            capture.setSampler("Panorama", 0);
            capture.setMatrix4("Turn", turn);
            float lod = (float) Math.max(0.0, Math.log(panoramaWidth / (4.0 * SOURCE_SIZE)) / Math.log(2));
            capture.setFloat("PanoramaLod", lod);
        }
        drawFaces(capture, source, 0, SOURCE_SIZE);

        GlStateManager._activeTexture(GL13.GL_TEXTURE0);
        GL11.glBindTexture(GL13.GL_TEXTURE_CUBE_MAP, source);
        GL30.glGenerateMipmap(GL13.GL_TEXTURE_CUBE_MAP);

        prefilter.begin();
        prefilter.setSampler("Source", 0);
        prefilter.setFloat("SourceSize", SOURCE_SIZE);
        for (int level = 0; level < LEVELS - 1; level++) {
            prefilter.setFloat("Roughness", level / (float) (LEVELS - 1));
            prefilter.setInt("Samples", level == 0 ? 1 : GGX_SAMPLES);
            drawFaces(prefilter, cube, level, SIZE >> level);
        }

        irradiance.begin();
        irradiance.setSampler("Source", 0);
        irradiance.setFloat("SourceSize", SOURCE_SIZE);
        irradiance.setInt("Samples", IRRADIANCE_SAMPLES);
        drawFaces(irradiance, cube, LEVELS - 1, SIZE >> (LEVELS - 1));

        GL11.glBindTexture(GL13.GL_TEXTURE_CUBE_MAP, 0);
        GlStateManager._glUseProgram(0);
        GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, prevFbo);
        GL11.glViewport(vp[0], vp[1], vp[2], vp[3]);
        GL11.glDepthMask(true);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        ready = true;
    }

    private static void drawFaces(ShaderProgram program, int target, int level, int size) {
        GL11.glViewport(0, 0, size, size);
        for (int f = 0; f < 6; f++) {
            GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0,
                    GL13.GL_TEXTURE_CUBE_MAP_POSITIVE_X + f, target, level);
            float[] b = FACES[f];
            program.setVec3("Forward", b[0], b[1], b[2]);
            program.setVec3("Up", b[3], b[4], b[5]);
            program.setVec3("Right", b[6], b[7], b[8]);
            program.draw();
        }
    }

    private void allocate() {
        source = GL11.glGenTextures();
        cube = GL11.glGenTextures();
        fbo = GL30.glGenFramebuffers();
        GlStateManager._activeTexture(GL13.GL_TEXTURE0);
        cubeStorage(source, SOURCE_SIZE, 1 + (int) (Math.log(SOURCE_SIZE) / Math.log(2)));
        cubeStorage(cube, SIZE, LEVELS);
        GL11.glBindTexture(GL13.GL_TEXTURE_CUBE_MAP, 0);
    }

    private static void cubeStorage(int texture, int size, int levels) {
        GL11.glBindTexture(GL13.GL_TEXTURE_CUBE_MAP, texture);
        for (int level = 0; level < levels; level++) {
            int side = Math.max(1, size >> level);
            for (int f = 0; f < 6; f++) {
                GL11.glTexImage2D(GL13.GL_TEXTURE_CUBE_MAP_POSITIVE_X + f, level, GL30.GL_RGBA16F,
                        side, side, 0, GL11.GL_RGBA, GL11.GL_FLOAT, (ByteBuffer) null);
            }
        }
        GL11.glTexParameteri(GL13.GL_TEXTURE_CUBE_MAP, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR_MIPMAP_LINEAR);
        GL11.glTexParameteri(GL13.GL_TEXTURE_CUBE_MAP, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL13.GL_TEXTURE_CUBE_MAP, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL13.GL_TEXTURE_CUBE_MAP, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL13.GL_TEXTURE_CUBE_MAP, GL12.GL_TEXTURE_WRAP_R, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL13.GL_TEXTURE_CUBE_MAP, GL12.GL_TEXTURE_BASE_LEVEL, 0);
        GL11.glTexParameteri(GL13.GL_TEXTURE_CUBE_MAP, GL12.GL_TEXTURE_MAX_LEVEL, levels - 1);
    }

    public void dispose() {
        if (capture != null) { capture.close(); capture = null; }
        if (prefilter != null) { prefilter.close(); prefilter = null; }
        if (irradiance != null) { irradiance.close(); irradiance = null; }
        if (fbo != 0) { GL30.glDeleteFramebuffers(fbo); fbo = 0; }
        if (cube != 0) { GL11.glDeleteTextures(cube); cube = 0; }
        if (source != 0) { GL11.glDeleteTextures(source); source = 0; }
        ready = false;
        lastSunBucket = Integer.MIN_VALUE;
    }
}

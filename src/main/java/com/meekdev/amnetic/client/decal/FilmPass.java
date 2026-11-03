package com.meekdev.amnetic.client.decal;

import com.meekdev.amnetic.client.compat.VanillaCompat;
import com.meekdev.amnetic.client.decal.internal.DecalBounds;
import com.meekdev.amnetic.client.framebuffer.Framebuffer;
import com.meekdev.amnetic.client.framebuffer.Framebuffers;
import com.meekdev.amnetic.client.gbuffer.internal.GBufferTargets;
import com.meekdev.amnetic.client.instanced.internal.MainTargetFramebuffer;
import com.meekdev.amnetic.client.light.Light;
import com.meekdev.amnetic.client.light.LightType;
import com.meekdev.amnetic.client.light.internal.LightRegistry;
import com.meekdev.amnetic.client.render.CameraSnapshot;
import com.meekdev.amnetic.client.render.GlState;
import com.meekdev.amnetic.client.render.ImportedTextures;
import com.meekdev.amnetic.client.render.ScreenPass;
import com.meekdev.amnetic.client.render.ShaderProgram;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.renderpearl.backend.opengl.GlStateManager;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL33;
import org.lwjgl.system.MemoryStack;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class FilmPass extends ScreenPass {
    static final FilmPass INSTANCE = new FilmPass();

    private static final Identifier VSH = Identifier.fromNamespaceAndPath("amnetic", "shaders/decal/decal.vsh");
    private static final Identifier FSH = Identifier.fromNamespaceAndPath("amnetic", "shaders/decal/film.fsh");
    private static final Identifier GBUFFER_FSH = Identifier.fromNamespaceAndPath("amnetic", "shaders/decal/film_gbuffer.fsh");

    private static final int PALETTE_COLORS = 8;
    private static final int MAX_LIGHTS = 4;
    private static final long TEXEL_WRAP = 1024L;

    private final DecalBounds bounds = new DecalBounds();
    private final List<Light> nearest = new ArrayList<>();
    private final Matrix4f box = new Matrix4f();
    private Framebuffer depthCapture;
    private ShaderProgram gbufferProgram;
    private List<FilmDecal> films;
    private final long startNanos = System.nanoTime();
    long lastPixels;
    int lastDrawn;

    private FilmPass() {
        super("FilmDecal");
    }

    void render(List<FilmDecal> films) {
        this.films = films;
        for (FilmDecal f : films) {
            if (f.removed) release(f);
        }
        films.removeIf(f -> f.removed);
        dispatch();
    }

    @Override
    protected boolean enabled() {
        if (films == null) return false;
        for (FilmDecal f : films) {
            if (!f.isEmpty()) return true;
        }
        return false;
    }

    @Override
    protected ShaderProgram createProgram() {
        depthCapture = Framebuffers.captureDepth();
        gbufferProgram = new ShaderProgram(VSH, GBUFFER_FSH);
        return new ShaderProgram(VSH, FSH);
    }

    @Override
    protected boolean record(CameraSnapshot cam, ShaderProgram program) {
        int mcFbo = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int[] viewport = new int[4];
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);
        RenderTarget main = Minecraft.getInstance().gameRenderer.mainRenderTarget();
        int width = main.width;
        int height = main.height;
        Vec3 eye = cam.eye;
        float time = (System.nanoTime() - startNanos) * 1e-9f;
        float[] env = Decals.filmEnvironment();

        for (FilmDecal f : films) upload(f);

        depthCapture.blitDepthFromMain();
        for (FilmDecal f : films) {
            if (!f.isEmpty() && !f.look.pixelArt && f.look.screenReflect > 0f) {
                depthCapture.blitColorFromMain();
                break;
            }
        }
        lastPixels = 0;
        lastDrawn = 0;

        int prev = MainTargetFramebuffer.bind();
        if (prev < 0) return false;
        bounds.begin();
        try {
            GlStateManager._enableBlend(0);
            GL11.glEnable(GL11.GL_BLEND);
            GlStateManager._blendFuncSeparate(GL11.GL_ONE, GL33.GL_SRC1_COLOR, GL11.GL_ZERO, GL11.GL_ONE);
            GL14.glBlendFuncSeparate(GL11.GL_ONE, GL33.GL_SRC1_COLOR, GL11.GL_ZERO, GL11.GL_ONE);
            program.begin();
            common(program, cam, time, env);
            lights(program, eye);
            for (FilmDecal f : films) {
                if (f.isEmpty() || f.texA == 0 || !cull(f, cam, eye, width, height)) continue;
                bounds.apply();
                bind(program, f, eye);
                program.draw();
                lastPixels += bounds.pixels();
                lastDrawn++;
            }
        } finally {
            bounds.end();
            MainTargetFramebuffer.restore(prev);
            GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, mcFbo);
            GlStateManager._viewport(viewport[0], viewport[1], viewport[2], viewport[3]);
        }

        renderGBuffer(cam, eye, time, env, mcFbo, viewport, width, height);
        return true;
    }

    private void renderGBuffer(CameraSnapshot cam, Vec3 eye, float time, float[] env, int mcFbo, int[] viewport, int width, int height) {
        GBufferTargets g = GBufferTargets.INSTANCE;
        if (!g.isPopulated() || gbufferProgram == null) return;
        int prevFbo = g.bind();
        if (prevFbo == -1) return;
        bounds.begin();
        try {
            GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT, GL11.GL_TEXTURE_2D, 0, 0);
            try (MemoryStack s = MemoryStack.stackPush()) {
                IntBuffer bufs = s.ints(GL30.GL_NONE, GL30.GL_COLOR_ATTACHMENT1, GL30.GL_COLOR_ATTACHMENT2, GL30.GL_NONE);
                GL30.glDrawBuffers(bufs);
            }
            GlStateManager._enableBlend(0);
            GL11.glEnable(GL11.GL_BLEND);
            GlStateManager._blendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ZERO, GL11.GL_ONE);
            GL14.glBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ZERO, GL11.GL_ONE);
            gbufferProgram.begin();
            common(gbufferProgram, cam, time, env);
            for (FilmDecal f : films) {
                if (f.look.pixelArt || f.isEmpty() || f.texA == 0 || !cull(f, cam, eye, width, height)) continue;
                bounds.apply();
                bind(gbufferProgram, f, eye);
                gbufferProgram.draw();
            }
        } finally {
            bounds.end();
            GBufferTargets.drawAll();
            GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, mcFbo);
            GlStateManager._viewport(viewport[0], viewport[1], viewport[2], viewport[3]);
        }
    }

    private boolean cull(FilmDecal f, CameraSnapshot cam, Vec3 eye, int width, int height) {
        box.identity()
                .translate((float) (f.originX - eye.x), (float) (f.minY - eye.y), (float) (f.originZ - eye.z))
                .scale(f.sizeX, f.sizeY, f.sizeZ);
        return bounds.compute(cam.viewProj, box, false, width, height);
    }

    private void common(ShaderProgram p, CameraSnapshot cam, float time, float[] env) {
        p.setSampler("DepthSampler", 0);
        p.setSampler("FieldA", 1);
        p.setSampler("FieldB", 2);
        p.setSampler("PatternWet", 3);
        p.setSampler("PatternDry", 4);
        p.setSampler("SceneColor", 5);
        p.setMatrix4("InvViewProj", cam.invViewProj);
        p.setMatrix4("ViewProj", cam.viewProj);
        p.setMatrix4("View", cam.view);
        p.setInt("ZeroToOne", cam.zeroToOne ? 1 : 0);
        p.setFloat("Time", time);
        p.setVec3("SunDir", env[0], env[1], env[2]);
        p.setVec3("SunColor", env[3], env[4], env[5]);
        p.setVec3("SkyColor", env[6], env[7], env[8]);
        p.setVec3("BlockColor", env[9], env[10], env[11]);
        GlState.bindTexture(0, depthCapture.depthTextureGlId());
        GlState.bindTexture(5, depthCapture.colorTextureGlId(0));
    }

    private void bind(ShaderProgram p, FilmDecal f, Vec3 eye) {
        FilmLook l = f.look;
        GlState.bindTexture(1, f.texA);
        GlState.bindTexture(2, f.texB);
        p.setVec3("Origin", (float) (f.originX - eye.x), (float) (f.minY - eye.y), (float) (f.originZ - eye.z));
        p.setVec3("Size", f.sizeX, f.sizeY, f.sizeZ);
        p.setVec2("Res", f.resX, f.resZ);
        p.setFloat("Opacity", f.opacity);
        p.setFloat("MaxThickness", l.maxThickness);
        p.setVec3("Absorb", l.absorption.x, l.absorption.y, l.absorption.z);
        p.setVec3("AbsorbAged", l.agedAbsorption.x, l.agedAbsorption.y, l.agedAbsorption.z);
        p.setVec3("StainTint", l.stainTint.x, l.stainTint.y, l.stainTint.z);
        p.setVec3("Scatter", l.scatter.x, l.scatter.y, l.scatter.z);
        p.setVec2("Edge", l.edgeThickness, l.edgeSoftness);
        p.setVec2("EdgeNoise", l.edgeNoise, l.edgeNoiseScale);
        p.setFloat("StainThreshold", l.stainThreshold);
        p.setFloat("Ring", l.ring);
        p.setVec2("Normals", l.normalStrength, l.rim);
        p.setVec2("Flow", l.flowSpeed, l.rippleStrength);
        p.setFloat("Parallax", l.parallax);
        p.setFloat("Specular", l.specular);
        p.setVec2("Rough", l.wetRoughness, l.dryRoughness);
        p.setFloat("Reflectance", l.reflectance);
        p.setVec2("ScreenReflect", l.screenReflect, l.screenReflectDistance);
        p.setVec3("RainRipples", l.rain, l.rainScale, l.rainStrength);
        p.setInt("PixelArt", l.pixelArt ? 1 : 0);
        p.setFloat("TexelsPerUnit", l.texelsPerUnit);
        if (l.pixelArt) {
            for (int i = 0; i < PALETTE_COLORS; i++) {
                p.setVec3("Palette[" + i + "]", l.palette[i * 3], l.palette[i * 3 + 1], l.palette[i * 3 + 2]);
            }
            float texelX = (float) Math.floorMod((long) Math.floor(f.originX * l.texelsPerUnit), TEXEL_WRAP);
            float texelZ = (float) Math.floorMod((long) Math.floor(f.originZ * l.texelsPerUnit), TEXEL_WRAP);
            p.setVec2("OriginTexel", texelX, texelZ);
            p.setFloat("PixelJitter", l.jitter);
            p.setFloat("PixelOutline", l.outline);
            p.setFloat("PixelCore", l.core ? 1f : 0f);
            p.setFloat("PixelHighlights", l.highlights);
            int wet = l.patternWet == null ? 0 : textureGlId(l.patternWet);
            int dry = l.patternDry == null ? wet : textureGlId(l.patternDry);
            p.setInt("HasPattern", wet != 0 ? 1 : 0);
            if (wet != 0) {
                GlState.bindTexture(3, wet);
                GlState.bindTexture(4, dry != 0 ? dry : wet);
            }
        }
    }

    private void lights(ShaderProgram p, Vec3 eye) {
        List<Light> all = LightRegistry.INSTANCE.all();
        nearest.clear();
        for (Light l : all) {
            if (!l.isEnabled() || l.type() == LightType.DIRECTIONAL) continue;
            nearest.add(l);
        }
        nearest.sort((a, b) -> Double.compare(dist2(a, eye), dist2(b, eye)));
        int n = Math.min(MAX_LIGHTS, nearest.size());
        for (int i = 0; i < n; i++) {
            Light l = nearest.get(i);
            boolean spot = l.type() == LightType.SPOT;
            float x = (float) (l.x() - eye.x);
            float y = (float) (l.y() - eye.y);
            float z = (float) (l.z() - eye.z);
            p.setVec4("LightPos[" + i + "]", x, y, z, l.range());
            float red = l.red() * l.intensity();
            float green = l.green() * l.intensity();
            float blue = l.blue() * l.intensity();
            p.setVec4("LightCol[" + i + "]", red, green, blue, spot ? l.cosOuter() : -1f);
            p.setVec4("LightDir[" + i + "]", l.dirX(), l.dirY(), l.dirZ(), spot ? 1f : 0f);
        }
        p.setInt("LightCount", n);
    }

    private static double dist2(Light l, Vec3 eye) {
        double dx = l.x() - eye.x;
        double dy = l.y() - eye.y;
        double dz = l.z() - eye.z;
        return dx * dx + dy * dy + dz * dz;
    }

    private void upload(FilmDecal f) {
        if (f.fieldA == null) return;
        if (f.texA == 0) {
            f.texA = create(f.resX, f.resZ, GL11.GL_LINEAR);
            f.texB = create(f.resX, f.resZ, GL11.GL_LINEAR);
            f.dirtyMin = 0;
            f.dirtyMax = f.resZ - 1;
        }
        if (f.dirtyMax < f.dirtyMin) return;
        int rows = f.dirtyMax - f.dirtyMin + 1;
        int offset = f.dirtyMin * f.resX * 4;
        GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 1);
        GL11.glPixelStorei(GL12.GL_UNPACK_ROW_LENGTH, 0);
        GL11.glPixelStorei(GL12.GL_UNPACK_SKIP_ROWS, 0);
        GL11.glPixelStorei(GL12.GL_UNPACK_SKIP_PIXELS, 0);
        GlState.bindTexture(0, f.texA);
        int end = offset + rows * f.resX * 4;
        GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, f.dirtyMin, f.resX, rows, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE,
                f.fieldA.position(offset).limit(end));
        GlState.bindTexture(0, f.texB);
        GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, f.dirtyMin, f.resX, rows, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE,
                f.fieldB.position(offset).limit(end));
        f.fieldA.clear();
        f.fieldB.clear();
        GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 4);
        GlState.bindTexture(0, 0);
        f.dirtyMin = Integer.MAX_VALUE;
        f.dirtyMax = -1;
    }

    private final Set<Identifier> loadedTextures = new HashSet<>();

    private int textureGlId(Identifier id) {
        var tm = Minecraft.getInstance().getTextureManager();
        if (!ImportedTextures.isImported(id) && loadedTextures.add(id)) {
            VanillaCompat.loadTexture(tm, id);
        }
        return VanillaCompat.glId(tm.getTexture(id));
    }

    private static int create(int w, int h, int filter) {
        int id = GL11.glGenTextures();
        GlState.bindTexture(0, id);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, filter);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, filter);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_MAX_LEVEL, 0);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, w, h, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null);
        GlState.bindTexture(0, 0);
        return id;
    }

    private static void release(FilmDecal f) {
        if (f.texA != 0) {
            GL11.glDeleteTextures(f.texA);
            f.texA = 0;
        }
        if (f.texB != 0) {
            GL11.glDeleteTextures(f.texB);
            f.texB = 0;
        }
        f.freeCpu();
    }

    @Override
    protected void onDispose() {
        if (films != null) {
            for (FilmDecal f : films) release(f);
        }
        if (depthCapture != null) {
            depthCapture.dispose();
            depthCapture = null;
        }
        if (gbufferProgram != null) {
            gbufferProgram.close();
            gbufferProgram = null;
        }
    }
}

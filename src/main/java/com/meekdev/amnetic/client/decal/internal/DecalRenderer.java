package com.meekdev.amnetic.client.decal.internal;

import com.meekdev.amnetic.client.decal.Decal;
import com.meekdev.amnetic.client.framebuffer.Framebuffer;
import com.meekdev.amnetic.client.framebuffer.Framebuffers;
import com.meekdev.amnetic.client.gbuffer.internal.GBufferTargets;
import com.meekdev.amnetic.client.instanced.internal.MainTargetFramebuffer;
import com.meekdev.amnetic.client.render.CameraSnapshot;
import com.meekdev.amnetic.client.render.GlState;
import com.meekdev.amnetic.client.render.ImportedTextures;
import com.meekdev.amnetic.client.render.ScreenPass;
import com.meekdev.amnetic.client.render.ShaderProgram;
import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.opengl.GlTexture;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.SimpleTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import java.nio.IntBuffer;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL33;
import org.lwjgl.system.MemoryStack;

public final class DecalRenderer extends ScreenPass {

    public static final DecalRenderer INSTANCE = new DecalRenderer();

    private static final Identifier VSH = Identifier.fromNamespaceAndPath("amnetic", "shaders/decal/decal.vsh");
    private static final Identifier FSH = Identifier.fromNamespaceAndPath("amnetic", "shaders/decal/decal.fsh");
    private static final Identifier GBUFFER_FSH = Identifier.fromNamespaceAndPath("amnetic", "shaders/decal/decal_gbuffer.fsh");

    private Framebuffer depthCapture;
    private int nearestSampler;
    private ShaderProgram gbufferProgram;
    private final Set<Identifier> loaded = new HashSet<>();
    private List<Decal> decals;

    private DecalRenderer() { super("Decal"); }

    public void render(List<Decal> decals) {
        this.decals = decals;
        dispatch();
    }

    @Override protected boolean enabled() { return decals != null && !decals.isEmpty(); }

    @Override
    protected ShaderProgram createProgram() {
        depthCapture = Framebuffers.captureDepth();
        nearestSampler = GL33.glGenSamplers();
        GL33.glSamplerParameteri(nearestSampler, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GL33.glSamplerParameteri(nearestSampler, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        GL33.glSamplerParameteri(nearestSampler, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL33.glSamplerParameteri(nearestSampler, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        gbufferProgram = new ShaderProgram(VSH, GBUFFER_FSH);
        return new ShaderProgram(VSH, FSH);
    }

    @Override
    protected boolean record(CameraSnapshot cam, ShaderProgram program) {
        Vec3 eye = cam.eye;

        int mcFbo = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int[] mcViewport = new int[4];
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, mcViewport);

        depthCapture.blitDepthFromMain();

        GlStateManager._enableBlend();
        GlStateManager._blendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ZERO, GL11.GL_ONE);

        MainTargetFramebuffer.bind();
        try {
            GlState.bindTexture(1, depthCapture.depthTextureGlId());

            program.begin();
            program.setSampler("DepthSampler", 1);
            program.setMatrix4("InvViewProj", cam.invViewProj);
            program.setInt("ZeroToOne", cam.zeroToOne ? 1 : 0);

            Matrix4f model = new Matrix4f();
            Matrix4f inv = new Matrix4f();
            for (Decal d : decals) {
                if (d.isRemoved()) continue;
                decalMatrix(d, eye, model);
                inv.set(model).invert();

                boolean hasTex = false;
                if (d.texture() != null) {
                    int g = loadTextureGlId(d.texture());
                    if (g != 0) {
                        GlStateManager._activeTexture(GL13.GL_TEXTURE0);
                        GL11.glBindTexture(GL11.GL_TEXTURE_2D, g);
                        GL33.glBindSampler(0, nearestSampler);
                        hasTex = true;
                    }
                }
                program.setSampler("DecalSampler", 0);
                program.setInt("HasTexture", hasTex ? 1 : 0);
                program.setMatrix4("DecalInv", inv);
                program.setVec3("DecalAxis", d.normal().x, d.normal().y, d.normal().z);
                program.setFloat("Opacity", d.opacity());
                program.setFloat("AngleFade", d.angleFade());
                program.setVec3("Tint", d.tint().x, d.tint().y, d.tint().z);
                program.draw();
            }
        } finally {
            GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, mcFbo);
            GlStateManager._viewport(mcViewport[0], mcViewport[1], mcViewport[2], mcViewport[3]);
        }

        renderGBufferDecals(cam, eye, mcFbo, mcViewport);
        return true;
    }

    private void renderGBufferDecals(CameraSnapshot cam, Vec3 eye, int mcFbo, int[] mcViewport) {
        boolean any = false;
        for (Decal d : decals) if (!d.isRemoved() && d.writesGBuffer()) { any = true; break; }
        if (!any || gbufferProgram == null) return;

        GBufferTargets g = GBufferTargets.INSTANCE;
        if (!g.isPopulated()) return; // need the depth-reconstructed normals underneath
        int prevFbo = g.bind();
        if (prevFbo == -1) return;
        try {
            GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT, GL11.GL_TEXTURE_2D, 0, 0);
            try (MemoryStack s = MemoryStack.stackPush()) {
                IntBuffer bufs = s.ints(GL30.GL_NONE, GL30.GL_COLOR_ATTACHMENT1, GL30.GL_COLOR_ATTACHMENT2, GL30.GL_NONE);
                GL30.glDrawBuffers(bufs);
            }
            GlState.bindTexture(1, depthCapture.depthTextureGlId());
            gbufferProgram.begin();
            gbufferProgram.setSampler("DepthSampler", 1);
            gbufferProgram.setMatrix4("InvViewProj", cam.invViewProj);
            gbufferProgram.setInt("ZeroToOne", cam.zeroToOne ? 1 : 0);

            Matrix4f model = new Matrix4f();
            Matrix4f inv = new Matrix4f();
            Vector3f n = new Vector3f(), right = new Vector3f(), up = new Vector3f();
            for (Decal d : decals) {
                if (d.isRemoved() || !d.writesGBuffer()) continue;
                decalMatrix(d, eye, model);
                inv.set(model).invert();
                basis(d, n, right, up);

                boolean hasNormal = false;
                if (d.normalMap() != null) {
                    int gid = loadTextureGlId(d.normalMap());
                    if (gid != 0) {
                        GlStateManager._activeTexture(GL13.GL_TEXTURE0);
                        GL11.glBindTexture(GL11.GL_TEXTURE_2D, gid);
                        GL33.glBindSampler(0, nearestSampler);
                        hasNormal = true;
                    }
                }
                gbufferProgram.setSampler("NormalSampler", 0);
                gbufferProgram.setInt("HasNormalMap", hasNormal ? 1 : 0);
                gbufferProgram.setMatrix4("DecalInv", inv);
                gbufferProgram.setVec3("DecalAxis", n.x, n.y, n.z);
                gbufferProgram.setVec3("DecalRight", right.x, right.y, right.z);
                gbufferProgram.setVec3("DecalUp", up.x, up.y, up.z);
                gbufferProgram.setFloat("Opacity", d.opacity());
                gbufferProgram.setFloat("AngleFade", d.angleFade());
                gbufferProgram.setFloat("Roughness", d.roughnessOr(0.7f));
                gbufferProgram.setFloat("Metallic", d.metallic());
                gbufferProgram.draw();
            }
        } finally {
            try (MemoryStack s = MemoryStack.stackPush()) {
                IntBuffer bufs = s.ints(GL30.GL_COLOR_ATTACHMENT0, GL30.GL_COLOR_ATTACHMENT1, GL30.GL_COLOR_ATTACHMENT2,
                        GL30.GL_COLOR_ATTACHMENT3);
                GL30.glDrawBuffers(bufs);
            }
            GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, mcFbo);
            GlStateManager._viewport(mcViewport[0], mcViewport[1], mcViewport[2], mcViewport[3]);
        }
    }

    @Override
    protected void onDispose() {
        if (depthCapture != null) { depthCapture.dispose(); depthCapture = null; }
        if (nearestSampler != 0) { GL33.glDeleteSamplers(nearestSampler); nearestSampler = 0; }
        if (gbufferProgram != null) { gbufferProgram.close(); gbufferProgram = null; }
    }

    private static void basis(Decal d, Vector3f n, Vector3f right, Vector3f up) {
        n.set(d.normal()).normalize();
        right.set(n).cross(0f, 1f, 0f);
        if (right.lengthSquared() < 1e-6f) right.set(1f, 0f, 0f);
        right.normalize();
        up.set(right).cross(n).normalize();
    }

    private static void decalMatrix(Decal d, Vec3 cam, Matrix4f out) {
        Vector3f n = new Vector3f(d.normal()).normalize();
        Vector3f right = new Vector3f(n).cross(0f, 1f, 0f);
        if (right.lengthSquared() < 1e-6f) right.set(1f, 0f, 0f);
        right.normalize();
        Vector3f up = new Vector3f(right).cross(n).normalize();
        Vec3 c = d.center();
        float w = d.width(), h = d.height(), dp = d.depth();
        out.identity();
        out.m00(right.x * w); out.m01(right.y * w); out.m02(right.z * w);
        out.m10(n.x * dp); out.m11(n.y * dp); out.m12(n.z * dp);
        out.m20(up.x * h); out.m21(up.y * h); out.m22(up.z * h);
        out.m30((float) (c.x - cam.x)); out.m31((float) (c.y - cam.y)); out.m32((float) (c.z - cam.z));
    }

    private int loadTextureGlId(Identifier id) {
        var tm = Minecraft.getInstance().getTextureManager();
        if (!ImportedTextures.isImported(id) && loaded.add(id)) {
            tm.registerAndLoad(id, new SimpleTexture(id));
        }
        AbstractTexture tex = tm.getTexture(id);
        if (tex != null && tex.getTexture() instanceof GlTexture gl) {
            return gl.glId();
        }
        return 0;
    }
}

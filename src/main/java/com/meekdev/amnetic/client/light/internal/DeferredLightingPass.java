package com.meekdev.amnetic.client.light.internal;

import com.meekdev.amnetic.client.bloom.internal.FullscreenPass;
import com.meekdev.amnetic.client.framebuffer.ColorFormat;
import com.meekdev.amnetic.client.framebuffer.Framebuffer;
import com.meekdev.amnetic.client.framebuffer.FramebufferSpec;
import com.meekdev.amnetic.client.framebuffer.Framebuffers;
import com.meekdev.amnetic.client.instanced.internal.MainTargetFramebuffer;
import com.meekdev.amnetic.client.light.LightSettings;
import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.phys.Vec3;
import org.joml.FrustumIntersection;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL33;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class DeferredLightingPass {

    public static final DeferredLightingPass INSTANCE = new DeferredLightingPass();

    private static final Logger LOG = LoggerFactory.getLogger("Amnetic/Light");

    private static final net.minecraft.resources.Identifier VSH =
            net.minecraft.resources.Identifier.fromNamespaceAndPath("amnetic", "shaders/light/deferred.vsh");
    private static final net.minecraft.resources.Identifier FSH =
            net.minecraft.resources.Identifier.fromNamespaceAndPath("amnetic", "shaders/light/deferred.fsh");

    private FullscreenPass pass;
    private Framebuffer capture;
    private LightBuffer lightBuffer;
    private final FrustumIntersection frustum = new FrustumIntersection();
    private boolean enabled = true;

    public static int debugMode = 0;
    private int frameLog;

    private DeferredLightingPass() {}

    public void render() {
        if (!enabled) return;
        if (LightRegistry.INSTANCE.isEmpty()) return;
        if (FabricLoader.getInstance().isModLoaded("iris")) return; // shaderpack owns its pipeline

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.getMainRenderTarget() == null) return;
        var grs = mc.gameRenderer.getGameRenderState();
        if (grs == null || grs.levelRenderState == null || grs.levelRenderState.cameraRenderState == null) return;
        CameraRenderState crs = grs.levelRenderState.cameraRenderState;
        Vec3 cam = crs.pos;

        ensureResources();

        Matrix4f viewProj = new Matrix4f(crs.projectionMatrix).mul(crs.viewRotationMatrix);
        frustum.set(viewProj);
        int count = lightBuffer.pack(LightRegistry.INSTANCE.all(), cam, frustum);
        if (count == 0) return;

        try {
            capture.blitColorFromMain();
            capture.blitDepthFromMain();

            Matrix4f invViewProj = new Matrix4f(viewProj).invert();
            boolean zeroToOne = RenderSystem.getDevice().isZZeroToOne();

            GlStateManager._disableBlend();
            GlStateManager._disableDepthTest();
            GlStateManager._depthMask(false);
            GlStateManager._disableCull();

            int prevFbo = MainTargetFramebuffer.bind();
            try {
                bindTexture(0, GL11.GL_TEXTURE_2D, capture.colorTextureGlId(0));
                bindTexture(1, GL11.GL_TEXTURE_2D, capture.depthTextureGlId());
                GL33.glBindSampler(0, 0);
                GL33.glBindSampler(1, 0);
                lightBuffer.bind(0);

                pass.begin();
                pass.setSampler("AlbedoSampler", 0);
                pass.setSampler("DepthSampler", 1);
                pass.setMatrix4("InvViewProj", invViewProj);
                pass.setInt("ZeroToOne", zeroToOne ? 1 : 0);
                pass.setInt("LightCount", count);
                pass.setInt("VolumetricSteps", LightSettings.defaults().volumetricSteps());
                pass.setFloat("VolumetricStrength", LightSettings.defaults().volumetricStrength());
                pass.setInt("DebugMode", debugMode);

                if (debugMode == 0) {
                    GlStateManager._enableBlend();
                    GlStateManager._blendFuncSeparate(GL11.GL_ONE, GL11.GL_ONE, GL11.GL_ZERO, GL11.GL_ONE);
                }

                if ((frameLog++ % 600) == 0) {
                    LOG.info("[Light] lights={} cam=({},{},{}) zeroToOne={} debug={}",
                            count, (int) cam.x, (int) cam.y, (int) cam.z, zeroToOne, debugMode);
                }
                pass.draw();
            } finally {
                for (int u = 1; u >= 0; u--) {
                    GlStateManager._activeTexture(GL13.GL_TEXTURE0 + u);
                    GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
                    GL11.glBindTexture(GL12.GL_TEXTURE_3D, 0);
                    GL33.glBindSampler(u, 0);
                }
                GlStateManager._activeTexture(GL13.GL_TEXTURE0);
                GlStateManager._glUseProgram(0);
                GlStateManager._glBindVertexArray(0);
                GlStateManager._depthMask(true);
                GlStateManager._enableDepthTest();
                GlStateManager._enableCull();
                GlStateManager._disableBlend();
                MainTargetFramebuffer.restore(prevFbo);
            }
        } catch (Throwable e) {
            LOG.error("[Light] pass failed; disabling", e);
            enabled = false;
        }
    }

    public void dispose() {
        if (pass != null) { pass.close(); pass = null; }
        if (lightBuffer != null) { lightBuffer.close(); lightBuffer = null; }
        if (capture != null) { capture.dispose(); capture = null; }
    }

    private void bindTexture(int unit, int target, int glId) {
        GlStateManager._activeTexture(GL13.GL_TEXTURE0 + unit);
        GL11.glBindTexture(target, glId);
    }

    private void ensureResources() {
        if (pass == null) {
            pass = new FullscreenPass(VSH, FSH);
            capture = Framebuffers.screen(FramebufferSpec.builder()
                    .color(ColorFormat.RGBA8)
                    .depthTexture()
                    .build());
            lightBuffer = new LightBuffer(LightSettings.defaults().maxLights());
        }
    }
}

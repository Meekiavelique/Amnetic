package com.meekdev.amnetic.client.model.internal;

import java.util.List;

import com.meekdev.amnetic.client.instanced.RenderState;
import com.meekdev.amnetic.client.model.ModelLighting;
import com.mojang.blaze3d.opengl.GlStateManager;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class OffscreenModelRenderer implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger("Amnetic/ModelView");

    private ModelShader shader;
    private boolean failed;

    public boolean draw(GpuModel gpu, Matrix4f projView, Matrix4f world, Matrix4f[] pose,
                        float block, float sky, float emissiveStrength) {
        if (failed) {
            return false;
        }
        if (shader == null) {
            try {
                shader = ModelShader.load();
            } catch (Exception e) {
                LOG.error("Amnetic: failed to load model shader for offscreen view", e);
                failed = true;
                return false;
            }
        }

        // the GUI phase drives GL through RenderPass encoders that bypass GlStateManager's caches, so its
        // cached depth/cull/blend/activeTexture flags can be stale here and apply() would silently no-op,
        // leaving depth testing off (torn, z-fighting geometry). toggling each cached flag to the opposite
        // first guarantees the following apply() emits real GL calls and leaves the cache accurate
        GlStateManager._disableDepthTest();
        GlStateManager._depthMask(false);
        GlStateManager._disableCull();
        GlStateManager._enableBlend();
        GlStateManager._activeTexture(GL13.GL_TEXTURE1);
        GlStateManager._activeTexture(GL13.GL_TEXTURE0);
        RenderState.DEFAULT.apply();
        GL11.glDepthFunc(GL11.GL_LEQUAL);
        shader.bind();
        try {
            // this program is separate from ModelRegistry's so it never gets the per-frame lighting upload,
            // without this SunIntensity/Ambient/Exposure stay at GL-default 0 and everything renders black
            shader.uploadLighting(ModelLighting.INSTANCE);
            shader.uploadProjView(projView);
            shader.uploadEmissiveStrength(emissiveStrength);
            GpuModel.DrawInstance instance = new GpuModel.DrawInstance(world, pose, block, sky);
            gpu.draw(shader, List.of(instance));
            return true;
        } finally {
            GlStateManager._glUseProgram(0);
            GlStateManager._glBindVertexArray(0);
            RenderState.DEFAULT.restore();
        }
    }

    @Override
    public void close() {
        if (shader != null) {
            shader.close();
            shader = null;
        }
        failed = false;
    }
}

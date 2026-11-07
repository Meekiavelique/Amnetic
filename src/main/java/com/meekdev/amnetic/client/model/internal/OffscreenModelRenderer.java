package com.meekdev.amnetic.client.model.internal;

import com.meekdev.amnetic.client.render.GlState;
import java.util.List;

import com.meekdev.amnetic.client.compat.VanillaCompat;
import com.meekdev.amnetic.client.instanced.RenderState;
import com.meekdev.amnetic.client.model.ModelLighting;
import com.meekdev.amnetic.client.model.ModelLook;
import com.mojang.renderpearl.backend.opengl.GlStateManager;
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
        return draw(gpu, projView, world, pose, block, sky, emissiveStrength, null);
    }

    public boolean draw(GpuModel gpu, Matrix4f projView, Matrix4f world, Matrix4f[] pose,
                        float block, float sky, float emissiveStrength, ModelLook look) {
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
        if (!shader.ready()) {
            if (shader.failed()) failed = true;
            return false;
        }

        GlStateManager._disableDepthTest();
        GlStateManager._depthMask(false);
        GlStateManager._disableCull();
        GlStateManager._enableBlend(0);
        GlStateManager._activeTexture(GL13.GL_TEXTURE1);
        GlStateManager._activeTexture(GL13.GL_TEXTURE0);
        RenderState.DEFAULT.apply();
        GL11.glDepthFunc(VanillaCompat.depthNearerOrEqual());
        shader.bind();
        try {
            shader.uploadLighting(ModelLighting.INSTANCE);
            shader.uploadProjView(projView);
            shader.uploadEmissiveStrength(emissiveStrength);
            GlStateManager._activeTexture(GL13.GL_TEXTURE0 + ModelShader.LIGHTMAP_UNIT);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, VanillaCompat.levelLightmapGlId());
            GlStateManager._activeTexture(GL13.GL_TEXTURE0);
            GpuModel.DrawInstance instance = new GpuModel.DrawInstance(world, pose, block, sky, look);
            gpu.draw(shader, List.of(instance));
            return true;
        } finally {
            GlStateManager._glUseProgram(0);
            GlState.resetVertexArray();
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

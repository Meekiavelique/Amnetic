package com.meekdev.amnetic.client.gbuffer.internal;

import com.meekdev.amnetic.client.render.CameraSnapshot;
import com.meekdev.amnetic.client.render.ShaderProgram;
import com.meekdev.amnetic.mixin.accessor.LevelRendererAccessor;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.vertex.VertexBuffer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;

final class TerrainNormalFill {

    private static final Identifier VSH = Identifier.fromNamespaceAndPath("amnetic", "shaders/gbuffer/terrain_normal.vsh");
    private static final Identifier FSH = Identifier.fromNamespaceAndPath("amnetic", "shaders/gbuffer/terrain_normal.fsh");
    private static final RenderType[] LAYERS = {RenderType.solid(), RenderType.cutoutMipped(), RenderType.cutout()};

    private static ShaderProgram program;

    private TerrainNormalFill() {}

    static void render(CameraSnapshot cam) {
        Minecraft mc = Minecraft.getInstance();
        var sections = ((LevelRendererAccessor) mc.levelRenderer).amnetic$getVisibleSections();
        if (sections.isEmpty()) return;
        if (program == null) program = new ShaderProgram(VSH, FSH);

        boolean depthTest = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glDepthFunc(GL11.GL_LEQUAL);
        GL11.glDepthMask(false);
        GL11.glEnable(GL11.GL_CULL_FACE);
        boolean pbr = BlockPbrAtlas.bind(1, 2);
        GlStateManager._activeTexture(GL13.GL_TEXTURE0);
        GlStateManager._bindTexture(mc.getTextureManager().getTexture(TextureAtlas.LOCATION_BLOCKS).getId());

        program.begin();
        program.setMatrix4("ViewProj", cam.viewProj);
        program.setSampler("BlockAtlas", 0);
        program.setSampler("NormalAtlas", 1);
        program.setSampler("SpecularAtlas", 2);
        program.setInt("HasPbr", pbr ? 1 : 0);
        for (RenderType layer : LAYERS) {
            program.setInt("AlphaTest", layer == RenderType.solid() ? 0 : 1);
            for (SectionRenderDispatcher.RenderSection section : sections) {
                if (section.getCompiled().isEmpty(layer)) continue;
                BlockPos o = section.getOrigin();
                program.setVec3("ChunkOffset", (float) (o.getX() - cam.eye.x), (float) (o.getY() - cam.eye.y), (float) (o.getZ() - cam.eye.z));
                VertexBuffer buffer = section.getBuffer(layer);
                buffer.bind();
                buffer.draw();
            }
        }
        VertexBuffer.unbind();
        GlStateManager._glUseProgram(0);

        GL11.glDepthMask(true);
        if (!depthTest) GL11.glDisable(GL11.GL_DEPTH_TEST);
    }

    static void dispose() {
        BlockPbrAtlas.delete();
        if (program != null) {
            program.close();
            program = null;
        }
    }
}

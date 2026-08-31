package com.meekdev.amnetic.client.emissive.internal;

import com.meekdev.amnetic.client.emissive.EmissiveContext;
import com.meekdev.amnetic.client.render.GlState;
import com.meekdev.amnetic.client.render.ShaderProgram;
import com.mojang.blaze3d.opengl.GlTexture;
import java.nio.FloatBuffer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import org.joml.Matrix4f;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

public final class BlockEmissiveSource {

    public static final BlockEmissiveSource INSTANCE = new BlockEmissiveSource();

    private static final Identifier VSH =
            Identifier.fromNamespaceAndPath("amnetic", "shaders/emissive/block.vsh");
    private static final Identifier FSH =
            Identifier.fromNamespaceAndPath("amnetic", "shaders/emissive/block.fsh");

    private static final int STRIDE = BlockEmissiveGeometry.FLOATS_PER_VERTEX;
    private static final int MAX_VERTICES = 400_000;

    private final ShaderProgram program = new ShaderProgram(VSH, FSH);

    private int vao;
    private int vbo;
    private int vertexCount;
    private int capacityVertices;

    private int anchorX = Integer.MIN_VALUE, anchorY, anchorZ;
    private boolean dirty = true;
    private int chunkRadius = 4;
    private float intensity = 1f;

    private BlockEmissiveSource() {}

    public void invalidate() {
        dirty = true;
    }

    public void chunkRadius(int chunks) {
        this.chunkRadius = Math.max(1, chunks);
        dirty = true;
    }

    public int chunkRadius() {
        return chunkRadius;
    }

    public void intensity(float v) {
        this.intensity = Math.max(0f, v);
    }

    public float intensity() {
        return intensity;
    }

    public void draw(EmissiveContext ctx) {
        ClientLevel level = ctx.level();
        if (level == null) return;

        int cx = (int) Math.floor(ctx.cameraPos().x);
        int cy = (int) Math.floor(ctx.cameraPos().y);
        int cz = (int) Math.floor(ctx.cameraPos().z);

        boolean movedSection = (cx >> 4) != (anchorX >> 4) || (cz >> 4) != (anchorZ >> 4)
                || (cy >> 4) != (anchorY >> 4);
        if (dirty || anchorX == Integer.MIN_VALUE || movedSection) {
            rebuild(level, cx, cy, cz);
        }
        if (vertexCount == 0) return;

        int atlas = atlasGlId();
        if (atlas == 0) return;

        Matrix4f viewProj = new Matrix4f(ctx.projection()).mul(new Matrix4f(ctx.view()));

        GlState.bindTexture(0, atlas);
        program.begin();
        program.setSampler("AtlasSampler", 0);
        program.setMatrix4("ViewProj", viewProj);
        program.setVec3("Anchor",
                (float) (anchorX - ctx.cameraPos().x),
                (float) (anchorY - ctx.cameraPos().y),
                (float) (anchorZ - ctx.cameraPos().z));
        program.setFloat("Intensity", intensity);

        GL30.glBindVertexArray(vao);
        GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, vertexCount);
        GL30.glBindVertexArray(0);
    }

    private void rebuild(ClientLevel level, int cx, int cy, int cz) {
        dirty = false;
        anchorX = cx; anchorY = cy; anchorZ = cz;
        vertexCount = 0;

        FloatBuffer buf = BufferUtils.createFloatBuffer(MAX_VERTICES * STRIDE);
        BlockPos.MutableBlockPos mut = new BlockPos.MutableBlockPos();

        int chunkX = cx >> 4;
        int chunkZ = cz >> 4;
        int minSectionY = level.getMinSectionY();
        int sectionCount = level.getSectionsCount();

        outer:
        for (int dx = -chunkRadius; dx <= chunkRadius; dx++) {
            for (int dz = -chunkRadius; dz <= chunkRadius; dz++) {
                ChunkAccess chunk = level.getChunk(chunkX + dx, chunkZ + dz,
                        net.minecraft.world.level.chunk.status.ChunkStatus.FULL, false);
                if (chunk == null) continue;

                LevelChunkSection[] sections = chunk.getSections();
                for (int si = 0; si < sections.length && si < sectionCount; si++) {
                    LevelChunkSection section = sections[si];
                    if (section == null || section.hasOnlyAir()) continue;

                    // palette-level reject: most sections contain no light source at all
                    if (!section.maybeHas(s -> s.getLightEmission() > 0)) continue;

                    int baseY = (minSectionY + si) << 4;
                    int baseX = (chunkX + dx) << 4;
                    int baseZ = (chunkZ + dz) << 4;

                    for (int ly = 0; ly < 16; ly++) {
                        for (int lz = 0; lz < 16; lz++) {
                            for (int lx = 0; lx < 16; lx++) {
                                BlockState state = section.getBlockState(lx, ly, lz);
                                if (state.isAir() || state.getLightEmission() <= 0) continue;

                                float[] quads = BlockEmissiveGeometry.quads(state);
                                if (quads == null) continue;

                                int verts = quads.length / STRIDE;
                                if (buf.position() + verts * STRIDE > buf.capacity()) break outer;

                                mut.set(baseX + lx, baseY + ly, baseZ + lz);
                                float ox = mut.getX() - anchorX;
                                float oy = mut.getY() - anchorY;
                                float oz = mut.getZ() - anchorZ;
                                for (int i = 0; i + STRIDE - 1 < quads.length; i += STRIDE) {
                                    buf.put(quads[i] + ox).put(quads[i + 1] + oy).put(quads[i + 2] + oz);
                                    for (int k = 3; k < STRIDE; k++) buf.put(quads[i + k]);
                                }
                            }
                        }
                    }
                }
            }
        }

        buf.flip();
        vertexCount = buf.limit() / STRIDE;
        if (vertexCount == 0) return;

        if (vao == 0) vao = GL30.glGenVertexArrays();
        if (vbo == 0) vbo = GL15.glGenBuffers();
        GL30.glBindVertexArray(vao);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
        if (vertexCount > capacityVertices) {
            GL15.glBufferData(GL15.GL_ARRAY_BUFFER, buf, GL15.GL_DYNAMIC_DRAW);
            capacityVertices = vertexCount;
        } else {
            GL15.glBufferSubData(GL15.GL_ARRAY_BUFFER, 0L, buf);
        }
        int stride = STRIDE * Float.BYTES;
        GL20.glEnableVertexAttribArray(0);
        GL20.glVertexAttribPointer(0, 3, GL11.GL_FLOAT, false, stride, 0L);
        GL20.glEnableVertexAttribArray(1);
        GL20.glVertexAttribPointer(1, 2, GL11.GL_FLOAT, false, stride, 3 * Float.BYTES);
        GL20.glEnableVertexAttribArray(2);
        GL20.glVertexAttribPointer(2, 2, GL11.GL_FLOAT, false, stride, 5 * Float.BYTES);
        GL20.glEnableVertexAttribArray(3);
        GL20.glVertexAttribPointer(3, 2, GL11.GL_FLOAT, false, stride, 7 * Float.BYTES);
        GL30.glBindVertexArray(0);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
    }

    private static int atlasGlId() {
        AbstractTexture tex = Minecraft.getInstance().getTextureManager()
                .getTexture(TextureAtlas.LOCATION_BLOCKS);
        return tex != null && tex.getTexture() instanceof GlTexture gl ? gl.glId() : 0;
    }

    public void dispose() {
        if (vbo != 0) { GL15.glDeleteBuffers(vbo); vbo = 0; }
        if (vao != 0) { GL30.glDeleteVertexArrays(vao); vao = 0; }
        vertexCount = 0;
        capacityVertices = 0;
        anchorX = Integer.MIN_VALUE;
        dirty = true;
    }
}

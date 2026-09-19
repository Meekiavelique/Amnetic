package com.meekdev.amnetic.client.emissive.internal;

import com.meekdev.amnetic.client.compat.VanillaCompat;
import com.meekdev.amnetic.client.emissive.EmissiveContext;
import com.meekdev.amnetic.client.render.GlState;
import com.meekdev.amnetic.client.render.ShaderProgram;
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
import net.minecraft.world.level.chunk.status.ChunkStatus;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryUtil;

public final class BlockEmissiveSource {

    public static final BlockEmissiveSource INSTANCE = new BlockEmissiveSource();

    private static final Identifier VSH =
            Identifier.fromNamespaceAndPath("amnetic", "shaders/emissive/block.vsh");
    private static final Identifier FSH =
            Identifier.fromNamespaceAndPath("amnetic", "shaders/emissive/block.fsh");

    private static final int STRIDE = BlockEmissiveGeometry.FLOATS_PER_VERTEX;
    private static final int MAX_VERTICES = 400_000;
    private static final int INITIAL_VERTICES = 4096;
    private static final int MISSED_CHUNK_RETRY_FRAMES = 20;

    private ShaderProgram program;

    private int vao;
    private int vbo;
    private int vertexCount;
    private int capacityVertices;

    private FloatBuffer scratch;
    private final Matrix4f viewProj = new Matrix4f();

    private int anchorX = Integer.MIN_VALUE, anchorY, anchorZ;
    private boolean dirty = true;
    private boolean missedChunk;
    private int retryCooldown;
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

        boolean movedChunk = (cx >> 4) != (anchorX >> 4) || (cz >> 4) != (anchorZ >> 4);
        boolean retry = missedChunk && --retryCooldown <= 0;
        if (dirty || anchorX == Integer.MIN_VALUE || movedChunk || retry) {
            rebuild(level, cx, cy, cz);
        }
        if (vertexCount == 0) return;

        int atlas = atlasGlId();
        if (atlas == 0) return;

        viewProj.set(ctx.projection()).mul(ctx.view());

        GlState.bindTexture(0, atlas);
        if (program == null) program = new ShaderProgram(VSH, FSH);
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
        missedChunk = false;
        anchorX = cx; anchorY = cy; anchorZ = cz;
        vertexCount = 0;

        if (scratch == null) scratch = MemoryUtil.memAllocFloat(INITIAL_VERTICES * STRIDE);
        scratch.clear();
        BlockPos.MutableBlockPos mut = new BlockPos.MutableBlockPos();
        int radius = Math.min(chunkRadius, Math.max(1, Minecraft.getInstance().options.getEffectiveRenderDistance()));

        int chunkX = cx >> 4;
        int chunkZ = cz >> 4;
        //? if >=1.21.2 {
        int minSectionY = level.getMinSectionY();
        //?} else {
        /*int minSectionY = level.getMinSection();
        *///?}
        int sectionCount = level.getSectionsCount();

        outer:
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                ChunkAccess chunk = level.getChunk(chunkX + dx, chunkZ + dz,
                        ChunkStatus.FULL, false);
                if (chunk == null) {
                    missedChunk = true;
                    continue;
                }

                LevelChunkSection[] sections = chunk.getSections();
                for (int si = 0; si < sections.length && si < sectionCount; si++) {
                    LevelChunkSection section = sections[si];
                    if (section == null || section.hasOnlyAir()) continue;

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
                                if (!ensureRoom(verts)) break outer;
                                FloatBuffer buf = scratch;

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

        FloatBuffer buf = scratch;
        buf.flip();
        vertexCount = buf.limit() / STRIDE;
        if (missedChunk) retryCooldown = MISSED_CHUNK_RETRY_FRAMES;
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

    private boolean ensureRoom(int verts) {
        int needed = scratch.position() + verts * STRIDE;
        if (needed <= scratch.capacity()) return true;
        if (needed > MAX_VERTICES * STRIDE) return false;
        int capacity = Math.min(MAX_VERTICES * STRIDE, Math.max(needed, scratch.capacity() * 2));
        scratch = MemoryUtil.memRealloc(scratch, capacity);
        return true;
    }

    private static int atlasGlId() {
        AbstractTexture tex = Minecraft.getInstance().getTextureManager()
                .getTexture(TextureAtlas.LOCATION_BLOCKS);
        return VanillaCompat.glId(tex);
    }

    public void dispose() {
        if (vbo != 0) { GL15.glDeleteBuffers(vbo); vbo = 0; }
        if (vao != 0) { GL30.glDeleteVertexArrays(vao); vao = 0; }
        if (scratch != null) { MemoryUtil.memFree(scratch); scratch = null; }
        vertexCount = 0;
        capacityVertices = 0;
        anchorX = Integer.MIN_VALUE;
        dirty = true;
    }
}

package com.meekdev.amnetic.client.emissive.internal;

import com.meekdev.amnetic.client.emissive.EmissiveContext;
import com.meekdev.amnetic.client.render.GlState;
import com.meekdev.amnetic.client.render.ShaderProgram;
import com.mojang.blaze3d.opengl.GlTexture;
import java.nio.FloatBuffer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.state.BlockState;
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

    private static final int FLOATS_PER_VERTEX = 6;
    private static final int MAX_VERTICES = 400_000;

    private final ShaderProgram program = new ShaderProgram(VSH, FSH);

    private int vao;
    private int vbo;
    private int vertexCount;

    private int anchorX = Integer.MIN_VALUE, anchorY, anchorZ;
    private boolean dirty = true;
    private float radius = 32f;
    private float intensity = 1f;

    private BlockEmissiveSource() {}

    public void invalidate() {
        dirty = true;
    }

    public void radius(float blocks) {
        this.radius = Math.max(1f, blocks);
        dirty = true;
    }

    public float radius() {
        return radius;
    }

    public void intensity(float v) {
        this.intensity = Math.max(0f, v);
    }

    public float intensity() {
        return intensity;
    }

    public void draw(EmissiveContext ctx) {
        if (ctx.level() == null) return;

        int cx = (int) Math.floor(ctx.cameraPos().x);
        int cy = (int) Math.floor(ctx.cameraPos().y);
        int cz = (int) Math.floor(ctx.cameraPos().z);

        int moved = Math.abs(cx - anchorX) + Math.abs(cy - anchorY) + Math.abs(cz - anchorZ);
        if (dirty || anchorX == Integer.MIN_VALUE || moved > 8) {
            rebuild(ctx, cx, cy, cz);
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

    private void rebuild(EmissiveContext ctx, int cx, int cy, int cz) {
        dirty = false;
        anchorX = cx; anchorY = cy; anchorZ = cz;
        vertexCount = 0;

        var level = ctx.level();
        int r = (int) Math.ceil(radius);
        float r2 = radius * radius;
        BlockPos.MutableBlockPos mut = new BlockPos.MutableBlockPos();

        FloatBuffer buf = BufferUtils.createFloatBuffer(MAX_VERTICES * FLOATS_PER_VERTEX);

        outer:
        for (int x = cx - r; x <= cx + r; x++) {
            float dx = x - cx;
            float dx2 = dx * dx;
            if (dx2 > r2) continue;
            for (int z = cz - r; z <= cz + r; z++) {
                float dz = z - cz;
                float dxz2 = dx2 + dz * dz;
                if (dxz2 > r2) continue;
                for (int y = cy - r; y <= cy + r; y++) {
                    float dy = y - cy;
                    if (dxz2 + dy * dy > r2) continue;

                    mut.set(x, y, z);
                    if (!level.hasChunkAt(mut)) continue;
                    BlockState state = level.getBlockState(mut);
                    if (state.isAir()) continue;
                    int emission = state.getLightEmission();
                    if (emission <= 0) continue;

                    float[] quads = BlockEmissiveGeometry.quads(state);
                    if (quads == null) continue;

                    if (buf.position() + quads.length / 5 * FLOATS_PER_VERTEX > buf.capacity()) break outer;

                    float strength = emission / 15f;
                    float ox = x - anchorX, oy = y - anchorY, oz = z - anchorZ;
                    for (int i = 0; i + 4 < quads.length; i += 5) {
                        buf.put(quads[i] + ox).put(quads[i + 1] + oy).put(quads[i + 2] + oz)
                           .put(quads[i + 3]).put(quads[i + 4]).put(strength);
                    }
                }
            }
        }

        buf.flip();
        vertexCount = buf.limit() / FLOATS_PER_VERTEX;
        if (vertexCount == 0) return;

        if (vao == 0) vao = GL30.glGenVertexArrays();
        if (vbo == 0) vbo = GL15.glGenBuffers();
        GL30.glBindVertexArray(vao);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, buf, GL15.GL_DYNAMIC_DRAW);
        int stride = FLOATS_PER_VERTEX * Float.BYTES;
        GL20.glEnableVertexAttribArray(0);
        GL20.glVertexAttribPointer(0, 3, GL11.GL_FLOAT, false, stride, 0L);
        GL20.glEnableVertexAttribArray(1);
        GL20.glVertexAttribPointer(1, 2, GL11.GL_FLOAT, false, stride, 3 * Float.BYTES);
        GL20.glEnableVertexAttribArray(2);
        GL20.glVertexAttribPointer(2, 1, GL11.GL_FLOAT, false, stride, 5 * Float.BYTES);
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
        anchorX = Integer.MIN_VALUE;
        dirty = true;
    }
}

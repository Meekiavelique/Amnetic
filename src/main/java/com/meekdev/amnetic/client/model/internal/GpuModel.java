package com.meekdev.amnetic.client.model.internal;

import com.meekdev.amnetic.client.model.TextureFilter;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Objects;

import com.mojang.blaze3d.opengl.GlStateManager;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL31;
import org.lwjgl.opengl.GL33;
import org.lwjgl.system.MemoryUtil;

public final class GpuModel implements AutoCloseable {

    private static final int INSTANCE_STRIDE = 80;
    private static final int VERTEX_STRIDE_BYTES = ModelIR.VERTEX_STRIDE_FLOATS * Float.BYTES;
    public static final int MAX_JOINTS = 128;

    public static final class DrawInstance {
        final Matrix4f world = new Matrix4f();
        Matrix4f[] pose;
        float blockLight;
        float skyLight;

        public DrawInstance() {}

        public DrawInstance(Matrix4fc world, Matrix4f[] pose, float blockLight, float skyLight) {
            set(world, pose, blockLight, skyLight);
        }

        void set(Matrix4fc world, Matrix4f[] pose, float blockLight, float skyLight) {
            this.world.set(world);
            this.pose = pose;
            this.blockLight = blockLight;
            this.skyLight = skyLight;
        }

        public Matrix4f world() { return world; }
        public Matrix4f[] pose() { return pose; }
        public float blockLight() { return blockLight; }
        public float skyLight() { return skyLight; }
    }

    private final ModelIR ir;
    private final GpuPart[] parts;
    private final ModelTexture[] baseColor;
    private final ModelTexture[] normal;
    private final ModelTexture[] orm;
    private final ModelTexture[] emissive;
    private final Object[] resolvedKey;
    private boolean uploaded;
    private boolean closed;

    private ByteBuffer instanceScratch = MemoryUtil.memAlloc(INSTANCE_STRIDE * 64);
    private Matrix4f[] palette;

    private ByteBuffer paletteScratch;
    private int jointTbo;
    private int jointTboTex;
    private final Matrix4f skinTmp = new Matrix4f();

    public GpuModel(ModelIR ir) {
        this.ir = ir;
        this.parts = new GpuPart[ir.parts().size()];
        int matCount = ir.materials().size();
        this.baseColor = new ModelTexture[matCount];
        this.normal = new ModelTexture[matCount];
        this.orm = new ModelTexture[matCount];
        this.emissive = new ModelTexture[matCount];
        this.resolvedKey = new Object[matCount];
    }

    public ModelIR ir() {
        return ir;
    }

    public boolean isUploaded() {
        return uploaded;
    }

    private Vector3f boundsMin;
    private Vector3f tightMin;
    private Vector3f tightMax;
    private Vector3f boundsMax;

    public void localBounds(Vector3f outMin, Vector3f outMax) {
        if (boundsMin == null) {
            Vector3f mn = new Vector3f();
            Vector3f mx = new Vector3f();
            tightBounds(mn, mx);
            Vector3f ext = new Vector3f(mx).sub(mn).mul(0.15f).add(0.05f, 0.05f, 0.05f);
            mn.sub(ext);
            mx.add(ext);
            boundsMin = mn;
            boundsMax = mx;
        }
        outMin.set(boundsMin);
        outMax.set(boundsMax);
    }

    public void tightBounds(Vector3f outMin, Vector3f outMax) {
        if (tightMin == null) {
            Vector3f mn = new Vector3f(Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE);
            Vector3f mx = new Vector3f(-Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE);
            Vector3f v = new Vector3f();
            int stride = ModelIR.VERTEX_STRIDE_FLOATS;
            for (ModelIR.Part p : ir.parts()) {
                float[] vert = p.vertices;
                for (int i = 0; i + 2 < vert.length; i += stride) {
                    v.set(vert[i], vert[i + 1], vert[i + 2]);
                    p.transform.transformPosition(v);
                    mn.min(v);
                    mx.max(v);
                }
            }
            if (mn.x > mx.x) { mn.set(0f); mx.set(0f); }
            tightMin = mn;
            tightMax = mx;
        }
        outMin.set(tightMin);
        outMax.set(tightMax);
    }

    private void ensureUploaded() {
        if (uploaded) {
            return;
        }
        for (int i = 0; i < parts.length; i++) {
            parts[i] = GpuPart.upload(ir.parts().get(i));
        }
        uploaded = true;
    }

    public void draw(ModelShader shader, List<DrawInstance> instances) {
        draw(shader, instances, 0);
    }

    public void draw(ModelShader shader, List<DrawInstance> instances, int lod) {
        if (closed || instances.isEmpty()) {
            return;
        }
        ensureUploaded();

        drawPass(shader, instances, false, lod);
        drawPass(shader, instances, true, lod);

        GlStateManager._glBindVertexArray(0);
        GlStateManager._activeTexture(GL13.GL_TEXTURE0);
    }

    private void drawPass(ModelShader shader, List<DrawInstance> instances, boolean blendPass, int lod) {
        for (GpuPart part : parts) {
            if (part == null) {
                continue;
            }
            ModelIR.Material mat = materialFor(part.materialIndex);
            if (mat.blend != blendPass) {
                continue;
            }
            bindMaterial(shader, part.materialIndex, mat);
            applyMaterialState(mat);
            if (part.skinned) {
                drawSkinned(shader, part, instances);
            } else {
                part.selectLod(lod);
                drawBatched(shader, part, instances);
            }
            restoreMaterialState(mat);
        }
    }

    public void drawShadow(ModelShadowProgram shadow, List<DrawInstance> instances) {
        if (closed || instances.isEmpty()) {
            return;
        }
        ensureUploaded();
        for (GpuPart part : parts) {
            if (part == null) {
                continue;
            }
            ModelIR.Material mat = materialFor(part.materialIndex);
            if (mat.blend) {
                continue;
            }
            boolean cutout = mat.alphaCutoff > 0f;
            boolean hasAlbedo = false;
            if (cutout) {
                resolveTextures(part.materialIndex, mat);
                hasAlbedo = bind(baseColor, part.materialIndex, 0);
            }
            shadow.setMaterial(mat.alphaCutoff, hasAlbedo);
            shadow.setSkinned(part.skinned);
            if (mat.doubleSided) {
                GlStateManager._disableCull();
            }
            if (part.skinned) {
                drawShadowSkinned(shadow, part, instances);
            } else {
                drawShadowBatched(part, instances);
            }
            if (mat.doubleSided) {
                GlStateManager._enableCull();
            }
        }
        GlStateManager._glBindVertexArray(0);
        GlStateManager._activeTexture(GL13.GL_TEXTURE0);
    }

    private void drawShadowBatched(GpuPart part, List<DrawInstance> instances) {
        int count = instances.size();
        packInstances(part, instances, count);
        uploadInstances(part);

        GlStateManager._glBindVertexArray(part.vao);
        if (part.indexed) {
            GL31.glDrawElementsInstanced(GL11.GL_TRIANGLES, part.indexCount, GL11.GL_UNSIGNED_INT, 0L, count);
        } else {
            GL31.glDrawArraysInstanced(GL11.GL_TRIANGLES, 0, part.vertexCount, count);
        }
    }

    private void drawShadowSkinned(ModelShadowProgram shadow, GpuPart part, List<DrawInstance> instances) {
        GlStateManager._glBindVertexArray(part.vao);
        for (DrawInstance inst : instances) {
            buildPalette(part, inst.pose());
            shadow.uploadJointMatrices(palette, part.jointNodes.length);

            packSingle(inst.world(), inst.blockLight(), inst.skyLight());
            uploadInstances(part);

            if (part.indexed) {
                GL31.glDrawElementsInstanced(GL11.GL_TRIANGLES, part.indexCount, GL11.GL_UNSIGNED_INT, 0L, 1);
            } else {
                GL31.glDrawArraysInstanced(GL11.GL_TRIANGLES, 0, part.vertexCount, 1);
            }
        }
    }

    public void drawFlat(FlatProgram flat, Matrix4f projView, Matrix4f world, Matrix4f[] pose) {
        if (closed) {
            return;
        }
        ensureUploaded();
        Matrix4f model = new Matrix4f();
        Matrix4f mvp = new Matrix4f();
        for (GpuPart part : parts) {
            if (part == null) {
                continue;
            }
            Matrix4f nodeTransform = pose != null && part.nodeIndex >= 0 && part.nodeIndex < pose.length
                    ? pose[part.nodeIndex] : part.transform;
            world.mul(nodeTransform, model);
            projView.mul(model, mvp);
            flat.setMvp(mvp);
            GlStateManager._glBindVertexArray(part.vao);
            if (part.indexed) {
                GL11.glDrawElements(GL11.GL_TRIANGLES, part.indexCount, GL11.GL_UNSIGNED_INT, 0L);
            } else {
                GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, part.vertexCount);
            }
        }
        GlStateManager._glBindVertexArray(0);
    }

    private void drawBatched(ModelShader shader, GpuPart part, List<DrawInstance> instances) {
        int count = instances.size();
        shader.setSkinned(false);
        packInstances(part, instances, count);
        uploadInstances(part);

        GlStateManager._glBindVertexArray(part.vao);
        if (part.indexed) {
            if (part.drawIbo != part.ibo) {
                GlStateManager._glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, part.drawIbo);
            }
            GL31.glDrawElementsInstanced(GL11.GL_TRIANGLES, part.drawCount, GL11.GL_UNSIGNED_INT, 0L, count);
            if (part.drawIbo != part.ibo) {
                GlStateManager._glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, part.ibo);
            }
        } else {
            GL31.glDrawArraysInstanced(GL11.GL_TRIANGLES, 0, part.vertexCount, count);
        }
    }

    private void drawSkinned(ModelShader shader, GpuPart part, List<DrawInstance> instances) {
        int count = instances.size();
        int jointCount = Math.min(part.jointNodes.length, MAX_JOINTS);
        if (count == 0 || jointCount <= 0) {
            return;
        }

        int matrices = count * jointCount;
        ensurePaletteScratch(matrices);
        paletteScratch.clear();
        for (int k = 0; k < count; k++) {
            Matrix4f[] pose = instances.get(k).pose();
            int instBase = k * jointCount;
            for (int j = 0; j < jointCount; j++) {
                int node = part.jointNodes[j];
                if (pose != null && node >= 0 && node < pose.length) {
                    pose[node].mul(part.inverseBind[j], skinTmp);
                } else {
                    skinTmp.identity();
                }
                skinTmp.get((instBase + j) * 64, paletteScratch);
            }
        }
        paletteScratch.position(0).limit(matrices * 64);
        uploadJointTbo(paletteScratch);

        shader.setSkinned(true);
        shader.setJointCount(jointCount);
        bindJointTbo();

        packInstancesWorldOnly(instances, count);
        uploadInstances(part);

        GlStateManager._glBindVertexArray(part.vao);
        if (part.indexed) {
            GL31.glDrawElementsInstanced(GL11.GL_TRIANGLES, part.indexCount, GL11.GL_UNSIGNED_INT, 0L, count);
        } else {
            GL31.glDrawArraysInstanced(GL11.GL_TRIANGLES, 0, part.vertexCount, count);
        }
    }

    private void ensurePaletteScratch(int matrices) {
        int needed = matrices * 64;
        if (paletteScratch == null) {
            paletteScratch = MemoryUtil.memAlloc(Math.max(needed, 64 * 64));
        } else if (paletteScratch.capacity() < needed) {
            paletteScratch = MemoryUtil.memRealloc(paletteScratch, needed);
        }
    }

    private void uploadJointTbo(ByteBuffer data) {
        if (jointTbo == 0) {
            jointTbo = GlStateManager._glGenBuffers();
            jointTboTex = GL11.glGenTextures();
        }
        GlStateManager._glBindBuffer(GL31.GL_TEXTURE_BUFFER, jointTbo);
        GL15.glBufferData(GL31.GL_TEXTURE_BUFFER, data, GL15.GL_STREAM_DRAW);
        GlStateManager._glBindBuffer(GL31.GL_TEXTURE_BUFFER, 0);
    }

    private void bindJointTbo() {
        GlStateManager._activeTexture(GL13.GL_TEXTURE0 + ModelShader.JOINT_UNIT);
        GL11.glBindTexture(GL31.GL_TEXTURE_BUFFER, jointTboTex);
        GL31.glTexBuffer(GL31.GL_TEXTURE_BUFFER, GL30.GL_RGBA32F, jointTbo);
        GlStateManager._activeTexture(GL13.GL_TEXTURE0);
    }

    private void uploadInstances(GpuPart part) {
        GlStateManager._glBindBuffer(GL15.GL_ARRAY_BUFFER, part.instanceVbo);
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, instanceScratch, GL15.GL_STREAM_DRAW);
        GlStateManager._glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
    }

    private void buildPalette(GpuPart part, Matrix4f[] pose) {
        int count = Math.min(part.jointNodes.length, MAX_JOINTS);
        ensurePalette(count);
        for (int j = 0; j < count; j++) {
            int node = part.jointNodes[j];
            if (pose != null && node >= 0 && node < pose.length) {
                pose[node].mul(part.inverseBind[j], palette[j]);
            } else {
                palette[j].identity();
            }
        }
    }

    private void ensurePalette(int count) {
        if (palette == null || palette.length < count) {
            palette = new Matrix4f[Math.max(count, 16)];
            for (int i = 0; i < palette.length; i++) {
                palette[i] = new Matrix4f();
            }
        }
    }

    private void packSingle(Matrix4f world, float block, float sky) {
        instanceScratch.clear();
        world.get(0, instanceScratch);
        instanceScratch.position(64);
        instanceScratch.putFloat(block);
        instanceScratch.putFloat(sky);
        instanceScratch.putFloat(0f);
        instanceScratch.putFloat(0f);
        instanceScratch.position(0).limit(INSTANCE_STRIDE);
    }

    private void packInstances(GpuPart part, List<DrawInstance> instances, int count) {
        int needed = count * INSTANCE_STRIDE;
        if (instanceScratch.capacity() < needed) {
            instanceScratch = MemoryUtil.memRealloc(instanceScratch, needed);
        }
        instanceScratch.clear();
        Matrix4f tmp = new Matrix4f();
        for (int i = 0; i < count; i++) {
            DrawInstance inst = instances.get(i);
            int base = i * INSTANCE_STRIDE;
            Matrix4f nodeTransform = nodeTransform(inst, part);
            inst.world().mul(nodeTransform, tmp);
            tmp.get(base, instanceScratch);
            instanceScratch.position(base + 64);
            instanceScratch.putFloat(inst.blockLight());
            instanceScratch.putFloat(inst.skyLight());
            instanceScratch.putFloat(0f);
            instanceScratch.putFloat(0f);
        }
        instanceScratch.position(0).limit(needed);
    }

    private void packInstancesWorldOnly(List<DrawInstance> instances, int count) {
        int needed = count * INSTANCE_STRIDE;
        if (instanceScratch.capacity() < needed) {
            instanceScratch = MemoryUtil.memRealloc(instanceScratch, needed);
        }
        instanceScratch.clear();
        for (int i = 0; i < count; i++) {
            DrawInstance inst = instances.get(i);
            int base = i * INSTANCE_STRIDE;
            inst.world().get(base, instanceScratch);
            instanceScratch.position(base + 64);
            instanceScratch.putFloat(inst.blockLight());
            instanceScratch.putFloat(inst.skyLight());
            instanceScratch.putFloat(0f);
            instanceScratch.putFloat(0f);
        }
        instanceScratch.position(0).limit(needed);
    }

    private Matrix4f nodeTransform(DrawInstance inst, GpuPart part) {
        Matrix4f[] pose = inst.pose();
        if (pose != null && part.nodeIndex >= 0 && part.nodeIndex < pose.length) {
            return pose[part.nodeIndex];
        }
        return part.transform;
    }

    private ModelIR.Material materialFor(int materialIndex) {
        if (materialIndex >= 0 && materialIndex < ir.materials().size()) {
            return ir.materials().get(materialIndex);
        }
        return DEFAULT_MATERIAL;
    }

    private void bindMaterial(ModelShader shader, int materialIndex, ModelIR.Material mat) {
        resolveTextures(materialIndex, mat);

        boolean hasBase = bind(baseColor, materialIndex, 0);
        boolean hasNormal = bind(normal, materialIndex, 1);
        boolean hasOrm = bind(orm, materialIndex, 2);
        boolean hasEmissive = bind(emissive, materialIndex, 3);

        GL33.glBindSampler(0, hasBase && mat.baseColorFilter == TextureFilter.NEAREST ? nearestSampler() : 0);

        shader.uploadMaterial(mat, hasBase, hasNormal, hasOrm, hasEmissive);
    }

    private int nearestSampler;

    private int nearestSampler() {
        if (nearestSampler == 0) {
            nearestSampler = GL33.glGenSamplers();
            GL33.glSamplerParameteri(nearestSampler, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST_MIPMAP_LINEAR);
            GL33.glSamplerParameteri(nearestSampler, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        }
        return nearestSampler;
    }

    private boolean bind(ModelTexture[] set, int materialIndex, int unit) {
        if (materialIndex < 0 || materialIndex >= set.length || set[materialIndex] == null) {
            return false;
        }
        GlStateManager._activeTexture(GL13.GL_TEXTURE0 + unit);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, set[materialIndex].id());
        GlStateManager._bindTexture(set[materialIndex].id());
        return true;
    }

    // leaving a sampler bound would follow us out into vanilla's own draws
    private void clearSamplerState() {
        GL33.glBindSampler(0, 0);
    }

    private void applyMaterialState(ModelIR.Material mat) {
        if (mat.blend) {
            GlStateManager._enableBlend();
            GlStateManager._blendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GlStateManager._depthMask(false);
        }
        if (mat.doubleSided) {
            GlStateManager._disableCull();
        }
    }

    private void restoreMaterialState(ModelIR.Material mat) {
        clearSamplerState();
        if (mat.blend) {
            GlStateManager._disableBlend();
            GlStateManager._depthMask(true);
        }
        if (mat.doubleSided) {
            GlStateManager._enableCull();
        }
    }

    private void resolveTextures(int materialIndex, ModelIR.Material mat) {
        if (materialIndex < 0 || materialIndex >= resolvedKey.length) {
            return;
        }
        Object key = textureKey(mat);
        if (Objects.equals(key, resolvedKey[materialIndex])) {
            return;
        }
        resolvedKey[materialIndex] = key;
        closeSlot(baseColor, materialIndex);
        closeSlot(normal, materialIndex);
        closeSlot(orm, materialIndex);
        closeSlot(emissive, materialIndex);
        baseColor[materialIndex] = mat.baseColorGlId != 0
                ? ModelTexture.external(mat.baseColorGlId)
                : resolve(mat.baseColorImageBytes, mat.baseColorTexture, true);
        normal[materialIndex] = resolve(mat.normalImageBytes, mat.normalTexture, false);
        orm[materialIndex] = resolve(mat.ormImageBytes, mat.ormTexture, false);
        emissive[materialIndex] = mat.emissiveGlId != 0
                ? ModelTexture.external(mat.emissiveGlId)
                : resolve(mat.emissiveImageBytes, mat.emissiveTexture, true);
    }

    private static ModelTexture resolve(byte[] bytes, Identifier id, boolean srgb) {
        if (bytes != null) {
            return ModelTexture.fromBytes(bytes, srgb);
        }
        if (id != null) {
            return ModelTexture.fromIdentifier(id, srgb);
        }
        return null;
    }

    private static Object textureKey(ModelIR.Material mat) {
        return new TextureKey(mat.baseColorImageBytes,
                mat.baseColorGlId != 0 ? mat.baseColorGlId : mat.baseColorTexture,
                mat.normalImageBytes, mat.normalTexture,
                mat.ormImageBytes, mat.ormTexture,
                mat.emissiveImageBytes, mat.emissiveGlId != 0 ? mat.emissiveGlId : mat.emissiveTexture);
    }

    private record TextureKey(byte[] baseBytes, Object baseId, byte[] normalBytes, Object normalId,
                              byte[] ormBytes, Object ormId, byte[] emissiveBytes, Object emissiveId) {
    }

    private static void closeSlot(ModelTexture[] set, int index) {
        if (set[index] != null) {
            set[index].close();
            set[index] = null;
        }
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        GpuPart[] localParts = parts;
        ByteBuffer scratch = instanceScratch;
        ByteBuffer palScratch = paletteScratch;
        int tbo = jointTbo;
        int tboTex = jointTboTex;
        instanceScratch = null;
        paletteScratch = null;
        jointTbo = 0;
        jointTboTex = 0;
        for (ModelTexture[] set : new ModelTexture[][]{baseColor, normal, orm, emissive}) {
            for (ModelTexture t : set) {
                if (t != null) {
                    t.close();
                }
            }
        }
        GlReaper.submit(() -> {
            for (GpuPart p : localParts) {
                if (p != null) {
                    p.deleteNow();
                }
            }
            if (scratch != null) {
                MemoryUtil.memFree(scratch);
            }
            if (palScratch != null) {
                MemoryUtil.memFree(palScratch);
            }
            if (tbo != 0) {
                GlStateManager._glDeleteBuffers(tbo);
            }
            if (tboTex != 0) {
                GL11.glDeleteTextures(tboTex);
            }
        });
    }

    private static final ModelIR.Material DEFAULT_MATERIAL = new ModelIR.Material();

    private static final class GpuPart {
        int vao;
        int vbo;
        int ibo;
        int tangentVbo;
        int jointVbo;
        int weightVbo;
        int instanceVbo;
        int vertexCount;
        int indexCount;
        int instanceCapacity = 64;
        boolean indexed;
        boolean skinned;
        int materialIndex;
        int nodeIndex;
        int[] jointNodes;
        Matrix4f[] inverseBind;
        final Matrix4f transform;

        ModelIR.Part src;
        int drawIbo;
        int drawCount;
        int[] lodIbo;
        int[] lodCount;

        private GpuPart(Matrix4f transform) {
            this.transform = transform;
        }

        void selectLod(int lod) {
            if (lod <= 0 || !indexed || src == null) {
                drawIbo = ibo;
                drawCount = indexCount;
                return;
            }
            int[][] lods = src.lods;
            if (lods == null) {
                drawIbo = ibo;
                drawCount = indexCount;
                return;
            }
            if (lod >= lods.length) {
                lod = lods.length - 1;
            }
            if (lodIbo == null) {
                lodIbo = new int[lods.length];
                lodCount = new int[lods.length];
                lodIbo[0] = ibo;
                lodCount[0] = indexCount;
            }
            if (lodIbo[lod] == 0) {
                int[] idx = lods[lod];
                GlStateManager._glBindVertexArray(0);
                int b = GlStateManager._glGenBuffers();
                GlStateManager._glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, b);
                ByteBuffer buf = MemoryUtil.memAlloc(idx.length * Integer.BYTES);
                buf.asIntBuffer().put(idx);
                GL15.glBufferData(GL15.GL_ELEMENT_ARRAY_BUFFER, buf, GL15.GL_STATIC_DRAW);
                MemoryUtil.memFree(buf);
                GlStateManager._glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, 0);
                lodIbo[lod] = b;
                lodCount[lod] = idx.length;
            }
            drawIbo = lodIbo[lod];
            drawCount = lodCount[lod];
        }

        static GpuPart upload(ModelIR.Part part) {
            GpuPart p = new GpuPart(part.transform);
            p.src = part;
            p.materialIndex = part.materialIndex;
            p.nodeIndex = part.nodeIndex;
            p.vertexCount = part.vertexCount();
            p.indexed = part.hasIndices();
            p.indexCount = part.hasIndices() ? part.indices.length : 0;
            p.skinned = part.hasSkin();
            if (p.skinned) {
                p.jointNodes = part.jointNodes;
                p.inverseBind = part.inverseBind;
            }

            p.vao = GlStateManager._glGenVertexArrays();
            GlStateManager._glBindVertexArray(p.vao);

            p.vbo = GlStateManager._glGenBuffers();
            GlStateManager._glBindBuffer(GL15.GL_ARRAY_BUFFER, p.vbo);
            ByteBuffer vb = MemoryUtil.memAlloc(part.vertices.length * Float.BYTES);
            vb.asFloatBuffer().put(part.vertices);
            GL15.glBufferData(GL15.GL_ARRAY_BUFFER, vb, GL15.GL_STATIC_DRAW);
            MemoryUtil.memFree(vb);

            GlStateManager._vertexAttribPointer(0, 3, GL11.GL_FLOAT, false, VERTEX_STRIDE_BYTES, 0L);
            GlStateManager._enableVertexAttribArray(0);
            GlStateManager._vertexAttribPointer(1, 3, GL11.GL_FLOAT, false, VERTEX_STRIDE_BYTES, 12L);
            GlStateManager._enableVertexAttribArray(1);
            GlStateManager._vertexAttribPointer(2, 2, GL11.GL_FLOAT, false, VERTEX_STRIDE_BYTES, 24L);
            GlStateManager._enableVertexAttribArray(2);

            if (part.hasTangents()) {
                p.tangentVbo = GlStateManager._glGenBuffers();
                GlStateManager._glBindBuffer(GL15.GL_ARRAY_BUFFER, p.tangentVbo);
                ByteBuffer tb = MemoryUtil.memAlloc(part.tangents.length * Float.BYTES);
                tb.asFloatBuffer().put(part.tangents);
                GL15.glBufferData(GL15.GL_ARRAY_BUFFER, tb, GL15.GL_STATIC_DRAW);
                MemoryUtil.memFree(tb);
                GlStateManager._vertexAttribPointer(8, 4, GL11.GL_FLOAT, false, 4 * Float.BYTES, 0L);
                GlStateManager._enableVertexAttribArray(8);
            }

            if (p.skinned) {
                p.jointVbo = GlStateManager._glGenBuffers();
                GlStateManager._glBindBuffer(GL15.GL_ARRAY_BUFFER, p.jointVbo);
                ByteBuffer jb = MemoryUtil.memAlloc(part.jointIndices.length * Float.BYTES);
                jb.asFloatBuffer().put(part.jointIndices);
                GL15.glBufferData(GL15.GL_ARRAY_BUFFER, jb, GL15.GL_STATIC_DRAW);
                MemoryUtil.memFree(jb);
                GlStateManager._vertexAttribPointer(9, 4, GL11.GL_FLOAT, false, 4 * Float.BYTES, 0L);
                GlStateManager._enableVertexAttribArray(9);

                p.weightVbo = GlStateManager._glGenBuffers();
                GlStateManager._glBindBuffer(GL15.GL_ARRAY_BUFFER, p.weightVbo);
                ByteBuffer wb = MemoryUtil.memAlloc(part.jointWeights.length * Float.BYTES);
                wb.asFloatBuffer().put(part.jointWeights);
                GL15.glBufferData(GL15.GL_ARRAY_BUFFER, wb, GL15.GL_STATIC_DRAW);
                MemoryUtil.memFree(wb);
                GlStateManager._vertexAttribPointer(10, 4, GL11.GL_FLOAT, false, 4 * Float.BYTES, 0L);
                GlStateManager._enableVertexAttribArray(10);
            }

            if (p.indexed) {
                p.ibo = GlStateManager._glGenBuffers();
                GlStateManager._glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, p.ibo);
                ByteBuffer ibuf = MemoryUtil.memAlloc(part.indices.length * Integer.BYTES);
                ibuf.asIntBuffer().put(part.indices);
                GL15.glBufferData(GL15.GL_ELEMENT_ARRAY_BUFFER, ibuf, GL15.GL_STATIC_DRAW);
                MemoryUtil.memFree(ibuf);
            }

            p.instanceVbo = GlStateManager._glGenBuffers();
            GlStateManager._glBindBuffer(GL15.GL_ARRAY_BUFFER, p.instanceVbo);
            GL15.glBufferData(GL15.GL_ARRAY_BUFFER, (long) INSTANCE_STRIDE * p.instanceCapacity, GL15.GL_STREAM_DRAW);
            for (int i = 0; i < 4; i++) {
                GlStateManager._vertexAttribPointer(3 + i, 4, GL11.GL_FLOAT, false, INSTANCE_STRIDE, i * 16L);
                GlStateManager._enableVertexAttribArray(3 + i);
                GL33.glVertexAttribDivisor(3 + i, 1);
            }
            GlStateManager._vertexAttribPointer(7, 4, GL11.GL_FLOAT, false, INSTANCE_STRIDE, 64L);
            GlStateManager._enableVertexAttribArray(7);
            GL33.glVertexAttribDivisor(7, 1);

            GlStateManager._glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
            GlStateManager._glBindVertexArray(0);
            p.drawIbo = p.ibo;
            p.drawCount = p.indexCount;
            return p;
        }

        void deleteNow() {
            if (ibo != 0) {
                GlStateManager._glDeleteBuffers(ibo);
            }
            if (vbo != 0) {
                GlStateManager._glDeleteBuffers(vbo);
            }
            if (tangentVbo != 0) {
                GlStateManager._glDeleteBuffers(tangentVbo);
            }
            if (jointVbo != 0) {
                GlStateManager._glDeleteBuffers(jointVbo);
            }
            if (weightVbo != 0) {
                GlStateManager._glDeleteBuffers(weightVbo);
            }
            if (instanceVbo != 0) {
                GlStateManager._glDeleteBuffers(instanceVbo);
            }
            if (vao != 0) {
                GL30.glDeleteVertexArrays(vao);
            }
        }
    }
}

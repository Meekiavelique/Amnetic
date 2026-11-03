package com.meekdev.amnetic.client.model.internal;

import com.meekdev.amnetic.client.model.MaterialLook;
import com.meekdev.amnetic.client.model.Model;
import com.meekdev.amnetic.client.model.ModelLook;
import com.meekdev.amnetic.client.model.TextureFilter;
import com.meekdev.amnetic.client.render.GlState;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.mojang.renderpearl.backend.opengl.GlStateManager;
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
import com.meekdev.amnetic.client.material.SurfaceInputs;

public final class GpuModel implements AutoCloseable {
    private static final int INSTANCE_STRIDE = 144;
    private static final float DITHERED = -1f;
    private static final int VERTEX_STRIDE_BYTES = ModelIR.VERTEX_STRIDE_FLOATS * Float.BYTES;
    public static final int MAX_JOINTS = 128;

    public static final class DrawInstance {
        final Matrix4f world = new Matrix4f();
        final Matrix4f lastWorld = new Matrix4f();
        Matrix4f[] pose;
        Matrix4f[] lastPose;
        float blockLight;
        float skyLight;
        SurfaceInputs inputs;
        ModelLook look;
        int lod;
        int batch;

        public DrawInstance() {}

        public DrawInstance(Matrix4fc world, Matrix4f[] pose, float blockLight, float skyLight) {
            set(world, pose, blockLight, skyLight, null, null);
        }

        public DrawInstance(Matrix4fc world, Matrix4f[] pose, float blockLight, float skyLight, ModelLook look) {
            set(world, pose, blockLight, skyLight, null, look);
        }

        void set(Matrix4fc world, Matrix4f[] pose, float blockLight, float skyLight, SurfaceInputs inputs, ModelLook look) {
            this.world.set(world);
            this.lastWorld.set(world);
            this.pose = pose;
            this.lastPose = pose;
            this.blockLight = blockLight;
            this.skyLight = skyLight;
            this.inputs = inputs;
            this.look = look;
            this.lod = 0;
            this.batch = 0;
        }

        void set(DrawInstance other) {
            set(other.world, other.pose, other.blockLight, other.skyLight, other.inputs, other.look);
            moved(other.lastWorld, other.lastPose);
            lod = other.lod;
            batch = other.batch;
        }

        void moved(Matrix4fc lastWorld, Matrix4f[] lastPose) {
            this.lastWorld.set(lastWorld);
            this.lastPose = lastPose == null ? pose : lastPose;
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
    private Matrix4f[] rest;

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
                if (p.hasSkin()) {
                    Skinning.bounds(p, rest(), mn, mx);
                    continue;
                }
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

    private Matrix4f[] rest() {
        if (rest == null) {
            rest = Skinning.restPose(ir);
        }
        return rest;
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

    public interface Blended {
        void add(int part, DrawInstance instance);
    }

    public void draw(ModelShader shader, List<DrawInstance> instances, int lod) {
        if (closed || instances.isEmpty()) {
            return;
        }
        ensureUploaded();

        drawPass(shader, instances, false, lod, null);
        drawPass(shader, instances, true, lod, null);

        GlState.resetVertexArray();
        GlStateManager._activeTexture(GL13.GL_TEXTURE0);
    }

    public void drawSolid(ModelShader shader, List<DrawInstance> instances, int lod, Blended later) {
        if (closed || instances.isEmpty()) {
            return;
        }
        ensureUploaded();
        drawPass(shader, instances, false, lod, later);
        GlState.resetVertexArray();
        GlStateManager._activeTexture(GL13.GL_TEXTURE0);
    }

    public void drawBlended(ModelShader shader, int partIndex, DrawInstance instance) {
        GpuPart part = closed || partIndex < 0 || partIndex >= parts.length ? null : parts[partIndex];
        if (part == null) {
            return;
        }
        if (!part.skinned) part.selectLod(instance.lod);
        List<DrawInstance> one = List.of(instance);
        drawRun(shader, part, effective(part.materialIndex, instance.look), one);
        GlState.resetVertexArray();
        GlStateManager._activeTexture(GL13.GL_TEXTURE0);
    }

    public float distance(int partIndex, DrawInstance instance) {
        GpuPart part = parts[partIndex];
        Vector3f middle = part.middle();
        Matrix4f[] pose = instance.pose;
        Matrix4f node = part.skinned || pose == null || part.nodeIndex < 0 || part.nodeIndex >= pose.length
                ? part.transform : pose[part.nodeIndex];
        Vector3f at = node.transformPosition(middle, new Vector3f());
        Model.Piece piece = instance.look == null ? null : instance.look.piece();
        if (piece != null) piece.frame().transformPosition(at);
        return instance.world.transformPosition(at).length();
    }

    private void drawPass(ModelShader shader, List<DrawInstance> instances, boolean blendPass, int lod, Blended later) {
        for (int p = 0; p < parts.length; p++) {
            GpuPart part = parts[p];
            if (part == null) {
                continue;
            }
            int start = 0;
            while (start < instances.size()) {
                DrawInstance first = instances.get(start);
                int end = start + 1;
                while (end < instances.size() && together(first, instances.get(end))) end++;
                List<DrawInstance> run = start == 0 && end == instances.size() ? instances : instances.subList(start, end);
                start = end;
                if (!part.skinned) part.selectLod(first.lod < 0 ? lod : first.lod);
                Model.Piece piece = first.look == null ? null : first.look.piece();
                if (piece != null && !piece.shows(part.nodeIndex)) {
                    continue;
                }
                ModelIR.Material mat = effective(part.materialIndex, first.look);
                if (mat.blend != blendPass) {
                    if (mat.blend && later != null) {
                        for (DrawInstance instance : run) later.add(p, instance);
                    }
                    continue;
                }
                drawRun(shader, part, mat, run);
            }
        }
    }

    private static boolean together(DrawInstance a, DrawInstance b) {
        if (a.lod != b.lod || a.batch != b.batch) return false;
        if (a.look != b.look && (a.look == null || !a.look.sameAs(b.look))) return false;
        return a.inputs == b.inputs || a.inputs != null && a.inputs.sameAs(b.inputs);
    }

    private void drawRun(ModelShader shader, GpuPart part, ModelIR.Material mat, List<DrawInstance> run) {
        DrawInstance first = run.get(0);
        MaterialLook look = first.look == null ? null : first.look.resolve(materialFor(part.materialIndex).name);
        bindMaterial(shader, part.materialIndex, mat, look);
        int override = shadingOverride(part.nodeIndex);
        if (override >= 0) {
            shader.setMaterialId(override);
        }
        if (look == null) shader.uploadUv(1f, 1f, 0f, 0f);
        else shader.uploadUv(look.repeatU(), look.repeatV(), look.offsetU(), look.offsetV());
        shader.uploadWarp(look == null ? 0f : look.warp());
        shader.uploadVertexColors(part.colored);
        applyMaterialState(mat);
        shader.uploadSurface(first.inputs);
        if (part.skinned) drawSkinned(shader, part, run);
        else drawBatched(shader, part, run);
        restoreMaterialState(mat);
    }

    private final ModelIR.Material looked = new ModelIR.Material();

    private ModelIR.Material effective(int materialIndex, ModelLook draw) {
        ModelIR.Material base = materialFor(materialIndex);
        if (draw == null) {
            return base;
        }
        MaterialLook look = draw.resolve(base.name);
        ModelIR.Material out = looked;
        out.name = base.name;
        out.baseR = base.baseR * look.tintR();
        out.baseG = base.baseG * look.tintG();
        out.baseB = base.baseB * look.tintB();
        out.baseA = base.baseA * look.opacity();
        out.metallic = Float.isNaN(look.metallic()) ? base.metallic : look.metallic();
        out.roughness = Float.isNaN(look.roughness()) ? base.roughness : look.roughness();
        out.transmission = base.transmission;
        boolean ownEmission = !Float.isNaN(look.emissiveR());
        out.emR = (ownEmission ? look.emissiveR() : base.emR) * look.emissiveStrength();
        out.emG = (ownEmission ? look.emissiveG() : base.emG) * look.emissiveStrength();
        out.emB = (ownEmission ? look.emissiveB() : base.emB) * look.emissiveStrength();
        out.alphaCutoff = look.dithered() ? DITHERED : base.alphaCutoff;
        out.shadingModelId = base.shadingModelId;
        out.blend = !look.dithered() && (base.blend || out.baseA < 0.999f);
        out.doubleSided = base.doubleSided || look.doubleSided();
        out.unlit = base.unlit;
        out.baseColorFilter = look.filter() == null ? base.baseColorFilter : look.filter();
        return out;
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
            List<DrawInstance> casting = casting(part, instances);
            if (casting.isEmpty()) {
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
                drawShadowSkinned(shadow, part, casting);
            } else {
                int start = 0;
                while (start < casting.size()) {
                    int level = casting.get(start).lod;
                    int end = start + 1;
                    while (end < casting.size() && casting.get(end).lod == level) end++;
                    part.selectLod(level);
                    drawShadowBatched(part, start == 0 && end == casting.size() ? casting : casting.subList(start, end));
                    start = end;
                }
            }
            if (mat.doubleSided) {
                GlStateManager._enableCull();
            }
        }
        GlState.resetVertexArray();
        GlStateManager._activeTexture(GL13.GL_TEXTURE0);
    }

    private final ArrayList<DrawInstance> casting = new ArrayList<>();

    private List<DrawInstance> casting(GpuPart part, List<DrawInstance> instances) {
        boolean plain = true;
        for (DrawInstance inst : instances) {
            if (inst.look != null) {
                plain = false;
                break;
            }
        }
        if (plain) {
            return instances;
        }
        casting.clear();
        for (DrawInstance inst : instances) {
            Model.Piece piece = inst.look == null ? null : inst.look.piece();
            if (piece != null && !piece.shows(part.nodeIndex)) continue;
            if (inst.look != null && effective(part.materialIndex, inst.look).blend) continue;
            casting.add(inst);
        }
        return casting;
    }

    private void drawShadowBatched(GpuPart part, List<DrawInstance> instances) {
        int count = instances.size();
        packInstances(part, instances, count);
        uploadInstances(part);

        GlStateManager._glBindVertexArray(part.vao);
        DrawCounts.shadow();
        if (part.indexed) {
            if (part.drawIbo != part.ibo) GlStateManager._glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, part.drawIbo);
            GL31.glDrawElementsInstanced(GL11.GL_TRIANGLES, part.drawCount, GL11.GL_UNSIGNED_INT, 0L, count);
            if (part.drawIbo != part.ibo) GlStateManager._glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, part.ibo);
        } else {
            GL31.glDrawArraysInstanced(GL11.GL_TRIANGLES, 0, part.vertexCount, count);
        }
    }

    private void drawShadowSkinned(ModelShadowProgram shadow, GpuPart part, List<DrawInstance> instances) {
        GlStateManager._glBindVertexArray(part.vao);
        for (DrawInstance inst : instances) {
            buildPalette(part, inst.pose());
            shadow.uploadJointMatrices(palette, part.jointNodes.length);

            packSingle(placed(inst, skinTmp), inst.blockLight(), inst.skyLight());
            uploadInstances(part);
            DrawCounts.shadow();

            if (part.indexed) {
                GL31.glDrawElementsInstanced(GL11.GL_TRIANGLES, part.indexCount, GL11.GL_UNSIGNED_INT, 0L, 1);
            } else {
                GL31.glDrawArraysInstanced(GL11.GL_TRIANGLES, 0, part.vertexCount, 1);
            }
        }
    }

    public void drawFlat(FlatProgram flat, Matrix4f projView, Matrix4f world, Matrix4f[] pose, Model.Piece piece) {
        if (closed) {
            return;
        }
        ensureUploaded();
        Matrix4f model = new Matrix4f();
        Matrix4f mvp = new Matrix4f();
        Matrix4f placed = piece == null ? world : new Matrix4f(world).mul(piece.frame());
        for (GpuPart part : parts) {
            if (part == null || piece != null && !piece.shows(part.nodeIndex)) {
                continue;
            }
            Matrix4f nodeTransform = pose != null && part.nodeIndex >= 0 && part.nodeIndex < pose.length
                    ? pose[part.nodeIndex] : part.transform;
            flat.setSkinned(part.skinned);
            if (part.skinned) {
                buildPalette(part, pose);
                flat.uploadJointMatrices(palette, Math.min(part.jointNodes.length, MAX_JOINTS));
                nodeTransform = skinTmp.identity();
            }
            placed.mul(nodeTransform, model);
            projView.mul(model, mvp);
            flat.setMvp(mvp);
            GlStateManager._glBindVertexArray(part.vao);
            if (part.indexed) {
                GL11.glDrawElements(GL11.GL_TRIANGLES, part.indexCount, GL11.GL_UNSIGNED_INT, 0L);
            } else {
                GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, part.vertexCount);
            }
        }
        GlState.resetVertexArray();
    }

    private void drawBatched(ModelShader shader, GpuPart part, List<DrawInstance> instances) {
        int count = instances.size();
        shader.setSkinned(false);
        packInstances(part, instances, count);
        uploadInstances(part);

        GlStateManager._glBindVertexArray(part.vao);
        DrawCounts.draw(count, part.indexed ? part.drawCount : part.vertexCount, part.drawLevel);
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
        ensurePaletteScratch(matrices * 2);
        paletteScratch.clear();
        for (int k = 0; k < count; k++) {
            DrawInstance inst = instances.get(k);
            joints(part, inst.pose(), k * jointCount, jointCount);
            joints(part, inst.lastPose, matrices + k * jointCount, jointCount);
        }
        paletteScratch.position(0).limit(matrices * 128);
        uploadJointTbo(paletteScratch);

        shader.setSkinned(true);
        shader.setJointCount(jointCount);
        shader.setPrevJointBase(matrices);
        bindJointTbo();

        packInstancesWorldOnly(instances, count);
        uploadInstances(part);

        GlStateManager._glBindVertexArray(part.vao);
        DrawCounts.draw(count, part.indexed ? part.indexCount : part.vertexCount, 0);
        if (part.indexed) {
            GL31.glDrawElementsInstanced(GL11.GL_TRIANGLES, part.indexCount, GL11.GL_UNSIGNED_INT, 0L, count);
        } else {
            GL31.glDrawArraysInstanced(GL11.GL_TRIANGLES, 0, part.vertexCount, count);
        }
    }

    private void joints(GpuPart part, Matrix4f[] pose, int base, int jointCount) {
        if (pose == null) pose = rest();
        for (int j = 0; j < jointCount; j++) {
            int node = part.jointNodes[j];
            if (node >= 0 && node < pose.length) {
                pose[node].mul(part.inverseBind[j], skinTmp);
            } else {
                skinTmp.identity();
            }
            skinTmp.get((base + j) * 64, paletteScratch);
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
        if (pose == null) pose = rest();
        for (int j = 0; j < count; j++) {
            int node = part.jointNodes[j];
            if (node >= 0 && node < pose.length) {
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
        world.get(80, instanceScratch);
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
            placed(inst, tmp).mul(nodeTransform);
            tmp.get(base, instanceScratch);
            placedBefore(inst, tmp).mul(nodeTransform(inst.lastPose, part)).get(base + 80, instanceScratch);
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
            placed(inst, skinTmp).get(base, instanceScratch);
            placedBefore(inst, skinTmp).get(base + 80, instanceScratch);
            instanceScratch.position(base + 64);
            instanceScratch.putFloat(inst.blockLight());
            instanceScratch.putFloat(inst.skyLight());
            instanceScratch.putFloat(0f);
            instanceScratch.putFloat(0f);
        }
        instanceScratch.position(0).limit(needed);
    }

    private static Matrix4f placed(DrawInstance inst, Matrix4f into) {
        Model.Piece piece = inst.look == null ? null : inst.look.piece();
        return piece == null ? into.set(inst.world) : inst.world.mul(piece.frame(), into);
    }

    private static Matrix4f placedBefore(DrawInstance inst, Matrix4f into) {
        Model.Piece piece = inst.look == null ? null : inst.look.piece();
        return piece == null ? into.set(inst.lastWorld) : inst.lastWorld.mul(piece.frame(), into);
    }

    private Matrix4f nodeTransform(DrawInstance inst, GpuPart part) {
        return nodeTransform(inst.pose(), part);
    }

    private Matrix4f nodeTransform(Matrix4f[] pose, GpuPart part) {
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

    private Map<Integer, Integer> nodeShading;

    public void setNodeShading(Map<Integer, Integer> byNode) {
        nodeShading = byNode == null || byNode.isEmpty() ? null : Map.copyOf(byNode);
    }

    private int shadingOverride(int nodeIndex) {
        if (nodeShading == null || nodeIndex < 0) {
            return -1;
        }
        Integer id = nodeShading.get(nodeIndex);
        return id == null ? -1 : id;
    }

    public ModelIR internalIr() {
        return ir;
    }

    public boolean hasEmissiveMaterial() {
        for (ModelIR.Material mat : ir.materials()) {
            if (mat.emR > 0f || mat.emG > 0f || mat.emB > 0f) return true;
        }
        return false;
    }

    public boolean mayEmit(List<Model.Draw> draws) {
        if (hasEmissiveMaterial() || nodeShading != null) return true;
        for (ModelIR.Material mat : ir.materials()) {
            if (mat.shadingModelId != 0) return true;
        }
        for (Model.Draw draw : draws) {
            if (draw.look() != null && draw.look().emits()) return true;
        }
        return false;
    }

    private void bindMaterial(ModelShader shader, int materialIndex, ModelIR.Material mat, MaterialLook look) {
        resolveTextures(materialIndex, materialFor(materialIndex));

        boolean ownBase = look != null && look.baseColorGlTexture() != 0;
        boolean hasBase = ownBase ? bindOwn(look.baseColorGlTexture(), 0) : bind(baseColor, materialIndex, 0);
        int ownNormal = look == null ? 0 : look.normalGlTexture();
        int ownEmissive = look == null ? 0 : look.emissiveGlTexture();
        boolean hasNormal = ownNormal != 0 ? bindOwn(ownNormal, 1) : bind(normal, materialIndex, 1);
        boolean hasOrm = bind(orm, materialIndex, 2);
        boolean hasEmissive = ownEmissive != 0 ? bindOwn(ownEmissive, 3) : bind(emissive, materialIndex, 3);
        boolean hasRoughness = look != null && bindOwn(look.roughnessGlTexture(), ModelShader.ROUGHNESS_UNIT);
        boolean hasMetallic = look != null && bindOwn(look.metallicGlTexture(), ModelShader.METALLIC_UNIT);
        boolean hasOcclusion = look != null && bindOwn(look.occlusionGlTexture(), ModelShader.OCCLUSION_UNIT);

        TextureFilter asked = look == null ? null : look.filter();
        int sampler = 0;
        if (ownBase) {
            sampler = sampler(asked == TextureFilter.NEAREST ? GL11.GL_NEAREST : GL11.GL_LINEAR,
                    asked == TextureFilter.NEAREST ? GL11.GL_NEAREST : GL11.GL_LINEAR);
            GL33.glBindSampler(0, sampler);
            sampler = 0;
        }
        if (mat.baseColorFilter == TextureFilter.NEAREST) {
            sampler = sampler(GL11.GL_NEAREST_MIPMAP_LINEAR, GL11.GL_NEAREST);
        } else if (asked == TextureFilter.LINEAR) {
            sampler = sampler(GL11.GL_LINEAR_MIPMAP_LINEAR, GL11.GL_LINEAR);
        }
        for (int unit : ModelShader.MATERIAL_UNITS) {
            if (unit != 0 || !ownBase) GL33.glBindSampler(unit, sampler);
        }

        shader.uploadMaterial(mat, hasBase, hasNormal, hasOrm, hasEmissive);
        shader.uploadAlbedoEncoded(ownBase);
        shader.uploadOwnMaps(ownEmissive != 0, hasRoughness, hasMetallic, hasOcclusion);
    }

    private static boolean bindOwn(int glId, int unit) {
        if (glId == 0) return false;
        if (unit >= TRACKED_UNITS) {
            GL13.glActiveTexture(GL13.GL_TEXTURE0 + unit);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, glId);
            GL13.glActiveTexture(GL13.GL_TEXTURE0);
            GlStateManager._activeTexture(GL13.GL_TEXTURE0);
            return true;
        }
        GlStateManager._activeTexture(GL13.GL_TEXTURE0 + unit);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, glId);
        GlStateManager._bindTexture(glId);
        return true;
    }

    private static final int TRACKED_UNITS = 12;

    private final Map<Integer, Integer> samplers = new HashMap<>();

    private int sampler(int min, int mag) {
        return samplers.computeIfAbsent(min * 31 + mag, ignored -> {
            int made = GL33.glGenSamplers();
            GL33.glSamplerParameteri(made, GL11.GL_TEXTURE_MIN_FILTER, min);
            GL33.glSamplerParameteri(made, GL11.GL_TEXTURE_MAG_FILTER, mag);
            return made;
        });
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

    private void clearSamplerState() {
        for (int unit : ModelShader.MATERIAL_UNITS) {
            GL33.glBindSampler(unit, 0);
        }
    }

    private void applyMaterialState(ModelIR.Material mat) {
        if (mat.blend) {
            GlStateManager._enableBlend(0);
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
            GlStateManager._disableBlend(0);
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
                : resolve(mat.baseColorImageBytes, mat.baseColorImageKey, mat.baseColorTexture, true, mat.baseColorFilter);
        normal[materialIndex] = resolve(mat.normalImageBytes, mat.normalImageKey, mat.normalTexture, false, TextureFilter.LINEAR);
        orm[materialIndex] = resolve(mat.ormImageBytes, mat.ormImageKey, mat.ormTexture, false, TextureFilter.LINEAR);
        emissive[materialIndex] = mat.emissiveGlId != 0
                ? ModelTexture.external(mat.emissiveGlId)
                : resolve(mat.emissiveImageBytes, mat.emissiveImageKey, mat.emissiveTexture, true, TextureFilter.LINEAR);
    }

    private ModelTexture resolve(byte[] bytes, String key, Identifier id, boolean srgb, TextureFilter filter) {
        if (bytes != null) {
            return ModelTexture.fromBytes(bytes, srgb);
        }
        if (key != null && ir.textures() != null) {
            return ModelTexture.fromStore(ir.textures(), key, srgb, filter);
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
                mat.emissiveImageBytes, mat.emissiveGlId != 0 ? mat.emissiveGlId : mat.emissiveTexture,
                mat.baseColorImageKey, mat.normalImageKey, mat.ormImageKey, mat.emissiveImageKey);
    }

    private record TextureKey(byte[] baseBytes, Object baseId, byte[] normalBytes, Object normalId,
                              byte[] ormBytes, Object ormId, byte[] emissiveBytes, Object emissiveId,
                              String baseKey, String normalKey, String ormKey, String emissiveKey) {
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
        List<Integer> ownSamplers = List.copyOf(samplers.values());
        samplers.clear();
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
            for (int sampler : ownSamplers) {
                GL33.glDeleteSamplers(sampler);
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
        int colorVbo;
        boolean colored;
        int instanceVbo;
        int vertexCount;
        int indexCount;
        int instanceCapacity = 64;
        boolean indexed;
        boolean skinned;
        Vector3f middle;
        int materialIndex;
        int nodeIndex;
        int[] jointNodes;
        Matrix4f[] inverseBind;
        final Matrix4f transform;

        ModelIR.Part src;
        int drawIbo;
        int drawCount;
        int drawLevel;
        int[] lodIbo;
        int[] lodCount;

        private GpuPart(Matrix4f transform) {
            this.transform = transform;
        }

        Vector3f middle() {
            if (middle == null) {
                Vector3f min = new Vector3f(Float.MAX_VALUE);
                Vector3f max = new Vector3f(-Float.MAX_VALUE);
                Vector3f v = new Vector3f();
                float[] verts = src.vertices;
                for (int i = 0; i + 2 < verts.length; i += ModelIR.VERTEX_STRIDE_FLOATS) {
                    min.min(v.set(verts[i], verts[i + 1], verts[i + 2]));
                    max.max(v);
                }
                middle = min.x > max.x ? new Vector3f() : min.add(max).mul(0.5f);
            }
            return middle;
        }

        void selectLod(int lod) {
            drawLevel = 0;
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
            int base = lod;
            while (base > 0 && lods[base] == lods[base - 1]) base--;
            if (lodIbo[base] == 0) {
                int[] idx = lods[base];
                GlState.resetVertexArray();
                int b = GlStateManager._glGenBuffers();
                GlStateManager._glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, b);
                ByteBuffer buf = MemoryUtil.memAlloc(idx.length * Integer.BYTES);
                buf.asIntBuffer().put(idx);
                GL15.glBufferData(GL15.GL_ELEMENT_ARRAY_BUFFER, buf, GL15.GL_STATIC_DRAW);
                MemoryUtil.memFree(buf);
                GlStateManager._glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, 0);
                lodIbo[base] = b;
                lodCount[base] = idx.length;
            }
            drawIbo = lodIbo[base];
            drawCount = lodCount[base];
            drawLevel = base;
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
            p.colored = part.hasColors();
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

            if (p.colored) {
                p.colorVbo = GlStateManager._glGenBuffers();
                GlStateManager._glBindBuffer(GL15.GL_ARRAY_BUFFER, p.colorVbo);
                ByteBuffer cb = MemoryUtil.memAlloc(part.colors.length * Float.BYTES);
                cb.asFloatBuffer().put(part.colors);
                GL15.glBufferData(GL15.GL_ARRAY_BUFFER, cb, GL15.GL_STATIC_DRAW);
                MemoryUtil.memFree(cb);
                GlStateManager._vertexAttribPointer(15, 4, GL11.GL_FLOAT, false, 4 * Float.BYTES, 0L);
                GlStateManager._enableVertexAttribArray(15);
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
            for (int i = 0; i < 4; i++) {
                GlStateManager._vertexAttribPointer(11 + i, 4, GL11.GL_FLOAT, false, INSTANCE_STRIDE, 80L + i * 16L);
                GlStateManager._enableVertexAttribArray(11 + i);
                GL33.glVertexAttribDivisor(11 + i, 1);
            }

            GlStateManager._glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
            GlState.resetVertexArray();
            p.drawIbo = p.ibo;
            p.drawCount = p.indexCount;
            return p;
        }

        void deleteNow() {
            if (lodIbo != null) {
                for (int n = 1; n < lodIbo.length; n++) {
                    if (lodIbo[n] != 0 && lodIbo[n] != ibo) GlStateManager._glDeleteBuffers(lodIbo[n]);
                }
            }
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
            if (colorVbo != 0) {
                GlStateManager._glDeleteBuffers(colorVbo);
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

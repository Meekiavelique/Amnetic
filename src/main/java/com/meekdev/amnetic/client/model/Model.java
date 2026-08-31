package com.meekdev.amnetic.client.model;

import java.util.HashMap;
import java.util.LinkedHashMap;
import org.joml.Vector3fc;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.meekdev.amnetic.client.dev.ShaderHotReload;
import com.meekdev.amnetic.client.model.internal.FlatProgram;
import com.meekdev.amnetic.client.material.ShadingModel;
import com.meekdev.amnetic.client.model.internal.GpuModel;
import com.meekdev.amnetic.client.model.internal.ModelIR;
import com.meekdev.amnetic.client.model.internal.ModelLod;
import com.meekdev.amnetic.client.model.internal.ModelRegistry;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Quaternionfc;
import org.joml.Vector3f;

public final class Model {

    public record Draw(Matrix4f world, Matrix4f[] pose,
                       float blockLight, float skyLight, float emissive) {

        public Draw(Matrix4f world, Matrix4f[] pose) {
            this(world, pose, Float.NaN, Float.NaN, Float.NaN);
        }
    }

    private volatile GpuModel gpu;
    private volatile ModelIR ir;
    private final String name;
    private final ModelConfig config;
    private final List<ModelMaterial> materials = new ArrayList<>();
    private final List<Draw> pending = new ArrayList<>();
    private List<Draw> lastFrameDraws = List.of();
    private final Vector3f boundsMin = new Vector3f();
    private final Vector3f boundsMax = new Vector3f();
    private final Vector3f center = new Vector3f();
    private float radius;
    private boolean boundsReady;
    private volatile boolean ready = true;
    private boolean disposed;

    private static final FlatProgram FLAT = new FlatProgram();

    static {
        // dev hot reload for the flat fallback shader
        ShaderHotReload.onReload(FLAT::invalidate);
    }

    public Model(ModelIR ir, ModelConfig config) {
        this.gpu = new GpuModel(ir);
        this.ir = ir;
        this.name = ir.name();
        this.config = config;
        for (ModelIR.Material m : ir.materials()) {
            materials.add(new ModelMaterial(m));
        }
    }

    /** a model whose backing .ammesh hasn't finished converting/loading yet. render(...) is safe to
     *  call on it until {@link #completeLoad(ModelIR)} swaps in the real GPU data, the render loop
     *  just skips it silently */
    private Model(String name) {
        this.gpu = null;
        this.ir = null;
        this.name = name;
        this.config = new ModelConfig();
        this.ready = false;
    }

    public static Model pending(String name) {
        return new Model(name);
    }

    private volatile ModelIR pendingUpload;

    /** called from a background conversion thread once the .ammesh bytes are ready. only stores the
     *  IR, the actual GPU upload must happen on the render thread since GL calls from another thread
     *  can contend and stall the frame. drained once per frame by ModelRegistry via
     *  {@link #internalUploadPendingIfAny()} */
    public void completeLoad(ModelIR loadedIr) {
        if (ready) return;
        pendingUpload = loadedIr;
    }

    private boolean lodDone;

    /** generates LODs once if this model opted in via {@link ModelConfig#lod}. called from the render
     *  loop so the consumer can set the config first. generation itself is off-thread */
    public void internalEnsureLod() {
        if (lodDone) {
            return;
        }
        ModelIR local = ir;
        if (local == null || !config.isLod()) {
            return;
        }
        lodDone = true;
        ModelLod.generate(local);
    }

    /** render thread only. if a background conversion finished since last frame, do the GPU upload
     *  now and flip this model ready. cheap no-op otherwise (single volatile read) */
    public synchronized void internalUploadPendingIfAny() {
        ModelIR loadedIr = pendingUpload;
        if (loadedIr == null || ready) return;
        pendingUpload = null;
        this.gpu = new GpuModel(loadedIr);
        this.ir = loadedIr;
        for (ModelIR.Material m : loadedIr.materials()) {
            materials.add(new ModelMaterial(m));
        }
        this.ready = true;
    }

    public boolean isReady() {
        return ready;
    }

    public String name() {
        return name;
    }

    public ModelConfig config() {
        return config;
    }

    public boolean isAnimated() {
        return ir != null && ir.hasAnimations();
    }

    public boolean hasBones() {
        return ir != null && ir.nodes().size() > 1;
    }

    public List<String> boneNames() {
        if (ir == null) {
            return List.of();
        }
        List<String> names = new ArrayList<>(ir.nodes().size());
        for (var node : ir.nodes()) {
            names.add(node.name);
        }
        return List.copyOf(names);
    }

    public List<String> clipNames() {
        if (ir == null) {
            return List.of();
        }
        List<String> names = new ArrayList<>(ir.animations().size());
        for (var animation : ir.animations()) {
            names.add(animation.name);
        }
        return List.copyOf(names);
    }

    public String firstClip() {
        if (ir == null || ir.animations().isEmpty()) {
            return null;
        }
        return ir.animations().get(0).name;
    }

    public Model render(Matrix4fc worldTransform) {
        if (!disposed) {
            pending.add(new Draw(new Matrix4f(worldTransform), null));
        }
        return this;
    }

    public Model render(Matrix4fc worldTransform, float blockLight, float skyLight, float emissive) {
        if (!disposed) {
            pending.add(new Draw(new Matrix4f(worldTransform), null, blockLight, skyLight, emissive));
        }
        return this;
    }

    public Model renderPosed(Matrix4fc worldTransform, Matrix4f[] pose,
                             float blockLight, float skyLight, float emissive) {
        if (!disposed) {
            pending.add(new Draw(new Matrix4f(worldTransform), pose, blockLight, skyLight, emissive));
        }
        return this;
    }

    public Model render(Vec3 worldPos, float yawDegrees, float scale) {
        return render(new Matrix4f()
                .translation((float) worldPos.x, (float) worldPos.y, (float) worldPos.z)
                .rotateY((float) Math.toRadians(yawDegrees))
                .scale(scale));
    }

    public Model render(Vec3 worldPos, float scale) {
        return render(new Matrix4f()
                .translation((float) worldPos.x, (float) worldPos.y, (float) worldPos.z)
                .scale(scale));
    }

    public Model render(Vec3 worldPos, Quaternionfc rotation, float scale) {
        return render(new Matrix4f()
                .translation((float) worldPos.x, (float) worldPos.y, (float) worldPos.z)
                .rotate(rotation)
                .scale(scale));
    }

    public Model renderInstanced(Matrix4fc... worldTransforms) {
        for (Matrix4fc t : worldTransforms) {
            render(t);
        }
        return this;
    }

    public Model renderPosed(Matrix4fc worldTransform, Matrix4f[] pose) {
        if (!disposed) {
            pending.add(new Draw(new Matrix4f(worldTransform), pose));
        }
        return this;
    }

    private final Map<String, ShadingModel> boneShading = new LinkedHashMap<>();

    public Model boneShading(String bone, ShadingModel shading) {
        if (bone == null || bone.isBlank()) {
            return this;
        }
        if (shading == null) {
            boneShading.remove(bone);
        } else {
            boneShading.put(bone, shading);
        }
        rebuildBoneShading();
        return this;
    }

    public Model clearBoneShading() {
        boneShading.clear();
        rebuildBoneShading();
        return this;
    }

    public Map<String, ShadingModel> boneShadingView() {
        return Map.copyOf(boneShading);
    }

    private void rebuildBoneShading() {
        GpuModel target = gpu;
        ModelIR source = ir;
        if (target == null || source == null) {
            return;
        }
        Map<Integer, Integer> byNode = new HashMap<>();
        for (Map.Entry<String, ShadingModel> entry : boneShading.entrySet()) {
            int index = nodeIndex(entry.getKey());
            if (index >= 0) {
                spread(source, index, entry.getValue().id(), byNode);
            }
        }
        target.setNodeShading(byNode);
    }

    public int nodeIndex(String name) {
        ModelIR source = ir;
        if (source == null || name == null) {
            return -1;
        }
        for (int i = 0; i < source.nodes().size(); i++) {
            if (name.equals(source.nodes().get(i).name)) {
                return i;
            }
        }
        return -1;
    }

    private static void spread(ModelIR source, int node,
                               int id, Map<Integer, Integer> out) {
        if (node < 0 || node >= source.nodes().size() || out.containsKey(node)) {
            return;
        }
        out.put(node, id);
        for (int child : source.nodes().get(node).children) {
            spread(source, child, id, out);
        }
    }

    public record BoneRotation(Vector3f axis, float degrees) {
    }

    private final Map<String, BoneRotation> boneRotations =
            new LinkedHashMap<>();

    public Model boneRotation(String bone, float axisX, float axisY, float axisZ, float degrees) {
        if (bone == null || bone.isBlank()) {
            return this;
        }
        BoneRotation current = boneRotations.get(bone);
        if (current != null && current.degrees() == degrees
                && current.axis().x == axisX && current.axis().y == axisY
                && current.axis().z == axisZ) {
            return this;
        }
        boneRotations.put(bone, new BoneRotation(new Vector3f(axisX, axisY, axisZ), degrees));
        return this;
    }

    public Model boneRotation(String bone, Vector3fc axis, float degrees) {
        return axis == null ? this
                : boneRotation(bone, axis.x(), axis.y(), axis.z(), degrees);
    }

    public Model clearBoneRotation(String bone) {
        boneRotations.remove(bone);
        return this;
    }

    public Model clearBoneRotations() {
        boneRotations.clear();
        return this;
    }

    public Map<String, BoneRotation> boneRotationView() {
        return Map.copyOf(boneRotations);
    }

    public boolean internalHasBoneRotations() {
        return !boneRotations.isEmpty();
    }

    public void internalApplyBoneRotations(Animator animator) {
        animator.clearSpins();
        for (Map.Entry<String, BoneRotation> entry : boneRotations.entrySet()) {
            int node = nodeIndex(entry.getKey());
            if (node < 0) {
                continue;
            }
            BoneRotation rotation = entry.getValue();
            animator.spin(node, rotation.axis().x, rotation.axis().y, rotation.axis().z,
                    rotation.degrees());
        }
    }

    public ModelMaterial material(String name) {
        for (ModelMaterial m : materials) {
            if (m.name().equals(name)) {
                return m;
            }
        }
        return null;
    }

    public List<ModelMaterial> materials() {
        return materials;
    }

    public Animator createAnimator() {
        return new Animator(this, ir);
    }

    public void fillMask(Matrix4fc projView, Matrix4fc world, Matrix4f[] pose, float r, float g, float b, float a) {
        if (disposed) {
            return;
        }
        FLAT.bind();
        FLAT.setColor(r, g, b, a);
        gpu.drawFlat(FLAT, new Matrix4f(projView), new Matrix4f(world), pose);
        FLAT.unbind();
    }

    public static void disposeShared() {
        FLAT.close();
    }

    public Vector3f center() {
        ensureBounds();
        return center;
    }

    public float radius() {
        ensureBounds();
        return radius;
    }

    public Vector3f boundsMin() {
        ensureBounds();
        return boundsMin;
    }

    public Vector3f boundsMax() {
        ensureBounds();
        return boundsMax;
    }

    private static final float[] NO_BONE_GEOMETRY = new float[0];
    private final Map<Integer, float[]> boneBoundsCache =
            new ConcurrentHashMap<>();

    public boolean boneBounds(int node, Vector3f min, Vector3f max) {
        ModelIR local = ir;
        if (local == null || node < 0) {
            return false;
        }
        float[] cached = boneBoundsCache.get(node);
        if (cached == null) {
            float minX = Float.MAX_VALUE;
            float minY = Float.MAX_VALUE;
            float minZ = Float.MAX_VALUE;
            float maxX = -Float.MAX_VALUE;
            float maxY = -Float.MAX_VALUE;
            float maxZ = -Float.MAX_VALUE;
            int stride = ModelIR.VERTEX_STRIDE_FLOATS;
            boolean any = false;
            for (ModelIR.Part part : local.parts()) {
                if (part.nodeIndex != node) {
                    continue;
                }
                float[] verts = part.vertices;
                for (int i = 0; i + 2 < verts.length; i += stride) {
                    any = true;
                    minX = Math.min(minX, verts[i]);
                    minY = Math.min(minY, verts[i + 1]);
                    minZ = Math.min(minZ, verts[i + 2]);
                    maxX = Math.max(maxX, verts[i]);
                    maxY = Math.max(maxY, verts[i + 1]);
                    maxZ = Math.max(maxZ, verts[i + 2]);
                }
            }
            cached = any
                    ? new float[] {minX, minY, minZ, maxX, maxY, maxZ}
                    : NO_BONE_GEOMETRY;
            boneBoundsCache.put(node, cached);
        }
        if (cached.length == 0) {
            return false;
        }
        min.set(cached[0], cached[1], cached[2]);
        max.set(cached[3], cached[4], cached[5]);
        return true;
    }

    private void ensureBounds() {
        if (boundsReady) {
            return;
        }
        if (ir == null) {
            return; // not loaded yet, retry once completeLoad() runs
        }
        boundsReady = true;
        float minX = Float.MAX_VALUE;
        float minY = Float.MAX_VALUE;
        float minZ = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE;
        float maxY = -Float.MAX_VALUE;
        float maxZ = -Float.MAX_VALUE;
        Vector3f v = new Vector3f();
        int stride = ModelIR.VERTEX_STRIDE_FLOATS;
        for (ModelIR.Part part : ir.parts()) {
            float[] verts = part.vertices;
            for (int i = 0; i < verts.length; i += stride) {
                v.set(verts[i], verts[i + 1], verts[i + 2]);
                part.transform.transformPosition(v);
                minX = Math.min(minX, v.x);
                minY = Math.min(minY, v.y);
                minZ = Math.min(minZ, v.z);
                maxX = Math.max(maxX, v.x);
                maxY = Math.max(maxY, v.y);
                maxZ = Math.max(maxZ, v.z);
            }
        }
        if (minX > maxX) {
            minX = minY = minZ = 0f;
            maxX = maxY = maxZ = 0f;
        }
        boundsMin.set(minX, minY, minZ);
        boundsMax.set(maxX, maxY, maxZ);
        center.set((minX + maxX) * 0.5f, (minY + maxY) * 0.5f, (minZ + maxZ) * 0.5f);
        radius = Math.max(1e-3f, boundsMax.distance(center));
    }

    public void dispose() {
        if (!disposed) {
            ModelRegistry.INSTANCE.release(this);
        }
    }

    public void localBounds(org.joml.Vector3f outMin, org.joml.Vector3f outMax) {
        internalGpu().localBounds(outMin, outMax);
    }

    public void tightBounds(org.joml.Vector3f outMin, org.joml.Vector3f outMax) {
        internalGpu().tightBounds(outMin, outMax);
    }

    public GpuModel internalGpu() {
        return gpu;
    }

    public ModelConfig internalConfig() {
        return config;
    }

    public boolean internalHasPending() {
        return !pending.isEmpty();
    }

    public List<Draw> internalPending() {
        return pending;
    }

    public void internalClearPending() {
        lastFrameDraws = pending.isEmpty() ? List.of() : List.copyOf(pending);
        pending.clear();
    }

    public List<Draw> internalLastFrameDraws() {
        return lastFrameDraws;
    }

    public void internalDispose() {
        if (disposed) {
            return;
        }
        disposed = true;
        pending.clear();
        gpu.close();
    }
}

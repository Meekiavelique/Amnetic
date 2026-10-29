package com.meekdev.amnetic.client.model;

import java.util.HashMap;
import java.util.LinkedHashMap;
import org.joml.Vector3fc;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

import com.meekdev.amnetic.client.dev.ShaderHotReload;
import com.meekdev.amnetic.client.model.internal.FlatProgram;
import com.meekdev.amnetic.client.material.ShadingModel;
import com.meekdev.amnetic.client.material.SurfaceInputs;
import com.meekdev.amnetic.client.model.internal.GpuModel;
import com.meekdev.amnetic.client.model.internal.ModelIR;
import com.meekdev.amnetic.client.model.internal.ModelLod;
import com.meekdev.amnetic.client.model.internal.ModelRegistry;
import com.meekdev.amnetic.client.model.internal.Skinning;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Quaternionf;
import org.joml.Quaternionfc;
import org.joml.Vector3f;

public final class Model {

    /** lodPixels is how far on screen, in pixels, a coarser level may stray from the full model; 0 always draws
     *  the full one, NaN leaves it to {@link ModelConfig#lod} */
    public record Draw(Matrix4f world, Matrix4f[] pose,
                       float blockLight, float skyLight, float emissive, SurfaceInputs inputs, ModelLook look, Object key,
                       float lodPixels) {

        public Draw(Matrix4f world, Matrix4f[] pose) {
            this(world, pose, Float.NaN, Float.NaN, Float.NaN, null, null, null, Float.NaN);
        }

        public Draw(Matrix4f world, Matrix4f[] pose, float blockLight, float skyLight, float emissive) {
            this(world, pose, blockLight, skyLight, emissive, null, null, null, Float.NaN);
        }
    }

    public static final class Moved {
        public final Matrix4f world = new Matrix4f();
        public final Matrix4f lastWorld = new Matrix4f();
        public Matrix4f[] pose;
        public Matrix4f[] lastPose;
        public long frame = Long.MIN_VALUE;
        // the level of detail it was drawn at, so it only goes coarser once it is well inside the next level
        public int lod = -1;
    }

    /**
     * one node of the file with everything under it, drawn in the node's own frame: its position and
     * turn in the file are taken off, its scale is kept
     */
    public record Piece(int node, boolean[] nodes, Matrix4f frame, Vector3f min, Vector3f max) {

        public boolean shows(int nodeIndex) {
            return nodeIndex >= 0 && nodeIndex < nodes.length && nodes[nodeIndex];
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
        for (ModelIR.Part part : local.parts()) {
            // a processed model came with its levels
            if (part.lods != null) return;
        }
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

    // true when some part bends with a skin instead of following one node
    public boolean isSkinned() {
        if (ir == null) {
            return false;
        }
        for (ModelIR.Part part : ir.parts()) {
            if (part.hasSkin()) {
                return true;
            }
        }
        return false;
    }

    // seconds a clip lasts, 0 when there is no such clip
    public float clipLength(String clip) {
        ModelIR.Animation anim = ir == null || clip == null ? null : ir.animation(clip);
        return anim == null ? 0f : anim.duration;
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

    /** draws it with values for its surface snippets, see {@link ShadingModel#surface(String)} */
    public Model render(Matrix4fc worldTransform, Matrix4f[] pose, SurfaceInputs inputs) {
        return render(worldTransform, pose, inputs, null);
    }

    /** draws it with its own look, see {@link ModelLook}. the look is read when the frame is drawn */
    public Model render(Matrix4fc worldTransform, Matrix4f[] pose, SurfaceInputs inputs, ModelLook look) {
        return render(worldTransform, pose, inputs, look, null);
    }

    /** draws it as the thing key stands for, so the frame can tell how far that thing moved since the last one */
    public Model render(Matrix4fc worldTransform, Matrix4f[] pose, SurfaceInputs inputs, ModelLook look, Object key) {
        return render(worldTransform, pose, inputs, look, key, Float.NaN);
    }

    /** draws it at the coarsest level of detail that strays at most lodPixels from the full model on screen,
     *  see {@link Draw}. a skinned part always draws in full */
    public Model render(Matrix4fc worldTransform, Matrix4f[] pose, SurfaceInputs inputs, ModelLook look, Object key, float lodPixels) {
        if (!disposed) {
            pending.add(new Draw(new Matrix4f(worldTransform), pose, Float.NaN, Float.NaN, Float.NaN, inputs, look, key, lodPixels));
        }
        return this;
    }

    private float[] lodErrors;
    private int lodParts = -1;

    // the worst error of each level, null when it only has the full one
    public float[] internalLodErrors() {
        return lodErrors();
    }

    // how many levels of detail it has, 1 when it only has the full one
    public int lodLevels() {
        float[] errors = lodErrors();
        return errors == null ? 1 : errors.length;
    }

    // how far level strays from the full model at worst, in the model's own units
    public float lodError(int level) {
        float[] errors = lodErrors();
        return errors == null || level <= 0 ? 0f : errors[Math.min(level, errors.length - 1)];
    }

    // triangles drawn at level, for one copy
    public int lodTriangles(int level) {
        ModelIR source = ir;
        if (source == null) return 0;
        int total = 0;
        for (ModelIR.Part part : source.parts()) {
            int[][] lods = part.lods;
            if (lods != null && level > 0) total += lods[Math.min(level, lods.length - 1)].length / 3;
            else total += part.hasIndices() ? part.indices.length / 3 : part.vertexCount() / 3;
        }
        return total;
    }

    // the worst error of each level over every part, made again when a part gains its levels later
    private float[] lodErrors() {
        ModelIR source = ir;
        if (source == null) return null;
        int parts = 0;
        for (ModelIR.Part part : source.parts()) {
            if (part.lods != null && part.lodErrors != null) parts++;
        }
        if (parts == lodParts) return lodErrors;
        float[] errors = null;
        if (parts > 0) {
            errors = new float[ModelLod.LEVELS];
            for (ModelIR.Part part : source.parts()) {
                float[] own = part.lodErrors;
                if (part.lods == null || own == null) continue;
                Vector3f axes = part.transform.getScale(new Vector3f());
                float scale = Math.max(axes.x, Math.max(axes.y, axes.z));
                for (int n = 1; n < errors.length; n++) errors[n] = Math.max(errors[n], own[Math.min(n, own.length - 1)] * scale);
            }
        }
        lodErrors = errors;
        lodParts = parts;
        return errors;
    }

    private final Map<Object, Moved> moved = new WeakHashMap<>();

    public Moved internalMoved(Object key) {
        return moved.computeIfAbsent(key, k -> new Moved());
    }

    private final Map<String, Piece> pieces = new HashMap<>();

    /** the node called name, or else the node holding the mesh called name, or null */
    public Piece piece(String name) {
        ModelIR source = ir;
        if (source == null || name == null || name.isEmpty()) {
            return null;
        }
        Piece known = pieces.get(name);
        if (known != null) {
            return known;
        }
        int node = nodeIndex(name);
        if (node < 0) {
            for (ModelIR.Part part : source.parts()) {
                if (name.equals(part.name) && part.nodeIndex >= 0) {
                    node = part.nodeIndex;
                    break;
                }
            }
        }
        if (node < 0) {
            return null;
        }
        Piece made = cut(source, node);
        pieces.put(name, made);
        return made;
    }

    public List<String> pieceNames() {
        ModelIR source = ir;
        if (source == null) {
            return List.of();
        }
        List<String> names = new ArrayList<>();
        for (ModelIR.Node node : source.nodes()) {
            names.add(node.name);
        }
        return names;
    }

    private static Piece cut(ModelIR source, int node) {
        boolean[] nodes = new boolean[source.nodes().size()];
        mark(source, node, nodes);
        Matrix4f placed = new Matrix4f();
        for (int at = node; at >= 0; at = source.nodes().get(at).parent) {
            placed.mulLocal(source.nodes().get(at).local);
        }
        Vector3f at = placed.getTranslation(new Vector3f());
        Matrix4f frame = new Matrix4f().translationRotate(at.x, at.y, at.z,
                placed.getNormalizedRotation(new Quaternionf())).invert();
        Vector3f min = new Vector3f(Float.MAX_VALUE);
        Vector3f max = new Vector3f(-Float.MAX_VALUE);
        Matrix4f into = new Matrix4f();
        Vector3f v = new Vector3f();
        int stride = ModelIR.VERTEX_STRIDE_FLOATS;
        for (ModelIR.Part part : source.parts()) {
            if (part.nodeIndex < 0 || !nodes[part.nodeIndex]) {
                continue;
            }
            frame.mul(part.transform, into);
            float[] verts = part.vertices;
            for (int i = 0; i + 2 < verts.length; i += stride) {
                into.transformPosition(v.set(verts[i], verts[i + 1], verts[i + 2]));
                min.min(v);
                max.max(v);
            }
        }
        if (min.x > max.x) {
            min.set(0f);
            max.set(0f);
        }
        return new Piece(node, nodes, frame, min, max);
    }

    private static void mark(ModelIR source, int node, boolean[] nodes) {
        if (node < 0 || node >= nodes.length || nodes[node]) {
            return;
        }
        nodes[node] = true;
        for (int child : source.nodes().get(node).children) {
            mark(source, child, nodes);
        }
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
        fillMask(projView, world, pose, null, r, g, b, a);
    }

    public void fillMask(Matrix4fc projView, Matrix4fc world, Matrix4f[] pose, Piece piece, float r, float g, float b, float a) {
        if (disposed) {
            return;
        }
        FLAT.bind();
        FLAT.setColor(r, g, b, a);
        gpu.drawFlat(FLAT, new Matrix4f(projView), new Matrix4f(world), pose, piece);
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
        Matrix4f[] rest = null;
        for (ModelIR.Part part : ir.parts()) {
            if (part.hasSkin()) {
                // a skinned part is where its joints put it at rest, not where its node sits
                if (rest == null) rest = Skinning.restPose(ir);
                Vector3f lo = new Vector3f(minX, minY, minZ);
                Vector3f hi = new Vector3f(maxX, maxY, maxZ);
                Skinning.bounds(part, rest, lo, hi);
                minX = lo.x; minY = lo.y; minZ = lo.z;
                maxX = hi.x; maxY = hi.y; maxZ = hi.z;
                continue;
            }
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
        return !pending.isEmpty() || kept != null && kept.size() > 0;
    }

    private KeptDraws kept;

    /** draws of this model that stay where they are, drawn every frame until cleared, see {@link KeptDraws} */
    public KeptDraws kept() {
        if (kept == null) kept = new KeptDraws(this);
        return kept;
    }

    public KeptDraws internalKept() {
        return kept;
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
        if (kept != null) kept.clear();
        gpu.close();
    }
}

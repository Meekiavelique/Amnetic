package com.meekdev.amnetic.client.model;

import java.util.ArrayList;
import java.util.List;

import com.meekdev.amnetic.client.dev.ShaderHotReload;
import com.meekdev.amnetic.client.model.internal.FlatProgram;
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

    public record Draw(Matrix4f world, Matrix4f[] pose) {
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

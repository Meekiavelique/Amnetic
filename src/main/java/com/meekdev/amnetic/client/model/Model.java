package com.meekdev.amnetic.client.model;

import com.meekdev.amnetic.client.model.internal.GpuModel;
import com.meekdev.amnetic.client.model.internal.ModelIR;
import com.meekdev.amnetic.client.model.internal.ModelRegistry;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Quaternionfc;

public final class Model {

    private final GpuModel gpu;
    private final String name;
    private final List<ModelMaterial> materials = new ArrayList<>();
    private final List<Matrix4fc> pending = new ArrayList<>();
    private boolean disposed;

    public Model(ModelIR ir) {
        this.gpu = new GpuModel(ir);
        this.name = ir.name();
        for (ModelIR.Material m : ir.materials()) materials.add(new ModelMaterial(m));
    }

    public String name() { return name; }

    public Model render(Matrix4fc worldTransform) {
        if (!disposed) pending.add(new Matrix4f(worldTransform));
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
        for (Matrix4fc t : worldTransforms) render(t);
        return this;
    }

    public ModelMaterial material(String name) {
        for (ModelMaterial m : materials) if (m.name().equals(name)) return m;
        return null;
    }

    public List<ModelMaterial> materials() { return materials; }

    public Animator createAnimator() { return new Animator(this); }

    public void dispose() {
        if (disposed) return;
        ModelRegistry.INSTANCE.unregister(this);
        disposeInternal();
    }

    public GpuModel gpu() { return gpu; }
    public boolean hasPending() { return !pending.isEmpty(); }
    public List<Matrix4fc> pending() { return pending; }
    public void clearPending() { pending.clear(); }

    public void disposeInternal() {
        if (disposed) return;
        disposed = true;
        pending.clear();
        gpu.close();
    }
}

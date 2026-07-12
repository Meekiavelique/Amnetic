package com.meekdev.amnetic.client.ui.inspector;

import com.meekdev.amnetic.client.particle.ParticleMaterial;
import com.meekdev.amnetic.client.particle.ParticleSimulation;
import com.meekdev.amnetic.client.particle.Particles;
import com.meekdev.amnetic.client.particle.SpawnShape;
import com.meekdev.amnetic.client.ui.Inspector;
import com.meekdev.amnetic.client.ui.SpawnTarget;
import imgui.ImGui;
import imgui.type.ImInt;
import java.util.List;
import java.util.random.RandomGenerator;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

public final class ParticlesInspector extends Inspector {

    private static final SpawnShape SHAPE_SPHERE = (rng, off, dir) -> {
        randomUnit(rng, dir);
        off.set(0f, 0f, 0f);
    };
    private static final SpawnShape SHAPE_HEMISPHERE = (rng, off, dir) -> {
        randomUnit(rng, dir);
        if (dir.y < 0f) dir.y = -dir.y;
        off.set(0f, 0f, 0f);
    };
    private static final String[] SHAPE_NAMES = {"Sphere", "Hemisphere (up)"};
    private static final SpawnShape[] SHAPES = {SHAPE_SPHERE, SHAPE_HEMISPHERE};

    private final SpawnTarget spawn = new SpawnTarget();
    private final ImInt selected = new ImInt(0);
    private final ImInt shape = new ImInt(0);
    private final int[] count = {32};
    private final float[] speed = {1f, 3f};

    public ParticlesInspector() {
        super("Renderer", "Particles", false);
    }

    @Override
    public void render() {
        ImGui.text("Live particles: " + Particles.liveCount());
        ImGui.separator();

        List<ParticleMaterial> materials = ParticleSimulation.INSTANCE.materials();
        if (materials.isEmpty()) {
            ImGui.textWrapped("No particle materials registered by the host mod yet. ");
            return;
        }

        String[] labels = new String[materials.size()];
        for (int i = 0; i < materials.size(); i++) {
            labels[i] = i + ": " + materials.get(i).label();
        }
        if (selected.get() >= materials.size()) selected.set(0);
        ImGui.combo("Material", selected, labels);
        ParticleMaterial material = materials.get(selected.get());

        spawn.controls();
        ImGui.separator();

        if (ImGui.button("Spawn one")) {
            Vec3 p = spawn.position();
            Particles.spawn(material, p.x, p.y, p.z, 0, 0, 0);
        }

        ImGui.separator();
        ImGui.combo("Burst shape", shape, SHAPE_NAMES);
        ImGui.sliderInt("Count", count, 1, 1000);
        ImGui.dragFloat2("Speed min/max", speed, 0.05f, 0f, 64f);
        if (ImGui.button("Burst")) {
            Vec3 p = spawn.position();
            Particles.burst(material, p.x, p.y, p.z, count[0],
                    SHAPES[shape.get()], speed[0], speed[1]);
        }
    }

    private static void randomUnit(RandomGenerator rng, Vector3f out) {
        float z = rng.nextFloat() * 2f - 1f;
        float a = (float) (rng.nextFloat() * Math.PI * 2.0);
        float r = (float) Math.sqrt(Math.max(0f, 1f - z * z));
        out.set(r * (float) Math.cos(a), z, r * (float) Math.sin(a));
    }
}

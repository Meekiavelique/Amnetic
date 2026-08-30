package com.meekdev.amnetic.client.light.internal;

import com.meekdev.amnetic.client.compute.ShaderStorageBuffer;
import com.meekdev.amnetic.client.light.Light;
import com.meekdev.amnetic.client.light.LightSettings;
import com.meekdev.amnetic.client.light.LightType;
import net.minecraft.world.phys.Vec3;
import org.joml.FrustumIntersection;
import org.lwjgl.BufferUtils;

import java.nio.FloatBuffer;
import java.util.List;

public final class LightBuffer implements AutoCloseable {

    public static final int FLOATS_PER_LIGHT = 32; // 8 vec4 lanes

    private final int maxLights;
    private final ShaderStorageBuffer ssbo;
    private final FloatBuffer scratch;

    public LightBuffer(int maxLights) {
        this.maxLights = maxLights;
        this.ssbo = new ShaderStorageBuffer((long) maxLights * FLOATS_PER_LIGHT * Float.BYTES);
        this.scratch = BufferUtils.createFloatBuffer(maxLights * FLOATS_PER_LIGHT);
    }

    public int pack(List<Light> lights, Vec3 camPos, FrustumIntersection frustum) {
        LightSettings s = LightSettings.defaults();
        boolean cull = s.frustumCull();
        float fadeStart = s.lodFadeStart();
        float fadeEnd = s.lodFadeEnd();

        scratch.clear();
        int count = 0;
        for (Light l : lights) {
            if (!l.isEnabled() || count >= maxLights) continue;

            float rx = (float) (l.x() - camPos.x);
            float ry = (float) (l.y() - camPos.y);
            float rz = (float) (l.z() - camPos.z);
            boolean infinite = l.type() == LightType.DIRECTIONAL;

            float fade = 1f;
            if (!infinite) {
                if (cull && frustum != null && !frustum.testSphere(rx, ry, rz, l.range())) continue;
                float dist = (float) Math.sqrt(rx * rx + ry * ry + rz * rz);
                float edge = dist - l.range();
                if (edge > fadeEnd) continue;
                if (edge > fadeStart) fade = 1f - (edge - fadeStart) / (fadeEnd - fadeStart);
            }

            scratch.put(rx).put(ry).put(rz).put(l.range());
            scratch.put(l.red()).put(l.green()).put(l.blue()).put(l.intensity() * fade);
            scratch.put(l.dirX()).put(l.dirY()).put(l.dirZ()).put((float) l.type().id());
            scratch.put(l.cosInner()).put(l.cosOuter()).put((float) l.falloffId()).put(l.falloffParam());
            scratch.put(l.areaW()).put(l.areaH()).put(l.tubeLen()).put(l.shadowStrength());
            scratch.put(l.tanX()).put(l.tanY()).put(l.tanZ()).put((float) l.shadowRef());
            scratch.put(l.cookie() ? 1f : 0f).put((float) l.iesProfile()).put(l.godray()).put((float) l.style());
            scratch.put((float) l.godraySteps()).put(l.godrayDensity()).put(l.godrayAniso()).put(l.godrayShadows() ? 1f : 0f);
            count++;
        }
        scratch.flip();
        if (count > 0) ssbo.upload(scratch);
        return count;
    }

    public void bind(int binding) {
        ssbo.bind(binding);
    }

    @Override
    public void close() {
        ssbo.close();
    }
}

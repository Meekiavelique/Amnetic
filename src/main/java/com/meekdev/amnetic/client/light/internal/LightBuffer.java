package com.meekdev.amnetic.client.light.internal;

import com.meekdev.amnetic.client.compat.IrisCompat;
import com.meekdev.amnetic.client.light.Light;
import com.meekdev.amnetic.client.light.LightSettings;
import com.meekdev.amnetic.client.light.LightType;
import com.meekdev.amnetic.client.render.TexelBuffer;
import net.minecraft.world.phys.Vec3;
import org.joml.FrustumIntersection;
import org.lwjgl.BufferUtils;
import java.util.function.Predicate;

import java.nio.FloatBuffer;
import java.util.List;
//? if >=26.2 {
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
//?}

public final class LightBuffer implements AutoCloseable {
    public static final int FLOATS_PER_LIGHT = 32;
    public static final int UNIT = 11;

    private final int maxLights;
    private final TexelBuffer texels;
    private final FloatBuffer scratch;
    private final FloatBuffer previous;
    private int previousCount = -1;
    private int selectionCount;

    public int selectionCount() {
        return selectionCount;
    }

    public LightBuffer(int maxLights) {
        this.maxLights = maxLights;
        this.texels = new TexelBuffer((long) maxLights * FLOATS_PER_LIGHT * Float.BYTES);
        this.scratch = BufferUtils.createFloatBuffer(maxLights * FLOATS_PER_LIGHT);
        this.previous = BufferUtils.createFloatBuffer(maxLights * FLOATS_PER_LIGHT);
    }

    public int pack(List<Light> lights, Vec3 camPos, FrustumIntersection frustum) {
        return pack(lights, camPos, frustum, null);
    }

    public int pack(List<Light> lights, Vec3 camPos, FrustumIntersection frustum, List<Light> outPacked) {
        return pack(lights, camPos, frustum, outPacked, null);
    }

    public int pack(List<Light> lights, Vec3 camPos, FrustumIntersection frustum, List<Light> outPacked,
                    Predicate<Light> filter) {
        LightSettings s = LightSettings.defaults();
        boolean cull = s.frustumCull();
        float fadeStart = s.lodFadeStart();
        float fadeEnd = s.lodFadeEnd();
        boolean localOnly = IrisCompat.compositing();

        scratch.clear();
        if (outPacked != null) outPacked.clear();
        int count = 0;
        selectionCount = 0;
        for (Light l : lights) {
            if (selectionCount >= maxLights) break;
            if (!l.isEnabled() || l.intensity() <= 0f) continue;

            float rx = (float) (l.x() - camPos.x);
            float ry = (float) (l.y() - camPos.y);
            float rz = (float) (l.z() - camPos.z);
            boolean infinite = l.type() == LightType.DIRECTIONAL;
            if (localOnly && infinite) continue;

            float fade = 1f;
            if (!infinite) {
                float reach = l.range() + Math.max(Math.max(l.areaW(), l.areaH()), l.tubeLen() * 0.5f);
                if (cull && frustum != null && !frustum.testSphere(rx, ry, rz, reach)) continue;
                float distanceSquared = rx * rx + ry * ry + rz * rz;
                float cutoff = reach + fadeEnd;
                if (distanceSquared >= cutoff * cutoff) continue;
                float edge = (float) Math.sqrt(distanceSquared) - reach;
                if (edge > fadeStart) fade = 1f - (edge - fadeStart) / (fadeEnd - fadeStart);
            }

            selectionCount++;
            if (filter != null && !filter.test(l)) continue;
            write(l, rx, ry, rz, fade, localOnly);
            if (outPacked != null) outPacked.add(l);
            count++;
        }
        scratch.flip();
        if (count > 0 && (previousCount != count || scratch.mismatch(previous) != -1)) {
            texels.upload(scratch);
            previous.clear().put(scratch.duplicate()).flip();
        }
        previousCount = count;
        return count;
    }

    private void write(Light l, float rx, float ry, float rz, float fade, boolean localOnly) {
        scratch.put(rx).put(ry).put(rz).put(l.range());
        scratch.put(l.red()).put(l.green()).put(l.blue()).put(l.intensity() * fade);
        scratch.put(l.dirX()).put(l.dirY()).put(l.dirZ()).put((float) l.type().id());
        scratch.put(l.cosInner()).put(l.cosOuter()).put((float) l.falloffId()).put(l.falloffParam());
        scratch.put(l.areaW()).put(l.areaH()).put(l.tubeLen()).put(l.shadowStrength() * l.shadowBlend());
        scratch.put(l.tanX()).put(l.tanY()).put(l.tanZ()).put(localOnly ? -1f : (float) l.shadowRef());
        scratch.put(l.cookie() ? 1f : 0f).put((float) l.iesProfile()).put(l.godray()).put((float) l.style());
        scratch.put((float) l.godraySteps()).put(l.godrayDensity()).put(l.godrayAniso()).put(l.godrayShadows() ? 1f : 0f);
    }

    public void bind() {
        texels.bind(UNIT);
    }
    //? if >=26.2 {
    public GpuBufferSlice nativeSlice() {
        return texels.nativeSlice();
    }
    //?}

    @Override
    public void close() {
        texels.close();
    }
}

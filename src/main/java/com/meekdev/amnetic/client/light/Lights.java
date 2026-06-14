package com.meekdev.amnetic.client.light;

import com.meekdev.amnetic.client.light.internal.LightRegistry;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

public final class Lights {

    private Lights() {}

    public static Light point(Vec3 pos, float r, float g, float b, float range, float intensity) {
        Light light = new Light(LightType.POINT);
        light.setPosition(pos).setColor(r, g, b).setRange(range).setIntensity(intensity);
        LightRegistry.INSTANCE.add(light);
        return light;
    }

    public static Light spot(Vec3 pos, Vector3f dir, float innerDeg, float outerDeg,
                             float r, float g, float b, float range, float intensity) {
        Light light = new Light(LightType.SPOT);
        light.setPosition(pos).setDirection(dir).setSpotAngles(innerDeg, outerDeg)
                .setColor(r, g, b).setRange(range).setIntensity(intensity);
        LightRegistry.INSTANCE.add(light);
        return light;
    }

    public static Light directional(Vector3f dir, float r, float g, float b, float intensity) {
        Light light = new Light(LightType.DIRECTIONAL);
        light.setDirection(dir).setColor(r, g, b).setIntensity(intensity);
        LightRegistry.INSTANCE.add(light);
        return light;
    }

    public static Light areaRect(Vec3 pos, Vector3f normal, float halfW, float halfH,
                                 float r, float g, float b, float range, float intensity) {
        Light light = new Light(LightType.AREA_RECT);
        light.setPosition(pos).setDirection(normal).setAreaSize(halfW, halfH)
                .setColor(r, g, b).setRange(range).setIntensity(intensity);
        LightRegistry.INSTANCE.add(light);
        return light;
    }

    public static Light areaDisc(Vec3 pos, Vector3f normal, float radius,
                                 float r, float g, float b, float range, float intensity) {
        Light light = new Light(LightType.AREA_DISC);
        light.setPosition(pos).setDirection(normal).setAreaSize(radius, radius)
                .setColor(r, g, b).setRange(range).setIntensity(intensity);
        LightRegistry.INSTANCE.add(light);
        return light;
    }

    public static Light tube(Vec3 pos, Vector3f tangent, float len,
                             float r, float g, float b, float range, float intensity) {
        Light light = new Light(LightType.TUBE);
        light.setPosition(pos).setTangent(tangent.x, tangent.y, tangent.z).setTubeLength(len)
                .setColor(r, g, b).setRange(range).setIntensity(intensity);
        LightRegistry.INSTANCE.add(light);
        return light;
    }

    public static void clear() {
        LightRegistry.INSTANCE.clear();
    }
}

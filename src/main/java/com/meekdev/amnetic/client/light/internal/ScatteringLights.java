package com.meekdev.amnetic.client.light.internal;

import com.meekdev.amnetic.client.light.Light;
import com.meekdev.amnetic.client.light.LightType;
import com.meekdev.amnetic.client.render.CameraSnapshot;
import com.meekdev.amnetic.client.render.NativeScreenProgram;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import org.joml.FrustumIntersection;
import java.util.ArrayList;
import java.util.List;

public final class ScatteringLights {
    private static final List<Light> SELECTED = new ArrayList<>();
    public static int selected;
    public static int scattering;
    private static final float[] BOUNDS = new float[4];
    private static final float[] TEMP = new float[4];

    private ScatteringLights() {}

    public static int pack(LightBuffer buffer, List<Light> lights, Vec3 eye, FrustumIntersection frustum) {
        scattering = buffer.pack(lights, eye, frustum, SELECTED, light -> light.godray() > 0);
        selected = buffer.selectionCount();
        bounds(eye);
        return scattering;
    }

    private static void bounds(Vec3 eye) {
        var camera = CameraSnapshot.current();
        if (camera == null || scattering == 0) {
            SpotlightBounds.full(BOUNDS);
            return;
        }
        var target = Minecraft.getInstance().gameRenderer.mainRenderTarget();
        BOUNDS[0] = 1;
        BOUNDS[1] = 1;
        BOUNDS[2] = 0;
        BOUNDS[3] = 0;
        for (var light : SELECTED) {
            if (light.type() != LightType.SPOT) {
                SpotlightBounds.full(BOUNDS);
                return;
            }
            SpotlightBounds.project(camera.viewProj,
                    (float) (light.x() - eye.x), (float) (light.y() - eye.y), (float) (light.z() - eye.z),
                    light.dirX(), light.dirY(), light.dirZ(), light.range(), light.cosOuter(),
                    target.width, target.height, TEMP);
            BOUNDS[0] = Math.min(BOUNDS[0], TEMP[0]);
            BOUNDS[1] = Math.min(BOUNDS[1], TEMP[1]);
            BOUNDS[2] = Math.max(BOUNDS[2], TEMP[2]);
            BOUNDS[3] = Math.max(BOUNDS[3], TEMP[3]);
        }
    }

    public static void bind(NativeScreenProgram shader, boolean enabled) {
        shader.setInt("ScatteringClip", enabled ? 1 : 0);
        if (enabled) shader.setVector("ScatteringRect", BOUNDS[0], BOUNDS[1], BOUNDS[2], BOUNDS[3]);
    }
}

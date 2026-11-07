package com.meekdev.amnetic.client.ibl;

import com.meekdev.amnetic.client.ibl.internal.EnvProbe;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.Level;
import org.joml.Matrix4fc;

public final class Ibl {
    private Ibl() {}

    public static void panorama(int texture, int width, Matrix4fc worldToPanorama) {
        EnvProbe.INSTANCE.panorama(texture, width, worldToPanorama);
    }

    public static void proceduralSky() {
        EnvProbe.INSTANCE.clearPanorama();
    }

    public static void update() {
        Level level = Minecraft.getInstance().level;
        EnvProbe.INSTANCE.update(level != null ? (float) ((level.getGameTime() % 24000L) / 24000.0) : 0.25f);
    }

    public static int cube() { return EnvProbe.INSTANCE.glId(); }
    public static int maxLod() { return EnvProbe.INSTANCE.maxLod(); }
    public static boolean ready() { return EnvProbe.INSTANCE.isReady(); }
}

package com.meekdev.amnetic.client.camera.internal;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import org.joml.Matrix4f;

public final class Orthographic {

    private static volatile float halfHeight;
    private static volatile float depth = 2000f;

    private Orthographic() {}

    public static void set(float halfHeightMetres, float depthMetres) {
        halfHeight = Math.max(0.01f, halfHeightMetres);
        depth = Math.max(1f, depthMetres);
    }

    public static void clear() {
        halfHeight = 0;
    }

    public static boolean active() {
        return halfHeight > 0;
    }

    public static float halfHeight() {
        return halfHeight;
    }

    public static Matrix4f matrix(Matrix4f into) {
        var window = Minecraft.getInstance().getWindow();
        float aspect = (float) window.getWidth() / Math.max(1, window.getHeight());
        float h = halfHeight;
        return into.setOrtho(-h * aspect, h * aspect, -h, h, 0.05f, depth, RenderSystem.getDevice().isZZeroToOne());
    }
}

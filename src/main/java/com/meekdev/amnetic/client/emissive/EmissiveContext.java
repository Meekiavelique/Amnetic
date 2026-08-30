package com.meekdev.amnetic.client.emissive;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4fc;

public final class EmissiveContext {

    private final ClientLevel level;
    private final Vec3 cameraPos;
    private final Matrix4fc view;
    private final Matrix4fc projection;
    private final float deltaTick;
    private final int width;
    private final int height;

    public EmissiveContext(ClientLevel level, Vec3 cameraPos, Matrix4fc view, Matrix4fc projection,
                           float deltaTick, int width, int height) {
        this.level = level;
        this.cameraPos = cameraPos;
        this.view = view;
        this.projection = projection;
        this.deltaTick = deltaTick;
        this.width = width;
        this.height = height;
    }

    public ClientLevel level() { return level; }

    public Vec3 cameraPos() { return cameraPos; }

    public Matrix4fc view() { return view; }

    public Matrix4fc projection() { return projection; }

    public float deltaTick() { return deltaTick; }

    public int targetWidth() { return width; }

    public int targetHeight() { return height; }
}

package com.meekdev.amnetic.client.decal;

import java.nio.ByteBuffer;
import org.lwjgl.system.MemoryUtil;

public final class FilmDecal {
    final double originX;
    final double originZ;
    final double minY;
    final float sizeX;
    final float sizeY;
    final float sizeZ;
    final int resX;
    final int resZ;
    FilmLook look;
    float opacity = 1f;
    boolean removed;

    ByteBuffer fieldA;
    ByteBuffer fieldB;
    int dirtyMin = Integer.MAX_VALUE;
    int dirtyMax = -1;
    int texA;
    int texB;
    int nonEmpty;

    FilmDecal(double originX, double minY, double originZ, float sizeX, float sizeY, float sizeZ, int resX, int resZ, FilmLook look) {
        this.originX = originX;
        this.originZ = originZ;
        this.minY = minY;
        this.sizeX = sizeX;
        this.sizeY = sizeY;
        this.sizeZ = sizeZ;
        this.resX = Math.max(1, resX);
        this.resZ = Math.max(1, resZ);
        this.look = look;
        this.fieldA = MemoryUtil.memCalloc(this.resX * this.resZ * 4);
        this.fieldB = MemoryUtil.memCalloc(this.resX * this.resZ * 4);
        for (int i = 0; i < this.resX * this.resZ; i++) fieldB.put(i * 4 + 2, (byte) 255);
        markAll();
    }

    public FilmDecal texel(int x, int z, float floorY, float thickness, float wetness, float stain, float age, float flow) {
        if (removed || fieldA == null || x < 0 || z < 0 || x >= resX || z >= resZ) return this;
        int i = (z * resX + x) * 4;
        boolean wasEmpty = (fieldA.get(i) | fieldA.get(i + 2)) == 0;
        fieldA.put(i, unorm((float) Math.sqrt(Math.max(0f, thickness) / look.maxThickness)));
        fieldA.put(i + 1, unorm(wetness));
        fieldA.put(i + 2, unorm(stain));
        fieldA.put(i + 3, unorm(flow));
        fieldB.put(i, (byte) Math.max(1, unorm((float) ((floorY - minY) / sizeY)) & 255));
        fieldB.put(i + 1, unorm(age));
        boolean empty = (fieldA.get(i) | fieldA.get(i + 2)) == 0;
        if (wasEmpty != empty) nonEmpty += empty ? -1 : 1;
        mark(z);
        return this;
    }

    public FilmDecal light(int x, int z, float sky, float block) {
        if (removed || fieldA == null || x < 0 || z < 0 || x >= resX || z >= resZ) return this;
        int i = (z * resX + x) * 4;
        fieldB.put(i + 2, unorm(sky));
        fieldB.put(i + 3, unorm(block));
        mark(z);
        return this;
    }

    public FilmDecal clearTexel(int x, int z) {
        if (removed || fieldA == null || x < 0 || z < 0 || x >= resX || z >= resZ) return this;
        int i = (z * resX + x) * 4;
        if ((fieldA.get(i) | fieldA.get(i + 2)) != 0) nonEmpty--;
        fieldA.putInt(i, 0);
        mark(z);
        return this;
    }

    public FilmDecal clearAll() {
        if (removed || fieldA == null) return this;
        for (int i = 0; i < resX * resZ; i++) fieldA.putInt(i * 4, 0);
        nonEmpty = 0;
        markAll();
        return this;
    }

    public FilmDecal look(FilmLook look) { this.look = look; return this; }
    public FilmDecal opacity(float v) { this.opacity = Math.max(0f, Math.min(1f, v)); return this; }

    public boolean isEmpty() { return nonEmpty <= 0; }
    public boolean isRemoved() { return removed; }
    public FilmLook look() { return look; }
    public int resX() { return resX; }
    public int resZ() { return resZ; }
    public double originX() { return originX; }
    public double originZ() { return originZ; }
    public double minY() { return minY; }
    public float sizeX() { return sizeX; }
    public float sizeY() { return sizeY; }
    public float sizeZ() { return sizeZ; }

    public void remove() {
        removed = true;
    }

    private void mark(int row) {
        dirtyMin = Math.min(dirtyMin, row);
        dirtyMax = Math.max(dirtyMax, row);
    }

    private void markAll() {
        dirtyMin = 0;
        dirtyMax = resZ - 1;
    }

    void freeCpu() {
        if (fieldA != null) { MemoryUtil.memFree(fieldA); fieldA = null; }
        if (fieldB != null) { MemoryUtil.memFree(fieldB); fieldB = null; }
    }

    private static byte unorm(float v) {
        return (byte) Math.round(Math.max(0f, Math.min(1f, v)) * 255f);
    }
}

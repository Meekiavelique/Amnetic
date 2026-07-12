package com.meekdev.amnetic.client.decal;

import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

public final class Decal {

    Identifier texture;
    Vec3 center;
    final Vector3f normal = new Vector3f(0f, -1f, 0f);
    float width;
    float height;
    float depth;
    float opacity = 1.0f;
    float angleFade = 0.3f;
    final Vector3f tint = new Vector3f(1f, 1f, 1f); // white = show the texture as-is; darken for scorch marks
    boolean removed;

    // optional gbuffer-writing (relightable) decal: tangent-space normal map and/or material override
    // when any of these is set the decal also writes gbuffer normal/material so it relights
    Identifier normalMap;
    float roughness = -1f; // <0 = don't override the surface roughness
    float metallic = 0f;

    Decal(Identifier texture, Vec3 center, Vector3f normal, float width, float height, float depth) {
        this.texture = texture;
        this.center = center;
        this.normal.set(normal).normalize();
        this.width = width;
        this.height = height;
        this.depth = depth;
    }

    public Decal opacity(float v) { this.opacity = Math.max(0f, Math.min(1f, v)); return this; }
    public Decal angleFade(float v) { this.angleFade = Math.max(0f, Math.min(1f, v)); return this; }
    public Decal tint(float r, float g, float b) { this.tint.set(r, g, b); return this; }
    public Decal center(Vec3 c) { this.center = c; return this; }
    public Decal width(float v) { this.width = Math.max(0f, v); return this; }
    public Decal height(float v) { this.height = Math.max(0f, v); return this; }
    public Decal depth(float v) { this.depth = Math.max(0f, v); return this; }
    public Decal normal(float x, float y, float z) { this.normal.set(x, y, z); if (this.normal.lengthSquared() > 1e-8f) this.normal.normalize(); return this; }
    public Decal normalMap(Identifier id) { this.normalMap = id; return this; }
    public Decal roughness(float v) { this.roughness = v; return this; }
    public Decal metallic(float v) { this.metallic = Math.max(0f, Math.min(1f, v)); return this; }

    // true when this decal should also write the gbuffer (normal/material) so it relights
    public boolean writesGBuffer() { return normalMap != null || roughness >= 0f; }
    public Identifier normalMap() { return normalMap; }
    public float roughnessOr(float fallback) { return roughness >= 0f ? roughness : fallback; }
    public float metallic() { return metallic; }

    public boolean isRemoved() { return removed; }

    public void remove() {
        removed = true;
        Decals.ACTIVE.remove(this);
    }

    public Identifier texture() { return texture; }
    public Vec3 center() { return center; }
    public Vector3f normal() { return normal; }
    public float width() { return width; }
    public float height() { return height; }
    public float depth() { return depth; }
    public float opacity() { return opacity; }
    public float angleFade() { return angleFade; }
    public Vector3f tint() { return tint; }
}

package com.meekdev.amnetic.client.light;

import com.meekdev.amnetic.client.light.internal.LightRegistry;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

public final class Light {

    private final LightType type;

    private double x, y, z;                 // world position
    private float dx, dy = -1f, dz;         // primary direction (spot/area normal/directional)
    private float tx = 1f, ty, tz;          // tangent (area "right" axis; up = cross(dir, tangent))
    private float r = 1f, g = 1f, b = 1f;   // color
    private float range = 12f;
    private float intensity = 1f;           // radiance scale
    private float cosInner = (float) Math.cos(Math.toRadians(12));
    private float cosOuter = (float) Math.cos(Math.toRadians(25));
    private FalloffCurve falloff = FalloffCurve.SMOOTH;
    private float falloffParam = 2f;        // exponent for FalloffCurve.EXPONENT
    private float areaW = 1f, areaH = 1f;   // rect half-extents / disc radius (areaW)
    private float tubeLen = 2f;             // tube length
    private boolean enabled = true;
    private boolean removed;

    Light(LightType type) {
        this.type = type;
    }

    public LightType type() { return type; }

    public Light setPosition(Vec3 pos) { this.x = pos.x; this.y = pos.y; this.z = pos.z; return this; }
    public Light setPosition(double x, double y, double z) { this.x = x; this.y = y; this.z = z; return this; }

    public Light setColor(float r, float g, float b) { this.r = r; this.g = g; this.b = b; return this; }

    public Light setTemperature(float kelvin) {
        float t = Math.max(1000f, Math.min(40000f, kelvin)) / 100f;
        float rr, gg, bb;
        if (t <= 66f) { rr = 255f; gg = clamp255(99.47f * (float) Math.log(t) - 161.12f); }
        else { rr = clamp255(329.7f * (float) Math.pow(t - 60f, -0.1332f)); gg = clamp255(288.12f * (float) Math.pow(t - 60f, -0.0755f)); }
        if (t >= 66f) bb = 255f;
        else if (t <= 19f) bb = 0f;
        else bb = clamp255(138.52f * (float) Math.log(t - 10f) - 305.04f);
        return setColor(rr / 255f, gg / 255f, bb / 255f);
    }

    private static float clamp255(float v) { return Math.max(0f, Math.min(255f, v)); }

    public Light setRange(float range) { this.range = Math.max(0f, range); return this; }

    public Light setIntensity(float intensity) { this.intensity = Math.max(0f, intensity); return this; }

    public Light setLumens(float lumens) { this.intensity = Math.max(0f, lumens) / 100f; return this; }

    public Light setDirection(Vector3f dir) { return setDirection(dir.x, dir.y, dir.z); }

    public Light setDirection(float dx, float dy, float dz) {
        float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len > 1e-6f) { this.dx = dx / len; this.dy = dy / len; this.dz = dz / len; }
        return this;
    }

    public Light setTangent(float tx, float ty, float tz) {
        float len = (float) Math.sqrt(tx * tx + ty * ty + tz * tz);
        if (len > 1e-6f) { this.tx = tx / len; this.ty = ty / len; this.tz = tz / len; }
        return this;
    }

    public Light setSpotAngles(float innerDeg, float outerDeg) {
        this.cosInner = (float) Math.cos(Math.toRadians(innerDeg));
        this.cosOuter = (float) Math.cos(Math.toRadians(Math.max(innerDeg, outerDeg)));
        return this;
    }

    public Light setFalloff(FalloffCurve curve) { this.falloff = curve; return this; }

    public Light setFalloff(FalloffCurve curve, float param) { this.falloff = curve; this.falloffParam = Math.max(0.01f, param); return this; }

    public Light setAreaSize(float w, float h) { this.areaW = Math.max(0f, w); this.areaH = Math.max(0f, h); return this; }

    public Light setTubeLength(float len) { this.tubeLen = Math.max(0f, len); return this; }

    public Light setEnabled(boolean enabled) { this.enabled = enabled; return this; }

    public boolean isEnabled() { return enabled && !removed; }

    public void remove() {
        if (removed) return;
        removed = true;
        LightRegistry.INSTANCE.remove(this);
    }

    public double x() { return x; }
    public double y() { return y; }
    public double z() { return z; }
    public float dirX() { return dx; }
    public float dirY() { return dy; }
    public float dirZ() { return dz; }
    public float tanX() { return tx; }
    public float tanY() { return ty; }
    public float tanZ() { return tz; }
    public float red() { return r; }
    public float green() { return g; }
    public float blue() { return b; }
    public float range() { return range; }
    public float intensity() { return intensity; }
    public float cosInner() { return cosInner; }
    public float cosOuter() { return cosOuter; }
    public int falloffId() { return falloff.id(); }
    public float falloffParam() { return falloffParam; }
    public float areaW() { return areaW; }
    public float areaH() { return areaH; }
    public float tubeLen() { return tubeLen; }
}

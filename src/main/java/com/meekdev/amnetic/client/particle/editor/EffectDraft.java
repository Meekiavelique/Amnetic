package com.meekdev.amnetic.client.particle.editor;

import com.meekdev.amnetic.client.anim.Easing;
import com.meekdev.amnetic.client.particle.Affector;
import com.meekdev.amnetic.client.particle.Affectors;
import com.meekdev.amnetic.client.particle.BillboardMode;
import com.meekdev.amnetic.client.particle.Collider;
import com.meekdev.amnetic.client.particle.Colliders;
import com.meekdev.amnetic.client.particle.CollisionResponse;
import com.meekdev.amnetic.client.particle.ParticleMaterial;
import com.meekdev.amnetic.client.particle.Particles;
import com.meekdev.amnetic.client.particle.track.Curve;
import com.meekdev.amnetic.client.particle.track.Gradient;
import com.meekdev.amnetic.client.particle.track.Track;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

public final class EffectDraft {

    private static final Identifier SOFT = Identifier.fromNamespaceAndPath("amnetic", "particle/default_soft");
    private static final Identifier TEXTURED = Identifier.fromNamespaceAndPath("amnetic", "particle/default_textured");

    public String name = "effect";
    public String texture = "";
    public String blend = "ALPHA";
    public String billboard = "SPHERICAL";
    public boolean emissive = false;
    public float emissiveStrength = 1f;
    public boolean softDepth = false, sorted = false, depthWrite = false, lightmap = false;

    public float life = 1.2f;
    public float gravity = -1f;
    public float drag = 0.9f;
    public String easing = "LINEAR";

    public int flipCols = 1, flipRows = 1;
    public float flipFps = 0f;

    public boolean trail = false;
    public int trailPoints = 8;
    public float trailWidth = 0.6f;
    public float trailFade = 0.7f;
    public int trailInterval = 2;

    public List<float[]> sizeKeys = new ArrayList<>(List.of(new float[]{0f, 0.25f}, new float[]{1f, 0.0f}));
    public List<float[]> alphaKeys = new ArrayList<>(List.of(new float[]{0f, 1f}, new float[]{1f, 0f}));
    public List<float[]> colorStops = new ArrayList<>(List.of(new float[]{0f, 1f, 1f, 1f}, new float[]{1f, 1f, 0.6f, 0.2f}));

    public List<Aff> affectors = new ArrayList<>();

    public String collider = "NONE";
    public float planeY = 0f;
    public String response = "BOUNCE";
    public float restitution = 0.3f, friction = 0.5f;

    public static final class Aff {
        public String type = "TURBULENCE";
        public float p0 = 1f, p1 = 1f;
        public double x, y, z;
    }

    public ParticleMaterial build() {
        return toBuilder().register();
    }

    public void applyLive(ParticleMaterial material) {
        material.applyLiveFrom(toBuilder());
    }

    public String structuralSig() {
        return texture + "|" + blend + "|" + billboard + "|" + emissive + "|" + emissiveStrength
                + "|" + softDepth + "|" + sorted + "|" + depthWrite + "|" + lightmap;
    }

    private ParticleMaterial.Builder toBuilder() {
        ParticleMaterial.Builder b = Particles.material()
                .displayName(name.isBlank() ? "effect" : name)
                .shader(texture.isBlank() ? SOFT : TEXTURED)
                .blend(blend(blend))
                .billboard(billboard(billboard))
                .life(Math.max(0.05f, life))
                .gravity(gravity)
                .drag(drag)
                .easing(easing(easing))
                .size(curve(sizeKeys, 0.2f))
                .alpha(Track.curve(curve(alphaKeys, 1f)))
                .color(gradient(colorStops));
        if (!texture.isBlank()) b.texture(Identifier.parse(texture.trim()));
        if (emissive) b.emissive(emissiveStrength);
        if (softDepth) b.softDepth(true);
        if (sorted) b.sorted(true);
        b.depthWrite(depthWrite);
        if (lightmap) b.lightmap(true);
        if (flipCols > 1 || flipRows > 1) b.flipbook(Math.max(1, flipCols), Math.max(1, flipRows), flipFps);
        if (trail) b.trail(trailPoints, trailWidth, trailFade, trailInterval);
        // the gravity field only takes effect through a gravity affector, add one so it actually pulls
        if (gravity != 0f) b.affector(Affectors.gravity());
        for (Aff a : affectors) {
            Affector af = affector(a);
            if (af != null) b.affector(af);
        }
        Collider c = collider();
        if (c != null) b.collider(c);
        return b;
    }

    private static ParticleMaterial.Blend blend(String s) {
        return "ADDITIVE".equals(s) ? ParticleMaterial.Blend.ADDITIVE : ParticleMaterial.Blend.ALPHA;
    }

    private static BillboardMode billboard(String s) {
        try { return BillboardMode.valueOf(s); } catch (Exception e) { return BillboardMode.SPHERICAL; }
    }

    private static Easing easing(String s) {
        try { return Easing.valueOf(s); } catch (Exception e) { return Easing.LINEAR; }
    }

    private static Curve curve(List<float[]> keys, float fallback) {
        if (keys == null || keys.isEmpty()) return Curve.constant(fallback);
        if (keys.size() == 1) return Curve.constant(keys.get(0)[1]);
        List<float[]> sorted = new ArrayList<>(keys);
        sorted.sort((a, b) -> Float.compare(a[0], b[0]));
        Curve.Builder cb = Curve.builder();
        for (float[] k : sorted) cb.key(k[0], k[1]);
        return cb.build();
    }

    private static Gradient gradient(List<float[]> stops) {
        Gradient.Builder gb = Gradient.builder();
        if (stops == null || stops.isEmpty()) {
            gb.stop(0f, 1f, 1f, 1f);
        } else {
            List<float[]> sorted = new ArrayList<>(stops);
            sorted.sort((a, b) -> Float.compare(a[0], b[0]));
            for (float[] s : sorted) gb.stop(s[0], s[1], s[2], s[3]);
        }
        return gb.build();
    }

    private static Affector affector(Aff a) {
        switch (a.type) {
            case "GRAVITY": return Affectors.gravity(a.p0);
            case "DRAG": return Affectors.drag(a.p0);
            case "DRAG_PP": return Affectors.dragPerParticle();
            case "WIND": return Affectors.wind((float) a.x, (float) a.y, (float) a.z);
            case "TURBULENCE": return Affectors.turbulence(a.p0, a.p1);
            case "ATTRACTOR": return Affectors.attractor(a.x, a.y, a.z, a.p0);
            case "VORTEX": return Affectors.vortex(a.x, a.y, a.z, 0f, 1f, 0f, a.p0, a.p1);
            default: return null;
        }
    }

    private Collider collider() {
        CollisionResponse r = switch (response) {
            case "SLIDE" -> CollisionResponse.slide(friction);
            case "STOP" -> CollisionResponse.stop();
            default -> CollisionResponse.bounce(restitution, friction);
        };
        return switch (collider) {
            case "WORLD" -> Colliders.world(r);
            case "PLANE" -> Colliders.plane(planeY, r);
            default -> null;
        };
    }

    public EffectDraft copy() {
        EffectDraft d = new EffectDraft();
        d.name = name + "_copy";
        d.texture = texture; d.blend = blend; d.billboard = billboard;
        d.emissive = emissive; d.emissiveStrength = emissiveStrength;
        d.softDepth = softDepth; d.sorted = sorted; d.depthWrite = depthWrite; d.lightmap = lightmap;
        d.life = life; d.gravity = gravity; d.drag = drag; d.easing = easing;
        d.flipCols = flipCols; d.flipRows = flipRows; d.flipFps = flipFps;
        d.trail = trail; d.trailPoints = trailPoints; d.trailWidth = trailWidth; d.trailFade = trailFade; d.trailInterval = trailInterval;
        d.sizeKeys = deepCopy(sizeKeys); d.alphaKeys = deepCopy(alphaKeys); d.colorStops = deepCopy(colorStops);
        d.collider = collider; d.planeY = planeY; d.response = response; d.restitution = restitution; d.friction = friction;
        for (Aff a : affectors) {
            Aff n = new Aff();
            n.type = a.type; n.p0 = a.p0; n.p1 = a.p1; n.x = a.x; n.y = a.y; n.z = a.z;
            d.affectors.add(n);
        }
        return d;
    }

    private static List<float[]> deepCopy(List<float[]> src) {
        List<float[]> out = new ArrayList<>(src.size());
        for (float[] f : src) out.add(f.clone());
        return out;
    }
}

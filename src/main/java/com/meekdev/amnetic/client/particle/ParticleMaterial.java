package com.meekdev.amnetic.client.particle;

import com.meekdev.amnetic.client.anim.Easing;
import com.meekdev.amnetic.client.instanced.RenderState;
import com.meekdev.amnetic.client.particle.track.Curve;
import com.meekdev.amnetic.client.particle.track.Gradient;
import com.meekdev.amnetic.client.particle.track.Track;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.resources.Identifier;

public final class ParticleMaterial {

    final String displayName;

    final Identifier fragmentShaderId;
    final Identifier textureId;
    final Identifier texture2Id;
    final Blend blend;
    final boolean softDepth;
    final boolean sorted;
    final boolean emissive;
    final float emissiveStrength;
    final BillboardMode billboardMode;

    final int liveCap;

    final boolean lightmap;
    float lightMin;
    final int lightRefreshSteps;

    float defLife;
    float defSize0, defSize1;
    float defR0, defG0, defB0, defR1, defG1, defB1;
    float defA0, defA1;
    float defAFadeIn, defAFadeOut;
    float defGravity, defDrag;
    Easing defEasing;

    Affector[] affectors;
    Collider collider;

    Track sizeTrack;
    Track alphaTrack;
    Gradient colorGradient;

    // motion trail: each particle leaves fading billboard ghosts along its recent path
    boolean trail;
    int trailPoints = 8; // ghost billboards behind each particle
    float trailWidth = 0.6f; // ghost size as a fraction of the particle's size, tapers to the tail
    float trailFade = 0.7f; // ghost alpha multiplier, tapers to the tail
    int trailInterval = 2; // sim steps between recorded path samples, higher = longer coarser trail

    int flipCols, flipRows;
    float flipFps;
    boolean flipOverLife;

    final boolean depthWrite;
    final boolean overlay;

    Identifier meshId;
    Particle[] live;
    int liveCount;

    private ParticleMaterial(Builder b) {
        this.displayName = b.displayName;
        this.fragmentShaderId = b.fragmentShaderId;
        this.textureId = b.textureId;
        this.texture2Id = b.texture2Id;
        this.blend = b.blend;
        this.softDepth = b.softDepth;
        this.sorted = b.sorted;
        this.emissive = b.emissive;
        this.emissiveStrength = b.emissiveStrength;
        this.billboardMode = b.billboardMode;
        this.liveCap = b.liveCap;
        this.lightmap = b.lightmap;
        this.lightMin = b.lightMin;
        this.lightRefreshSteps = b.lightRefreshSteps;
        this.defLife = b.defLife;
        this.defSize0 = b.defSize0; this.defSize1 = b.defSize1;
        this.defR0 = b.defR0; this.defG0 = b.defG0; this.defB0 = b.defB0;
        this.defR1 = b.defR1; this.defG1 = b.defG1; this.defB1 = b.defB1;
        this.defA0 = b.defA0; this.defA1 = b.defA1;
        this.defAFadeIn = b.defAFadeIn; this.defAFadeOut = b.defAFadeOut;
        this.defGravity = b.defGravity; this.defDrag = b.defDrag;
        this.defEasing = b.defEasing;
        this.affectors = b.affectors.toArray(new Affector[0]);
        this.collider = b.collider;
        this.sizeTrack = b.sizeTrack;
        this.alphaTrack = b.alphaTrack;
        this.colorGradient = b.colorGradient;
        this.flipCols = b.flipCols; this.flipRows = b.flipRows;
        this.flipFps = b.flipFps; this.flipOverLife = b.flipOverLife;
        this.trail = b.trail; this.trailPoints = b.trailPoints; this.trailWidth = b.trailWidth;
        this.trailFade = b.trailFade; this.trailInterval = b.trailInterval;
        this.depthWrite = b.depthWrite;
        this.overlay = b.overlay;
        this.live = new Particle[Math.min(liveCap, 256)];
    }

    public void applyLiveFrom(Builder b) {
        this.defLife = b.defLife;
        this.defSize0 = b.defSize0; this.defSize1 = b.defSize1;
        this.defR0 = b.defR0; this.defG0 = b.defG0; this.defB0 = b.defB0;
        this.defR1 = b.defR1; this.defG1 = b.defG1; this.defB1 = b.defB1;
        this.defA0 = b.defA0; this.defA1 = b.defA1;
        this.defAFadeIn = b.defAFadeIn; this.defAFadeOut = b.defAFadeOut;
        this.defGravity = b.defGravity; this.defDrag = b.defDrag;
        this.defEasing = b.defEasing;
        this.affectors = b.affectors.toArray(new Affector[0]);
        this.collider = b.collider;
        this.sizeTrack = b.sizeTrack;
        this.alphaTrack = b.alphaTrack;
        this.colorGradient = b.colorGradient;
        this.flipCols = b.flipCols; this.flipRows = b.flipRows;
        this.flipFps = b.flipFps; this.flipOverLife = b.flipOverLife;
        this.trail = b.trail; this.trailPoints = b.trailPoints; this.trailWidth = b.trailWidth;
        this.trailFade = b.trailFade; this.trailInterval = b.trailInterval;
        this.lightMin = b.lightMin;
    }

    public String displayName() { return displayName; }

    public String label() {
        if (displayName != null && !displayName.isEmpty()) return displayName;
        if (textureId != null) return textureId.toString();
        if (fragmentShaderId != null) return fragmentShaderId.toString();
        return "material";
    }

    RenderState renderState() {
        RenderState.BlendMode mode = blend == Blend.ADDITIVE
                ? RenderState.BlendMode.ADDITIVE : RenderState.BlendMode.ALPHA;
        return RenderState.builder()
                .depthTest(true)
                .depthWrite(depthWrite)
                .backfaceCulling(false)
                .blend(mode)
                .build();
    }

    public enum Blend { ALPHA, ADDITIVE }

    public static final class Builder {
        private String displayName = "";
        private Identifier fragmentShaderId;
        private Identifier textureId;
        private Identifier texture2Id;
        private Blend blend = Blend.ALPHA;
        private boolean softDepth = false;
        private boolean sorted = false;
        private boolean depthWrite = false;
        private boolean overlay = false;
        private boolean emissive = false;
        private float emissiveStrength = 1.0f;
        private BillboardMode billboardMode = BillboardMode.SPHERICAL;
        private int liveCap = 4096;
        private boolean lightmap = false;
        private float lightMin = 0.15f;
        private int lightRefreshSteps = 4;
        private float defLife = 1f;
        private float defSize0 = 0.2f, defSize1 = 0.2f;
        private float defR0 = 1, defG0 = 1, defB0 = 1, defR1 = 1, defG1 = 1, defB1 = 1;
        private float defA0 = 1, defA1 = 0;
        private float defAFadeIn = 0f, defAFadeOut = 0f;
        private float defGravity = 0f, defDrag = 1f;
        private Easing defEasing = Easing.EASE_OUT;
        private final List<Affector> affectors = new ArrayList<>();
        private Collider collider = null;
        private Track sizeTrack = null;
        private Track alphaTrack = null;
        private Gradient colorGradient = null;
        private boolean trail = false;
        private int trailPoints = 8;
        private float trailWidth = 0.6f;
        private float trailFade = 0.7f;
        private int trailInterval = 2;
        private int flipCols = 0, flipRows = 0;
        private float flipFps = 0f;
        private boolean flipOverLife = false;

        Builder() {}

        public Builder displayName(String name) { this.displayName = name == null ? "" : name; return this; }

        public Builder shader(Identifier fragmentShaderId) { this.fragmentShaderId = fragmentShaderId; return this; }

        public Builder texture(Identifier textureId) { this.textureId = textureId; return this; }

        public Builder texture2(Identifier textureId) { this.texture2Id = textureId; return this; }

        public Builder blend(Blend blend) { this.blend = blend; return this; }

        public Builder softDepth(boolean v) { this.softDepth = v; return this; }

        public Builder sorted(boolean v) { this.sorted = v; return this; }

        public Builder depthWrite(boolean v) { this.depthWrite = v; return this; }

        public Builder overlay(boolean v) { this.overlay = v; return this; }

        public Builder emissive() { return emissive(1.0f); }

        public Builder emissive(float strength) { this.emissive = true; this.emissiveStrength = strength; return this; }

        public Builder billboard(BillboardMode mode) { this.billboardMode = mode; return this; }

        public Builder liveCap(int cap) { this.liveCap = Math.max(1, cap); return this; }

        public Builder lightmap(boolean v) { this.lightmap = v; return this; }

        public Builder lightMin(float v) { this.lightMin = v; return this; }

        public Builder lightRefreshSteps(int steps) { this.lightRefreshSteps = Math.max(1, steps); return this; }

        public Builder life(float seconds) { this.defLife = seconds; return this; }

        public Builder size(float constant) { this.defSize0 = this.defSize1 = constant; return this; }

        public Builder size(float start, float end) { this.defSize0 = start; this.defSize1 = end; return this; }

        public Builder color(float r, float g, float b) {
            this.defR0 = defR1 = r; this.defG0 = defG1 = g; this.defB0 = defB1 = b; return this;
        }

        public Builder color(float sr, float sg, float sb, float er, float eg, float eb) {
            this.defR0 = sr; this.defG0 = sg; this.defB0 = sb;
            this.defR1 = er; this.defG1 = eg; this.defB1 = eb; return this;
        }

        public Builder alpha(float start, float end) { this.defA0 = start; this.defA1 = end; return this; }

        public Builder alphaInOut(float fadeInFrac, float fadeOutFrac) {
            this.defAFadeIn = fadeInFrac; this.defAFadeOut = fadeOutFrac; return this;
        }

        public Builder gravity(float accel) { this.defGravity = accel; return this; }

        public Builder drag(float perSecondRetention) { this.defDrag = perSecondRetention; return this; }

        public Builder easing(Easing easing) { this.defEasing = easing; return this; }

        public Builder size(Track track) { this.sizeTrack = track; return this; }

        public Builder size(Curve curve) {
            this.sizeTrack = Track.curve(curve); return this;
        }

        public Builder alpha(Track track) { this.alphaTrack = track; return this; }

        public Builder color(Gradient gradient) { this.colorGradient = gradient; return this; }

        // motion trail of fading billboard ghosts along each particle's path
        public Builder trail(int points, float width, float fade, int sampleInterval) {
            this.trail = points > 0;
            this.trailPoints = Math.max(0, Math.min(32, points));
            this.trailWidth = Math.max(0f, width);
            this.trailFade = Math.max(0f, Math.min(1f, fade));
            this.trailInterval = Math.max(1, sampleInterval);
            return this;
        }

        public Builder flipbook(int cols, int rows, float fps) {
            this.flipCols = cols; this.flipRows = rows; this.flipFps = fps; this.flipOverLife = false; return this;
        }

        public Builder flipbookOverLife(int cols, int rows) {
            this.flipCols = cols; this.flipRows = rows; this.flipOverLife = true; return this;
        }

        public Builder affector(Affector affector) { this.affectors.add(affector); return this; }

        public Builder collider(Collider collider) { this.collider = collider; return this; }

        public ParticleMaterial register() {
            if (fragmentShaderId == null) throw new IllegalStateException("ParticleMaterial requires a shader()");
            ParticleMaterial material = new ParticleMaterial(this);
            ParticleSimulation.INSTANCE.register(material);
            return material;
        }
    }
}
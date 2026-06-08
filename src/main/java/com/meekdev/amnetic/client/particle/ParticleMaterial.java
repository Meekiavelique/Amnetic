package com.meekdev.amnetic.client.particle;

import com.meekdev.amnetic.client.anim.Easing;
import com.meekdev.amnetic.client.instanced.RenderState;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.resources.Identifier;

public final class ParticleMaterial {

    final Identifier fragmentShaderId;
    final Identifier textureId;
    final Blend blend;
    final boolean softDepth;
    final boolean sorted;
    final BillboardMode billboardMode;

    final int liveCap;

    final boolean lightmap;
    final float lightMin;
    final int lightRefreshSteps;

    final float defLife;
    final float defSize0, defSize1;
    final float defR0, defG0, defB0, defR1, defG1, defB1;
    final float defA0, defA1;
    final float defGravity, defDrag;
    final Easing defEasing;

    final Affector[] affectors;

    Particle[] live;
    int liveCount;

    private ParticleMaterial(Builder b) {
        this.fragmentShaderId = b.fragmentShaderId;
        this.textureId = b.textureId;
        this.blend = b.blend;
        this.softDepth = b.softDepth;
        this.sorted = b.sorted;
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
        this.defGravity = b.defGravity; this.defDrag = b.defDrag;
        this.defEasing = b.defEasing;
        this.affectors = b.affectors.toArray(new Affector[0]);
        this.live = new Particle[Math.min(liveCap, 256)];
    }

    RenderState renderState() {
        RenderState.BlendMode mode = blend == Blend.ADDITIVE
                ? RenderState.BlendMode.ADDITIVE : RenderState.BlendMode.ALPHA;
        return RenderState.builder()
                .depthTest(true)
                .depthWrite(false)
                .backfaceCulling(false)
                .blend(mode)
                .build();
    }

    public enum Blend { ALPHA, ADDITIVE }

    public static final class Builder {
        private Identifier fragmentShaderId;
        private Identifier textureId;
        private Blend blend = Blend.ALPHA;
        private boolean softDepth = false;
        private boolean sorted = false;
        private BillboardMode billboardMode = BillboardMode.SPHERICAL;
        private int liveCap = 4096;
        private boolean lightmap = false;
        private float lightMin = 0.15f;
        private int lightRefreshSteps = 4;
        private float defLife = 1f;
        private float defSize0 = 0.2f, defSize1 = 0.2f;
        private float defR0 = 1, defG0 = 1, defB0 = 1, defR1 = 1, defG1 = 1, defB1 = 1;
        private float defA0 = 1, defA1 = 0;
        private float defGravity = 0f, defDrag = 1f;
        private Easing defEasing = Easing.EASE_OUT;
        private final List<Affector> affectors = new ArrayList<>();

        Builder() {}

        public Builder shader(Identifier fragmentShaderId) { this.fragmentShaderId = fragmentShaderId; return this; }

        public Builder texture(Identifier textureId) { this.textureId = textureId; return this; }

        public Builder blend(Blend blend) { this.blend = blend; return this; }

        public Builder softDepth(boolean v) { this.softDepth = v; return this; }

        public Builder sorted(boolean v) { this.sorted = v; return this; }

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

        public Builder gravity(float accel) { this.defGravity = accel; return this; }

        public Builder drag(float perSecondRetention) { this.defDrag = perSecondRetention; return this; }

        public Builder easing(Easing easing) { this.defEasing = easing; return this; }

        public Builder affector(Affector affector) { this.affectors.add(affector); return this; }

        public ParticleMaterial register() {
            if (fragmentShaderId == null) throw new IllegalStateException("ParticleMaterial requires a shader()");
            ParticleMaterial material = new ParticleMaterial(this);
            ParticleSimulation.INSTANCE.register(material);
            return material;
        }
    }
}
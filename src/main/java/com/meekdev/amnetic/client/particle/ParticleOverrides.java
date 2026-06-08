package com.meekdev.amnetic.client.particle;

import com.meekdev.amnetic.client.anim.Easing;

public final class ParticleOverrides {

    public float life;
    public float size0, size1;
    public float r0, g0, b0, r1, g1, b1;
    public float a0, a1;
    public float gravity, drag;
    public float rot, rotSpeed;
    public Easing sizeEasing;

    ParticleOverrides() {}

    void seedFrom(ParticleMaterial m) {
        life = m.defLife;
        size0 = m.defSize0; size1 = m.defSize1;
        r0 = m.defR0; g0 = m.defG0; b0 = m.defB0;
        r1 = m.defR1; g1 = m.defG1; b1 = m.defB1;
        a0 = m.defA0; a1 = m.defA1;
        gravity = m.defGravity; drag = m.defDrag;
        rot = 0f; rotSpeed = 0f;
        sizeEasing = m.defEasing;
    }

    public ParticleOverrides life(float seconds) { this.life = seconds; return this; }

    public ParticleOverrides size(float constant) { this.size0 = this.size1 = constant; return this; }

    public ParticleOverrides size(float start, float end) { this.size0 = start; this.size1 = end; return this; }

    public ParticleOverrides color(float r, float g, float b) {
        this.r0 = r1 = r; this.g0 = g1 = g; this.b0 = b1 = b; return this;
    }

    public ParticleOverrides color(float sr, float sg, float sb, float er, float eg, float eb) {
        this.r0 = sr; this.g0 = sg; this.b0 = sb; this.r1 = er; this.g1 = eg; this.b1 = eb; return this;
    }

    public ParticleOverrides alpha(float constant) { this.a0 = this.a1 = constant; return this; }

    public ParticleOverrides alpha(float start, float end) { this.a0 = start; this.a1 = end; return this; }

    public ParticleOverrides gravity(float accel) { this.gravity = accel; return this; }

    public ParticleOverrides drag(float perSecondRetention) { this.drag = perSecondRetention; return this; }

    public ParticleOverrides rotation(float radians) { this.rot = radians; return this; }

    public ParticleOverrides spin(float radiansPerSecond) { this.rotSpeed = radiansPerSecond; return this; }

    public ParticleOverrides easing(Easing easing) { this.sizeEasing = easing; return this; }
}

package com.meekdev.amnetic.client.particle;

import com.meekdev.amnetic.client.anim.Easing;

public final class Particle {

    public double x, y, z;
    public double vx, vy, vz;

    public float age, life;

    public float size0, size1;
    public float r0, g0, b0;
    public float r1, g1, b1;
    public float a0, a1;
    public float aFadeIn, aFadeOut;

    public float rot, rotSpeed;

    public float seedX, seedY;

    public float gravity;
    public float drag;
    public float dragStep;

    public float brightness = 1f;
    public int lightTimer;

    public Easing sizeEasing = Easing.EASE_OUT;
    public boolean alive;
    boolean colliding;

    float rCx, rCy, rCz;
    float rVx, rVy, rVz;
    float rSize;
    float rR, rG, rB, rA;
    float rUvOffX, rUvOffY, rUvScaleX = 1f, rUvScaleY = 1f;

    // trail history: ring buffer of recent world positions (x,y,z per sample), null unless the material
    // uses trails. trailHead is the next slot to write, trailCount is how many valid samples exist
    public double[] trail;
    public int trailHead, trailCount;

    Particle() {}

    public void accelerate(double ax, double ay, double az, float dt) {
        vx += ax * dt; vy += ay * dt; vz += az * dt;
    }

    public float ageFraction() {
        return life <= 0f ? 1f : Math.min(age / life, 1f);
    }

    void clear() {
        x = y = z = 0; vx = vy = vz = 0;
        age = 0; life = 0;
        size0 = size1 = 0;
        r0 = g0 = b0 = r1 = g1 = b1 = 0;
        a0 = a1 = 0;
        aFadeIn = aFadeOut = 0;
        rot = rotSpeed = 0;
        seedX = seedY = 0;
        gravity = 0; drag = 1f; dragStep = 1f;
        brightness = 1f; lightTimer = 0;
        sizeEasing = Easing.EASE_OUT;
        alive = false;
        colliding = false;
        trailHead = 0;
        trailCount = 0; // keep the array allocated for reuse from the pool
    }
}
package com.meekdev.amnetic.client.camera.effect;

import com.meekdev.amnetic.client.camera.CameraFrame;
import com.meekdev.amnetic.client.camera.CameraModifier;

public final class Shake implements CameraModifier {

    private float trauma;
    private float decayPerSecond = 1.0f;
    private float positionScale = 0.18f;
    private float rotationScale = 4.0f;
    private float clock;

    public Shake add(float amount) {
        trauma = Math.max(0f, Math.min(1f, trauma + amount));
        return this;
    }

    public Shake decay(float perSecond) {
        decayPerSecond = Math.max(1.0e-3f, perSecond);
        return this;
    }

    public Shake scale(float position, float rotation) {
        positionScale = position;
        rotationScale = rotation;
        return this;
    }

    public float trauma() {
        return trauma;
    }

    @Override
    public void modify(CameraFrame frame) {
        if (trauma <= 0f) return;
        clock += frame.dt();

        // squared so small traumas stay subtle and large ones bite
        float s = trauma * trauma;
        frame.addPositionLocal(noise(clock, 1) * s * positionScale, noise(clock, 2) * s * positionScale, 0.0);
        frame.addRotation(noise(clock, 4) * s * rotationScale * 0.5f,
                noise(clock, 5) * s * rotationScale * 0.5f,
                noise(clock, 3) * s * rotationScale);

        trauma = Math.max(0f, trauma - decayPerSecond * frame.dt());
    }

    @Override
    public boolean finished() {
        return trauma <= 0f;
    }

    private static float noise(float t, int seed) {
        float a = (float) Math.sin(t * 17.0 + seed * 1.7);
        float b = (float) Math.sin(t * 31.3 + seed * 4.1);
        return a * 0.6f + b * 0.4f;
    }
}

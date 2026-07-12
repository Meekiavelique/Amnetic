package com.meekdev.amnetic.client.camera;

// per-frame camera hook, register via CameraEffects.addModifier and add offsets into the frame
// composes with impulse/shake/spring and other modifiers, a CameraDirector override still wins
@FunctionalInterface
public interface CameraModifier {

    // called once per rendered frame
    void modify(CameraFrame frame);
}

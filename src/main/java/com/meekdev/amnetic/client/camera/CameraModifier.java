package com.meekdev.amnetic.client.camera;

// composes with impulse/shake/spring and other modifiers, a CameraDirector override still wins
@FunctionalInterface
public interface CameraModifier {
    void modify(CameraFrame frame);
}

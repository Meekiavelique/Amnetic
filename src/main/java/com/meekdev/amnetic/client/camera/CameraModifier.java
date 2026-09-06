package com.meekdev.amnetic.client.camera;

// per-frame camera hook, register via CameraEffects.addModifier and add offsets into the frame
// composes with other modifiers, a CameraDirector override still wins
@FunctionalInterface
public interface CameraModifier {

    // called once per rendered frame
    void modify(CameraFrame frame);

    // return true to be dropped after this frame, how the built in effects end themselves
    default boolean finished() {
        return false;
    }
}

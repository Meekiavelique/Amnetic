package com.meekdev.amnetic.client.camera;

@FunctionalInterface
public interface CameraModifier {
    void modify(CameraFrame frame);

    default boolean finished() {
        return false;
    }
}

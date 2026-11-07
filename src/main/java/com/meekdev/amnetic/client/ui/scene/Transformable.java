package com.meekdev.amnetic.client.ui.scene;

public interface Transformable {
    double posX();
    double posY();
    double posZ();
    void setPosition(double x, double y, double z);

    default boolean aimable() { return false; }
    default float dirX() { return 0f; }
    default float dirY() { return -1f; }
    default float dirZ() { return 0f; }
    default void setDirection(float x, float y, float z) {}

    default boolean resizable() { return false; }
    default float extent() { return 1f; }
    default void setExtent(float e) {}
}

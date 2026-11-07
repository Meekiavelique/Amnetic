package com.meekdev.amnetic.client.ui.scene;

public interface Selectable {
    Object target();

    String category();
    String displayName();

    default Transformable transform() { return null; }

    void renderInspector();

    default void remove() {}
}

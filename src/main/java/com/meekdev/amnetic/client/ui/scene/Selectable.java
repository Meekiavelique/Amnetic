package com.meekdev.amnetic.client.ui.scene;

/**
 * one thing the editor can list, select, inspect and optionally manipulate
 * adapters wrap the real objects (lights, decals, effects) so the core stays UI-agnostic
 * {@link #target()} gives stable selection identity since adapters are rebuilt each frame
 */
public interface Selectable {

    /** underlying object, used for selection identity */
    Object target();

    String category(); // "Lights", "Decals", "Effects"
    String displayName();

    /** gizmo target, null if the object isn't movable in the world */
    default Transformable transform() { return null; }

    /** draws this object's property controls into the inspector pane */
    void renderInspector();

    /** removes the object from the world (outliner "x") */
    default void remove() {}
}

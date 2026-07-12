package com.meekdev.amnetic.client.instanced;

public enum InstancePhase {
    BEFORE_ENTITIES,
    AFTER_ENTITIES,

    WORLD_LAST,
    // drawn after translucent terrain (water/glass) so billboards sort in front of it; see AmneticClient
    WORLD_TRANSLUCENT,
    OVERLAY
}

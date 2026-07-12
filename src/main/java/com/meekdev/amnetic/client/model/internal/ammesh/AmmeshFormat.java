package com.meekdev.amnetic.client.model.internal.ammesh;

final class AmmeshFormat {

    static final byte[] MAGIC = {'A', 'M', 'S', 'H'};
    // TODO : adds tangents, skinning, node hierarchy, animation clips, normal/ORM/emissive slots
    static final int VERSION = 2;

    private AmmeshFormat() {}
}

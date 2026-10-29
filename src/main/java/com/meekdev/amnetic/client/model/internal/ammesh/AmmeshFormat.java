package com.meekdev.amnetic.client.model.internal.ammesh;

public final class AmmeshFormat {

    static final byte[] MAGIC = {'A', 'M', 'S', 'H'};
    // 4 names images by their processed texture key instead of carrying the image bytes
    // 5 carries each part's levels of detail
    static final int VERSION = 5;
    // version 2 files have no vertex colours, still read
    static final int OLDEST = 2;
    // bump when the converter reads more of a gltf, so cached conversions are redone
    public static final int CONVERTER = 7;

    static final byte SLOT_NONE = 0;
    static final byte SLOT_BYTES = 1;
    static final byte SLOT_KEY = 2;

    private AmmeshFormat() {}
}

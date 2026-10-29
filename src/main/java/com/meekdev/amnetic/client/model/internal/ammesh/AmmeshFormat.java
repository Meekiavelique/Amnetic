package com.meekdev.amnetic.client.model.internal.ammesh;

public final class AmmeshFormat {
    static final byte[] MAGIC = {'A', 'M', 'S', 'H'};
    static final int VERSION = 5;
    static final int OLDEST = 2;
    public static final int CONVERTER = 7;

    static final byte SLOT_NONE = 0;
    static final byte SLOT_BYTES = 1;
    static final byte SLOT_KEY = 2;

    private AmmeshFormat() {}
}

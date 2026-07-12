package com.meekdev.amnetic.client.model.internal.ammesh;

import com.meekdev.amnetic.client.model.internal.ModelIR;
import com.meekdev.amnetic.client.model.internal.parse.GltfParser;
import net.minecraft.resources.Identifier;

public final class AmmeshConverter {

    private AmmeshConverter() {}

    public static byte[] convert(byte[] gltfBytes, Identifier source) {
        ModelIR ir = GltfParser.parse(gltfBytes, source);
        return AmmeshWriter.write(ir);
    }
}

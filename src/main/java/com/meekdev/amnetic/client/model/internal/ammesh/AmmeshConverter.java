package com.meekdev.amnetic.client.model.internal.ammesh;

import com.meekdev.amnetic.client.model.internal.ModelIR;
import com.meekdev.amnetic.client.model.internal.ModelLod;
import com.meekdev.amnetic.client.model.internal.parse.GltfParser;
import java.util.function.Function;
import net.minecraft.resources.Identifier;

public final class AmmeshConverter {
    private AmmeshConverter() {}

    public static byte[] convert(byte[] gltfBytes, Identifier source) {
        return convert(gltfBytes, source, null);
    }

    public static byte[] convert(byte[] gltfBytes, Identifier source, Function<String, byte[]> files) {
        ModelIR ir = GltfParser.parse(gltfBytes, source, files);
        ModelLod.build(ir);
        return AmmeshWriter.write(ir);
    }
}

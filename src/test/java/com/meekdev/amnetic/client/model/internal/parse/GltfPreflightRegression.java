package com.meekdev.amnetic.client.model.internal.parse;

import com.meekdev.amnetic.client.content.Hostile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

public final class GltfPreflightRegression {
    private static final String ASSET = "\"asset\":{\"version\":\"2.0\"}";
    private static final int DEEP = 100_000;
    private static final int TRIANGLE_BYTES = 36;

    private GltfPreflightRegression() {}

    public static void verify() {
        Hostile.require(GltfParser.parse(triangle(), null).parts().size() == 1, "a real glTF triangle parses");
        System.out.println("PASS: a real glTF triangle parses");
        Hostile.refused("glTF nodes that are each other's child", "loop",
                () -> parse("\"nodes\":[{\"children\":[1]},{\"children\":[0]}]"));
        Hostile.refused("glTF node that is its own child", "loop", () -> parse("\"nodes\":[{\"children\":[0]}]"));
        Hostile.refused("glTF node with two parents", "two parents",
                () -> parse("\"nodes\":[{\"children\":[2]},{\"children\":[2]},{}]"));
        Hostile.refused("glTF node with a missing child", "missing child", () -> parse("\"nodes\":[{\"children\":[7]}]"));
        Hostile.refused("glTF accessor of two billion elements with no data", "claim over",
                () -> parse("\"accessors\":[{\"componentType\":5126,\"count\":2000000000,\"type\":\"VEC3\"}]"));
        Hostile.refused("glTF accessor running past its buffer view", "runs past its buffer view", () -> parse(
                "\"buffers\":[{\"byteLength\":12}],\"bufferViews\":[{\"buffer\":0,\"byteLength\":12}],"
                        + "\"accessors\":[{\"bufferView\":0,\"componentType\":5126,\"count\":1000,\"type\":\"VEC3\"}]"));
        Hostile.refused("glTF nested 100000 brackets deep", "nested deeper than",
                () -> parse("\"extras\":" + "[".repeat(DEEP) + "]".repeat(DEEP)));
    }

    private static byte[] triangle() {
        ByteBuffer corners = ByteBuffer.allocate(TRIANGLE_BYTES).order(ByteOrder.LITTLE_ENDIAN);
        corners.putFloat(0).putFloat(0).putFloat(0).putFloat(1).putFloat(0).putFloat(0).putFloat(0).putFloat(1).putFloat(0);
        String uri = "data:application/octet-stream;base64," + Base64.getEncoder().encodeToString(corners.array());
        return ("{" + ASSET + ",\"buffers\":[{\"byteLength\":36,\"uri\":\"" + uri + "\"}],"
                + "\"bufferViews\":[{\"buffer\":0,\"byteLength\":36}],"
                + "\"accessors\":[{\"bufferView\":0,\"componentType\":5126,\"count\":3,\"type\":\"VEC3\","
                + "\"min\":[0,0,0],\"max\":[1,1,0]}],"
                + "\"meshes\":[{\"primitives\":[{\"attributes\":{\"POSITION\":0}}]}],"
                + "\"nodes\":[{\"mesh\":0}],\"scenes\":[{\"nodes\":[0]}]}").getBytes(StandardCharsets.UTF_8);
    }

    private static void parse(String fields) {
        GltfParser.parse(("{" + ASSET + "," + fields + "}").getBytes(StandardCharsets.UTF_8), null);
    }
}

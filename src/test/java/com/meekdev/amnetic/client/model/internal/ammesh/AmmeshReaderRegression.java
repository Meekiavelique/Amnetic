package com.meekdev.amnetic.client.model.internal.ammesh;

import com.meekdev.amnetic.client.content.Hostile;
import com.meekdev.amnetic.client.model.internal.ModelIR;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public final class AmmeshReaderRegression {
    private static final int MATRIX_FLOATS = 16;

    private AmmeshReaderRegression() {}

    public static void verify() throws IOException {
        Hostile.refused(".ammesh claiming two billion vertices", "bytes are left", () -> AmmeshReader.read(claimingVertices(Integer.MAX_VALUE)));
        Hostile.refused(".ammesh claiming a negative vertex count", "bytes are left", () -> AmmeshReader.read(claimingVertices(-1)));
        Hostile.refused(".ammesh node that is its own parent", "loop", () -> AmmeshReader.read(AmmeshWriter.write(nodes(0))));
        Hostile.refused(".ammesh nodes that are each other's parent", "loop", () -> AmmeshReader.read(AmmeshWriter.write(nodes(1, 0))));
        Hostile.refused(".ammesh node hanging from a missing node", "missing node", () -> AmmeshReader.read(AmmeshWriter.write(nodes(5))));

        ModelIR tree = nodes(-1, 0, 1);
        tree.nodes().get(0).children = new int[] {1};
        tree.nodes().get(1).children = new int[] {2};
        Hostile.require(AmmeshReader.read(AmmeshWriter.write(tree)).nodes().size() == 3, "a real node tree reads");
        System.out.println("PASS: a real .ammesh node tree reads");
    }

    private static byte[] claimingVertices(int count) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        out.write(AmmeshFormat.MAGIC);
        out.writeInt(AmmeshFormat.VERSION);
        out.writeUTF("hostile");
        out.writeInt(1);
        out.writeUTF("part");
        out.writeInt(-1);
        out.writeInt(-1);
        for (int i = 0; i < MATRIX_FLOATS; i++) out.writeFloat(0);
        out.writeInt(count);
        return bytes.toByteArray();
    }

    private static ModelIR nodes(int... parents) {
        ModelIR ir = new ModelIR();
        for (int parent : parents) {
            ModelIR.Node node = new ModelIR.Node();
            node.parent = parent;
            ir.addNode(node);
        }
        return ir;
    }
}

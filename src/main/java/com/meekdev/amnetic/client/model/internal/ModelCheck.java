package com.meekdev.amnetic.client.model.internal;

import com.meekdev.amnetic.client.content.Bounded;
import com.meekdev.amnetic.client.content.ContentLimits;
import java.util.Arrays;
import java.util.List;

public final class ModelCheck {
    private static final int ROOT = -1;
    private static final int UNKNOWN = -2;

    private ModelCheck() {}

    public static void check(ModelIR ir) {
        List<ModelIR.Node> nodes = ir.nodes();
        Bounded.require(nodes.size() <= ContentLimits.MODEL_NODES, "a model with " + nodes.size() + " nodes, over " + ContentLimits.MODEL_NODES);
        int[] parents = new int[nodes.size()];
        for (int i = 0; i < parents.length; i++) parents[i] = nodes.get(i).parent;
        trees(parents);
        for (int i = 0; i < nodes.size(); i++) {
            for (int child : nodes.get(i).children) {
                Bounded.require(child >= 0 && child < nodes.size() && nodes.get(child).parent == i,
                        "node " + i + " lists child " + child + " that is not its child");
            }
        }
        for (ModelIR.Part part : ir.parts()) part(part, nodes.size());
        for (ModelIR.Animation animation : ir.animations()) {
            for (ModelIR.Channel channel : animation.channels) {
                Bounded.require(channel.node >= 0 && channel.node < nodes.size(), "animation " + animation.name + " moves missing node " + channel.node);
            }
        }
    }

    private static void part(ModelIR.Part part, int nodes) {
        Bounded.require(part.nodeIndex >= ROOT && part.nodeIndex < nodes, "part " + part.name + " hangs from missing node " + part.nodeIndex);
        Bounded.require(part.vertices.length % ModelIR.VERTEX_STRIDE_FLOATS == 0, "part " + part.name + " has a partial vertex");
        int vertices = part.vertexCount();
        indices(part, part.indices, vertices);
        if (part.lods != null) for (int[] level : part.lods) indices(part, level, vertices);
    }

    private static void indices(ModelIR.Part part, int[] indices, int vertices) {
        if (indices == null) return;
        for (int index : indices) {
            Bounded.require(index >= 0 && index < vertices, "part " + part.name + " points at vertex " + index + " of " + vertices);
        }
    }

    public static void trees(int[] parents) {
        int[] depth = new int[parents.length];
        Arrays.fill(depth, UNKNOWN);
        for (int i = 0; i < parents.length; i++) {
            int steps = 0;
            int at = i;
            while (at != ROOT && depth[at] == UNKNOWN) {
                int parent = parents[at];
                Bounded.require(parent >= ROOT && parent < parents.length, "node " + at + " hangs from missing node " + parent);
                Bounded.require(++steps <= ContentLimits.NODE_DEPTH, "the parents of node " + i + " loop or nest over "
                        + ContentLimits.NODE_DEPTH + " deep");
                at = parent;
            }
            int reached = (at == ROOT ? ROOT : depth[at]) + steps;
            Bounded.require(reached <= ContentLimits.NODE_DEPTH, "node " + i + " nests over " + ContentLimits.NODE_DEPTH + " deep");
            for (at = i; at != ROOT && depth[at] == UNKNOWN; at = parents[at]) depth[at] = reached--;
        }
    }
}

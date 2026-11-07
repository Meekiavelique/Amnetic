package com.meekdev.amnetic.client.model.internal.parse;

import com.meekdev.amnetic.client.content.Bounded;
import com.meekdev.amnetic.client.content.ContentLimits;
import com.meekdev.amnetic.client.content.JsonDepth;
import com.meekdev.amnetic.client.content.RejectedContentException;
import com.meekdev.amnetic.client.model.internal.ModelCheck;
import de.javagl.jgltf.impl.v2.Accessor;
import de.javagl.jgltf.impl.v2.Buffer;
import de.javagl.jgltf.impl.v2.BufferView;
import de.javagl.jgltf.impl.v2.GlTF;
import de.javagl.jgltf.impl.v2.Node;
import de.javagl.jgltf.model.io.GltfAsset;
import de.javagl.jgltf.model.io.v2.GltfAssetV2;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.lwjgl.opengl.GL11;

final class GltfPreflight {
    private static final int GLB_MAGIC = 0x46546C67;
    private static final int JSON_CHUNK = 0x4E4F534A;
    private static final int GLB_HEADER = 12;
    private static final int CHUNK_HEADER = 8;
    private static final int NO_PARENT = -1;

    private static final Map<Integer, Integer> COMPONENT_BYTES = Map.of(
            GL11.GL_BYTE, Byte.BYTES, GL11.GL_UNSIGNED_BYTE, Byte.BYTES, GL11.GL_SHORT, Short.BYTES,
            GL11.GL_UNSIGNED_SHORT, Short.BYTES, GL11.GL_UNSIGNED_INT, Integer.BYTES, GL11.GL_FLOAT, Float.BYTES);
    private static final Map<String, Integer> COMPONENTS = Map.of(
            "SCALAR", 1, "VEC2", 2, "VEC3", 3, "VEC4", 4, "MAT2", 4, "MAT3", 9, "MAT4", 16);

    record JsonChunk(int from, int length) {}

    private GltfPreflight() {}

    static JsonChunk json(byte[] bytes) {
        ByteBuffer in = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        if (bytes.length < GLB_HEADER + CHUNK_HEADER || in.getInt(0) != GLB_MAGIC) return new JsonChunk(0, bytes.length);
        int length = in.getInt(GLB_HEADER);
        int from = GLB_HEADER + CHUNK_HEADER;
        if (in.getInt(GLB_HEADER + 4) != JSON_CHUNK || length < 0 || from + length > bytes.length) return null;
        return new JsonChunk(from, length);
    }

    static void checkJson(byte[] bytes) {
        JsonChunk chunk = json(bytes);
        if (chunk == null) throw new RejectedContentException("a .glb whose json chunk is malformed");
        JsonDepth.check(bytes, chunk.from(), chunk.length());
    }

    static void check(GltfAsset asset) {
        if (!(asset instanceof GltfAssetV2 v2)) throw new RejectedContentException("only glTF 2.0 files can be used");
        GlTF gltf = v2.getGltf();
        accessors(list(gltf.getAccessors()), list(gltf.getBufferViews()), list(gltf.getBuffers()));
        nodes(list(gltf.getNodes()));
    }

    private static void accessors(List<Accessor> accessors, List<BufferView> views, List<Buffer> buffers) {
        long total = 0;
        for (BufferView view : views) {
            int buffer = or(view.getBuffer(), -1);
            Bounded.require(buffer >= 0 && buffer < buffers.size(), "a glTF buffer view names missing buffer " + buffer);
            long end = (long) or(view.getByteOffset(), 0) + or(view.getByteLength(), 0);
            Bounded.require(end <= or(buffers.get(buffer).getByteLength(), 0), "a glTF buffer view runs past its buffer");
        }
        for (Accessor accessor : accessors) {
            Integer component = COMPONENT_BYTES.get(accessor.getComponentType());
            Integer components = COMPONENTS.get(accessor.getType());
            Bounded.require(component != null && components != null, "a glTF accessor of unknown type " + accessor.getType());
            int count = or(accessor.getCount(), 0);
            Bounded.require(count >= 0, "a glTF accessor with " + count + " elements");
            long element = (long) component * components;
            total += element * count;
            Bounded.require(total <= ContentLimits.MODEL_DATA_BYTES, "the glTF accessors claim over " + ContentLimits.MODEL_DATA_BYTES + " bytes");
            if (accessor.getSparse() != null) {
                int sparse = or(accessor.getSparse().getCount(), 0);
                Bounded.require(sparse >= 0 && sparse <= count, "a sparse glTF accessor replaces " + sparse + " of " + count + " elements");
            }
            if (accessor.getBufferView() != null && count > 0) {
                int index = accessor.getBufferView();
                Bounded.require(index >= 0 && index < views.size(), "a glTF accessor names missing buffer view " + index);
                BufferView view = views.get(index);
                long stride = or(view.getByteStride(), (int) element);
                long end = or(accessor.getByteOffset(), 0) + stride * (count - 1) + element;
                Bounded.require(end <= or(view.getByteLength(), 0), "a glTF accessor of " + count + " elements runs past its buffer view");
            }
        }
    }

    private static void nodes(List<Node> nodes) {
        Bounded.require(nodes.size() <= ContentLimits.MODEL_NODES, "a glTF with " + nodes.size() + " nodes");
        int[] parents = new int[nodes.size()];
        Arrays.fill(parents, NO_PARENT);
        for (int i = 0; i < nodes.size(); i++) {
            for (Integer child : list(nodes.get(i).getChildren())) {
                Bounded.require(child != null && child >= 0 && child < nodes.size(), "glTF node " + i + " names missing child " + child);
                Bounded.require(parents[child] == NO_PARENT, "glTF node " + child + " has two parents");
                parents[child] = i;
            }
        }
        ModelCheck.trees(parents);
    }

    private static <T> List<T> list(List<T> maybe) {
        return maybe == null ? List.of() : maybe;
    }

    private static int or(Integer value, int fallback) {
        return value == null ? fallback : value;
    }
}

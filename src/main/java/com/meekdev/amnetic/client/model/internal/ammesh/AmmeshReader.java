package com.meekdev.amnetic.client.model.internal.ammesh;

import com.meekdev.amnetic.client.content.Bounded;
import com.meekdev.amnetic.client.content.RejectedContentException;
import com.meekdev.amnetic.client.model.TextureFilter;
import com.meekdev.amnetic.client.model.ModelLoadException;
import com.meekdev.amnetic.client.model.internal.ModelCheck;
import com.meekdev.amnetic.client.model.internal.ModelIR;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4f;

public final class AmmeshReader {

    private static final int MATRIX_FLOATS = 16;
    private static final int MATRIX_BYTES = MATRIX_FLOATS * Float.BYTES;

    private AmmeshReader() {}

    public static ModelIR read(byte[] bytes) {
        return read(ByteBuffer.wrap(bytes));
    }

    // reads from the buffer's position, a mapped file works, arrays come out in one copy each
    public static ModelIR read(ByteBuffer bytes) {
        ByteBuffer in = bytes.slice().order(ByteOrder.BIG_ENDIAN);
        try {
            byte[] magic = new byte[4];
            in.get(magic);
            for (int i = 0; i < 4; i++) {
                if (magic[i] != AmmeshFormat.MAGIC[i]) throw new ModelLoadException("Not an .ammesh file (bad magic)");
            }
            int version = in.getInt();
            if (version < AmmeshFormat.OLDEST || version > AmmeshFormat.VERSION) throw new ModelLoadException("Unsupported .ammesh version: " + version);

            ModelIR ir = new ModelIR();
            ir.setName(readUTF(in));

            int partCount = Bounded.count(in, 1, "parts");
            for (int i = 0; i < partCount; i++) readPart(in, ir, version);

            int materialCount = Bounded.count(in, 1, "materials");
            for (int i = 0; i < materialCount; i++) ir.addMaterial(readMaterial(in, version));

            int nodeCount = Bounded.count(in, 1, "nodes");
            for (int i = 0; i < nodeCount; i++) ir.addNode(readNode(in));

            int animCount = Bounded.count(in, 1, "animations");
            for (int i = 0; i < animCount; i++) ir.animations().add(readAnimation(in));

            ModelCheck.check(ir);
            return ir;
        } catch (RejectedContentException e) {
            throw new ModelLoadException("Refused .ammesh: " + e.getMessage(), e);
        } catch (IOException | BufferUnderflowException | IllegalArgumentException | IndexOutOfBoundsException e) {
            throw new ModelLoadException("Failed to read .ammesh", e);
        }
    }

    // a counted array, its count checked against the bytes left before anything is allocated
    private static float[] floats(ByteBuffer in, String what) {
        return floats(in, Bounded.count(in, Float.BYTES, what));
    }

    private static int[] ints(ByteBuffer in, String what) {
        return ints(in, Bounded.count(in, Integer.BYTES, what));
    }

    private static float[] floats(ByteBuffer in, int count) {
        float[] out = new float[count];
        in.asFloatBuffer().get(out);
        in.position(in.position() + count * Float.BYTES);
        return out;
    }

    private static int[] ints(ByteBuffer in, int count) {
        int[] out = new int[count];
        in.asIntBuffer().get(out);
        in.position(in.position() + count * Integer.BYTES);
        return out;
    }

    // the modified utf-8 writeUTF makes, decoded by the jdk so odd names come back the same
    private static String readUTF(ByteBuffer in) throws IOException {
        int length = Short.toUnsignedInt(in.getShort(in.position()));
        byte[] raw = new byte[2 + length];
        in.get(raw);
        return new DataInputStream(new ByteArrayInputStream(raw)).readUTF();
    }

    private static Matrix4f matrix(ByteBuffer in) {
        return new Matrix4f().set(floats(in, MATRIX_FLOATS));
    }

    private static boolean readBoolean(ByteBuffer in) {
        return in.get() != 0;
    }

    private static void readPart(ByteBuffer in, ModelIR ir, int version) throws IOException {
        String name = readUTF(in);
        int materialIndex = in.getInt();
        int nodeIndex = in.getInt();

        Matrix4f transform = matrix(in);

        float[] vertices = floats(in, "vertices");

        float[] tangents = null;
        if (readBoolean(in)) tangents = floats(in, "tangents");

        int indexCount = Bounded.count(in, Integer.BYTES, "indices");
        int[] indices = indexCount > 0 ? ints(in, indexCount) : null;

        ModelIR.Part part = new ModelIR.Part(vertices, tangents, indices, materialIndex, nodeIndex, transform, name);

        if (readBoolean(in)) {
            float[] jointIndices = floats(in, "joint indices");
            float[] jointWeights = floats(in, "joint weights");
            int[] jointNodes = ints(in, "joints");
            Bounded.require((long) jointNodes.length * MATRIX_BYTES <= in.remaining(), "a skin claims more joints than it holds");
            Matrix4f[] inverseBind = new Matrix4f[jointNodes.length];
            for (int i = 0; i < inverseBind.length; i++) inverseBind[i] = matrix(in);
            part.jointIndices = jointIndices;
            part.jointWeights = jointWeights;
            part.jointNodes = jointNodes;
            part.inverseBind = inverseBind;
        }

        if (version >= 3 && readBoolean(in)) part.colors = floats(in, "colors");

        int levels = version >= 5 ? Bounded.count(in, 1, "detail levels") : 0;
        if (levels > 0) {
            int[][] lods = new int[levels][];
            float[] errors = new float[levels];
            lods[0] = indices;
            for (int n = 1; n < levels; n++) {
                errors[n] = in.getFloat();
                lods[n] = readBoolean(in) ? lods[n - 1] : ints(in, "detail indices");
            }
            part.lodErrors = errors;
            part.lods = lods;
        }

        ir.addPart(part);
    }

    private static ModelIR.Material readMaterial(ByteBuffer in, int version) throws IOException {
        ModelIR.Material mat = new ModelIR.Material();
        mat.name = readUTF(in);
        mat.baseR = in.getFloat(); mat.baseG = in.getFloat(); mat.baseB = in.getFloat(); mat.baseA = in.getFloat();
        mat.metallic = in.getFloat(); mat.roughness = in.getFloat(); mat.transmission = in.getFloat();
        mat.emR = in.getFloat(); mat.emG = in.getFloat(); mat.emB = in.getFloat();
        mat.alphaCutoff = in.getFloat();
        int flags = in.getInt();
        if ((flags & 1) != 0) mat.baseColorFilter = TextureFilter.NEAREST;
        mat.unlit = (flags & 2) != 0;
        mat.shadingModelId = 0;
        mat.blend = readBoolean(in);
        mat.doubleSided = readBoolean(in);

        TextureSlot base = readTextureSlot(in, version);
        mat.baseColorTexture = base.id;
        mat.baseColorImageBytes = base.bytes;
        mat.baseColorImageKey = base.key;

        TextureSlot normal = readTextureSlot(in, version);
        mat.normalTexture = normal.id;
        mat.normalImageBytes = normal.bytes;
        mat.normalImageKey = normal.key;

        TextureSlot orm = readTextureSlot(in, version);
        mat.ormTexture = orm.id;
        mat.ormImageBytes = orm.bytes;
        mat.ormImageKey = orm.key;

        TextureSlot emissive = readTextureSlot(in, version);
        mat.emissiveTexture = emissive.id;
        mat.emissiveImageBytes = emissive.bytes;
        mat.emissiveImageKey = emissive.key;

        return mat;
    }

    private record TextureSlot(Identifier id, byte[] bytes, String key) {}

    private static TextureSlot readTextureSlot(ByteBuffer in, int version) throws IOException {
        Identifier id = readBoolean(in) ? Identifier.parse(readUTF(in)) : null;
        // before 4 a slot only said whether bytes follow
        byte kind = version >= 4 ? in.get() : readBoolean(in) ? AmmeshFormat.SLOT_BYTES : AmmeshFormat.SLOT_NONE;
        if (kind == AmmeshFormat.SLOT_KEY) return new TextureSlot(id, null, readUTF(in));
        if (kind != AmmeshFormat.SLOT_BYTES) return new TextureSlot(id, null, null);
        byte[] bytes = new byte[Bounded.count(in, 1, "image bytes")];
        in.get(bytes);
        return new TextureSlot(id, bytes, null);
    }

    private static ModelIR.Node readNode(ByteBuffer in) throws IOException {
        ModelIR.Node node = new ModelIR.Node();
        node.name = readUTF(in);
        node.parent = in.getInt();
        node.t.set(in.getFloat(), in.getFloat(), in.getFloat());
        node.r.set(in.getFloat(), in.getFloat(), in.getFloat(), in.getFloat());
        node.s.set(in.getFloat(), in.getFloat(), in.getFloat());
        node.children = ints(in, "children");
        node.rebuildLocal();
        return node;
    }

    private static ModelIR.Animation readAnimation(ByteBuffer in) throws IOException {
        ModelIR.Animation anim = new ModelIR.Animation();
        anim.name = readUTF(in);
        anim.duration = in.getFloat();
        int channelCount = Bounded.count(in, 1, "channels");
        for (int i = 0; i < channelCount; i++) {
            ModelIR.Channel ch = new ModelIR.Channel();
            ch.node = in.getInt();
            ch.path = ModelIR.Path.valueOf(readUTF(in));
            ch.interp = ModelIR.Interp.valueOf(readUTF(in));
            ch.times = floats(in, "key times");
            ch.values = floats(in, "key values");
            anim.channels.add(ch);
        }
        return anim;
    }
}

package com.meekdev.amnetic.client.model.internal.ammesh;

import com.meekdev.amnetic.client.model.TextureFilter;
import com.meekdev.amnetic.client.model.ModelLoadException;
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

            int partCount = in.getInt();
            for (int i = 0; i < partCount; i++) readPart(in, ir, version);

            int materialCount = in.getInt();
            for (int i = 0; i < materialCount; i++) ir.addMaterial(readMaterial(in, version));

            int nodeCount = in.getInt();
            for (int i = 0; i < nodeCount; i++) ir.addNode(readNode(in));

            int animCount = in.getInt();
            for (int i = 0; i < animCount; i++) ir.animations().add(readAnimation(in));

            return ir;
        } catch (IOException | BufferUnderflowException | IllegalArgumentException | IndexOutOfBoundsException | NegativeArraySizeException e) {
            throw new ModelLoadException("Failed to read .ammesh", e);
        }
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

    private static boolean readBoolean(ByteBuffer in) {
        return in.get() != 0;
    }

    private static void readPart(ByteBuffer in, ModelIR ir, int version) throws IOException {
        String name = readUTF(in);
        int materialIndex = in.getInt();
        int nodeIndex = in.getInt();

        Matrix4f transform = new Matrix4f().set(floats(in, 16));

        float[] vertices = floats(in, in.getInt());

        float[] tangents = null;
        if (readBoolean(in)) tangents = floats(in, in.getInt());

        int indexCount = in.getInt();
        int[] indices = indexCount > 0 ? ints(in, indexCount) : null;

        ModelIR.Part part = new ModelIR.Part(vertices, tangents, indices, materialIndex, nodeIndex, transform, name);

        if (readBoolean(in)) {
            float[] jointIndices = floats(in, in.getInt());
            float[] jointWeights = floats(in, in.getInt());
            int[] jointNodes = ints(in, in.getInt());
            Matrix4f[] inverseBind = new Matrix4f[jointNodes.length];
            for (int i = 0; i < inverseBind.length; i++) inverseBind[i] = new Matrix4f().set(floats(in, 16));
            part.jointIndices = jointIndices;
            part.jointWeights = jointWeights;
            part.jointNodes = jointNodes;
            part.inverseBind = inverseBind;
        }

        if (version >= 3 && readBoolean(in)) part.colors = floats(in, in.getInt());

        int levels = version >= 5 ? in.getInt() : 0;
        if (levels > 0) {
            int[][] lods = new int[levels][];
            float[] errors = new float[levels];
            lods[0] = indices;
            for (int n = 1; n < levels; n++) {
                errors[n] = in.getFloat();
                lods[n] = readBoolean(in) ? lods[n - 1] : ints(in, in.getInt());
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
        byte[] bytes = new byte[in.getInt()];
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
        node.children = ints(in, in.getInt());
        node.rebuildLocal();
        return node;
    }

    private static ModelIR.Animation readAnimation(ByteBuffer in) throws IOException {
        ModelIR.Animation anim = new ModelIR.Animation();
        anim.name = readUTF(in);
        anim.duration = in.getFloat();
        int channelCount = in.getInt();
        for (int i = 0; i < channelCount; i++) {
            ModelIR.Channel ch = new ModelIR.Channel();
            ch.node = in.getInt();
            ch.path = ModelIR.Path.valueOf(readUTF(in));
            ch.interp = ModelIR.Interp.valueOf(readUTF(in));
            ch.times = floats(in, in.getInt());
            ch.values = floats(in, in.getInt());
            anim.channels.add(ch);
        }
        return anim;
    }
}

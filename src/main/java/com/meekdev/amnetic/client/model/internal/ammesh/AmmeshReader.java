package com.meekdev.amnetic.client.model.internal.ammesh;

import com.meekdev.amnetic.client.model.ModelLoadException;
import com.meekdev.amnetic.client.model.internal.ModelIR;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4f;

public final class AmmeshReader {

    private AmmeshReader() {}

    public static ModelIR read(byte[] bytes) {
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes))) {
            byte[] magic = new byte[4];
            in.readFully(magic);
            for (int i = 0; i < 4; i++) {
                if (magic[i] != AmmeshFormat.MAGIC[i]) throw new ModelLoadException("Not an .ammesh file (bad magic)");
            }
            int version = in.readInt();
            if (version != AmmeshFormat.VERSION) throw new ModelLoadException("Unsupported .ammesh version: " + version);

            ModelIR ir = new ModelIR();
            ir.setName(in.readUTF());

            int partCount = in.readInt();
            for (int i = 0; i < partCount; i++) readPart(in, ir);

            int materialCount = in.readInt();
            for (int i = 0; i < materialCount; i++) ir.addMaterial(readMaterial(in));

            int nodeCount = in.readInt();
            for (int i = 0; i < nodeCount; i++) ir.addNode(readNode(in));

            int animCount = in.readInt();
            for (int i = 0; i < animCount; i++) ir.animations().add(readAnimation(in));

            return ir;
        } catch (IOException e) {
            throw new ModelLoadException("Failed to read .ammesh", e);
        }
    }

    private static void readPart(DataInputStream in, ModelIR ir) throws IOException {
        String name = in.readUTF();
        int materialIndex = in.readInt();
        int nodeIndex = in.readInt();

        float[] t = new float[16];
        for (int i = 0; i < 16; i++) t[i] = in.readFloat();
        Matrix4f transform = new Matrix4f().set(t);

        int vertexCount = in.readInt();
        float[] vertices = new float[vertexCount];
        for (int i = 0; i < vertexCount; i++) vertices[i] = in.readFloat();

        float[] tangents = null;
        if (in.readBoolean()) {
            int tangentCount = in.readInt();
            tangents = new float[tangentCount];
            for (int i = 0; i < tangentCount; i++) tangents[i] = in.readFloat();
        }

        int indexCount = in.readInt();
        int[] indices = null;
        if (indexCount > 0) {
            indices = new int[indexCount];
            for (int i = 0; i < indexCount; i++) indices[i] = in.readInt();
        }

        ModelIR.Part part = new ModelIR.Part(vertices, tangents, indices, materialIndex, nodeIndex, transform, name);

        if (in.readBoolean()) {
            float[] jointIndices = new float[in.readInt()];
            for (int i = 0; i < jointIndices.length; i++) jointIndices[i] = in.readFloat();
            float[] jointWeights = new float[in.readInt()];
            for (int i = 0; i < jointWeights.length; i++) jointWeights[i] = in.readFloat();
            int[] jointNodes = new int[in.readInt()];
            for (int i = 0; i < jointNodes.length; i++) jointNodes[i] = in.readInt();
            Matrix4f[] inverseBind = new Matrix4f[jointNodes.length];
            for (int i = 0; i < inverseBind.length; i++) {
                float[] m = new float[16];
                for (int j = 0; j < 16; j++) m[j] = in.readFloat();
                inverseBind[i] = new Matrix4f().set(m);
            }
            part.jointIndices = jointIndices;
            part.jointWeights = jointWeights;
            part.jointNodes = jointNodes;
            part.inverseBind = inverseBind;
        }

        ir.addPart(part);
    }

    private static ModelIR.Material readMaterial(DataInputStream in) throws IOException {
        ModelIR.Material mat = new ModelIR.Material();
        mat.name = in.readUTF();
        mat.baseR = in.readFloat(); mat.baseG = in.readFloat(); mat.baseB = in.readFloat(); mat.baseA = in.readFloat();
        mat.metallic = in.readFloat(); mat.roughness = in.readFloat(); mat.transmission = in.readFloat();
        mat.emR = in.readFloat(); mat.emG = in.readFloat(); mat.emB = in.readFloat();
        mat.alphaCutoff = in.readFloat();
        in.readInt(); // legacy shading model id slot, runtime-order-dependent so always ignored
        mat.shadingModelId = 0;
        mat.blend = in.readBoolean();
        mat.doubleSided = in.readBoolean();

        TextureSlot base = readTextureSlot(in);
        mat.baseColorTexture = base.id;
        mat.baseColorImageBytes = base.bytes;

        TextureSlot normal = readTextureSlot(in);
        mat.normalTexture = normal.id;
        mat.normalImageBytes = normal.bytes;

        TextureSlot orm = readTextureSlot(in);
        mat.ormTexture = orm.id;
        mat.ormImageBytes = orm.bytes;

        TextureSlot emissive = readTextureSlot(in);
        mat.emissiveTexture = emissive.id;
        mat.emissiveImageBytes = emissive.bytes;

        return mat;
    }

    private record TextureSlot(Identifier id, byte[] bytes) {}

    private static TextureSlot readTextureSlot(DataInputStream in) throws IOException {
        Identifier id = in.readBoolean() ? Identifier.parse(in.readUTF()) : null;
        byte[] bytes = null;
        if (in.readBoolean()) {
            bytes = new byte[in.readInt()];
            in.readFully(bytes);
        }
        return new TextureSlot(id, bytes);
    }

    private static ModelIR.Node readNode(DataInputStream in) throws IOException {
        ModelIR.Node node = new ModelIR.Node();
        node.name = in.readUTF();
        node.parent = in.readInt();
        node.t.set(in.readFloat(), in.readFloat(), in.readFloat());
        node.r.set(in.readFloat(), in.readFloat(), in.readFloat(), in.readFloat());
        node.s.set(in.readFloat(), in.readFloat(), in.readFloat());
        int[] children = new int[in.readInt()];
        for (int i = 0; i < children.length; i++) children[i] = in.readInt();
        node.children = children;
        node.rebuildLocal();
        return node;
    }

    private static ModelIR.Animation readAnimation(DataInputStream in) throws IOException {
        ModelIR.Animation anim = new ModelIR.Animation();
        anim.name = in.readUTF();
        anim.duration = in.readFloat();
        int channelCount = in.readInt();
        for (int i = 0; i < channelCount; i++) {
            ModelIR.Channel ch = new ModelIR.Channel();
            ch.node = in.readInt();
            ch.path = ModelIR.Path.valueOf(in.readUTF());
            ch.interp = ModelIR.Interp.valueOf(in.readUTF());
            float[] times = new float[in.readInt()];
            for (int j = 0; j < times.length; j++) times[j] = in.readFloat();
            float[] values = new float[in.readInt()];
            for (int j = 0; j < values.length; j++) values[j] = in.readFloat();
            ch.times = times;
            ch.values = values;
            anim.channels.add(ch);
        }
        return anim;
    }
}

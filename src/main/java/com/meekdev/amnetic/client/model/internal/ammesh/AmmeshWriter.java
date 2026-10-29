package com.meekdev.amnetic.client.model.internal.ammesh;

import com.meekdev.amnetic.client.model.TextureFilter;
import com.meekdev.amnetic.client.model.internal.ModelIR;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4f;

public final class AmmeshWriter {

    private AmmeshWriter() {}

    // keeps an image somewhere else and names it by a key, null keeps the bytes in the file
    public interface Images {
        String put(byte[] image, boolean srgb);
    }

    public static byte[] write(ModelIR ir) {
        return write(ir, null);
    }

    public static byte[] write(ModelIR ir, Images images) {
        ByteArrayOutputStream buf = new ByteArrayOutputStream(1 << 16);
        try (DataOutputStream out = new DataOutputStream(buf)) {
            out.write(AmmeshFormat.MAGIC);
            out.writeInt(AmmeshFormat.VERSION);
            out.writeUTF(ir.name());

            out.writeInt(ir.parts().size());
            for (ModelIR.Part part : ir.parts()) writePart(out, part);

            out.writeInt(ir.materials().size());
            for (ModelIR.Material mat : ir.materials()) writeMaterial(out, mat, images);

            out.writeInt(ir.nodes().size());
            for (ModelIR.Node node : ir.nodes()) writeNode(out, node);

            out.writeInt(ir.animations().size());
            for (ModelIR.Animation anim : ir.animations()) writeAnimation(out, anim);

            return buf.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write .ammesh", e);
        }
    }

    private static void writePart(DataOutputStream out, ModelIR.Part part) throws IOException {
        out.writeUTF(part.name);
        out.writeInt(part.materialIndex);
        out.writeInt(part.nodeIndex);

        float[] t = new float[16];
        part.transform.get(t);
        writeFloats(out, t);

        out.writeInt(part.vertices.length);
        writeFloats(out, part.vertices);

        out.writeBoolean(part.hasTangents());
        if (part.hasTangents()) {
            out.writeInt(part.tangents.length);
            writeFloats(out, part.tangents);
        }

        boolean indexed = part.hasIndices();
        out.writeInt(indexed ? part.indices.length : 0);
        if (indexed) writeInts(out, part.indices);

        out.writeBoolean(part.hasSkin());
        if (part.hasSkin()) {
            out.writeInt(part.jointIndices.length);
            writeFloats(out, part.jointIndices);
            out.writeInt(part.jointWeights.length);
            writeFloats(out, part.jointWeights);
            out.writeInt(part.jointNodes.length);
            writeInts(out, part.jointNodes);
            for (Matrix4f ib : part.inverseBind) {
                float[] m = new float[16];
                ib.get(m);
                writeFloats(out, m);
            }
        }

        out.writeBoolean(part.hasColors());
        if (part.hasColors()) {
            out.writeInt(part.colors.length);
            writeFloats(out, part.colors);
        }

        // the coarser levels after the full one, a level the same as the one before is only said so
        int[][] lods = part.lods;
        float[] errors = part.lodErrors;
        int levels = lods == null || errors == null ? 0 : Math.min(lods.length, errors.length);
        out.writeInt(levels);
        for (int n = 1; n < levels; n++) {
            out.writeFloat(errors[n]);
            boolean same = lods[n] == lods[n - 1];
            out.writeBoolean(same);
            if (same) continue;
            out.writeInt(lods[n].length);
            writeInts(out, lods[n]);
        }
    }

    private static void writeMaterial(DataOutputStream out, ModelIR.Material mat, Images images) throws IOException {
        out.writeUTF(mat.name);
        out.writeFloat(mat.baseR); out.writeFloat(mat.baseG); out.writeFloat(mat.baseB); out.writeFloat(mat.baseA);
        out.writeFloat(mat.metallic); out.writeFloat(mat.roughness); out.writeFloat(mat.transmission);
        out.writeFloat(mat.emR); out.writeFloat(mat.emG); out.writeFloat(mat.emB);
        out.writeFloat(mat.alphaCutoff);
        // was the shading model id, now flags: 1 nearest base colour filter, 2 unlit
        out.writeInt((mat.baseColorFilter == TextureFilter.NEAREST ? 1 : 0) | (mat.unlit ? 2 : 0));
        out.writeBoolean(mat.blend);
        out.writeBoolean(mat.doubleSided);

        writeTextureSlot(out, mat.baseColorTexture, mat.baseColorImageBytes, mat.baseColorImageKey, images, true);
        writeTextureSlot(out, mat.normalTexture, mat.normalImageBytes, mat.normalImageKey, images, false);
        writeTextureSlot(out, mat.ormTexture, mat.ormImageBytes, mat.ormImageKey, images, false);
        writeTextureSlot(out, mat.emissiveTexture, mat.emissiveImageBytes, mat.emissiveImageKey, images, true);
    }

    // texture reference: prefer an Identifier (path), only fall back to embedded bytes when the
    // source glTF had no external path to reference (data-uri / bufferView image)
    private static void writeTextureSlot(DataOutputStream out, Identifier texId, byte[] bytes, String key, Images images, boolean srgb)
            throws IOException {
        out.writeBoolean(texId != null);
        if (texId != null) out.writeUTF(texId.toString());

        if (key == null && bytes != null && images != null) key = images.put(bytes, srgb);
        if (key != null) {
            out.writeByte(AmmeshFormat.SLOT_KEY);
            out.writeUTF(key);
        } else if (bytes != null) {
            out.writeByte(AmmeshFormat.SLOT_BYTES);
            out.writeInt(bytes.length);
            out.write(bytes);
        } else {
            out.writeByte(AmmeshFormat.SLOT_NONE);
        }
    }

    private static void writeNode(DataOutputStream out, ModelIR.Node node) throws IOException {
        out.writeUTF(node.name);
        out.writeInt(node.parent);
        out.writeFloat(node.t.x); out.writeFloat(node.t.y); out.writeFloat(node.t.z);
        out.writeFloat(node.r.x); out.writeFloat(node.r.y); out.writeFloat(node.r.z); out.writeFloat(node.r.w);
        out.writeFloat(node.s.x); out.writeFloat(node.s.y); out.writeFloat(node.s.z);
        out.writeInt(node.children.length);
        for (int c : node.children) out.writeInt(c);
    }

    private static void writeAnimation(DataOutputStream out, ModelIR.Animation anim) throws IOException {
        out.writeUTF(anim.name);
        out.writeFloat(anim.duration);
        out.writeInt(anim.channels.size());
        for (ModelIR.Channel ch : anim.channels) {
            out.writeInt(ch.node);
            out.writeUTF(ch.path.name());
            out.writeUTF(ch.interp.name());
            out.writeInt(ch.times.length);
            writeFloats(out, ch.times);
            out.writeInt(ch.values.length);
            writeFloats(out, ch.values);
        }
    }

    // big arrays go out in one block, the same big endian bytes writeFloat makes
    private static void writeFloats(DataOutputStream out, float[] values) throws IOException {
        ByteBuffer block = ByteBuffer.allocate(values.length * Float.BYTES);
        block.asFloatBuffer().put(values);
        out.write(block.array());
    }

    private static void writeInts(DataOutputStream out, int[] values) throws IOException {
        ByteBuffer block = ByteBuffer.allocate(values.length * Integer.BYTES);
        block.asIntBuffer().put(values);
        out.write(block.array());
    }
}

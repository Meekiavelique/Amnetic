package com.meekdev.amnetic.client.model.internal.ammesh;

import com.meekdev.amnetic.client.model.internal.ModelIR;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4f;

public final class AmmeshWriter {

    private AmmeshWriter() {}

    public static byte[] write(ModelIR ir) {
        ByteArrayOutputStream buf = new ByteArrayOutputStream(1 << 16);
        try (DataOutputStream out = new DataOutputStream(buf)) {
            out.write(AmmeshFormat.MAGIC);
            out.writeInt(AmmeshFormat.VERSION);
            out.writeUTF(ir.name());

            out.writeInt(ir.parts().size());
            for (ModelIR.Part part : ir.parts()) writePart(out, part);

            out.writeInt(ir.materials().size());
            for (ModelIR.Material mat : ir.materials()) writeMaterial(out, mat);

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
        for (float f : t) out.writeFloat(f);

        out.writeInt(part.vertices.length);
        for (float f : part.vertices) out.writeFloat(f);

        out.writeBoolean(part.hasTangents());
        if (part.hasTangents()) {
            out.writeInt(part.tangents.length);
            for (float f : part.tangents) out.writeFloat(f);
        }

        boolean indexed = part.hasIndices();
        out.writeInt(indexed ? part.indices.length : 0);
        if (indexed) for (int i : part.indices) out.writeInt(i);

        out.writeBoolean(part.hasSkin());
        if (part.hasSkin()) {
            out.writeInt(part.jointIndices.length);
            for (float f : part.jointIndices) out.writeFloat(f);
            out.writeInt(part.jointWeights.length);
            for (float f : part.jointWeights) out.writeFloat(f);
            out.writeInt(part.jointNodes.length);
            for (int i : part.jointNodes) out.writeInt(i);
            for (Matrix4f ib : part.inverseBind) {
                float[] m = new float[16];
                ib.get(m);
                for (float f : m) out.writeFloat(f);
            }
        }
    }

    private static void writeMaterial(DataOutputStream out, ModelIR.Material mat) throws IOException {
        out.writeUTF(mat.name);
        out.writeFloat(mat.baseR); out.writeFloat(mat.baseG); out.writeFloat(mat.baseB); out.writeFloat(mat.baseA);
        out.writeFloat(mat.metallic); out.writeFloat(mat.roughness); out.writeFloat(mat.transmission);
        out.writeFloat(mat.emR); out.writeFloat(mat.emG); out.writeFloat(mat.emB);
        out.writeFloat(mat.alphaCutoff);
        // custom shading model ids depend on runtime registration order, only the
        // default (0) is stable across sessions so never persist anything else
        out.writeInt(0);
        out.writeBoolean(mat.blend);
        out.writeBoolean(mat.doubleSided);

        writeTextureSlot(out, mat.baseColorTexture, mat.baseColorImageBytes);
        writeTextureSlot(out, mat.normalTexture, mat.normalImageBytes);
        writeTextureSlot(out, mat.ormTexture, mat.ormImageBytes);
        writeTextureSlot(out, mat.emissiveTexture, mat.emissiveImageBytes);
    }

    // texture reference: prefer an Identifier (path), only fall back to embedded bytes when the
    // source glTF had no external path to reference (data-uri / bufferView image)
    private static void writeTextureSlot(DataOutputStream out, Identifier texId, byte[] bytes) throws IOException {
        out.writeBoolean(texId != null);
        if (texId != null) out.writeUTF(texId.toString());

        out.writeBoolean(bytes != null);
        if (bytes != null) {
            out.writeInt(bytes.length);
            out.write(bytes);
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
            for (float f : ch.times) out.writeFloat(f);
            out.writeInt(ch.values.length);
            for (float f : ch.values) out.writeFloat(f);
        }
    }
}

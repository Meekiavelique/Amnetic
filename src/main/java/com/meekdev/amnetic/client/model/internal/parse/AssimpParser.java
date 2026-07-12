package com.meekdev.amnetic.client.model.internal.parse;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.HashMap;
import java.util.Map;

import com.meekdev.amnetic.client.model.ModelLoadException;
import com.meekdev.amnetic.client.model.internal.ModelIR;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4f;
import org.lwjgl.PointerBuffer;
import org.lwjgl.assimp.AIAnimation;
import org.lwjgl.assimp.AIBone;
import org.lwjgl.assimp.AIColor4D;
import org.lwjgl.assimp.AIFace;
import org.lwjgl.assimp.AIMaterial;
import org.lwjgl.assimp.AIMatrix4x4;
import org.lwjgl.assimp.AIMesh;
import org.lwjgl.assimp.AINode;
import org.lwjgl.assimp.AINodeAnim;
import org.lwjgl.assimp.AIQuatKey;
import org.lwjgl.assimp.AIQuaternion;
import org.lwjgl.assimp.AIScene;
import org.lwjgl.assimp.AIString;
import org.lwjgl.assimp.AITexture;
import org.lwjgl.assimp.AIVector3D;
import org.lwjgl.assimp.AIVectorKey;
import org.lwjgl.assimp.AIVertexWeight;
import org.lwjgl.assimp.Assimp;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class AssimpParser {

    private static final Logger LOG = LoggerFactory.getLogger("Amnetic/Model");

    private static final int IMPORT_FLAGS =
            Assimp.aiProcess_Triangulate
                    | Assimp.aiProcess_GenSmoothNormals
                    | Assimp.aiProcess_CalcTangentSpace
                    | Assimp.aiProcess_JoinIdenticalVertices
                    | Assimp.aiProcess_ImproveCacheLocality
                    | Assimp.aiProcess_LimitBoneWeights;

    private AssimpParser() {
    }

    public static ModelIR parse(byte[] bytes, String hint, Identifier source) {
        ByteBuffer data = MemoryUtil.memAlloc(bytes.length);
        data.put(bytes);
        data.flip();

        AIScene scene = null;
        try {
            scene = importScene(data, hint);
            ModelIR ir = new ModelIR();
            if (source != null) {
                ir.setName(source.toString());
            }

            Map<String, Integer> nodeNames = new HashMap<>();
            buildNodes(ir, scene.mRootNode(), -1, nodeNames);

            ModelIR.Material[] materials = readMaterials(scene, source);
            for (ModelIR.Material m : materials) {
                ir.addMaterial(m);
            }

            addParts(ir, scene, nodeNames);
            if (ir.isEmpty()) {
                throw new ModelLoadException("model produced no drawable geometry: " + ir.name());
            }

            readAnimations(ir, scene, nodeNames);
            return ir;
        } finally {
            if (scene != null) {
                Assimp.aiReleaseImport(scene);
            }
            MemoryUtil.memFree(data);
        }
    }

    private static AIScene importScene(ByteBuffer data, String hint) {
        AIScene scene;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            ByteBuffer hintBuf = hint == null ? null : stack.ASCII(hint);
            scene = Assimp.aiImportFileFromMemory(data, IMPORT_FLAGS, hintBuf);
        }
        if (scene == null || scene.mRootNode() == null) {
            String err = Assimp.aiGetErrorString();
            throw new ModelLoadException("Assimp failed to load model: " + (err == null ? "unknown error" : err));
        }
        return scene;
    }

    private static void buildNodes(ModelIR ir, AINode node, int parent, Map<String, Integer> nodeNames) {
        ModelIR.Node out = new ModelIR.Node();
        out.name = node.mName().dataString();
        out.parent = parent;

        Matrix4f local = toMatrix(node.mTransformation());
        out.local.set(local);
        local.getTranslation(out.t);
        local.getUnnormalizedRotation(out.r);
        local.getScale(out.s);

        int index = ir.addNode(out);
        nodeNames.put(out.name, index);

        int childCount = node.mNumChildren();
        out.children = new int[childCount];
        PointerBuffer children = node.mChildren();
        if (children != null) {
            for (int i = 0; i < childCount; i++) {
                AINode child = AINode.create(children.get(i));
                out.children[i] = ir.nodes().size();
                buildNodes(ir, child, index, nodeNames);
            }
        }
    }

    private static void addParts(ModelIR ir, AIScene scene, Map<String, Integer> nodeNames) {
        PointerBuffer meshes = scene.mMeshes();
        if (meshes == null) {
            return;
        }
        walkMeshes(ir, scene.mRootNode(), meshes, nodeNames);
    }

    private static void walkMeshes(ModelIR ir, AINode node, PointerBuffer meshes, Map<String, Integer> nodeNames) {
        int nodeIndex = nodeNames.getOrDefault(node.mName().dataString(), -1);
        Matrix4f worldTransform = nodeIndex < 0 ? new Matrix4f() : globalTransform(ir, nodeIndex);

        IntBuffer nodeMeshes = node.mMeshes();
        if (nodeMeshes != null) {
            for (int i = 0; i < node.mNumMeshes(); i++) {
                AIMesh mesh = AIMesh.create(meshes.get(nodeMeshes.get(i)));
                ModelIR.Part part = buildPart(mesh, nodeIndex, worldTransform, nodeNames);
                if (part != null) {
                    ir.addPart(part);
                }
            }
        }

        PointerBuffer children = node.mChildren();
        if (children != null) {
            for (int i = 0; i < node.mNumChildren(); i++) {
                walkMeshes(ir, AINode.create(children.get(i)), meshes, nodeNames);
            }
        }
    }

    private static ModelIR.Part buildPart(AIMesh mesh, int nodeIndex, Matrix4f worldTransform,
                                          Map<String, Integer> nodeNames) {
        int vcount = mesh.mNumVertices();
        if (vcount == 0) {
            return null;
        }

        AIVector3D.Buffer positions = mesh.mVertices();
        AIVector3D.Buffer normals = mesh.mNormals();
        AIVector3D.Buffer uvs = mesh.mTextureCoords(0);
        AIVector3D.Buffer tangents = mesh.mTangents();
        AIVector3D.Buffer bitangents = mesh.mBitangents();

        float[] vertices = new float[vcount * ModelIR.VERTEX_STRIDE_FLOATS];
        for (int i = 0; i < vcount; i++) {
            int o = i * ModelIR.VERTEX_STRIDE_FLOATS;
            AIVector3D p = positions.get(i);
            vertices[o] = p.x();
            vertices[o + 1] = p.y();
            vertices[o + 2] = p.z();
            if (normals != null) {
                AIVector3D n = normals.get(i);
                vertices[o + 3] = n.x();
                vertices[o + 4] = n.y();
                vertices[o + 5] = n.z();
            }
            if (uvs != null) {
                AIVector3D uv = uvs.get(i);
                vertices[o + 6] = uv.x();
                vertices[o + 7] = uv.y();
            }
        }

        float[] tangentData = null;
        if (tangents != null && bitangents != null && normals != null) {
            tangentData = new float[vcount * 4];
            for (int i = 0; i < vcount; i++) {
                AIVector3D t = tangents.get(i);
                AIVector3D b = bitangents.get(i);
                AIVector3D n = normals.get(i);
                float cx = n.y() * t.z() - n.z() * t.y();
                float cy = n.z() * t.x() - n.x() * t.z();
                float cz = n.x() * t.y() - n.y() * t.x();
                float handed = cx * b.x() + cy * b.y() + cz * b.z() < 0f ? -1f : 1f;
                tangentData[i * 4] = t.x();
                tangentData[i * 4 + 1] = t.y();
                tangentData[i * 4 + 2] = t.z();
                tangentData[i * 4 + 3] = handed;
            }
        }

        int[] indices = readIndices(mesh);
        String name = mesh.mName().dataString();
        ModelIR.Part part = new ModelIR.Part(vertices, tangentData, indices, mesh.mMaterialIndex(), nodeIndex, worldTransform, name);
        readSkin(mesh, part, vcount, nodeNames);
        return part;
    }

    private static void readSkin(AIMesh mesh, ModelIR.Part part, int vcount, Map<String, Integer> nodeNames) {
        int boneCount = mesh.mNumBones();
        if (boneCount == 0) {
            return;
        }
        PointerBuffer bones = mesh.mBones();
        if (bones == null) {
            return;
        }

        float[] jointIndices = new float[vcount * 4];
        float[] jointWeights = new float[vcount * 4];
        int[] filled = new int[vcount];
        int[] jointNodes = new int[boneCount];
        Matrix4f[] inverseBind = new Matrix4f[boneCount];

        for (int b = 0; b < boneCount; b++) {
            AIBone bone = AIBone.create(bones.get(b));
            jointNodes[b] = nodeNames.getOrDefault(bone.mName().dataString(), -1);
            inverseBind[b] = toMatrix(bone.mOffsetMatrix());

            AIVertexWeight.Buffer weights = bone.mWeights();
            for (int w = 0; w < bone.mNumWeights(); w++) {
                AIVertexWeight weight = weights.get(w);
                int v = weight.mVertexId();
                if (v < 0 || v >= vcount || filled[v] >= 4) {
                    continue;
                }
                int slot = v * 4 + filled[v];
                jointIndices[slot] = b;
                jointWeights[slot] = weight.mWeight();
                filled[v]++;
            }
        }

        for (int v = 0; v < vcount; v++) {
            float sum = jointWeights[v * 4] + jointWeights[v * 4 + 1] + jointWeights[v * 4 + 2] + jointWeights[v * 4 + 3];
            if (sum > 1e-5f) {
                jointWeights[v * 4] /= sum;
                jointWeights[v * 4 + 1] /= sum;
                jointWeights[v * 4 + 2] /= sum;
                jointWeights[v * 4 + 3] /= sum;
            } else {
                jointWeights[v * 4] = 1f;
            }
        }

        part.jointIndices = jointIndices;
        part.jointWeights = jointWeights;
        part.jointNodes = jointNodes;
        part.inverseBind = inverseBind;
    }

    private static int[] readIndices(AIMesh mesh) {
        int faceCount = mesh.mNumFaces();
        if (faceCount == 0) {
            return null;
        }
        AIFace.Buffer faces = mesh.mFaces();
        int[] indices = new int[faceCount * 3];
        int n = 0;
        for (int i = 0; i < faceCount; i++) {
            AIFace face = faces.get(i);
            if (face.mNumIndices() != 3) {
                continue;
            }
            IntBuffer fi = face.mIndices();
            indices[n++] = fi.get(0);
            indices[n++] = fi.get(1);
            indices[n++] = fi.get(2);
        }
        if (n == indices.length) {
            return indices;
        }
        int[] trimmed = new int[n];
        System.arraycopy(indices, 0, trimmed, 0, n);
        return trimmed;
    }

    private static Matrix4f globalTransform(ModelIR ir, int nodeIndex) {
        Matrix4f result = new Matrix4f();
        int current = nodeIndex;
        while (current >= 0) {
            ModelIR.Node node = ir.nodes().get(current);
            result.mulLocal(node.local);
            current = node.parent;
        }
        return result;
    }

    private static ModelIR.Material[] readMaterials(AIScene scene, Identifier source) {
        int count = scene.mNumMaterials();
        ModelIR.Material[] out = new ModelIR.Material[count];
        PointerBuffer pointers = scene.mMaterials();
        for (int i = 0; i < count; i++) {
            AIMaterial mat = AIMaterial.create(pointers.get(i));
            out[i] = readMaterial(scene, mat, source);
        }
        return out;
    }

    private static ModelIR.Material readMaterial(AIScene scene, AIMaterial mat, Identifier source) {
        ModelIR.Material m = new ModelIR.Material();
        m.name = materialName(mat);

        AIColor4D color = AIColor4D.create();
        if (getColor(mat, Assimp.AI_MATKEY_BASE_COLOR, color) || getColor(mat, Assimp.AI_MATKEY_COLOR_DIFFUSE, color)) {
            m.baseR = color.r();
            m.baseG = color.g();
            m.baseB = color.b();
            m.baseA = color.a();
        }
        if (getColor(mat, Assimp.AI_MATKEY_COLOR_EMISSIVE, color)) {
            m.emR = color.r();
            m.emG = color.g();
            m.emB = color.b();
        }

        m.metallic = getFloat(mat, Assimp.AI_MATKEY_METALLIC_FACTOR, m.metallic);
        m.roughness = getFloat(mat, Assimp.AI_MATKEY_ROUGHNESS_FACTOR, m.roughness);
        float opacity = getFloat(mat, Assimp.AI_MATKEY_OPACITY, 1f);
        m.baseA *= opacity;
        // KHR_materials_transmission: glass-like surfaces carry a transmission factor instead of alpha,
        // assimp imports it as $mat.transmission.factor. fold it into opacity so glass renders see-through
        // instead of opaque white, with a small floor so the pane stays visible
        float transmission = getFloat(mat, Assimp.AI_MATKEY_TRANSMISSION_FACTOR, 0f);
        if (transmission > 0f) {
            m.baseA *= Math.max(1f - transmission, 0.08f);
        }
        m.doubleSided = getInt(mat, Assimp.AI_MATKEY_TWOSIDED, 0) != 0;
        m.blend = m.baseA < 0.999f;

        resolveTexture(scene, mat, Assimp.aiTextureType_BASE_COLOR, source, TextureSlot.BASE_COLOR, m);
        if (m.baseColorTexture == null && m.baseColorImageBytes == null) {
            resolveTexture(scene, mat, Assimp.aiTextureType_DIFFUSE, source, TextureSlot.BASE_COLOR, m);
        }
        resolveTexture(scene, mat, Assimp.aiTextureType_NORMALS, source, TextureSlot.NORMAL, m);
        resolveTexture(scene, mat, Assimp.aiTextureType_EMISSIVE, source, TextureSlot.EMISSIVE, m);
        resolveTexture(scene, mat, Assimp.aiTextureType_METALNESS, source, TextureSlot.ORM, m);
        if (m.ormTexture == null && m.ormImageBytes == null) {
            resolveTexture(scene, mat, Assimp.aiTextureType_UNKNOWN, source, TextureSlot.ORM, m);
        }
        return m;
    }

    private enum TextureSlot {
        BASE_COLOR,
        NORMAL,
        ORM,
        EMISSIVE
    }

    private static void resolveTexture(AIScene scene, AIMaterial mat, int type, Identifier source,
                                       TextureSlot slot, ModelIR.Material m) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            AIString path = AIString.calloc(stack);
            int result = Assimp.aiGetMaterialTexture(mat, type, 0, path, (IntBuffer) null,
                    null, null, null, null, null);
            if (result != Assimp.aiReturn_SUCCESS) {
                return;
            }
            String value = path.dataString();
            if (value.isEmpty()) {
                return;
            }
            byte[] embedded = readEmbedded(scene, value);
            if (embedded != null) {
                applyEmbedded(slot, m, embedded);
            } else if (source != null) {
                applyExternal(slot, m, sibling(source, value));
            } else {
                LOG.warn("Amnetic: unresolved model texture '{}' (slot {})", value, slot);
            }
        }
    }

    private static byte[] readEmbedded(AIScene scene, String reference) {
        PointerBuffer textures = scene.mTextures();
        if (textures == null || scene.mNumTextures() == 0) {
            return null;
        }
        AITexture tex = null;
        if (reference.startsWith("*")) {
            try {
                int index = Integer.parseInt(reference.substring(1));
                if (index >= 0 && index < scene.mNumTextures()) {
                    tex = AITexture.create(textures.get(index));
                }
            } catch (NumberFormatException ignored) {
            }
        }
        if (tex == null) {
            String ref = baseName(reference);
            for (int i = 0; i < scene.mNumTextures(); i++) {
                AITexture candidate = AITexture.create(textures.get(i));
                if (ref.equals(baseName(candidate.mFilename().dataString()))) {
                    tex = candidate;
                    break;
                }
            }
        }
        if (tex == null || tex.mHeight() != 0) {
            return null;
        }
        ByteBuffer raw = tex.pcDataCompressed();
        byte[] bytes = new byte[raw.remaining()];
        raw.get(bytes);
        return bytes;
    }

    private static void applyEmbedded(TextureSlot slot, ModelIR.Material m, byte[] bytes) {
        if (bytes == null) {
            return;
        }
        switch (slot) {
            case BASE_COLOR -> m.baseColorImageBytes = bytes;
            case NORMAL -> m.normalImageBytes = bytes;
            case ORM -> m.ormImageBytes = bytes;
            case EMISSIVE -> m.emissiveImageBytes = bytes;
        }
    }

    private static void applyExternal(TextureSlot slot, ModelIR.Material m, Identifier id) {
        switch (slot) {
            case BASE_COLOR -> m.baseColorTexture = id;
            case NORMAL -> m.normalTexture = id;
            case ORM -> m.ormTexture = id;
            case EMISSIVE -> m.emissiveTexture = id;
        }
    }

    private static void readAnimations(ModelIR ir, AIScene scene, Map<String, Integer> nodeNames) {
        int count = scene.mNumAnimations();
        if (count == 0) {
            return;
        }
        PointerBuffer pointers = scene.mAnimations();
        for (int i = 0; i < count; i++) {
            AIAnimation aiAnim = AIAnimation.create(pointers.get(i));
            ModelIR.Animation anim = new ModelIR.Animation();
            anim.name = aiAnim.mName().dataString();
            if (anim.name.isEmpty()) {
                anim.name = "clip" + i;
            }
            double ticksPerSecond = aiAnim.mTicksPerSecond();
            if (ticksPerSecond <= 0) {
                ticksPerSecond = 25.0;
            }
            anim.duration = (float) (aiAnim.mDuration() / ticksPerSecond);

            PointerBuffer channels = aiAnim.mChannels();
            for (int c = 0; c < aiAnim.mNumChannels(); c++) {
                AINodeAnim channel = AINodeAnim.create(channels.get(c));
                int node = nodeNames.getOrDefault(channel.mNodeName().dataString(), -1);
                if (node < 0) {
                    continue;
                }
                addChannels(anim, channel, node, ticksPerSecond);
            }
            if (!anim.channels.isEmpty()) {
                ir.animations().add(anim);
            }
        }
    }

    private static void addChannels(ModelIR.Animation anim, AINodeAnim channel, int node, double ticksPerSecond) {
        int posCount = channel.mNumPositionKeys();
        if (posCount > 0) {
            ModelIR.Channel ch = new ModelIR.Channel();
            ch.node = node;
            ch.path = ModelIR.Path.TRANSLATION;
            ch.times = new float[posCount];
            ch.values = new float[posCount * 3];
            AIVectorKey.Buffer keys = channel.mPositionKeys();
            for (int k = 0; k < posCount; k++) {
                AIVectorKey key = keys.get(k);
                ch.times[k] = (float) (key.mTime() / ticksPerSecond);
                AIVector3D v = key.mValue();
                ch.values[k * 3] = v.x();
                ch.values[k * 3 + 1] = v.y();
                ch.values[k * 3 + 2] = v.z();
            }
            anim.channels.add(ch);
        }

        int rotCount = channel.mNumRotationKeys();
        if (rotCount > 0) {
            ModelIR.Channel ch = new ModelIR.Channel();
            ch.node = node;
            ch.path = ModelIR.Path.ROTATION;
            ch.times = new float[rotCount];
            ch.values = new float[rotCount * 4];
            AIQuatKey.Buffer keys = channel.mRotationKeys();
            for (int k = 0; k < rotCount; k++) {
                AIQuatKey key = keys.get(k);
                ch.times[k] = (float) (key.mTime() / ticksPerSecond);
                AIQuaternion q = key.mValue();
                ch.values[k * 4] = q.x();
                ch.values[k * 4 + 1] = q.y();
                ch.values[k * 4 + 2] = q.z();
                ch.values[k * 4 + 3] = q.w();
            }
            anim.channels.add(ch);
        }

        int scaleCount = channel.mNumScalingKeys();
        if (scaleCount > 0) {
            ModelIR.Channel ch = new ModelIR.Channel();
            ch.node = node;
            ch.path = ModelIR.Path.SCALE;
            ch.times = new float[scaleCount];
            ch.values = new float[scaleCount * 3];
            AIVectorKey.Buffer keys = channel.mScalingKeys();
            for (int k = 0; k < scaleCount; k++) {
                AIVectorKey key = keys.get(k);
                ch.times[k] = (float) (key.mTime() / ticksPerSecond);
                AIVector3D v = key.mValue();
                ch.values[k * 3] = v.x();
                ch.values[k * 3 + 1] = v.y();
                ch.values[k * 3 + 2] = v.z();
            }
            anim.channels.add(ch);
        }
    }

    private static String materialName(AIMaterial mat) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            AIString name = AIString.calloc(stack);
            if (Assimp.aiGetMaterialString(mat, Assimp.AI_MATKEY_NAME, 0, 0, name) == Assimp.aiReturn_SUCCESS) {
                String value = name.dataString();
                if (!value.isEmpty()) {
                    return value;
                }
            }
        }
        return "material";
    }

    private static boolean getColor(AIMaterial mat, String key, AIColor4D out) {
        return Assimp.aiGetMaterialColor(mat, key, 0, 0, out) == Assimp.aiReturn_SUCCESS;
    }

    private static float getFloat(AIMaterial mat, String key, float fallback) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            FloatBuffer value = stack.mallocFloat(1);
            IntBuffer count = stack.ints(1);
            if (Assimp.aiGetMaterialFloatArray(mat, key, 0, 0, value, count) == Assimp.aiReturn_SUCCESS) {
                return value.get(0);
            }
        }
        return fallback;
    }

    private static int getInt(AIMaterial mat, String key, int fallback) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer value = stack.mallocInt(1);
            IntBuffer count = stack.ints(1);
            if (Assimp.aiGetMaterialIntegerArray(mat, key, 0, 0, value, count) == Assimp.aiReturn_SUCCESS) {
                return value.get(0);
            }
        }
        return fallback;
    }

    private static Matrix4f toMatrix(AIMatrix4x4 m) {
        return new Matrix4f(
                m.a1(), m.b1(), m.c1(), m.d1(),
                m.a2(), m.b2(), m.c2(), m.d2(),
                m.a3(), m.b3(), m.c3(), m.d3(),
                m.a4(), m.b4(), m.c4(), m.d4());
    }

    private static String baseName(String path) {
        String p = path.replace('\\', '/');
        int slash = p.lastIndexOf('/');
        return slash < 0 ? p : p.substring(slash + 1);
    }

    public static Identifier sibling(Identifier source, String relative) {
        String path = source.getPath();
        int slash = path.lastIndexOf('/');
        String dir = slash < 0 ? "" : path.substring(0, slash + 1);
        String rel = relative.replace('\\', '/');
        while (rel.startsWith("./")) {
            rel = rel.substring(2);
        }
        return Identifier.fromNamespaceAndPath(source.getNamespace(), dir + rel);
    }
}

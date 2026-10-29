package com.meekdev.amnetic.client.model.internal.parse;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import com.meekdev.amnetic.client.model.ModelLoadException;
import com.meekdev.amnetic.client.model.internal.GpuModel;
import com.meekdev.amnetic.client.model.internal.ModelIR;
import de.javagl.jgltf.model.AccessorModel;
import de.javagl.jgltf.model.AnimationModel;
import de.javagl.jgltf.model.GltfModel;
import de.javagl.jgltf.model.ImageModel;
import de.javagl.jgltf.model.MaterialModel;
import de.javagl.jgltf.model.MeshModel;
import de.javagl.jgltf.model.MeshPrimitiveModel;
import de.javagl.jgltf.model.NodeModel;
import de.javagl.jgltf.model.SkinModel;
import com.meekdev.amnetic.client.model.TextureFilter;
import de.javagl.jgltf.model.TextureModel;
import de.javagl.jgltf.model.GltfModels;
import de.javagl.jgltf.model.io.GltfAsset;
import de.javagl.jgltf.model.io.GltfAssetReader;
import de.javagl.jgltf.model.io.GltfReference;
import de.javagl.jgltf.model.v2.MaterialModelV2;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * pure-java glTF/GLB parser on jgltf. reads UVs, materials and textures straight from the accessors
 * per primitive so nothing gets reordered or merged like the Assimp path does, and emits the same
 * {@link ModelIR} the rest of the pipeline consumes
 */
public final class GltfParser {

    private static final Logger LOG = LoggerFactory.getLogger("Amnetic/Gltf");

    private static final int MODE_TRIANGLES = 4;

    private GltfParser() {
    }

    public static ModelIR parse(byte[] bytes, Identifier source) {
        return parse(bytes, source, null);
    }

    // files reads an image the file points at by a relative uri, given the decoded uri, null when it has none
    public static ModelIR parse(byte[] bytes, Identifier source, Function<String, byte[]> files) {
        try {
            GltfModel gltf = read(bytes, files);
            GltfExtras extras = new GltfExtras(GltfExtras.json(bytes), gltf.getMaterialModels(), files);
            ModelIR ir = new ModelIR();
            if (source != null) {
                ir.setName(source.toString());
            }

            List<NodeModel> nodes = gltf.getNodeModels();
            Map<NodeModel, Integer> nodeIndices = new IdentityHashMap<>();
            for (int i = 0; i < nodes.size(); i++) {
                nodeIndices.put(nodes.get(i), i);
            }
            buildNodes(ir, nodes, nodeIndices);

            Map<MaterialModel, Integer> materialIndices = new IdentityHashMap<>();
            buildParts(ir, gltf, nodes, nodeIndices, materialIndices, extras);

            if (ir.isEmpty()) {
                throw new ModelLoadException("glTF produced no drawable geometry: " + ir.name());
            }

            buildAnimations(ir, gltf, nodeIndices);
            return ir;
        } catch (ModelLoadException e) {
            throw e;
        } catch (Exception e) {
            throw new ModelLoadException("Failed to parse glTF model: " + (source == null ? "<bytes>" : source), e);
        }
    }

    private static void buildNodes(ModelIR ir, List<NodeModel> nodes, Map<NodeModel, Integer> indices) {
        for (NodeModel node : nodes) {
            ModelIR.Node out = new ModelIR.Node();
            String name = node.getName();
            out.name = name == null ? "node" : name;
            out.parent = node.getParent() == null ? -1 : indices.getOrDefault(node.getParent(), -1);

            float[] m = node.getMatrix();
            if (m != null && m.length == 16) {
                out.local.set(m); // jgltf matrices are column-major, same as JOML
                out.local.getTranslation(out.t);
                out.local.getScale(out.s);
                // a mirrored node keeps its mirror as a negative x scale, or posing it would flip it back
                Matrix4f turn = new Matrix4f(out.local);
                if (out.local.determinant3x3() < 0f) {
                    out.s.x = -out.s.x;
                    turn.m00(-turn.m00()).m01(-turn.m01()).m02(-turn.m02());
                }
                turn.getUnnormalizedRotation(out.r);
                out.r.normalize();
            } else {
                // the file's own parts are kept as they are, decomposing them again would lose a negative scale
                float[] t = node.getTranslation();
                float[] r = node.getRotation();
                float[] s = node.getScale();
                if (t != null) out.t.set(t[0], t[1], t[2]);
                if (r != null) out.r.set(r[0], r[1], r[2], r[3]).normalize();
                if (s != null) out.s.set(s[0], s[1], s[2]);
                out.rebuildLocal();
            }

            List<NodeModel> children = node.getChildren();
            out.children = new int[children == null ? 0 : children.size()];
            if (children != null) {
                for (int i = 0; i < children.size(); i++) {
                    out.children[i] = indices.getOrDefault(children.get(i), -1);
                }
            }
            ir.addNode(out);
        }
    }

    private static void buildParts(ModelIR ir, GltfModel gltf, List<NodeModel> nodes,
                                   Map<NodeModel, Integer> nodeIndices, Map<MaterialModel, Integer> materialIndices,
                                   GltfExtras extras) {
        for (NodeModel node : nodes) {
            List<MeshModel> meshes = node.getMeshModels();
            if (meshes == null || meshes.isEmpty()) {
                continue;
            }
            int nodeIndex = nodeIndices.getOrDefault(node, -1);
            Matrix4f world = new Matrix4f().set(node.computeGlobalTransform(new float[16]));

            for (MeshModel mesh : meshes) {
                List<MeshPrimitiveModel> prims = mesh.getMeshPrimitiveModels();
                if (prims == null) {
                    continue;
                }
                for (MeshPrimitiveModel prim : prims) {
                    if (prim.getMode() != MODE_TRIANGLES) {
                        continue;
                    }
                    ModelIR.Part part = buildPart(ir, gltf, prim, nodeIndex, world, materialIndices, mesh.getName(), extras);
                    if (part != null) {
                        readSkin(part, prim.getAttributes(), node.getSkinModel(), nodeIndices, ir.name());
                        ir.addPart(part);
                    }
                }
            }
        }
    }

    private static ModelIR.Part buildPart(ModelIR ir, GltfModel gltf, MeshPrimitiveModel prim, int nodeIndex,
                                          Matrix4f world, Map<MaterialModel, Integer> materialIndices, String name,
                                          GltfExtras extras) {
        Map<String, AccessorModel> attrs = prim.getAttributes();
        AccessorModel posAcc = attrs.get("POSITION");
        if (posAcc == null) {
            return null;
        }
        float[] positions = readFloats(posAcc);
        int vcount = positions.length / 3;
        if (vcount == 0) {
            return null;
        }

        float[] normals = attrs.containsKey("NORMAL") ? readFloats(attrs.get("NORMAL")) : null;
        GltfExtras.UvTransform tiling = extras.uvTransform(prim.getMaterialModel());
        String uvSet = "TEXCOORD_" + (tiling == null ? 0 : tiling.texCoord());
        if (!attrs.containsKey(uvSet)) uvSet = "TEXCOORD_0";
        float[] uvs = attrs.containsKey(uvSet) ? readFloats(attrs.get(uvSet)) : null;
        if (uvs != null && tiling != null) tiling.apply(uvs);
        float[] tangentsSrc = attrs.containsKey("TANGENT") ? readFloats(attrs.get("TANGENT")) : null;

        float[] vertices = new float[vcount * ModelIR.VERTEX_STRIDE_FLOATS];
        for (int i = 0; i < vcount; i++) {
            int o = i * ModelIR.VERTEX_STRIDE_FLOATS;
            vertices[o] = positions[i * 3];
            vertices[o + 1] = positions[i * 3 + 1];
            vertices[o + 2] = positions[i * 3 + 2];
            if (normals != null) {
                vertices[o + 3] = normals[i * 3];
                vertices[o + 4] = normals[i * 3 + 1];
                vertices[o + 5] = normals[i * 3 + 2];
            }
            if (uvs != null) {
                vertices[o + 6] = uvs[i * 2];
                vertices[o + 7] = uvs[i * 2 + 1];
            }
        }

        float[] tangentData = null;
        if (tangentsSrc != null && tangentsSrc.length >= vcount * 4) {
            tangentData = new float[vcount * 4];
            System.arraycopy(tangentsSrc, 0, tangentData, 0, vcount * 4);
        }

        int[] indices = prim.getIndices() != null ? readUnsignedInts(prim.getIndices()) : sequentialIndices(vcount);

        int materialIndex = resolveMaterial(ir, prim.getMaterialModel(), materialIndices, extras);
        ModelIR.Part part = new ModelIR.Part(vertices, tangentData, indices, materialIndex, nodeIndex, world, name);
        if (attrs.containsKey("COLOR_0")) part.colors = GltfExtras.rgba(readFloats(attrs.get("COLOR_0")), vcount);
        return part;
    }

    // joints and weights of a skinned primitive, the 4 heaviest influences kept and summed to 1
    private static void readSkin(ModelIR.Part part, Map<String, AccessorModel> attrs, SkinModel skin,
                                 Map<NodeModel, Integer> nodeIndices, String model) {
        if (skin == null || !attrs.containsKey("JOINTS_0") || !attrs.containsKey("WEIGHTS_0")) {
            return;
        }
        List<NodeModel> joints = skin.getJoints();
        int jointCount = joints == null ? 0 : joints.size();
        if (jointCount == 0) {
            return;
        }
        int[] jointNodes = new int[jointCount];
        Matrix4f[] inverseBind = new Matrix4f[jointCount];
        float[] binds = skin.getInverseBindMatrices() == null ? null : readFloats(skin.getInverseBindMatrices());
        for (int j = 0; j < jointCount; j++) {
            jointNodes[j] = nodeIndices.getOrDefault(joints.get(j), -1);
            inverseBind[j] = new Matrix4f();
            if (binds != null && binds.length >= (j + 1) * 16) {
                inverseBind[j].set(binds, j * 16);
            }
        }
        if (jointCount > GpuModel.MAX_JOINTS) {
            LOG.warn("{} has a skin with {} joints, only the first {} move", model, jointCount, GpuModel.MAX_JOINTS);
        }

        int vcount = part.vertexCount();
        float[][] sets = {
                readFloats(attrs.get("JOINTS_0")), readFloats(attrs.get("WEIGHTS_0")),
                attrs.containsKey("JOINTS_1") && attrs.containsKey("WEIGHTS_1") ? readFloats(attrs.get("JOINTS_1")) : null,
                attrs.containsKey("JOINTS_1") && attrs.containsKey("WEIGHTS_1") ? readFloats(attrs.get("WEIGHTS_1")) : null
        };
        float[] jointIndices = new float[vcount * 4];
        float[] jointWeights = new float[vcount * 4];
        int[] pickJoint = new int[4];
        float[] pickWeight = new float[4];
        for (int v = 0; v < vcount; v++) {
            Arrays.fill(pickWeight, 0f);
            Arrays.fill(pickJoint, 0);
            for (int set = 0; set < 4; set += 2) {
                float[] js = sets[set];
                float[] ws = sets[set + 1];
                if (js == null || ws == null) {
                    continue;
                }
                for (int k = 0; k < 4; k++) {
                    int at = v * 4 + k;
                    if (at >= js.length || at >= ws.length) {
                        break;
                    }
                    int joint = (int) js[at];
                    float weight = ws[at];
                    if (!(weight > 0f) || joint < 0 || joint >= jointCount) {
                        continue;
                    }
                    int lightest = 0;
                    for (int s = 1; s < 4; s++) {
                        if (pickWeight[s] < pickWeight[lightest]) {
                            lightest = s;
                        }
                    }
                    if (weight > pickWeight[lightest]) {
                        pickWeight[lightest] = weight;
                        pickJoint[lightest] = joint;
                    }
                }
            }
            float sum = pickWeight[0] + pickWeight[1] + pickWeight[2] + pickWeight[3];
            for (int k = 0; k < 4; k++) {
                jointIndices[v * 4 + k] = pickJoint[k];
                jointWeights[v * 4 + k] = sum > 1e-6f ? pickWeight[k] / sum : (k == 0 ? 1f : 0f);
            }
        }
        part.jointIndices = jointIndices;
        part.jointWeights = jointWeights;
        part.jointNodes = jointNodes;
        part.inverseBind = inverseBind;
    }

    private static int resolveMaterial(ModelIR ir, MaterialModel mat, Map<MaterialModel, Integer> indices, GltfExtras extras) {
        if (mat == null) {
            return -1;
        }
        Integer existing = indices.get(mat);
        if (existing != null) {
            return existing;
        }
        ModelIR.Material out = new ModelIR.Material();
        if (mat.getName() != null && !mat.getName().isEmpty()) out.name = mat.getName();
        if (mat instanceof MaterialModelV2 v2) {
            float[] base = v2.getBaseColorFactor();
            if (base != null && base.length >= 4) {
                out.baseR = base[0];
                out.baseG = base[1];
                out.baseB = base[2];
                out.baseA = base[3];
            }
            out.metallic = v2.getMetallicFactor();
            out.roughness = v2.getRoughnessFactor();
            float[] em = v2.getEmissiveFactor();
            if (em != null && em.length >= 3) {
                float strength = extensionFactor(v2, "KHR_materials_emissive_strength", "emissiveStrength", 1f);
                out.emR = em[0] * strength;
                out.emG = em[1] * strength;
                out.emB = em[2] * strength;
            }
            out.doubleSided = v2.isDoubleSided();
            String alphaMode = String.valueOf(v2.getAlphaMode());
            if ("MASK".equals(alphaMode)) {
                out.alphaCutoff = v2.getAlphaCutoff();
            }

            out.transmission = transmissionFactor(v2);
            out.blend = "BLEND".equals(alphaMode) || out.baseA < 0.999f || out.transmission > 0f;

            out.unlit = extras.unlit(mat);
            out.baseColorImageBytes = imageBytes(v2.getBaseColorTexture(), extras);
            if (nearest(v2.getBaseColorTexture())) out.baseColorFilter = TextureFilter.NEAREST;
            out.normalImageBytes = imageBytes(v2.getNormalTexture(), extras);
            // glTF metallicRoughness packs roughness in G and metallic in B, same layout model.fsh reads
            out.ormImageBytes = imageBytes(v2.getMetallicRoughnessTexture(), extras);
            out.emissiveImageBytes = imageBytes(v2.getEmissiveTexture(), extras);
        }
        int index = ir.addMaterial(out);
        indices.put(mat, index);
        return index;
    }

    private static float extensionFactor(MaterialModelV2 v2, String extension, String key, float fallback) {
        Map<String, Object> extensions = v2.getExtensions();
        Object values = extensions == null ? null : extensions.get(extension);
        if (values instanceof Map<?, ?> map && map.get(key) instanceof Number n) {
            return n.floatValue();
        }
        return fallback;
    }

    private static float transmissionFactor(MaterialModelV2 v2) {
        // KHR_materials_transmission isn't in MaterialModelV2's typed API, read the raw extension map
        // instead. best effort, any failure just means opaque
        try {
            Object props = v2.getExtensions();
            if (props instanceof Map<?, ?> ext) {
                Object t = ext.get("KHR_materials_transmission");
                if (t instanceof Map<?, ?> tm) {
                    Object f = tm.get("transmissionFactor");
                    if (f instanceof Number n) {
                        return n.floatValue();
                    }
                    return 1.0f; // present with default factor 1.0
                }
            }
        } catch (Throwable ignored) {
        }
        return 0f;
    }

    // a sampler asking for nearest magnification, the way pixel art and psx exports come out
    private static boolean nearest(TextureModel texture) {
        if (texture == null) return false;
        Integer mag = texture.getMagFilter();
        return mag != null && mag == 9728;
    }

    private static byte[] imageBytes(TextureModel texture, GltfExtras extras) {
        if (texture == null) {
            return null;
        }
        ImageModel image = texture.getImageModel();
        if (image == null) {
            return null;
        }
        ByteBuffer data = image.getImageData();
        if (data == null) {
            // an image by uri, a data uri or a file next to the model
            return extras.image(image.getUri());
        }
        ByteBuffer dup = data.slice();
        byte[] bytes = new byte[dup.remaining()];
        dup.get(bytes);
        return bytes.length == 0 ? null : bytes;
    }

    private static void buildAnimations(ModelIR ir, GltfModel gltf, Map<NodeModel, Integer> nodeIndices) {
        List<AnimationModel> anims = gltf.getAnimationModels();
        if (anims == null) {
            return;
        }
        for (int a = 0; a < anims.size(); a++) {
            AnimationModel anim = anims.get(a);
            ModelIR.Animation out = new ModelIR.Animation();
            String name = anim.getName();
            out.name = (name == null || name.isEmpty()) ? "clip" + a : name;

            float duration = 0f;
            List<AnimationModel.Channel> channels = anim.getChannels();
            if (channels != null) {
                for (AnimationModel.Channel channel : channels) {
                    NodeModel target = channel.getNodeModel();
                    AnimationModel.Sampler sampler = channel.getSampler();
                    if (target == null || sampler == null) {
                        continue;
                    }
                    int node = nodeIndices.getOrDefault(target, -1);
                    if (node < 0) {
                        continue;
                    }
                    ModelIR.Path path = switch (String.valueOf(channel.getPath())) {
                        case "translation" -> ModelIR.Path.TRANSLATION;
                        case "rotation" -> ModelIR.Path.ROTATION;
                        case "scale" -> ModelIR.Path.SCALE;
                        default -> null;
                    };
                    if (path == null) {
                        continue;
                    }
                    float[] times = readFloats(sampler.getInput());
                    float[] values = readFloats(sampler.getOutput());
                    if (times.length == 0) {
                        continue;
                    }
                    int width = path == ModelIR.Path.ROTATION ? 4 : 3;
                    ModelIR.Interp interp = switch (String.valueOf(sampler.getInterpolation())) {
                        case "STEP" -> ModelIR.Interp.STEP;
                        case "CUBICSPLINE" -> ModelIR.Interp.CUBICSPLINE;
                        default -> ModelIR.Interp.LINEAR;
                    };
                    if (values.length < times.length * width * (interp == ModelIR.Interp.CUBICSPLINE ? 3 : 1)) {
                        continue;
                    }
                    ModelIR.Channel ch = new ModelIR.Channel();
                    ch.node = node;
                    ch.path = path;
                    ch.interp = interp;
                    ch.times = times;
                    ch.values = values;
                    out.channels.add(ch);
                    duration = Math.max(duration, times[times.length - 1]);
                }
            }
            out.duration = duration;
            if (!out.channels.isEmpty()) {
                ir.animations().add(out);
            }
        }
    }

    private static int[] sequentialIndices(int vcount) {
        int[] out = new int[vcount];
        for (int i = 0; i < vcount; i++) {
            out[i] = i;
        }
        return out;
    }

    // the file with every buffer and image it names by uri filled in through files, one it cannot find comes in empty
    // jgltf reads image data while building the model, so an image left unresolved would fail the whole file
    private static GltfModel read(byte[] bytes, Function<String, byte[]> files) throws java.io.IOException {
        GltfAsset asset = new GltfAssetReader().readWithoutReferences(byteStream(bytes));
        for (GltfReference reference : asset.getReferences()) {
            byte[] found = GltfExtras.image(reference.getUri(), files);
            reference.getTarget().accept(ByteBuffer.wrap(found == null ? new byte[0] : found));
        }
        return GltfModels.create(asset);
    }

    private static InputStream byteStream(byte[] bytes) {
        return new ByteArrayInputStream(bytes);
    }

    private static float[] readFloats(AccessorModel accessor) {
        return GltfAccessorReaders.readFloatArray(accessor);
    }

    private static int[] readUnsignedInts(AccessorModel accessor) {
        return GltfAccessorReaders.readUnsignedIntArray(accessor);
    }
}

package com.meekdev.amnetic.client.model.internal.parse;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import com.meekdev.amnetic.client.model.ModelLoadException;
import com.meekdev.amnetic.client.model.internal.ModelIR;
import de.javagl.jgltf.model.AccessorModel;
import de.javagl.jgltf.model.AnimationModel;
import de.javagl.jgltf.model.GltfModel;
import de.javagl.jgltf.model.ImageModel;
import de.javagl.jgltf.model.MaterialModel;
import de.javagl.jgltf.model.MeshModel;
import de.javagl.jgltf.model.MeshPrimitiveModel;
import de.javagl.jgltf.model.NodeModel;
import de.javagl.jgltf.model.TextureModel;
import de.javagl.jgltf.model.io.GltfModelReader;
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
        try {
            GltfModel gltf = new GltfModelReader().readWithoutReferences(byteStream(bytes));
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
            buildParts(ir, gltf, nodes, nodeIndices, materialIndices);

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
            } else {
                float[] t = node.getTranslation();
                float[] r = node.getRotation();
                float[] s = node.getScale();
                if (t != null) out.t.set(t[0], t[1], t[2]);
                if (r != null) out.r.set(r[0], r[1], r[2], r[3]);
                if (s != null) out.s.set(s[0], s[1], s[2]);
                out.rebuildLocal();
            }
            out.local.getTranslation(out.t);
            out.local.getUnnormalizedRotation(out.r);
            out.local.getScale(out.s);

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
                                   Map<NodeModel, Integer> nodeIndices, Map<MaterialModel, Integer> materialIndices) {
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
                    ModelIR.Part part = buildPart(ir, gltf, prim, nodeIndex, world, materialIndices);
                    if (part != null) {
                        ir.addPart(part);
                    }
                }
            }
        }
    }

    private static ModelIR.Part buildPart(ModelIR ir, GltfModel gltf, MeshPrimitiveModel prim, int nodeIndex,
                                          Matrix4f world, Map<MaterialModel, Integer> materialIndices) {
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
        float[] uvs = attrs.containsKey("TEXCOORD_0") ? readFloats(attrs.get("TEXCOORD_0")) : null;
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

        int materialIndex = resolveMaterial(ir, prim.getMaterialModel(), materialIndices);
        return new ModelIR.Part(vertices, tangentData, indices, materialIndex, nodeIndex, world, "part");
    }

    private static int resolveMaterial(ModelIR ir, MaterialModel mat, Map<MaterialModel, Integer> indices) {
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
                out.emR = em[0];
                out.emG = em[1];
                out.emB = em[2];
            }
            out.doubleSided = v2.isDoubleSided();
            String alphaMode = String.valueOf(v2.getAlphaMode());
            if ("MASK".equals(alphaMode)) {
                out.alphaCutoff = v2.getAlphaCutoff();
            }

            out.transmission = transmissionFactor(v2);
            out.blend = "BLEND".equals(alphaMode) || out.baseA < 0.999f || out.transmission > 0f;

            out.baseColorImageBytes = imageBytes(v2.getBaseColorTexture());
            out.normalImageBytes = imageBytes(v2.getNormalTexture());
            // glTF metallicRoughness packs roughness in G and metallic in B, same layout model.fsh reads
            out.ormImageBytes = imageBytes(v2.getMetallicRoughnessTexture());
            out.emissiveImageBytes = imageBytes(v2.getEmissiveTexture());
        }
        int index = ir.addMaterial(out);
        indices.put(mat, index);
        return index;
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

    private static byte[] imageBytes(TextureModel texture) {
        if (texture == null) {
            return null;
        }
        ImageModel image = texture.getImageModel();
        if (image == null) {
            return null;
        }
        ByteBuffer data = image.getImageData();
        if (data == null) {
            return null;
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
                    ModelIR.Channel ch = new ModelIR.Channel();
                    ch.node = node;
                    ch.path = path;
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

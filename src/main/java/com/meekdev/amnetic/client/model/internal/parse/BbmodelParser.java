package com.meekdev.amnetic.client.model.internal.parse;

import com.meekdev.amnetic.client.model.TextureFilter;
import com.danrus.bb4j.api.ReadOptions;
import com.danrus.bb4j.api.utils.RenderUtils;
import com.danrus.bb4j.io.BbModelReader;
import com.danrus.bb4j.model.BbModelDocument;
import com.danrus.bb4j.model.animation.Animation;
import com.danrus.bb4j.model.animation.DataPoint;
import com.danrus.bb4j.model.animation.Keyframe;
import com.danrus.bb4j.model.geometry.CubeElement;
import com.danrus.bb4j.model.geometry.Element;
import com.danrus.bb4j.model.texture.Texture;
import com.meekdev.amnetic.client.model.ModelLoadException;
import com.meekdev.amnetic.client.model.internal.ModelIR;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

public final class BbmodelParser {

    private static final float PIXELS_PER_BLOCK = 16f;

    private BbmodelParser() {}

    public static ModelIR parse(byte[] bytes, String source) {
        BbModelDocument document;
        try {
            document = BbModelReader.read(new ByteArrayInputStream(bytes), ReadOptions.builder());
        } catch (RuntimeException | Error malformed) {
            throw new ModelLoadException("not a readable .bbmodel: " + source, malformed);
        }
        if (document == null) {
            throw new ModelLoadException("not a readable .bbmodel: " + source);
        }

        separateCoplanarFaces(document);

        ModelIR ir = new ModelIR();
        ir.setName(source);

        float resW = 16f;
        float resH = 16f;
        if (document.getResolution() != null) {
            Integer w = document.getResolution().getWidth();
            Integer h = document.getResolution().getHeight();
            resW = w == null ? resW : Math.max(1, w);
            resH = h == null ? resH : Math.max(1, h);
        }

        Map<String, Tex> byUuid = new HashMap<>();
        List<Tex> ordered = new ArrayList<>();
        if (document.getTextures() != null) {
            for (Texture texture : document.getTextures()) {
                ModelIR.Material material = blank();
                material.name = texture.getName() == null ? "texture" : texture.getName();
                material.baseColorImageBytes = decode(texture.getSource());
                float uvW = texture.getUvWidth() == null ? resW : Math.max(1, texture.getUvWidth());
                float uvH = texture.getUvHeight() == null ? resH : Math.max(1, texture.getUvHeight());
                Tex tex = new Tex(ir.addMaterial(material), uvW, uvH);
                ordered.add(tex);
                if (texture.getUuid() != null) {
                    byUuid.put(texture.getUuid(), tex);
                }
                if (texture.getId() != null) {
                    // two textures in one project can carry the same id, so the first keeps it
                    byUuid.putIfAbsent(String.valueOf(texture.getId()), tex);
                }
            }
        }
        if (ordered.isEmpty()) {
            ordered.add(new Tex(ir.addMaterial(blank()), resW, resH));
        }
        Tex fallback = ordered.getFirst();

        List<RenderUtils.RenderableMesh> meshes;
        try {
            meshes = RenderUtils.forDocument(document).getAllMeshes();
        } catch (RuntimeException failed) {
            throw new ModelLoadException("could not build geometry from " + source, failed);
        }

        Skeleton skeleton = new Skeleton(ir, groupNames(document));
        Map<Long, Builder> builders = new LinkedHashMap<>();
        Map<Long, String> partNames = new HashMap<>();

        for (RenderUtils.RenderableMesh mesh : meshes) {
            if (mesh.getFaces() == null) {
                continue;
            }
            List<RenderUtils.RenderableMesh.TransformStep> steps = mesh.getTransformSteps();
            int bone = skeleton.boneFor(steps);
            Matrix4f geometry = skeleton.geometryOf(bone, steps);
            for (RenderUtils.RenderableFace face : mesh.getFaces()) {
                Tex tex = resolve(byUuid, ordered, face.getTextureUuid(),
                        mesh.getTextureUuid(), fallback);
                long key = (((long) bone) << 32) | (tex.material() & 0xFFFFFFFFL);
                // bb4j already hands back 0..1 uvs, divided by the size of the texture the face
                // actually uses (RenderUtils.createFace / getMeshUv), falling back to the project
                // resolution only when that texture has no size of its own. Scaling again by
                // resolution/textureSize double-corrects it: on a 128px project a 64px texture
                // came out at 2x and a 32px one at 4x, and only a texture matching the project
                // resolution looked right. The uvs arrive normalised, so they pass through as-is.
                builders.computeIfAbsent(key, k -> new Builder())
                        .face(face.getVertices(), face.getVertexUvs(), face.getNormal(),
                                1f, 1f, geometry);
                partNames.putIfAbsent(key, skeleton.name(bone));
            }
        }

        if (builders.isEmpty()) {
            throw new ModelLoadException("no geometry in " + source);
        }
        for (Map.Entry<Long, Builder> entry : builders.entrySet()) {
            long key = entry.getKey();
            int bone = (int) (key >> 32);
            int material = (int) (key & 0xFFFFFFFFL);
            ir.addPart(entry.getValue().build(material, bone, skeleton.restWorld(bone),
                    partNames.get(key)));
        }
        skeleton.finish();
        importAnimations(ir, document, skeleton);
        return ir;
    }


    private static final int COPLANAR_LIMIT = 2000;

    private static void separateCoplanarFaces(BbModelDocument document) {
        if (document.getElements() == null) {
            return;
        }
        List<Element> cubes = new ArrayList<>();
        for (Element element : document.getElements()) {
            if (element instanceof CubeElement && element.getFrom() != null
                    && element.getTo() != null
                    && element.getFrom().length >= 3 && element.getTo().length >= 3) {
                cubes.add(element);
            }
        }
        if (cubes.isEmpty() || cubes.size() > COPLANAR_LIMIT) {
            return;
        }
        double[][] boxes = new double[cubes.size()][6];
        for (int i = 0; i < cubes.size(); i++) {
            Double[] from = cubes.get(i).getFrom();
            Double[] to = cubes.get(i).getTo();
            for (int axis = 0; axis < 3; axis++) {
                boxes[i][axis] = from[axis];
                boxes[i][axis + 3] = to[axis];
            }
        }

        CoplanarSeparation.separate(boxes);

        for (int i = 0; i < cubes.size(); i++) {
            Double[] from = new Double[3];
            Double[] to = new Double[3];
            for (int axis = 0; axis < 3; axis++) {
                from[axis] = boxes[i][axis];
                to[axis] = boxes[i][axis + 3];
            }
            cubes.get(i).setFrom(from);
            cubes.get(i).setTo(to);
        }
    }

    private static Map<String, String> groupNames(BbModelDocument document) {
        Map<String, String> names = new HashMap<>();
        if (document.getGroups() == null) {
            return names;
        }
        for (BbModelDocument.Group group : document.getGroups()) {
            if (group.getUuid() != null && group.getName() != null) {
                names.put(group.getUuid(), group.getName());
            }
        }
        return names;
    }

    private static void importAnimations(ModelIR ir, BbModelDocument document, Skeleton skeleton) {
        List<Animation> animations = document.getAnimations();
        if (animations == null || animations.isEmpty() || skeleton.boneCount() <= 1) {
            return;
        }
        for (Animation animation : animations) {
            Map<String, com.danrus.bb4j.model.animation.Animator> animators = animation.getAnimators();
            if (animators == null || animators.isEmpty()) {
                continue;
            }
            ModelIR.Animation clip = new ModelIR.Animation();
            clip.name = animation.getName() == null ? "animation" : animation.getName();
            clip.duration = animation.getLength() == null ? 0f : animation.getLength().floatValue();
            for (Map.Entry<String, com.danrus.bb4j.model.animation.Animator> entry : animators.entrySet()) {
                int node = skeleton.indexOf(entry.getKey());
                if (node < 0 || entry.getValue() == null) {
                    continue;
                }
                channels(clip, node, entry.getValue().getKeyframes(), skeleton);
            }
            if (clip.channels.isEmpty()) {
                continue;
            }
            if (clip.duration <= 0f) {
                for (ModelIR.Channel channel : clip.channels) {
                    clip.duration = Math.max(clip.duration, channel.times[channel.times.length - 1]);
                }
            }
            ir.animations().add(clip);
        }
    }

    private static void channels(ModelIR.Animation clip, int node, List<Keyframe> keyframes,
                                 Skeleton skeleton) {
        if (keyframes == null || keyframes.isEmpty()) {
            return;
        }
        Map<String, List<Keyframe>> byChannel = new LinkedHashMap<>();
        for (Keyframe keyframe : keyframes) {
            if (keyframe == null || keyframe.getChannel() == null || keyframe.getTime() == null) {
                continue;
            }
            byChannel.computeIfAbsent(keyframe.getChannel(), k -> new ArrayList<>()).add(keyframe);
        }
        for (Map.Entry<String, List<Keyframe>> entry : byChannel.entrySet()) {
            ModelIR.Path path = null;
            if (Keyframe.CHANNEL_POSITION.equals(entry.getKey())) {
                path = ModelIR.Path.TRANSLATION;
            } else if (Keyframe.CHANNEL_ROTATION.equals(entry.getKey())) {
                path = ModelIR.Path.ROTATION;
            } else if (Keyframe.CHANNEL_SCALE.equals(entry.getKey())) {
                path = ModelIR.Path.SCALE;
            }
            if (path == null) {
                continue;
            }
            List<Keyframe> sorted = new ArrayList<>(entry.getValue());
            sorted.sort(Comparator.comparingDouble(Keyframe::getTime));

            ModelIR.Channel channel = new ModelIR.Channel();
            channel.node = node;
            channel.path = path;
            channel.interp = interpOf(sorted);
            channel.times = new float[sorted.size()];
            channel.values = new float[sorted.size() * (path == ModelIR.Path.ROTATION ? 4 : 3)];

            for (int i = 0; i < sorted.size(); i++) {
                Keyframe keyframe = sorted.get(i);
                channel.times[i] = keyframe.getTime().floatValue();
                float[] point = point(keyframe, path == ModelIR.Path.SCALE ? 1f : 0f);
                switch (path) {
                    case TRANSLATION -> {
                        Vector3f rest = skeleton.restTranslation(node);
                        channel.values[i * 3] = rest.x + point[0] / PIXELS_PER_BLOCK;
                        channel.values[i * 3 + 1] = rest.y + point[1] / PIXELS_PER_BLOCK;
                        channel.values[i * 3 + 2] = rest.z + point[2] / PIXELS_PER_BLOCK;
                    }
                    case SCALE -> {
                        Vector3f rest = skeleton.restScale(node);
                        channel.values[i * 3] = rest.x * point[0];
                        channel.values[i * 3 + 1] = rest.y * point[1];
                        channel.values[i * 3 + 2] = rest.z * point[2];
                    }
                    case ROTATION -> {
                        Vector3f rest = skeleton.restEuler(node);
                        org.joml.Quaternionf q = new org.joml.Quaternionf().rotationXYZ(
                                (float) Math.toRadians(rest.x + point[0]),
                                (float) Math.toRadians(rest.y + point[1]),
                                (float) Math.toRadians(rest.z + point[2]));
                        channel.values[i * 4] = q.x;
                        channel.values[i * 4 + 1] = q.y;
                        channel.values[i * 4 + 2] = q.z;
                        channel.values[i * 4 + 3] = q.w;
                    }
                }
            }
            clip.channels.add(channel);
        }
    }

    private static ModelIR.Interp interpOf(List<Keyframe> keyframes) {
        boolean allStepped = true;
        boolean anySmooth = false;
        for (Keyframe keyframe : keyframes) {
            com.danrus.bb4j.model.animation.Interpolation interpolation = keyframe.getInterpolation();
            boolean stepped = interpolation != null && interpolation.isStepped();
            allStepped &= stepped;
            if (interpolation != null && (interpolation.isCatmullrom() || interpolation.isBezier())) {
                anySmooth = true;
            }
        }
        if (allStepped) {
            return ModelIR.Interp.STEP;
        }
        return anySmooth ? ModelIR.Interp.CATMULLROM : ModelIR.Interp.LINEAR;
    }

    private static float[] point(Keyframe keyframe, float missing) {
        float[] out = {missing, missing, missing};
        List<DataPoint> points = keyframe.getDataPoints();
        if (points == null || points.isEmpty() || points.getFirst() == null) {
            return out;
        }
        DataPoint point = points.getFirst();
        out[0] = number(point.getX(), missing);
        out[1] = number(point.getY(), missing);
        out[2] = number(point.getZ(), missing);
        return out;
    }

    private static float number(String value, float missing) {
        if (value == null || value.isBlank()) {
            return missing;
        }
        try {
            return Float.parseFloat(value.trim());
        } catch (NumberFormatException expression) {
            return missing;
        }
    }

    private static final class Skeleton {

        private final ModelIR ir;
        private final Map<String, String> groupNames;
        private final Map<String, Integer> byUuid = new HashMap<>();
        private final List<double[]> origins = new ArrayList<>();
        private final List<List<Integer>> children = new ArrayList<>();
        private final List<Matrix4f> restWorld = new ArrayList<>();
        private final List<Vector3f> restEuler = new ArrayList<>();

        Skeleton(ModelIR ir, Map<String, String> groupNames) {
            this.ir = ir;
            this.groupNames = groupNames;
            ModelIR.Node root = new ModelIR.Node();
            root.name = "root";
            root.parent = -1;
            root.rebuildLocal();
            ir.addNode(root);
            origins.add(new double[] {0.0, 0.0, 0.0});
            children.add(new ArrayList<>());
            restWorld.add(new Matrix4f());
            restEuler.add(new Vector3f());
        }

        int boneCount() {
            return ir.nodes().size();
        }

        int indexOf(String uuid) {
            Integer index = uuid == null ? null : byUuid.get(uuid);
            return index == null ? -1 : index;
        }

        String name(int index) {
            return ir.nodes().get(index).name;
        }

        Matrix4f restWorld(int index) {
            return restWorld.get(index);
        }

        Vector3f restTranslation(int index) {
            return ir.nodes().get(index).t;
        }

        Vector3f restScale(int index) {
            return ir.nodes().get(index).s;
        }

        Vector3f restEuler(int index) {
            return restEuler.get(index);
        }

        int boneFor(List<RenderUtils.RenderableMesh.TransformStep> steps) {
            int parent = 0;
            if (steps == null) {
                return parent;
            }
            for (int i = 0; i < steps.size() - 1; i++) {
                parent = node(steps.get(i), parent);
            }
            return parent;
        }

        Matrix4f geometryOf(int bone, List<RenderUtils.RenderableMesh.TransformStep> steps) {
            double[] origin = origins.get(bone);
            Matrix4f matrix = new Matrix4f().translation(
                    (float) -origin[0], (float) -origin[1], (float) -origin[2]);
            if (steps == null || steps.isEmpty()) {
                return matrix;
            }
            return matrix.mul(matrixOf(List.of(steps.getLast())));
        }

        void finish() {
            for (int i = 0; i < children.size(); i++) {
                List<Integer> kids = children.get(i);
                int[] array = new int[kids.size()];
                for (int k = 0; k < array.length; k++) {
                    array[k] = kids.get(k);
                }
                ir.nodes().get(i).children = array;
            }
        }

        private int node(RenderUtils.RenderableMesh.TransformStep step, int parent) {
            String uuid = step.getUuid();
            Integer existing = uuid == null ? null : byUuid.get(uuid);
            if (existing != null) {
                return existing;
            }
            double[] origin = or(step.getOrigin(), 0.0);
            double[] position = or(step.getPosition(), 0.0);
            double[] rotation = or(step.getRotation(), 0.0);
            double[] scale = or(step.getScale(), 1.0);
            double[] parentOrigin = origins.get(parent);

            ModelIR.Node node = new ModelIR.Node();
            node.name = groupNames.getOrDefault(uuid, "bone_" + ir.nodes().size());
            node.parent = parent;
            node.t.set(
                    (float) ((origin[0] + position[0] - parentOrigin[0]) / PIXELS_PER_BLOCK),
                    (float) ((origin[1] + position[1] - parentOrigin[1]) / PIXELS_PER_BLOCK),
                    (float) ((origin[2] + position[2] - parentOrigin[2]) / PIXELS_PER_BLOCK));
            node.r.rotationXYZ(
                    (float) Math.toRadians(rotation[0]),
                    (float) Math.toRadians(rotation[1]),
                    (float) Math.toRadians(rotation[2]));
            node.s.set((float) scale[0], (float) scale[1], (float) scale[2]);
            node.rebuildLocal();

            int index = ir.addNode(node);
            if (uuid != null) {
                byUuid.put(uuid, index);
            }
            origins.add(origin);
            children.add(new ArrayList<>());
            children.get(parent).add(index);
            restWorld.add(new Matrix4f(restWorld.get(parent)).mul(node.local));
            restEuler.add(new Vector3f(
                    (float) rotation[0], (float) rotation[1], (float) rotation[2]));
            return index;
        }
    }

    private record Tex(int material, float uvW, float uvH) {}

    private static Tex resolve(Map<String, Tex> byUuid, List<Tex> ordered, String faceUuid,
                               String meshUuid, Tex fallback) {
        Tex tex = lookup(byUuid, ordered, faceUuid);
        if (tex == null) {
            tex = lookup(byUuid, ordered, meshUuid);
        }
        return tex == null ? fallback : tex;
    }

    private static Tex lookup(Map<String, Tex> byUuid, List<Tex> ordered, String key) {
        if (key == null) {
            return null;
        }
        Tex named = byUuid.get(key);
        if (named != null) {
            return named;
        }
        try {
            int index = Integer.parseInt(key.trim());
            if (index >= 0 && index < ordered.size()) {
                return ordered.get(index);
            }
        } catch (NumberFormatException notAnIndex) {
            // a uuid that simply is not in this project, so nothing more to try
        }
        return null;
    }

    private static Matrix4f matrixOf(List<RenderUtils.RenderableMesh.TransformStep> steps) {
        Matrix4f matrix = new Matrix4f();
        if (steps == null) {
            return matrix;
        }
        for (RenderUtils.RenderableMesh.TransformStep step : steps) {
            double[] origin = or(step.getOrigin(), 0.0);
            double[] position = or(step.getPosition(), 0.0);
            double[] rotation = or(step.getRotation(), 0.0);
            double[] scale = or(step.getScale(), 1.0);
            matrix.translate((float) position[0], (float) position[1], (float) position[2]);
            matrix.translate((float) origin[0], (float) origin[1], (float) origin[2]);
            matrix.rotateX((float) Math.toRadians(rotation[0]));
            matrix.rotateY((float) Math.toRadians(rotation[1]));
            matrix.rotateZ((float) Math.toRadians(rotation[2]));
            matrix.scale((float) scale[0], (float) scale[1], (float) scale[2]);
            matrix.translate((float) -origin[0], (float) -origin[1], (float) -origin[2]);
        }
        return matrix;
    }

    private static double[] or(double[] value, double missing) {
        if (value != null && value.length >= 3) {
            return value;
        }
        return new double[] {missing, missing, missing};
    }

    private static ModelIR.Material blank() {
        ModelIR.Material material = new ModelIR.Material();
        material.roughness = 1f;
        material.metallic = 0f;
        material.baseColorFilter = TextureFilter.NEAREST;
        material.alphaCutoff = 0.5f;
        material.doubleSided = true;
        return material;
    }

    private static byte[] decode(String source) {
        if (source == null) {
            return null;
        }
        int comma = source.indexOf(',');
        String payload = comma < 0 ? source : source.substring(comma + 1);
        try {
            return Base64.getDecoder().decode(payload);
        } catch (IllegalArgumentException notBase64) {
            return null;
        }
    }

    private static final class Builder {

        private final List<Float> vertices = new ArrayList<>();
        private final List<Integer> indices = new ArrayList<>();

        private void face(double[][] corners, double[][] uvs, double[] normal,
                          float uScale, float vScale, Matrix4f matrix) {
            if (corners == null || corners.length < 3) {
                return;
            }
            Vector3f n = normal == null
                    ? new Vector3f(0f, 1f, 0f)
                    : matrix.transformDirection(
                            new Vector3f((float) normal[0], (float) normal[1], (float) normal[2]));
            if (n.lengthSquared() < 1.0e-8f) {
                n.set(0f, 1f, 0f);
            } else {
                n.normalize();
            }

            int base = vertices.size() / ModelIR.VERTEX_STRIDE_FLOATS;
            for (int corner = 0; corner < corners.length; corner++) {
                double[] point = corners[corner];
                Vector4f position = matrix.transform(new Vector4f(
                        (float) point[0], (float) point[1], (float) point[2], 1f));
                float u = 0f;
                float v = 0f;
                if (uvs != null && corner < uvs.length && uvs[corner] != null && uvs[corner].length >= 2) {
                    u = (float) uvs[corner][0] * uScale;
                    v = (float) uvs[corner][1] * vScale;
                }
                vertices.add(position.x / PIXELS_PER_BLOCK);
                vertices.add(position.y / PIXELS_PER_BLOCK);
                vertices.add(position.z / PIXELS_PER_BLOCK);
                vertices.add(n.x);
                vertices.add(n.y);
                vertices.add(n.z);
                vertices.add(u);
                vertices.add(v);
            }
            for (int corner = 2; corner < corners.length; corner++) {
                indices.add(base);
                indices.add(base + corner - 1);
                indices.add(base + corner);
            }
        }

        private ModelIR.Part build(int material, int node, Matrix4f transform, String name) {
            float[] flat = new float[vertices.size()];
            for (int i = 0; i < flat.length; i++) {
                flat[i] = vertices.get(i);
            }
            int[] order = new int[indices.size()];
            for (int i = 0; i < order.length; i++) {
                order[i] = indices.get(i);
            }
            return new ModelIR.Part(flat, null, order, material, node, transform, name);
        }
    }
}

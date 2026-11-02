package com.meekdev.amnetic.client.shadow.internal;

import com.meekdev.amnetic.client.framebuffer.*;
import com.meekdev.amnetic.client.compat.VanillaCompat;
import com.meekdev.amnetic.client.framebuffer.internal.NativeFramebuffer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.commands.CommandEncoder;
import com.mojang.renderpearl.api.commands.RenderPass;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.texture.TextureAtlas;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import com.meekdev.amnetic.client.light.*;
import com.meekdev.amnetic.client.light.internal.LightRegistry;
import com.meekdev.amnetic.client.render.*;
import com.meekdev.amnetic.client.shadow.*;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.pipeline.*;
import com.mojang.renderpearl.api.vertex.VertexFormat;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.system.MemoryUtil;
import java.util.*;

public final class NativeLocalShadows implements AutoCloseable {
    public static final NativeLocalShadows INSTANCE = new NativeLocalShadows();
    private static final int CUBE_FACES = 6;
    private static final int SUN_COLUMNS = 4;
    private static final int MAX_SUN_CASCADES = 4;
    private static final long SUN_CACHE_ID = Long.MIN_VALUE;
    private static final int POINT_SHADOW_REF = 1000;
    private static final int SUN_SHADOW_REF = 2000;
    private static final float NEAR_PLANE = .05f;
    private static final float MIN_FAR_PLANE = .15f;
    private static final float MIN_SPOT_FOV = .0175f;
    private static final int SUN_ANCHOR_GRID = 8;
    private static final int SUN_OCCLUDER_MARGIN = 7;
    private static final float SUN_ENTITY_RADIUS = 64;
    private static final int MATRIX_FLOATS = 16;
    private static final int BOX_VERTICES = 36;
    private static final int BOX_FLOATS = BOX_VERTICES * 3;
    private static final int CUTOUT_VERTEX_FLOATS = 5;
    private static final int MIN_UPLOAD_BYTES = 4096;
    private static final CubeFace[] FACES = {
            new CubeFace(1, 0, 0, 0, -1, 0),
            new CubeFace(-1, 0, 0, 0, -1, 0),
            new CubeFace(0, 1, 0, 0, 0, 1),
            new CubeFace(0, -1, 0, 0, 0, -1),
            new CubeFace(0, 0, 1, 0, -1, 0),
            new CubeFace(0, 0, -1, 0, -1, 0)
    };

    private record CubeFace(float x, float y, float z, float upX, float upY, float upZ) {}

    private record Mesh(OccluderCache.Collected collected, GpuBuffer opaque, int opaqueCount, GpuBuffer cutout, int cutoutCount) {
        void close() {
            if (opaque != null) opaque.close();
            if (cutout != null) cutout.close();
        }
    }

    private record Tile(int x, int y, int columns, int rows) {
        void bind(NativeScreenProgram program) {
            program.setVector("Tile", 1f / columns, 1f / rows, (2f * x + 1) / columns - 1, (2f * y + 1) / rows - 1);
        }

        void scissor(RenderPass pass, NativeFramebuffer target) {
            int width = target.width() / columns;
            int height = target.height() / rows;
            pass.enableScissor(x * target.width() / columns, y * target.height() / rows, width, height);
        }
    }

    private final NativeFramebuffer point = target("Amnetic point shadows");
    private final NativeFramebuffer spot = target("Amnetic spot shadows");
    private final NativeFramebuffer sun = target("Amnetic directional shadows");
    private Mesh sunMesh;
    private final Matrix4f[] sunBakedMatrices = new Matrix4f[MAX_SUN_CASCADES];
    private long sunEntitySignature;
    private boolean sunEntityModels;
    public int sunCascades;
    public final float[] sunMatrices = new float[MAX_SUN_CASCADES * MATRIX_FLOATS];
    public final float[] sunRadii = new float[MAX_SUN_CASCADES];
    private final EntityOccluders entities = new EntityOccluders();
    private boolean entityGeometry;
    private final Map<Long, Mesh> meshes = new HashMap<>();
    private NativeScreenProgram opaque;
    private NativeScreenProgram cutout;
    private NativeScreenProgram clear;
    private final Map<Long, Integer> baked = new HashMap<>();
    private final ArrayList<Light> candidates = new ArrayList<>();
    private final Set<Long> live = new HashSet<>();
    private ByteBuffer opaqueUpload;
    private ByteBuffer cutoutUpload;
    public int points;
    public int spots;
    public int rows;
    public int spotGrid = 1;
    public final float[] spotMatrices = new float[ShadowSettings.MAX_SPOT * MATRIX_FLOATS];

    private static NativeFramebuffer target(String name) {
        return new NativeFramebuffer(FramebufferSpec.builder().color(ColorFormat.RGBA8).depthTexture().build(), name);
    }

    public GpuTextureView sunDepth() {
        return sun.isAllocated() ? sun.depthView() : null;
    }

    public GpuTextureView pointDepth() {
        return point.isAllocated() ? point.depthView() : null;
    }

    public GpuTextureView spotDepth() {
        return spot.isAllocated() ? spot.depthView() : null;
    }

    public GpuTextureView spotColor() {
        return spot.isAllocated() ? spot.colorView(0) : null;
    }

    public void render(CameraSnapshot camera) {
        points = 0;
        spots = 0;
        sunCascades = 0;
        var mc = Minecraft.getInstance();
        for (var light : LightRegistry.INSTANCE.all()) light.setShadowRef(-1);
        if (!Shadows.isEnabled() || camera == null || mc.level == null) return;
        var settings = ShadowSettings.defaults();
        renderSun(camera, settings);
        collectCandidates(camera, settings);
        if (candidates.isEmpty()) {
            closeMeshes();
            return;
        }
        ensure();
        int wantedPoints = Math.min(settings.maxPointShadows(), countCandidates(LightType.POINT));
        int wantedSpots = Math.min(settings.maxSpotShadows(), countCandidates(LightType.SPOT));
        rows = Math.max(1, wantedPoints);
        if (allocateTargets(settings.pointFaceSize(), settings.resolution(), wantedPoints, wantedSpots)) baked.clear();
        live.clear();
        for (var light : candidates) {
            boolean isPoint = light.type() == LightType.POINT;
            if (isPoint ? points >= settings.maxPointShadows() : spots >= settings.maxSpotShadows()) continue;
            live.add(light.id());
            renderLight(light, camera, settings, mc.level);
        }
        meshes.entrySet().removeIf(entry -> {
            if (live.contains(entry.getKey())) return false;
            entry.getValue().close();
            return true;
        });
        if (sunCascades > 0) live.add(SUN_CACHE_ID);
        OccluderCache.retainOnly(live);
        baked.keySet().retainAll(live);
    }

    private void collectCandidates(CameraSnapshot camera, ShadowSettings settings) {
        candidates.clear();
        double maxDistance = settings.maxDistance() * settings.maxDistance();
        for (var light : LightRegistry.INSTANCE.all()) {
            boolean local = light.type() == LightType.POINT || light.type() == LightType.SPOT;
            if (light.isEnabled() && light.castsShadow() && local && distanceSquared(light, camera) <= maxDistance) {
                candidates.add(light);
            }
        }
        candidates.sort(Comparator.comparingDouble(light -> distanceSquared(light, camera)));
    }

    private static double distanceSquared(Light light, CameraSnapshot camera) {
        double dx = light.x() - camera.eye.x;
        double dy = light.y() - camera.eye.y;
        double dz = light.z() - camera.eye.z;
        return dx * dx + dy * dy + dz * dz;
    }

    private int countCandidates(LightType type) {
        return (int) candidates.stream().filter(light -> light.type() == type).count();
    }

    private boolean allocateTargets(int faceSize, int tileSize, int wantedPoints, int wantedSpots) {
        int neededGrid = wantedSpots <= 1 ? 1 : wantedSpots <= 4 ? 2 : 4;
        int nextGrid = Math.max(spot.isAllocated() ? spotGrid : 1, neededGrid);
        int pointWidth = faceSize * CUBE_FACES;
        int pointHeight = faceSize * rows;
        boolean pointResized = wantedPoints > 0 && (point.width() != pointWidth || point.height() != pointHeight);
        boolean spotResized = wantedSpots > 0 && (spot.width() != tileSize * nextGrid || spot.height() != tileSize * nextGrid);
        if (wantedPoints > 0) point.allocate(pointWidth, pointHeight);
        else point.close();
        if (wantedSpots > 0) {
            spotGrid = nextGrid;
            spot.allocate(tileSize * spotGrid, tileSize * spotGrid);
        }
        return pointResized || spotResized;
    }

    private void renderLight(Light light, CameraSnapshot camera, ShadowSettings settings, ClientLevel level) {
        boolean isPoint = light.type() == LightType.POINT;
        var collected = OccluderCache.getOrCompute(light.id(), level, (float) light.x(), (float) light.y(), (float) light.z(), light.range());
        var mesh = meshes.get(light.id());
        if (mesh == null || mesh.collected.list != collected.list) {
            if (mesh != null) mesh.close();
            mesh = build(collected);
            meshes.put(light.id(), mesh);
        }
        var projection = new Matrix4f().perspective(isPoint ? (float) Math.PI / 2 : spotFov(light), 1, NEAR_PLANE, farPlane(light));
        float x = (float) (light.x() - collected.anchorX);
        float y = (float) (light.y() - collected.anchorY);
        float z = (float) (light.z() - collected.anchorZ);
        float partial = VanillaCompat.partialTick(false);
        long entitySignature = settings.entityShadows()
                ? entities.gather(level, light.x(), light.y(), light.z(), light.range(), partial)
                : 0;
        int signature = Objects.hash(entitySignature, System.identityHashCode(collected.list),
                light.x(), light.y(), light.z(), light.dirX(), light.dirY(), light.dirZ(),
                light.cosOuter(), light.range(), settings.entityModels(), isPoint ? POINT_SHADOW_REF + points : spots);
        boolean dirty = !Objects.equals(baked.get(light.id()), signature);
        entityGeometry = dirty && entitySignature != 0
                && entities.build(collected.anchorX, collected.anchorY, collected.anchorZ, settings.entityModels(), partial);
        if (isPoint) {
            light.setShadowRef(POINT_SHADOW_REF + points);
            for (int face = 0; dirty && face < CUBE_FACES; face++) {
                var f = FACES[face];
                var view = new Matrix4f().lookAt(x, y, z, x + f.x(), y + f.y(), z + f.z(), f.upX(), f.upY(), f.upZ());
                var tile = new Tile(face, points, CUBE_FACES, rows);
                draw(mesh, new Matrix4f(projection).mul(view), point, tile);
            }
            points++;
        } else {
            light.setShadowRef(spots);
            var up = spotUp(light);
            if (dirty) {
                var view = new Matrix4f().lookAt(new Vector3f(x, y, z), new Vector3f(x + light.dirX(), y + light.dirY(), z + light.dirZ()), up);
                draw(mesh, new Matrix4f(projection).mul(view), spot, new Tile(spots % spotGrid, spots / spotGrid, spotGrid, spotGrid));
            }
            x = (float) (light.x() - camera.eye.x);
            y = (float) (light.y() - camera.eye.y);
            z = (float) (light.z() - camera.eye.z);
            var view = new Matrix4f().lookAt(new Vector3f(x, y, z), new Vector3f(x + light.dirX(), y + light.dirY(), z + light.dirZ()), up);
            new Matrix4f(projection).mul(view).get(spotMatrices, spots * MATRIX_FLOATS);
            spots++;
        }
        baked.put(light.id(), signature);
    }

    private static float farPlane(Light light) {
        return Math.max(MIN_FAR_PLANE, light.range());
    }

    private static float spotFov(Light light) {
        return Math.max(MIN_SPOT_FOV, 2 * (float) Math.acos(Math.clamp(light.cosOuter(), -1f, 1f)));
    }

    private static Vector3f spotUp(Light light) {
        return Math.abs(light.dirY()) > .99f ? new Vector3f(1, 0, 0) : new Vector3f(0, 1, 0);
    }

    private void renderSun(CameraSnapshot cam, ShadowSettings settings) {
        Light light = findSun();
        if (light == null) {
            sun.close();
            closeSunMesh();
            return;
        }
        ensure();
        var level = Minecraft.getInstance().level;
        int resolution = settings.sunResolution();
        int count = settings.sunCascades();
        boolean resized = sun.width() != resolution * SUN_COLUMNS || sun.height() != resolution;
        sun.allocate(resolution * SUN_COLUMNS, resolution);
        float far = settings.sunDistance();
        float radiusLimit = Math.min(settings.sunBlockOccluderRadius(), far);
        var collected = OccluderCache.getOrCompute(SUN_CACHE_ID, level, sunAnchor(cam.eye.x), sunAnchor(cam.eye.y),
                sunAnchor(cam.eye.z), radiusLimit + SUN_OCCLUDER_MARGIN);
        boolean geometryChanged = sunMesh == null || sunMesh.collected.list != collected.list;
        if (geometryChanged) {
            closeSunMesh();
            sunMesh = build(collected);
        }
        float partial = VanillaCompat.partialTick(false);
        long entitySignature = settings.entityShadows()
                ? entities.gather(level, cam.eye.x, cam.eye.y, cam.eye.z, Math.min(far, SUN_ENTITY_RADIUS), partial)
                : 0;
        boolean forceBake = resized || geometryChanged || entitySignature != sunEntitySignature
                || settings.entityModels() != sunEntityModels;
        entityGeometry = false;
        boolean entitiesPrepared = false;
        var direction = new Vector3f(light.dirX(), light.dirY(), light.dirZ()).normalize();
        var up = Math.abs(direction.y) > .99f ? new Vector3f(0, 0, 1) : new Vector3f(0, 1, 0);
        var right = new Vector3f(direction).cross(up).normalize();
        up = new Vector3f(right).cross(direction).normalize();
        var forward = cam.view.positiveZ(new Vector3f()).negate();
        float tanH = 1 / cam.projection.m00();
        float tanV = 1 / cam.projection.m11();
        float k2 = tanH * tanH + tanV * tanV;
        float[] splits = cascadeSplits(count, far, settings.sunSplitLambda());
        for (int i = 0; i < count; i++) {
            float a = splits[i];
            float b = splits[i + 1];
            float t = Math.clamp((a + b) * (1 + k2) * .5f, a, b);
            float radius = (float) Math.ceil(Math.sqrt(Math.max(b * b * k2 + (t - b) * (t - b), a * a * k2 + (t - a) * (t - a))) * 2) / 2;
            radius = Math.max(radius, .5f);
            double cx = cam.eye.x + forward.x * t;
            double cy = cam.eye.y + forward.y * t;
            double cz = cam.eye.z + forward.z * t;
            double texel = 2.0 * radius / resolution;
            double r = cx * right.x + cy * right.y + cz * right.z;
            double u = cx * up.x + cy * up.y + cz * up.z;
            double dr = Math.floor(r / texel) * texel - r;
            double du = Math.floor(u / texel) * texel - u;
            cx += right.x * dr + up.x * du;
            cy += right.y * dr + up.y * du;
            cz += right.z * dr + up.z * du;
            float eyeDistance = radius + settings.sunCasterExtension();
            var projection = new Matrix4f().ortho(-radius, radius, -radius, radius, 0, 2 * radius + settings.sunCasterExtension());
            var eye = new Vector3f(
                    (float) (cx - collected.anchorX) - direction.x * eyeDistance,
                    (float) (cy - collected.anchorY) - direction.y * eyeDistance,
                    (float) (cz - collected.anchorZ) - direction.z * eyeDistance);
            var bake = new Matrix4f(projection).mul(new Matrix4f().lookAt(eye, new Vector3f(eye).add(direction), up));
            if (forceBake || sunBakedMatrices[i] == null || !bake.equals(sunBakedMatrices[i])) {
                if (!entitiesPrepared) {
                    entityGeometry = entitySignature != 0 && entities.build(collected.anchorX, collected.anchorY,
                            collected.anchorZ, settings.entityModels(), partial);
                    entitiesPrepared = true;
                }
                draw(sunMesh, bake, sun, new Tile(i, 0, SUN_COLUMNS, 1));
                if (sunBakedMatrices[i] == null) sunBakedMatrices[i] = new Matrix4f();
                sunBakedMatrices[i].set(bake);
            }
            eye.set(
                    (float) (cx - cam.eye.x) - direction.x * eyeDistance,
                    (float) (cy - cam.eye.y) - direction.y * eyeDistance,
                    (float) (cz - cam.eye.z) - direction.z * eyeDistance);
            var view = new Matrix4f().lookAt(eye, new Vector3f(eye).add(direction), up);
            new Matrix4f(projection).mul(view).get(sunMatrices, i * MATRIX_FLOATS);
            sunRadii[i] = radius;
        }
        sunEntitySignature = entitySignature;
        sunEntityModels = settings.entityModels();
        sunCascades = count;
        light.setShadowRef(SUN_SHADOW_REF);
    }

    private static Light findSun() {
        for (var candidate : LightRegistry.INSTANCE.all()) {
            if (candidate.isEnabled() && candidate.castsShadow() && candidate.type() == LightType.DIRECTIONAL) return candidate;
        }
        return null;
    }

    private static float sunAnchor(double eye) {
        return (float) (Math.floor(eye / SUN_ANCHOR_GRID) * SUN_ANCHOR_GRID + SUN_ANCHOR_GRID / 2);
    }

    private static float[] cascadeSplits(int count, float far, float lambda) {
        float near = NEAR_PLANE;
        float[] splits = new float[count + 1];
        splits[0] = near;
        splits[count] = far;
        for (int i = 1; i < count; i++) {
            float f = i / (float) count;
            splits[i] = lambda * near * (float) Math.pow(far / near, f) + (1 - lambda) * (near + (far - near) * f);
        }
        return splits;
    }

    private void ensure() {
        if (opaque != null) return;
        Set<String> switches = VanillaCompat.zeroToOne() ? Set.of(ShaderSwitches.ZERO_TO_ONE) : Set.of();
        var depth = Optional.of(new DepthStencilState(CompareOp.LESS_THAN, true));
        var positions = VertexFormat.builder(0)
                .addAttribute("Position", GpuFormat.RGB32_FLOAT)
                .build();
        var texturedPositions = VertexFormat.builder(0)
                .addAttribute("Position", GpuFormat.RGB32_FLOAT)
                .addAttribute("UV", GpuFormat.RG32_FLOAT)
                .build();
        opaque = new NativeScreenProgram(id("native/depth.vsh"), id("depth.fsh"), new VertexFormat[]{positions},
                false, depth, switches, ColorTargetState.DEFAULT);
        cutout = new NativeScreenProgram(id("native/depth_cutout.vsh"), id("depth_cutout.fsh"),
                new VertexFormat[]{texturedPositions}, false, depth, switches, ColorTargetState.DEFAULT);
        clear = new NativeScreenProgram(id("native/clear.vsh"), id("clear.fsh"), new VertexFormat[0], false,
                Optional.of(new DepthStencilState(CompareOp.ALWAYS_PASS, true)), ColorTargetState.DEFAULT);
    }

    private static Identifier id(String file) {
        return Identifier.fromNamespaceAndPath("amnetic", "shaders/shadow/" + file);
    }

    private void draw(Mesh mesh, Matrix4f matrix, NativeFramebuffer target, Tile tile) {
        tile.bind(clear);
        var encoder = RenderSystem.getDevice().createCommandEncoder();
        clear.draw(encoder, target.descriptor(), pass -> {
            tile.scissor(pass, target);
            pass.draw(3, 1, 0, 0);
        });
        draw(encoder, opaque, mesh.opaque, mesh.opaqueCount, matrix, target, tile);
        if (entityGeometry) draw(encoder, opaque, entities.nativeVertices(), entities.nativeCount(), matrix, target, tile);
        if (mesh.cutoutCount > 0) {
            var atlas = Minecraft.getInstance().getTextureManager().getTexture(TextureAtlas.LOCATION_BLOCKS);
            cutout.setTexture("uAtlas", atlas.getTextureView());
            draw(encoder, cutout, mesh.cutout, mesh.cutoutCount, matrix, target, tile);
        }
        encoder.submit();
    }

    private static void draw(CommandEncoder encoder, NativeScreenProgram program, GpuBuffer buffer, int count,
                             Matrix4f matrix, NativeFramebuffer target, Tile tile) {
        if (count == 0) return;
        draw(encoder, program, buffer.slice(), count, matrix, target, tile);
    }

    private static void draw(CommandEncoder encoder, NativeScreenProgram program, GpuBufferSlice buffer, int count,
                             Matrix4f matrix, NativeFramebuffer target, Tile tile) {
        if (count == 0) return;
        program.setMatrix("uViewProj", matrix);
        tile.bind(program);
        program.draw(encoder, target.descriptor(), pass -> {
            tile.scissor(pass, target);
            pass.setVertexBuffer(0, buffer);
            pass.draw(count, 1, 0, 0);
        });
    }

    private Mesh build(OccluderCache.Collected c) {
        int[] boxes = {0};
        int cutoutFloats = 0;
        for (var entry : c.list) {
            if (entry.shape != null) entry.shape.forAllBoxes((minX, minY, minZ, maxX, maxY, maxZ) -> boxes[0]++);
            if (entry.cutoutVerts != null) cutoutFloats += entry.cutoutVerts.length;
        }
        int opaqueBytes = boxes[0] * BOX_FLOATS * Float.BYTES;
        int cutoutBytes = cutoutFloats * Float.BYTES;
        opaqueUpload = reserve(opaqueUpload, opaqueBytes);
        cutoutUpload = reserve(cutoutUpload, cutoutBytes);
        var opaqueData = opaqueUpload.clear().limit(opaqueBytes);
        var cutoutData = cutoutUpload.clear().limit(cutoutBytes);
        var opaqueVertices = opaqueData.asFloatBuffer();
        var cutoutVertices = cutoutData.asFloatBuffer();
        for (var entry : c.list) {
            float x = entry.pos.getX() - c.anchorX;
            float y = entry.pos.getY() - c.anchorY;
            float z = entry.pos.getZ() - c.anchorZ;
            if (entry.shape != null) {
                entry.shape.forAllBoxes((minX, minY, minZ, maxX, maxY, maxZ) -> OccluderMesh.emitBox(opaqueVertices,
                        x + (float) minX, y + (float) minY, z + (float) minZ,
                        x + (float) maxX, y + (float) maxY, z + (float) maxZ));
            }
            if (entry.cutoutVerts != null) putCutout(cutoutVertices, entry.cutoutVerts, x, y, z);
        }
        var device = RenderSystem.getDevice();
        GpuBuffer opaqueBuffer = opaqueData.remaining() > 0
                ? device.createBuffer(() -> "Amnetic shadow geometry", GpuBuffer.USAGE_VERTEX, opaqueData)
                : null;
        GpuBuffer cutoutBuffer = cutoutData.remaining() > 0
                ? device.createBuffer(() -> "Amnetic cutout shadows", GpuBuffer.USAGE_VERTEX, cutoutData)
                : null;
        return new Mesh(c, opaqueBuffer, boxes[0] * BOX_VERTICES, cutoutBuffer, cutoutFloats / CUTOUT_VERTEX_FLOATS);
    }

    private static void putCutout(FloatBuffer out, float[] vertices, float x, float y, float z) {
        for (int i = 0; i + 4 < vertices.length; i += CUTOUT_VERTEX_FLOATS) {
            out.put(vertices[i] + x);
            out.put(vertices[i + 1] + y);
            out.put(vertices[i + 2] + z);
            out.put(vertices[i + 3]);
            out.put(vertices[i + 4]);
        }
    }

    private static ByteBuffer reserve(ByteBuffer buffer, int bytes) {
        if (buffer != null && buffer.capacity() >= bytes) return buffer;
        var next = MemoryUtil.memAlloc(Math.max(MIN_UPLOAD_BYTES, bytes));
        if (buffer != null) MemoryUtil.memFree(buffer);
        return next;
    }

    private void closeSunMesh() {
        if (sunMesh == null) return;
        sunMesh.close();
        sunMesh = null;
    }

    private void closeMeshes() {
        for (var mesh : meshes.values()) mesh.close();
        meshes.clear();
    }

    private void closePrograms() {
        if (opaque != null) {
            opaque.close();
            opaque = null;
        }
        if (cutout != null) {
            cutout.close();
            cutout = null;
        }
        if (clear != null) {
            clear.close();
            clear = null;
        }
    }

    private void freeUploads() {
        if (opaqueUpload != null) {
            MemoryUtil.memFree(opaqueUpload);
            opaqueUpload = null;
        }
        if (cutoutUpload != null) {
            MemoryUtil.memFree(cutoutUpload);
            cutoutUpload = null;
        }
    }

    public void close() {
        candidates.clear();
        live.clear();
        entityGeometry = false;
        entities.dispose();
        freeUploads();
        sun.close();
        closeSunMesh();
        Arrays.fill(sunBakedMatrices, null);
        sunCascades = 0;
        closeMeshes();
        point.close();
        spot.close();
        closePrograms();
        points = 0;
        spots = 0;
        spotGrid = 1;
        baked.clear();
    }
}

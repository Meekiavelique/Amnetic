package com.meekdev.amnetic.client.light.internal;

import com.meekdev.amnetic.client.light.Light;
import com.meekdev.amnetic.client.light.LightType;
import com.meekdev.amnetic.client.render.CameraSnapshot;
import com.meekdev.amnetic.client.render.NativeScreenProgram;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import org.joml.FrustumIntersection;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.lwjgl.BufferUtils;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class TiledLighting {
    private static final int GEOMETRY_STRIDE = 10;
    private static final int RECTANGLE_STRIDE = 6;
    private static final int MIN_TILED_LIGHTS = 4;
    private static final int FIRST_TILE_PIXELS = 32;
    private static final List<Light> SELECTED = new ArrayList<>();
    private static final int[] BOUNDS = new int[4];
    private static final float[] SPOT_BOUNDS = new float[4];
    private static GpuBuffer buffer;
    private static int capacity;
    private static int columns;
    private static int rows;
    private static int limit = 1 << 20;
    private static final LightClusterLists CLUSTERS = new LightClusterLists();
    private static ByteBuffer uploadBytes;
    private static final Matrix4f projection = new Matrix4f();
    private static final Matrix4f viewDepth = new Matrix4f();
    private static float[] geometry = new float[0];
    private static float[] previousGeometry = new float[0];
    private static int previousWidth = -1;
    private static int previousHeight = -1;
    private static int previousCount = -1;
    private static int[] rectangles = new int[0];
    private static int[] previousRectangles = new int[0];
    private static int rectangleCount = -1;
    private static int rectangleColumns;
    private static int rectangleRows;
    public static float averageLights;

    private TiledLighting() {}

    public static int pack(LightBuffer lightBuffer, List<Light> lights, Vec3 eye, FrustumIntersection frustum, List<Light> order) {
        var selected = order == null ? SELECTED : order;
        int count = lightBuffer.pack(lights, eye, frustum, selected);
        var camera = CameraSnapshot.current();
        if (camera == null || count <= MIN_TILED_LIGHTS) {
            columns = 0;
            previousCount = -1;
            return count;
        }
        var target = Minecraft.getInstance().gameRenderer.mainRenderTarget();
        int width = target.width;
        int height = target.height;
        fillGeometry(selected, count, eye);
        if (sameView(count, width, height, camera)) return count;
        rememberView(count, width, height, camera);
        chooseTiles(width, height, count);
        fillRectangles(selected, count, eye, camera, width, height);
        if (sameRectangles(count)) return count;
        rectangleCount = count;
        rectangleColumns = columns;
        rectangleRows = rows;
        System.arraycopy(rectangles, 0, previousRectangles, 0, count * RECTANGLE_STRIDE);
        CLUSTERS.build(columns, rows, LightDepthSlices.COUNT, rectangles, count);
        upload();
        return count;
    }

    private static void fillGeometry(List<Light> selected, int count, Vec3 eye) {
        if (geometry.length < count * GEOMETRY_STRIDE) {
            geometry = new float[count * GEOMETRY_STRIDE];
            previousGeometry = new float[count * GEOMETRY_STRIDE];
            previousCount = -1;
        }
        for (int i = 0; i < count; i++) {
            var light = selected.get(i);
            int at = i * GEOMETRY_STRIDE;
            geometry[at] = (float) (light.x() - eye.x);
            geometry[at + 1] = (float) (light.y() - eye.y);
            geometry[at + 2] = (float) (light.z() - eye.z);
            geometry[at + 3] = influenceRadius(light);
            geometry[at + 4] = light.type().id();
            geometry[at + 5] = light.style() == 0 ? 0 : 1;
            boolean spot = light.type() == LightType.SPOT && light.style() == 0;
            geometry[at + 6] = spot ? light.dirX() : 0;
            geometry[at + 7] = spot ? light.dirY() : 0;
            geometry[at + 8] = spot ? light.dirZ() : 0;
            geometry[at + 9] = spot ? light.cosOuter() : 0;
        }
    }

    private static boolean sameView(int count, int width, int height, CameraSnapshot camera) {
        return previousCount == count && previousWidth == width && previousHeight == height
                && projection.equals(camera.viewProj) && viewDepth.equals(camera.view)
                && Arrays.equals(geometry, 0, count * GEOMETRY_STRIDE, previousGeometry, 0, count * GEOMETRY_STRIDE);
    }

    private static void rememberView(int count, int width, int height, CameraSnapshot camera) {
        previousCount = count;
        previousWidth = width;
        previousHeight = height;
        projection.set(camera.viewProj);
        viewDepth.set(camera.view);
        System.arraycopy(geometry, 0, previousGeometry, 0, count * GEOMETRY_STRIDE);
    }

    private static void chooseTiles(int width, int height, int count) {
        int pixels = FIRST_TILE_PIXELS;
        do {
            columns = (width + pixels - 1) / pixels;
            rows = (height + pixels - 1) / pixels;
            pixels *= 2;
        } while ((long) columns * rows * LightDepthSlices.COUNT * (count + 2) > limit);
    }

    private static void fillRectangles(List<Light> selected, int count, Vec3 eye, CameraSnapshot camera, int width, int height) {
        if (rectangles.length < count * RECTANGLE_STRIDE) {
            rectangles = new int[count * RECTANGLE_STRIDE];
            previousRectangles = new int[count * RECTANGLE_STRIDE];
            rectangleCount = -1;
        }
        for (int index = 0; index < count; index++) {
            var light = selected.get(index);
            boolean everywhere = light.type() == LightType.DIRECTIONAL || light.style() != 0;
            if (everywhere) {
                LightTileBounds.full(columns, rows, BOUNDS);
            } else if (light.type() == LightType.SPOT) {
                spotTiles(light, eye, camera.viewProj, width, height);
            } else {
                float radius = influenceRadius(light);
                LightTileBounds.project(camera.viewProj, (float) (light.x() - eye.x), (float) (light.y() - eye.y),
                        (float) (light.z() - eye.z), radius, columns, rows, BOUNDS);
            }
            int at = index * RECTANGLE_STRIDE;
            System.arraycopy(BOUNDS, 0, rectangles, at, 4);
            if (everywhere) {
                rectangles[at + 4] = 0;
                rectangles[at + 5] = LightDepthSlices.COUNT - 1;
            } else {
                depthSlices(light, eye, camera.view, index, at);
            }
        }
    }

    private static void spotTiles(Light light, Vec3 eye, Matrix4fc viewProj, int width, int height) {
        SpotlightBounds.project(viewProj, (float) (light.x() - eye.x), (float) (light.y() - eye.y), (float) (light.z() - eye.z),
                light.dirX(), light.dirY(), light.dirZ(), light.range(), light.cosOuter(), width, height, SPOT_BOUNDS);
        BOUNDS[0] = Math.clamp((int) Math.floor(SPOT_BOUNDS[0] * columns) - 1, 0, columns - 1);
        BOUNDS[1] = Math.clamp((int) Math.floor(SPOT_BOUNDS[1] * rows) - 1, 0, rows - 1);
        BOUNDS[2] = Math.clamp((int) Math.floor(SPOT_BOUNDS[2] * columns) + 1, 0, columns - 1);
        BOUNDS[3] = Math.clamp((int) Math.floor(SPOT_BOUNDS[3] * rows) + 1, 0, rows - 1);
    }

    private static void depthSlices(Light light, Vec3 eye, Matrix4fc view, int index, int at) {
        double x = light.x() - eye.x;
        double y = light.y() - eye.y;
        double z = light.z() - eye.z;
        double center = -(view.m02() * x + view.m12() * y + view.m22() * z + view.m32());
        double scale = Math.sqrt((double) view.m02() * view.m02() + (double) view.m12() * view.m12()
                + (double) view.m22() * view.m22());
        double radius = geometry[index * GEOMETRY_STRIDE + 3] * scale;
        rectangles[at + 4] = LightDepthSlices.first(center, radius);
        rectangles[at + 5] = LightDepthSlices.last(center, radius);
    }

    private static boolean sameRectangles(int count) {
        return rectangleCount == count && rectangleColumns == columns && rectangleRows == rows
                && Arrays.equals(rectangles, 0, count * RECTANGLE_STRIDE, previousRectangles, 0, count * RECTANGLE_STRIDE);
    }

    private static void upload() {
        int length = CLUSTERS.length();
        int clusters = columns * rows * LightDepthSlices.COUNT;
        averageLights = CLUSTERS.references() / (float) clusters;
        if (uploadBytes == null || uploadBytes.capacity() < length * 4) {
            int grown = uploadBytes == null ? 0 : uploadBytes.capacity() + uploadBytes.capacity() / 2;
            uploadBytes = BufferUtils.createByteBuffer(Math.max(length * 4, grown));
        }
        var bytes = uploadBytes.clear().limit(length * 4);
        bytes.asIntBuffer().put(CLUSTERS.entries(), 0, length);
        if (buffer == null || capacity < length) {
            if (buffer != null) buffer.close();
            capacity = length;
            buffer = RenderSystem.getDevice().createBuffer(() -> "Amnetic clustered light indices",
                    GpuBuffer.USAGE_UNIFORM_TEXEL_BUFFER | GpuBuffer.USAGE_COPY_DST, (long) capacity * 4);
        }
        var encoder = RenderSystem.getDevice().createCommandEncoder();
        encoder.writeToBuffer(buffer.slice(0, (long) length * 4), bytes);
        encoder.submit();
    }

    private static float influenceRadius(Light light) {
        float extent = Math.max(Math.max(Math.abs(light.areaW()), Math.abs(light.areaH())), Math.abs(light.tubeLen()) * .5F);
        if (light.type() == LightType.AREA_RECT) extent = Math.max(extent, (float) Math.hypot(light.areaW(), light.areaH()));
        return light.range() + extent;
    }

    public static void bind(NativeScreenProgram shader, boolean enabled) {
        enabled &= columns > 0 && buffer != null;
        shader.setInt("TileLighting", enabled ? 1 : 0);
        if (!enabled) return;
        shader.setTexels("TileLights", buffer.slice());
        var view = CameraSnapshot.current().view;
        shader.setInt("TileDepthSlices", LightDepthSlices.COUNT);
        shader.setVector("TileViewDepth", -view.m02(), -view.m12(), -view.m22(), -view.m32());
        shader.setInt("TileColumns", columns);
        shader.setInt("TileRows", rows);
    }

    public static void close() {
        if (buffer != null) buffer.close();
        buffer = null;
        capacity = 0;
        columns = 0;
        previousCount = -1;
        rectangleCount = -1;
        CLUSTERS.clear();
        uploadBytes = null;
    }
}

package com.example.mirror;

import com.meekdev.amnetic.client.instanced.*;
import com.meekdev.amnetic.client.scene.CaptureContext;
import com.meekdev.amnetic.client.scene.PlanarReflection;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

public final class MirrorFeature {

    public static final Identifier ID = Identifier.fromNamespaceAndPath("example", "mirror");
    private static final Identifier REFLECTION_ID = Identifier.fromNamespaceAndPath("example", "mirror_reflection");
    private static final Identifier FACE_ID = Identifier.fromNamespaceAndPath("example", "mirror_face");

    private static final int ACQUIRE_RADIUS = 12;
    private static final int TRACK_DIST = 64;

    public static Block MIRROR;

    private static BlockPos trackedPos;
    private static Direction facing;
    private static final List<BlockPos> surface = new ArrayList<>();

    private MirrorFeature() {}

    public static void register() {
        ResourceKey<Block> blockKey = ResourceKey.create(Registries.BLOCK, ID);
        MIRROR = Registry.register(BuiltInRegistries.BLOCK, ID,
                new MirrorBlock(BlockBehaviour.Properties.of()
                        .mapColor(MapColor.METAL)
                        .strength(1.0f)
                        .sound(SoundType.METAL)
                        .setId(blockKey)));

        ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, ID);
        Registry.register(BuiltInRegistries.ITEM, ID,
                new BlockItem(MIRROR, new Item.Properties().useBlockDescriptionPrefix().setId(itemKey)));
    }

    public static void registerClient() {
        PlanarReflection.builder()
                .plane(MirrorFeature::findPlane)
                .resolution(0.5f)
                .obliqueClip(true)
                .activateWithin(TRACK_DIST)
                .register(REFLECTION_ID);

        InstancedMesh.builder(BuiltinShader.TRANSFORM)
                .geometry(MeshData.quad())
                .shaders(PlanarReflection.SCREEN_SHADER, PlanarReflection.SCREEN_SHADER)
                .extraSampler("ReflectionSampler", REFLECTION_ID, 1)
                .renderState(RenderState.builder()
                        .depthTest(true).depthWrite(true)
                        .blend(RenderState.BlendMode.NONE)
                        .backfaceCulling(false)
                        .build())
                .phase(InstancePhase.WORLD_LAST)
                .onRender(MirrorFeature::renderFaces)
                .register(FACE_ID);
    }

    private static PlanarReflection.Plane findPlane(CaptureContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return null;
        Vec3 eye = ctx.mainEye();

        BlockPos best = null;
        Direction bestFacing = null;

        if (trackedPos != null) {
            BlockState st = mc.level.getBlockState(trackedPos);
            double d = eye.distanceToSqr(trackedPos.getX() + 0.5, trackedPos.getY() + 0.5, trackedPos.getZ() + 0.5);
            if (st.getBlock() instanceof MirrorBlock && d <= (double) TRACK_DIST * TRACK_DIST) {
                best = trackedPos;
                bestFacing = st.getValue(HorizontalDirectionalBlock.FACING);
            }
        }

        if (best == null) {
            BlockPos center = BlockPos.containing(eye.x, eye.y, eye.z);
            double bestDist = Double.MAX_VALUE;
            for (BlockPos p : BlockPos.betweenClosed(
                    center.offset(-ACQUIRE_RADIUS, -ACQUIRE_RADIUS, -ACQUIRE_RADIUS),
                    center.offset(ACQUIRE_RADIUS, ACQUIRE_RADIUS, ACQUIRE_RADIUS))) {
                BlockState st = mc.level.getBlockState(p);
                if (st.getBlock() instanceof MirrorBlock) {
                    double d = eye.distanceToSqr(p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5);
                    if (d < bestDist) {
                        bestDist = d;
                        best = p.immutable();
                        bestFacing = st.getValue(HorizontalDirectionalBlock.FACING);
                    }
                }
            }
        }

        if (best == null) {
            trackedPos = null;
            facing = null;
            surface.clear();
            return null;
        }

        trackedPos = best;
        facing = bestFacing;
        collectSurface(mc, best, bestFacing);

        Vec3i n = bestFacing.getUnitVec3i();
        Vec3 point = new Vec3(best.getX() + 0.5 + n.getX() * 0.5,
                best.getY() + 0.5 + n.getY() * 0.5,
                best.getZ() + 0.5 + n.getZ() * 0.5);
        return new PlanarReflection.Plane(point, new Vector3f(n.getX(), n.getY(), n.getZ()));
    }

    private static void collectSurface(Minecraft mc, BlockPos start, Direction face) {
        surface.clear();
        Vec3i nv = face.getUnitVec3i();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        HashSet<BlockPos> seen = new HashSet<>();
        queue.add(start);
        seen.add(start);
        int cap = 256;
        while (!queue.isEmpty() && surface.size() < cap) {
            BlockPos p = queue.poll();
            surface.add(p);
            for (Direction d : Direction.values()) {
                Vec3i dv = d.getUnitVec3i();
                if (dv.getX() * nv.getX() + dv.getY() * nv.getY() + dv.getZ() * nv.getZ() != 0) continue; // in-plane only
                BlockPos np = p.relative(d);
                if (!seen.add(np)) continue;
                BlockState st = mc.level.getBlockState(np);
                if (st.getBlock() instanceof MirrorBlock
                        && st.getValue(HorizontalDirectionalBlock.FACING) == face) {
                    queue.add(np);
                }
            }
        }
    }

    private static void renderFaces(InstanceRenderContext ctx,
                                    InstanceBatch<BuiltinShader.Transform> batch) {
        if (facing == null || surface.isEmpty()) return;

        Vec3i ni = facing.getUnitVec3i();
        Vector3f n = new Vector3f(ni.getX(), ni.getY(), ni.getZ()).normalize();
        Vector3f right = new Vector3f(n).cross(0f, 1f, 0f);
        if (right.lengthSquared() < 1e-6f) right.set(1f, 0f, 0f);
        right.normalize();
        Vector3f up = new Vector3f(right).cross(n).normalize();

        Vec3 cam = ctx.cameraPos();
        float s = 1.0f; // full-block faces tile seamlessly
        for (BlockPos pos : surface) {
            float cx = (float) (pos.getX() + 0.5 + n.x * 0.501 - cam.x);
            float cy = (float) (pos.getY() + 0.5 + n.y * 0.501 - cam.y);
            float cz = (float) (pos.getZ() + 0.5 + n.z * 0.501 - cam.z);

            Matrix4f m = new Matrix4f();
            m.m00(right.x * s); m.m01(right.y * s); m.m02(right.z * s);
            m.m10(n.x);         m.m11(n.y);         m.m12(n.z);
            m.m20(up.x * s);    m.m21(up.y * s);    m.m22(up.z * s);
            m.m30(cx);          m.m31(cy);          m.m32(cz);

            batch.add(new BuiltinShader.Transform(m));
        }
    }
}

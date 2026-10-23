package com.example.reflective;

import com.meekdev.amnetic.client.instanced.BuiltinShader;
import com.meekdev.amnetic.client.instanced.InstanceBatch;
import com.meekdev.amnetic.client.instanced.InstanceRenderContext;
import com.meekdev.amnetic.client.instanced.InstancedMesh;
import com.meekdev.amnetic.client.instanced.InstancePhase;
import com.meekdev.amnetic.client.instanced.MeshData;
import com.meekdev.amnetic.client.instanced.RenderState;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

public final class DiamondReflectionDemo {

    private static final Identifier ID = Identifier.fromNamespaceAndPath("example", "diamond_reflect");
    private static final Identifier VSH = Identifier.fromNamespaceAndPath("example", "reflective/reflective");
    private static final Identifier FSH = Identifier.fromNamespaceAndPath("example", "reflective/reflective");

    private static final int RADIUS_XZ = 20;
    private static final int RADIUS_Y = 4;
    private static final int MAX_FACES = 2048;

    private DiamondReflectionDemo() {}

    public static void init() {
        InstancedMesh.builder(BuiltinShader.TRANSFORM)
                .geometry(MeshData.quad())
                .shaders(VSH, FSH)
                .writeGBuffer(true)
                .renderState(RenderState.builder()
                        .depthTest(true).depthWrite(true)
                        .blend(RenderState.BlendMode.NONE)
                        .backfaceCulling(false)
                        .build())
                .phase(InstancePhase.WORLD_LAST)
                .onRender(DiamondReflectionDemo::render)
                .register(ID);
    }

    private static void render(InstanceRenderContext ctx, InstanceBatch<BuiltinShader.Transform> batch) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        Vec3 cam = ctx.cameraPos();
        BlockPos center = BlockPos.containing(cam.x, cam.y, cam.z);

        int added = 0;
        for (BlockPos p : BlockPos.betweenClosed(
                center.offset(-RADIUS_XZ, -RADIUS_Y, -RADIUS_XZ),
                center.offset(RADIUS_XZ, RADIUS_Y, RADIUS_XZ))) {
            if (added >= MAX_FACES) break;
            if (!mc.level.getBlockState(p).is(Blocks.DIAMOND_BLOCK)) continue;
            if (!mc.level.getBlockState(p.above()).isAir()) continue;

            float cx = (float) (p.getX() + 0.5 - cam.x);
            float cy = (float) (p.getY() + 1.002 - cam.y);
            float cz = (float) (p.getZ() + 0.5 - cam.z);
            batch.add(new BuiltinShader.Transform(new Matrix4f().translation(cx, cy, cz)));
            added++;
        }
    }
}

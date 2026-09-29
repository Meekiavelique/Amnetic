package com.meekdev.amnetic.client.gbuffer.internal;

import com.meekdev.amnetic.client.compat.VanillaCompat;
import com.meekdev.amnetic.client.gbuffer.GBuffer;
import com.meekdev.amnetic.client.render.CameraSnapshot;
import com.meekdev.amnetic.client.render.GlState;
import com.meekdev.amnetic.client.render.ScreenPass;
import com.meekdev.amnetic.client.render.ShaderProgram;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryStack;

public final class EntityMotion extends ScreenPass {

    public static final EntityMotion INSTANCE = new EntityMotion();

    private static final Identifier VSH = Identifier.fromNamespaceAndPath("amnetic", "shaders/light/deferred.vsh");
    private static final Identifier FSH = Identifier.fromNamespaceAndPath("amnetic", "shaders/gbuffer/entity_motion.fsh");
    private static final int MAX = 32;
    private static final double PAD = 0.15;
    private static final double FLOOR = 0.05;

    private record Seen(Vec3 at, long frame) {}

    private record Moving(Entity entity, Vec3 at, Vec3 motion, double distance) {}

    private final Int2ObjectOpenHashMap<Seen> seen = new Int2ObjectOpenHashMap<>();
    private final List<Moving> moving = new ArrayList<>();
    private final float[] boxes = new float[MAX * 12];

    private EntityMotion() { super("EntityMotion"); }

    public void render() { dispatch(); }

    @Override protected boolean enabled() { return GBuffer.isEnabled(); }

    @Override protected ShaderProgram createProgram() { return new ShaderProgram(VSH, FSH); }

    @Override
    protected boolean record(CameraSnapshot cam, ShaderProgram program) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return false;
        long frame = GBuffer.frame();
        float partialTick = VanillaCompat.partialTick(false);
        moving.clear();
        for (Entity e : mc.level.entitiesForRendering()) {
            Vec3 at = e.getPosition(partialTick);
            Seen before = seen.put(e.getId(), new Seen(at, frame));
            if (before == null || before.frame != frame - 1 || GBuffer.drawsOwnMotion(e) || e == mc.getCameraEntity()) continue;
            Vec3 motion = at.subtract(before.at);
            if (motion.lengthSqr() < 1e-8) continue;
            moving.add(new Moving(e, at, motion, at.distanceToSqr(cam.eye)));
        }
        if (seen.size() > 4096) seen.values().removeIf(s -> s.frame != frame);
        else if ((frame & 255) == 0) seen.values().removeIf(s -> frame - s.frame > 2);
        if (moving.isEmpty()) return false;
        moving.sort((a, b) -> Double.compare(a.distance, b.distance));
        int count = Math.min(moving.size(), MAX);
        for (int i = 0; i < count; i++) {
            Moving m = moving.get(i);
            Vec3 shift = m.at.subtract(m.entity.position());
            AABB box = m.entity.getBoundingBox().move(shift).inflate(PAD);
            int o = i * 12;
            boxes[o] = (float) (box.minX - cam.eye.x);
            boxes[o + 1] = (float) (box.minY + PAD + FLOOR - cam.eye.y);
            boxes[o + 2] = (float) (box.minZ - cam.eye.z);
            boxes[o + 4] = (float) (box.maxX - cam.eye.x);
            boxes[o + 5] = (float) (box.maxY - cam.eye.y);
            boxes[o + 6] = (float) (box.maxZ - cam.eye.z);
            boxes[o + 8] = (float) m.motion.x;
            boxes[o + 9] = (float) m.motion.y;
            boxes[o + 10] = (float) m.motion.z;
        }

        GBufferTargets g = GBufferTargets.INSTANCE;
        int prevFbo = g.bind();
        if (prevFbo == -1) return false;
        int depthId = g.depthGlId();
        try {
            GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT, GL11.GL_TEXTURE_2D, 0, 0);
            try (MemoryStack s = MemoryStack.stackPush()) {
                GL30.glDrawBuffers(s.ints(GL30.GL_NONE, GL30.GL_NONE, GL30.GL_NONE, GL30.GL_NONE, GL30.GL_NONE,
                        GL30.GL_COLOR_ATTACHMENT5));
            }
            GlState.bindTexture(0, depthId);
            program.begin();
            program.setSampler("DepthSampler", 0);
            program.setMatrix4("InvViewProj", cam.invViewProj);
            program.setInt("ZeroToOne", cam.zeroToOne ? 1 : 0);
            program.setInt("BoxCount", count);
            program.setVec4Array("Boxes", boxes, count * 3);
            program.draw();
        } finally {
            GBufferTargets.drawAll();
            GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT, GL11.GL_TEXTURE_2D, depthId, 0);
            g.restore(prevFbo);
        }
        return true;
    }
}

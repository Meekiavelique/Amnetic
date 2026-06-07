package com.meekdev.amnetic.client.particle;

import com.meekdev.amnetic.client.instanced.InstanceBatch;
import com.meekdev.amnetic.client.instanced.InstancePhase;
import com.meekdev.amnetic.client.instanced.InstanceRenderContext;
import com.meekdev.amnetic.client.instanced.InstancedMesh;
import com.meekdev.amnetic.client.instanced.MeshData;
import com.meekdev.amnetic.client.instanced.internal.InstanceMeshRegistry;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector4f;

public final class ParticleSimulation {

    public static final float STEP = 1f / 60f;

    private static final float MAX_FRAME_DT = 0.1f;
    private static final int MAX_PENDING = 200_000;

    public static final ParticleSimulation INSTANCE = new ParticleSimulation();

    private final CopyOnWriteArrayList<ParticleMaterial> materials = new CopyOnWriteArrayList<>();
    private final ConcurrentLinkedQueue<SpawnRequest> intake = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<SpawnRequest> requestPool = new ConcurrentLinkedQueue<>();
    private final AtomicInteger pendingCount = new AtomicInteger();

    private final ArrayDeque<Particle> pool = new ArrayDeque<>();
    private final ParticleContext ctx = new ParticleContext();
    private final Vector4f clip = new Vector4f();
    private Particle[] visibleBuf = new Particle[256];
    private long[] sortBuf = new long[256];
    private float accumulator;
    private float simClock;
    private long lastNano;
    private int stepCount;
    private boolean prePhaseRegistered;
    private int meshCounter;

    private ParticleSimulation() {}


    synchronized void register(ParticleMaterial material) {
        materials.add(material);
        Identifier meshId = Identifier.fromNamespaceAndPath("amnetic", "particle_material_" + (meshCounter++));
        InstancedMesh.Builder<Particle> b = InstancedMesh.builder(ParticleLayout.LAYOUT, ParticleLayout.WRITER)
                .geometry(MeshData.texturedQuad())
                .shaders(material.billboardMode.vertexShader(), material.fragmentShaderId)
                .renderState(material.renderState())
                .phase(InstancePhase.WORLD_LAST)
                .onRender((rctx, batch) -> packMaterial(material, rctx, batch));
        if (material.textureId != null) b.texture(material.textureId);
        if (material.softDepth) b.extraSampler("DepthSampler", SceneDepth.ID, 1);
        b.register(meshId);

        if (!prePhaseRegistered) {
            InstanceMeshRegistry.INSTANCE.addPrePhaseCallback(InstancePhase.WORLD_LAST, this::preRender);
            prePhaseRegistered = true;
        }
    }


    SpawnRequest borrowRequest() {
        SpawnRequest r = requestPool.poll();
        return r != null ? r : new SpawnRequest();
    }

    void submit(SpawnRequest req) {
        if (pendingCount.get() >= MAX_PENDING) { recycle(req); return; }
        pendingCount.incrementAndGet();
        intake.add(req);
    }

    private void recycle(SpawnRequest req) {
        req.material = null;
        requestPool.offer(req);
    }

    public int liveCount() {
        int total = 0;
        for (ParticleMaterial m : materials) total += m.liveCount;
        return total;
    }


    private void preRender(InstanceRenderContext rctx) {
        drainIntake();

        Minecraft mc = Minecraft.getInstance();
        long now = System.nanoTime();
        if (mc.isPaused()) { lastNano = now; return; }

        float frameDt = lastNano == 0 ? 0f : (now - lastNano) / 1e9f;
        lastNano = now;
        if (frameDt > MAX_FRAME_DT) frameDt = MAX_FRAME_DT;

        simClock += frameDt;
        if (simClock > 3600f) simClock -= 3600f;
        ctx.set(simClock, rctx.world(), rctx.cameraPos());

        accumulator += frameDt;
        while (accumulator >= STEP) {
            step(STEP);
            accumulator -= STEP;
        }
    }

    private void drainIntake() {
        SpawnRequest req;
        while ((req = intake.poll()) != null) {
            pendingCount.decrementAndGet();
            addParticle(req);
            recycle(req);
        }
    }

    private void addParticle(SpawnRequest r) {
        ParticleMaterial m = r.material;
        if (m == null || m.liveCount >= m.liveCap) return;

        Particle p = pool.poll();
        if (p == null) p = new Particle();
        p.x = r.x; p.y = r.y; p.z = r.z;
        p.vx = r.vx; p.vy = r.vy; p.vz = r.vz;
        p.age = 0f; p.life = Math.max(r.life, 0.01f);
        p.size0 = r.size0; p.size1 = r.size1;
        p.r0 = r.r0; p.g0 = r.g0; p.b0 = r.b0;
        p.r1 = r.r1; p.g1 = r.g1; p.b1 = r.b1;
        p.a0 = r.a0; p.a1 = r.a1;
        p.gravity = r.gravity; p.drag = r.drag;
        p.dragStep = (float) Math.pow(Math.max(r.drag, 0f), STEP);
        p.rot = r.rot; p.rotSpeed = r.rotSpeed;
        p.seedX = r.seedX; p.seedY = r.seedY;
        p.sizeEasing = r.easing != null ? r.easing : Easing.EASE_OUT;
        p.brightness = 1f; p.lightTimer = 0;
        p.alive = true;

        if (m.liveCount == m.live.length) {
            m.live = Arrays.copyOf(m.live, Math.min(m.liveCap, m.live.length * 2));
        }
        m.live[m.liveCount++] = p;
    }

    private void step(float dt) {
        stepCount++;
        ClientLevel level = ctx.world();
        for (ParticleMaterial m : materials) {
            Affector[] affectors = m.affectors;
            Particle[] live = m.live;
            int n = m.liveCount;
            for (int i = 0; i < n; i++) {
                Particle p = live[i];
                for (Affector a : affectors) a.apply(p, dt, ctx);
                p.x += p.vx * dt; p.y += p.vy * dt; p.z += p.vz * dt;
                p.rot += p.rotSpeed * dt;
                p.age += dt;
                if (m.lightmap && level != null && --p.lightTimer <= 0) {
                    p.brightness = sampleLight(level, m, p);
                    p.lightTimer = m.lightRefreshSteps;
                }
                if (p.age >= p.life) {
                    p.alive = false;
                    p.clear();
                    pool.offer(p);
                    live[i] = live[n - 1];
                    live[n - 1] = null;
                    n--; i--;
                }
            }
            m.liveCount = n;
        }
    }

    private float sampleLight(ClientLevel level, ParticleMaterial m, Particle p) {
        BlockPos pos = BlockPos.containing(p.x, p.y, p.z);
        int block = level.getBrightness(LightLayer.BLOCK, pos);
        int sky = Math.max(0, level.getBrightness(LightLayer.SKY, pos) - level.getSkyDarken());
        float b = Math.max(block, sky) / 15f;
        return m.lightMin + (1f - m.lightMin) * b;
    }

    public boolean captureSceneDepth() {
        for (ParticleMaterial m : materials) {
            if (m.softDepth && m.liveCount > 0) {
                return SceneDepth.update();
            }
        }
        return false;
    }


    private void packMaterial(ParticleMaterial m, InstanceRenderContext rctx, InstanceBatch<Particle> batch) {
        int n = m.liveCount;
        if (n == 0) return;

        Vec3 cam = rctx.cameraPos();
        Matrix4fc proj = rctx.projectionMatrix();
        Matrix4f projView = new Matrix4f(proj).mul(rctx.viewMatrix());
        float m00 = proj.m00(), m11 = proj.m11();

        if (visibleBuf.length < n) visibleBuf = new Particle[Integer.highestOneBit(n) * 2];

        int vis = 0;
        for (int i = 0; i < n; i++) {
            Particle p = m.live[i];
            float cx = (float) (p.x - cam.x), cy = (float) (p.y - cam.y), cz = (float) (p.z - cam.z);
            float t = p.ageFraction();
            float size = lerp(p.size0, p.size1, p.sizeEasing.apply(t));
            if (!visible(projView, cx, cy, cz, size, m00, m11)) continue;

            float light = m.lightmap ? p.brightness : 1f;
            p.rCx = cx; p.rCy = cy; p.rCz = cz;
            p.rVx = (float) p.vx; p.rVy = (float) p.vy; p.rVz = (float) p.vz;
            p.rSize = size;
            p.rR = lerp(p.r0, p.r1, t) * light;
            p.rG = lerp(p.g0, p.g1, t) * light;
            p.rB = lerp(p.b0, p.b1, t) * light;
            p.rA = lerp(p.a0, p.a1, t);
            visibleBuf[vis++] = p;
        }
        if (vis == 0) return;

        if (m.sorted) {
            packSorted(batch, vis);
        } else {
            for (int i = 0; i < vis; i++) batch.add(visibleBuf[i]);
        }
    }

    private void packSorted(InstanceBatch<Particle> batch, int vis) {
        if (sortBuf.length < vis) sortBuf = new long[Integer.highestOneBit(vis) * 2];
        for (int i = 0; i < vis; i++) {
            Particle p = visibleBuf[i];
            float dist2 = p.rCx * p.rCx + p.rCy * p.rCy + p.rCz * p.rCz;
            sortBuf[i] = ((long) Float.floatToIntBits(dist2) << 32) | (i & 0xffffffffL);
        }
        Arrays.sort(sortBuf, 0, vis);
        for (int i = vis - 1; i >= 0; i--) {
            int idx = (int) (sortBuf[i] & 0xffffffffL);
            batch.add(visibleBuf[idx]);
        }
    }

    private boolean visible(Matrix4fc projView, float cx, float cy, float cz, float size, float m00, float m11) {
        projView.transform(cx, cy, cz, 1f, clip);
        float w = clip.w;
        float r = size * 0.5f * 1.4142f;
        if (w <= 0f) return w > -r;
        float mx = r * m00, my = r * m11;
        if (clip.x < -w - mx || clip.x > w + mx) return false;
        if (clip.y < -w - my || clip.y > w + my) return false;
        if (clip.z < -w - r  || clip.z > w + r)  return false;
        return true;
    }

    private static float lerp(float a, float b, float t) { return a + (b - a) * t; }

    static final class SpawnRequest {
        ParticleMaterial material;
        double x, y, z, vx, vy, vz;
        float life, size0, size1;
        float r0, g0, b0, r1, g1, b1;
        float a0, a1;
        float gravity, drag, rot, rotSpeed;
        float seedX, seedY;
        Easing easing;
    }
}